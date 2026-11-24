#!/usr/bin/env python3
# -*- coding: utf-8 -*-

"""
Script de importación, limpieza y migración del inventario textil desde Excel a Cloud Firestore.
Cumple con las especificaciones técnicas del proyecto (Sección 6).
"""

import os
import re
import sys
import json
import argparse
from typing import Dict, Any, List, Optional
import openpyxl

VALID_CATEGORIES = [
    "HILOS", "ENTRETELA", "TEJIDO", "FORRO",
    "BOTONES", "CREMALLERAS", "LIGUETA", "PERCHAS",
    "BOLSAS", "ETIQUETAS"
]

def parse_ancho_util(val: Any) -> tuple[Optional[float], str]:
    """
    Parsea campos mixtos como '1,6 ROLLO' o '0,80 PLANO D/A'.
    Retorna (anchoUtil: float, formatoAncho: str).
    """
    if val is None:
        return None, ""
    val_str = str(val).strip()
    if not val_str:
        return None, ""

    # Reemplazar comas por puntos en números
    # Buscar patrón de número decimal o entero al inicio o dentro del string
    match = re.search(r"(\d+(?:[.,]\d+)?)", val_str)
    if match:
        num_str = match.group(1).replace(",", ".")
        try:
            ancho_util = float(num_str)
        except ValueError:
            ancho_util = None
        # El resto del texto como formatoAncho
        formato = val_str[:match.start()] + val_str[match.end():]
        formato = re.sub(r"\s+", " ", formato).strip(" -/,")
        return ancho_util, formato
    else:
        return None, val_str

def parse_precio(val: Any) -> Optional[float]:
    """
    Extrae el valor numérico decimal eliminando textos como 'EUROS', '€', etc.
    Ej: '1,5 EUROS' -> 1.5
    """
    if val is None:
        return None
    val_str = str(val).strip()
    if not val_str:
        return None
    match = re.search(r"(\d+(?:[.,]\d+)?)", val_str)
    if match:
        num_str = match.group(1).replace(",", ".")
        try:
            return float(num_str)
        except ValueError:
            return None
    return None

def parse_float_safe(val: Any) -> Optional[float]:
    if val is None:
        return None
    val_str = str(val).strip().replace(",", ".")
    if not val_str:
        return None
    try:
        return float(val_str)
    except ValueError:
        return None

def parse_int_safe(val: Any) -> Optional[int]:
    if val is None:
        return None
    val_str = str(val).strip()
    if not val_str:
        return None
    try:
        # Puede venir como '10' o '10.0'
        return int(float(val_str.replace(",", ".")))
    except ValueError:
        return None

def clean_cell(val: Any) -> str:
    if val is None:
        return ""
    return str(val).strip()

def infer_unidad_medida(categoria: str) -> str:
    cat = categoria.upper()
    if cat == "HILOS":
        return "BOBINAS"
    elif cat in ("TEJIDO", "ENTRETELA", "FORRO"):
        return "METROS"
    else:
        return "UNIDADES"

def process_excel(file_path: str) -> List[Dict[str, Any]]:
    if not os.path.exists(file_path):
        raise FileNotFoundError(f"No se encontró el archivo Excel en: {file_path}")

    wb = openpyxl.load_workbook(file_path, data_only=True)
    all_articulos: List[Dict[str, Any]] = []

    categories_with_data = set()

    for sheet_name in wb.sheetnames:
        norm_sheet_name = sheet_name.strip().upper()
        if norm_sheet_name not in VALID_CATEGORIES:
            continue

        sheet = wb[sheet_name]
        rows = list(sheet.iter_rows(values_only=True))
        if len(rows) < 2:
            continue

        # Encabezados en fila 1
        raw_headers = [clean_cell(c).upper() for c in rows[0]]
        
        # Mapeo de índices
        col_map = {}
        for idx, h in enumerate(raw_headers):
            if "TIPO" in h and "ENTRETELA" in h:
                col_map["subtipo"] = idx
            elif "ARTÍCULO" in h or "ARTICULO" in h:
                col_map["articulo"] = idx
            elif "REFERENCIA PROVEEDOR" in h:
                col_map["referenciaProveedor"] = idx
            elif "REFERENCIA" in h and "referencia" not in col_map:
                col_map["referencia"] = idx
            elif "COLOR PROOVEDOR" in h or "COLOR PROVEEDOR" in h:
                col_map["colorProveedor"] = idx
            elif "COLOR" in h and "color" not in col_map:
                col_map["color"] = idx
            elif "PROVEEDOR" in h and "proveedor" not in col_map:
                col_map["proveedor"] = idx
            elif "PRECIO" in h:
                col_map["precioPorMetro"] = idx
            elif "ANCHO" in h:
                col_map["anchoUtil"] = idx
            elif "COMPOSIC" in h:
                col_map["composicion"] = idx
            elif "ROLLO" in h:
                col_map["numRollos"] = idx
            elif "METRO" in h:
                col_map["metrosTotales"] = idx
            elif "ALMAC" in h or "NICHO" in h:
                col_map["almacen"] = idx
            elif "ESTANTER" in h:
                col_map["estanteria"] = idx

        # Variables para forward-fill
        last_articulo = ""
        last_subtipo = ""

        # Filtrar filas de datos
        for row_idx, row in enumerate(rows[1:], start=2):
            # Si toda la fila está vacía, saltar
            if not any(c is not None and str(c).strip() != "" for c in row):
                continue

            # Extraer campos directos
            raw_articulo = clean_cell(row[col_map["articulo"]]) if "articulo" in col_map and col_map["articulo"] < len(row) else ""
            raw_subtipo = clean_cell(row[col_map["subtipo"]]) if "subtipo" in col_map and col_map["subtipo"] < len(row) else ""
            raw_referencia = clean_cell(row[col_map["referencia"]]) if "referencia" in col_map and col_map["referencia"] < len(row) else ""

            # Forward-fill de artículo y subtipo
            if raw_articulo:
                last_articulo = raw_articulo
            else:
                raw_articulo = last_articulo

            if raw_subtipo:
                last_subtipo = raw_subtipo
            else:
                raw_subtipo = last_subtipo

            # Si después de forward-fill no hay ni referencia ni artículo, saltar
            if not raw_referencia and not raw_articulo:
                continue

            raw_proveedor = clean_cell(row[col_map["proveedor"]]) if "proveedor" in col_map and col_map["proveedor"] < len(row) else ""
            raw_ref_prov = clean_cell(row[col_map["referenciaProveedor"]]) if "referenciaProveedor" in col_map and col_map["referenciaProveedor"] < len(row) else ""
            raw_color_prov = clean_cell(row[col_map["colorProveedor"]]) if "colorProveedor" in col_map and col_map["colorProveedor"] < len(row) else ""
            raw_color = clean_cell(row[col_map["color"]]) if "color" in col_map and col_map["color"] < len(row) else ""
            raw_comp = clean_cell(row[col_map["composicion"]]) if "composicion" in col_map and col_map["composicion"] < len(row) else ""

            # Parsing mixto de Ancho Útil
            raw_ancho = row[col_map["anchoUtil"]] if "anchoUtil" in col_map and col_map["anchoUtil"] < len(row) else None
            ancho_util, formato_ancho = parse_ancho_util(raw_ancho)

            # Parsing de Precio por Metro
            raw_precio = row[col_map["precioPorMetro"]] if "precioPorMetro" in col_map and col_map["precioPorMetro"] < len(row) else None
            precio_metro = parse_precio(raw_precio)

            # Rollos y metros
            raw_rollos = row[col_map["numRollos"]] if "numRollos" in col_map and col_map["numRollos"] < len(row) else None
            num_rollos = parse_int_safe(raw_rollos)

            raw_metros = row[col_map["metrosTotales"]] if "metrosTotales" in col_map and col_map["metrosTotales"] < len(row) else None
            metros_totales = parse_float_safe(raw_metros)

            # Ubicación
            almacen = clean_cell(row[col_map["almacen"]]) if "almacen" in col_map and col_map["almacen"] < len(row) else ""
            estanteria = clean_cell(row[col_map["estanteria"]]) if "estanteria" in col_map and col_map["estanteria"] < len(row) else ""

            # Columnas sin cabecera extra (como 'PADI') -> observaciones
            observaciones_list = []
            max_mapped = max(col_map.values()) if col_map else 0
            if len(row) > max_mapped + 1:
                for extra_col_idx in range(max_mapped + 1, len(row)):
                    extra_val = clean_cell(row[extra_col_idx])
                    if extra_val:
                        observaciones_list.append(extra_val)
            observaciones = ", ".join(observaciones_list)

            # Unidades de medida y cálculo de stock inicial
            unidad_medida = infer_unidad_medida(norm_sheet_name)
            if metros_totales is not None and metros_totales > 0:
                stock_actual = float(metros_totales)
            elif num_rollos is not None:
                stock_actual = float(num_rollos)
            else:
                stock_actual = 0.0

            articulo_doc = {
                "categoria": norm_sheet_name,
                "articulo": raw_articulo,
                "subtipo": raw_subtipo,
                "referencia": raw_referencia,
                "proveedor": raw_proveedor,
                "referenciaProveedor": raw_ref_prov,
                "colorProveedor": raw_color_prov,
                "color": raw_color,
                "composicion": raw_comp,
                "anchoUtil": ancho_util,
                "formatoAncho": formato_ancho,
                "unidadMedida": unidad_medida,
                "numRollos": num_rollos if num_rollos is not None else 0,
                "stockActual": stock_actual,
                "stockMinimo": 0.0,
                "precioPorMetro": precio_metro,
                "ubicacion": {
                    "almacen": almacen,
                    "estanteria": estanteria
                },
                "observaciones": observaciones,
                "imagenUrl": "",
                "activo": True
            }

            all_articulos.append(articulo_doc)
            categories_with_data.add(norm_sheet_name)

    # Para categorías que están en el catálogo pero sin filas de datos en el Excel
    # (porque no había stock en ese momento en el almacén), se añaden con stock 0.0
    for cat in VALID_CATEGORIES:
        if cat not in categories_with_data:
            nombre_cat = cat.capitalize()
            ref_cat = f"{cat[:3]}-000" if len(cat) >= 3 else f"{cat}-000"
            default_doc = {
                "categoria": cat,
                "articulo": nombre_cat,
                "subtipo": "",
                "referencia": ref_cat,
                "proveedor": "",
                "referenciaProveedor": "",
                "colorProveedor": "",
                "color": "",
                "composicion": "",
                "anchoUtil": None,
                "formatoAncho": "",
                "unidadMedida": infer_unidad_medida(cat),
                "numRollos": 0,
                "stockActual": 0.0,
                "stockMinimo": 0.0,
                "precioPorMetro": None,
                "ubicacion": {
                    "almacen": "",
                    "estanteria": ""
                },
                "observaciones": "Sin stock en inventario inicial (pendiente de reposición)",
                "imagenUrl": "",
                "activo": True
            }
            all_articulos.append(default_doc)

    return all_articulos

def main():
    parser = argparse.ArgumentParser(description="Importación y migración de inventario textil a Firestore.")
    parser.add_argument("--file", default=r"scripts/data/INVENTARIO PRODUCTOS ALMACEN-Ruth16ABRIL (1)(2).xlsx", help="Ruta al archivo Excel")
    parser.add_argument("--dry-run", action="store_true", help="Previsualizar datos limpios en consola sin escribir en Firestore")
    parser.add_argument("--service-account", default=None, help="Ruta al JSON de credenciales de Firebase Admin (serviceAccountKey.json)")
    args = parser.parse_args()

    print(f"--> Leyendo archivo: {args.file}")
    articulos = process_excel(args.file)
    print(f"--> Total de articulos procesados y limpios: {len(articulos)}")

    # Conteo por categoría
    categorias_count = {}
    for a in articulos:
        cat = a["categoria"]
        categorias_count[cat] = categorias_count.get(cat, 0) + 1

    print("\nResumen por Categoria:")
    for cat, count in sorted(categorias_count.items()):
        print(f"  - {cat}: {count} articulos")

    if args.dry_run or not args.service_account:
        print("\n[MODO DRY-RUN / SIMULACION]")
        print("Muestra de los primeros 3 articulos procesados en JSON:")
        print(json.dumps(articulos[:3], indent=2, ensure_ascii=False))
        if not args.dry_run:
            print("\nNOTA: Para escribir en Firestore proporcione --service-account ruta/al/serviceAccountKey.json")
        return

    # Si no es dry-run y hay credenciales
    try:
        import firebase_admin
        from firebase_admin import credentials, firestore

        cred = credentials.Certificate(args.service_account)
        firebase_admin.initialize_app(cred)
        db = firestore.client()

        print(f"\n--> Escribiendo {len(articulos)} articulos en Cloud Firestore...")
        batch = db.batch()
        batch_size = 0
        total_escritos = 0

        for art in articulos:
            doc_ref = db.collection("articulos").document()
            batch.set(doc_ref, art)
            batch_size += 1

            if batch_size >= 400:
                batch.commit()
                total_escritos += batch_size
                print(f"  ... {total_escritos} articulos guardados")
                batch = db.batch()
                batch_size = 0

        if batch_size > 0:
            batch.commit()
            total_escritos += batch_size

        print(f"--> CARGA COMPLETADA CON EXITO: {total_escritos} articulos creados en Firestore.")
    except Exception as e:
        print(f"ERROR al cargar en Firestore: {e}", file=sys.stderr)
        sys.exit(1)

if __name__ == "__main__":
    main()
