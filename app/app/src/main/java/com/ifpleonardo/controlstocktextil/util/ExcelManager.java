package com.ifpleonardo.controlstocktextil.util;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import androidx.core.content.FileProvider;

import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.WriteBatch;
import com.ifpleonardo.controlstocktextil.data.model.Articulo;
import com.ifpleonardo.controlstocktextil.data.model.Ubicacion;

import org.dhatim.fastexcel.Workbook;
import org.dhatim.fastexcel.Worksheet;
import org.dhatim.fastexcel.reader.Cell;
import org.dhatim.fastexcel.reader.ReadableWorkbook;
import org.dhatim.fastexcel.reader.Row;
import org.dhatim.fastexcel.reader.Sheet;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Gestor de Importación y Exportación de Inventario en formatos Excel (.xlsx) y CSV.
 * 
 * Medidas de seguridad y optimización aplicadas:
 * 1. Mitigación de inyección de fórmulas CSV (CWE-1236):
 *    - Los campos de texto que comiencen con '=', '+', '-' o '@' se escapan anteponiendo
 *      una comilla simple (') para neutralizar la ejecución de código o fórmulas DDE al abrir en Excel.
 * 2. Rendimiento móvil con FastExcel:
 *    - Se evita Apache POI (que depende de Java AWT incompatible con Android y consume excesiva RAM).
 *    - FastExcel lee y escribe en streaming con bajo consumo de memoria.
 * 3. Compatibilidad UTF-8 con BOM:
 *    - La exportación a CSV incluye la marca de orden de bytes (0xEF, 0xBB, 0xBF) para que
 *      Microsoft Excel en Windows reconozca automáticamente tildes y caracteres especiales (ñ).
 * 4. Escritura atómica por lotes (WriteBatch):
 *    - Los artículos importados se envían en bloques de hasta 400 documentos a Firestore.
 * 5. Distribución segura de archivos vía FileProvider:
 *    - Se generan URIs temporales con permisos estrictos de lectura (FLAG_GRANT_READ_URI_PERMISSION)
 *      sin exponer rutas absolutas del almacenamiento interno del dispositivo.
 */
public class ExcelManager {

    private static final List<String> CATEGORIAS_VALIDAS = Arrays.asList(
            "HILOS", "ENTRETELA", "TEJIDO", "FORRO",
            "BOTONES", "CREMALLERAS", "LIGUETA", "PERCHAS",
            "BOLSAS", "ETIQUETAS"
    );

    public interface ExportCallback {
        void onSuccess(File archivo);
        void onError(String error);
    }

    public interface ImportCallback {
        void onSuccess(int totalArticulos);
        void onError(String error);
    }

    // ==========================================
    // EXPORTACIÓN A EXCEL (.XLSX)
    // ==========================================

    public static void exportarExcel(Context context, List<Articulo> articulos, ExportCallback callback) {
        new Thread(() -> {
            try {
                String timestamp = new SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(new Date());
                File dir = new File(context.getCacheDir(), "exports");
                if (!dir.exists()) dir.mkdirs();

                File archivoExcel = new File(dir, "Inventario_Textil_" + timestamp + ".xlsx");
                OutputStream os = new FileOutputStream(archivoExcel);

                Workbook wb = new Workbook(os, "StockTextil", "1.0");
                Worksheet ws = wb.newWorksheet("INVENTARIO");

                // Cabeceras
                String[] headers = {
                        "CATEGORÍA", "ARTÍCULO", "SUBTIPO", "REFERENCIA", "STOCK ACTUAL",
                        "UNIDAD", "STOCK MÍNIMO", "ALMACÉN", "ESTANTERÍA", "Nº ROLLOS",
                        "ANCHO ÚTIL (M)", "FORMATO ANCHO", "PRECIO/METRO (€)", "PROVEEDOR",
                        "REF. PROVEEDOR", "COLOR", "CÓDIGO COLOR PROV.", "COMPOSICIÓN", "OBSERVACIONES"
                };

                for (int col = 0; col < headers.length; col++) {
                    ws.value(0, col, headers[col]);
                    ws.style(0, col).bold().set();
                }

                int rowIdx = 1;
                for (Articulo a : articulos) {
                    ws.value(rowIdx, 0, a.getCategoria() != null ? a.getCategoria() : "");
                    ws.value(rowIdx, 1, a.getArticulo() != null ? a.getArticulo() : "");
                    ws.value(rowIdx, 2, a.getSubtipo() != null ? a.getSubtipo() : "");
                    ws.value(rowIdx, 3, a.getReferencia() != null ? a.getReferencia() : "");
                    ws.value(rowIdx, 4, a.getStockActual());
                    ws.value(rowIdx, 5, a.getUnidadMedida() != null ? a.getUnidadMedida() : "");
                    ws.value(rowIdx, 6, a.getStockMinimo());

                    Ubicacion u = a.getUbicacion();
                    ws.value(rowIdx, 7, u != null && u.getAlmacen() != null ? u.getAlmacen() : "");
                    ws.value(rowIdx, 8, u != null && u.getEstanteria() != null ? u.getEstanteria() : "");

                    ws.value(rowIdx, 9, a.getNumRollos() != null ? a.getNumRollos() : 0);
                    if (a.getAnchoUtil() != null) ws.value(rowIdx, 10, a.getAnchoUtil());
                    ws.value(rowIdx, 11, a.getFormatoAncho() != null ? a.getFormatoAncho() : "");
                    if (a.getPrecioPorMetro() != null) ws.value(rowIdx, 12, a.getPrecioPorMetro());

                    ws.value(rowIdx, 13, a.getProveedor() != null ? a.getProveedor() : "");
                    ws.value(rowIdx, 14, a.getReferenciaProveedor() != null ? a.getReferenciaProveedor() : "");
                    ws.value(rowIdx, 15, a.getColor() != null ? a.getColor() : "");
                    ws.value(rowIdx, 16, a.getColorProveedor() != null ? a.getColorProveedor() : "");
                    ws.value(rowIdx, 17, a.getComposicion() != null ? a.getComposicion() : "");
                    ws.value(rowIdx, 18, a.getObservaciones() != null ? a.getObservaciones() : "");

                    rowIdx++;
                }

                wb.finish();
                os.close();

                callback.onSuccess(archivoExcel);

            } catch (Exception e) {
                callback.onError("Error al exportar Excel: " + e.getLocalizedMessage());
            }
        }).start();
    }

    // ==========================================
    // EXPORTACIÓN A CSV (SEPARADO POR ;)
    // ==========================================

    public static void exportarCsv(Context context, List<Articulo> articulos, ExportCallback callback) {
        new Thread(() -> {
            try {
                String timestamp = new SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(new Date());
                File dir = new File(context.getCacheDir(), "exports");
                if (!dir.exists()) dir.mkdirs();

                File archivoCsv = new File(dir, "Inventario_Textil_" + timestamp + ".csv");
                FileOutputStream fos = new FileOutputStream(archivoCsv);
                // Escribir BOM UTF-8 para que Excel en Windows reconozca acentos directamente
                fos.write(new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF});

                PrintWriter writer = new PrintWriter(new OutputStreamWriter(fos, StandardCharsets.UTF_8));

                writer.println("CATEGORIA;ARTICULO;SUBTIPO;REFERENCIA;STOCK_ACTUAL;UNIDAD;STOCK_MINIMO;ALMACEN;ESTANTERIA;PROVEEDOR;COLOR;OBSERVACIONES");

                DecimalFormat df = new DecimalFormat("#.##");

                for (Articulo a : articulos) {
                    Ubicacion u = a.getUbicacion();
                    String linea = escaparCsv(a.getCategoria()) + ";" +
                            escaparCsv(a.getArticulo()) + ";" +
                            escaparCsv(a.getSubtipo()) + ";" +
                            escaparCsv(a.getReferencia()) + ";" +
                            df.format(a.getStockActual()) + ";" +
                            escaparCsv(a.getUnidadMedida()) + ";" +
                            df.format(a.getStockMinimo()) + ";" +
                            escaparCsv(u != null ? u.getAlmacen() : "") + ";" +
                            escaparCsv(u != null ? u.getEstanteria() : "") + ";" +
                            escaparCsv(a.getProveedor()) + ";" +
                            escaparCsv(a.getColor()) + ";" +
                            escaparCsv(a.getObservaciones());
                    writer.println(linea);
                }

                writer.flush();
                writer.close();
                fos.close();

                callback.onSuccess(archivoCsv);

            } catch (Exception e) {
                callback.onError("Error al exportar CSV: " + e.getLocalizedMessage());
            }
        }).start();
    }

    private static String escaparCsv(String valor) {
        if (valor == null) return "";
        String limpio = valor.trim();
        // Mitigación de inyección de fórmulas CSV (CWE-1236)
        if (limpio.startsWith("=") || limpio.startsWith("+") || limpio.startsWith("-") || limpio.startsWith("@")) {
            limpio = "'" + limpio;
        }
        if (limpio.contains(";") || limpio.contains("\"") || limpio.contains("\n") || limpio.contains("\r")) {
            return "\"" + limpio.replace("\"", "\"\"") + "\"";
        }
        return limpio;
    }

    // ==========================================
    // IMPORTACIÓN MASIVA DESDE EXCEL (.XLSX)
    // ==========================================

    public static void importarExcel(Context context, InputStream is, ImportCallback callback) {
        new Thread(() -> {
            try {
                ReadableWorkbook wb = new ReadableWorkbook(is);
                List<Articulo> articulosExtraidos = new ArrayList<>();
                Set<String> categoriasConDatos = new HashSet<>();

                for (Sheet sheet : wb.getSheets().toList()) {
                    String normCat = sheet.getName().trim().toUpperCase();
                    if (!CATEGORIAS_VALIDAS.contains(normCat)) {
                        continue;
                    }

                    List<Row> rows = sheet.read();
                    if (rows == null || rows.size() < 2) continue;

                    // Mapeo de cabeceras en fila 0
                    Row headerRow = rows.get(0);
                    Map<String, Integer> colMap = new HashMap<>();
                    for (int i = 0; i < headerRow.getCellCount(); i++) {
                        Cell c = headerRow.getCell(i);
                        String h = c != null ? c.getText().trim().toUpperCase() : "";
                        if (h.contains("TIPO") && h.contains("ENTRETELA")) colMap.put("subtipo", i);
                        else if (h.contains("ARTÍCULO") || h.contains("ARTICULO")) colMap.put("articulo", i);
                        else if (h.contains("REFERENCIA PROVEEDOR")) colMap.put("refProv", i);
                        else if (h.contains("REFERENCIA") && !colMap.containsKey("referencia")) colMap.put("referencia", i);
                        else if (h.contains("COLOR PROOVEDOR") || h.contains("COLOR PROVEEDOR")) colMap.put("colorProv", i);
                        else if (h.contains("COLOR") && !colMap.containsKey("color")) colMap.put("color", i);
                        else if (h.contains("PROVEEDOR") && !colMap.containsKey("proveedor")) colMap.put("proveedor", i);
                        else if (h.contains("PRECIO")) colMap.put("precio", i);
                        else if (h.contains("ANCHO")) colMap.put("ancho", i);
                        else if (h.contains("COMPOSIC")) colMap.put("comp", i);
                        else if (h.contains("ROLLO")) colMap.put("rollos", i);
                        else if (h.contains("METRO")) colMap.put("metros", i);
                        else if (h.contains("ALMAC") || h.contains("NICHO")) colMap.put("almacen", i);
                        else if (h.contains("ESTANTER")) colMap.put("estanteria", i);
                    }

                    String lastArticulo = "";
                    String lastSubtipo = "";

                    for (int r = 1; r < rows.size(); r++) {
                        Row row = rows.get(r);
                        if (row == null) continue;

                        String rawArticulo = getCellText(row, colMap.get("articulo"));
                        String rawSubtipo = getCellText(row, colMap.get("subtipo"));
                        String rawRef = getCellText(row, colMap.get("referencia"));

                        // Forward-fill
                        if (!rawArticulo.isEmpty()) lastArticulo = rawArticulo;
                        else rawArticulo = lastArticulo;

                        if (!rawSubtipo.isEmpty()) lastSubtipo = rawSubtipo;
                        else rawSubtipo = lastSubtipo;

                        if (rawArticulo.isEmpty() && rawRef.isEmpty()) continue;

                        String proveedor = getCellText(row, colMap.get("proveedor"));
                        String refProv = getCellText(row, colMap.get("refProv"));
                        String colorProv = getCellText(row, colMap.get("colorProv"));
                        String color = getCellText(row, colMap.get("color"));
                        String comp = getCellText(row, colMap.get("comp"));

                        // Parsing ancho
                        String rawAncho = getCellText(row, colMap.get("ancho"));
                        Double anchoUtil = parseNumero(rawAncho);
                        String formatoAncho = extraerTextoRestante(rawAncho);

                        // Parsing precio
                        Double precio = parseNumero(getCellText(row, colMap.get("precio")));

                        // Rollos y metros
                        Integer numRollos = parseInt(getCellText(row, colMap.get("rollos")));
                        Double metros = parseNumero(getCellText(row, colMap.get("metros")));

                        String almacen = getCellText(row, colMap.get("almacen"));
                        String estanteria = getCellText(row, colMap.get("estanteria"));

                        String unidadMedida = "UNIDADES";
                        if ("HILOS".equals(normCat)) unidadMedida = "BOBINAS";
                        else if (Arrays.asList("TEJIDO", "ENTRETELA", "FORRO").contains(normCat)) unidadMedida = "METROS";

                        double stockActual = 0.0;
                        if (metros != null && metros > 0) stockActual = metros;
                        else if (numRollos != null) stockActual = numRollos.doubleValue();

                        Articulo art = new Articulo();
                        art.setCategoria(normCat);
                        art.setArticulo(rawArticulo);
                        art.setSubtipo(rawSubtipo);
                        art.setReferencia(rawRef);
                        art.setProveedor(proveedor);
                        art.setReferenciaProveedor(refProv);
                        art.setColor(color);
                        art.setColorProveedor(colorProv);
                        art.setComposicion(comp);
                        art.setAnchoUtil(anchoUtil);
                        art.setFormatoAncho(formatoAncho);
                        art.setUnidadMedida(unidadMedida);
                        art.setNumRollos(numRollos != null ? numRollos : 0);
                        art.setStockActual(stockActual);
                        art.setStockMinimo(0.0);
                        art.setPrecioPorMetro(precio);
                        art.setUbicacion(new Ubicacion(almacen, estanteria));
                        art.setActivo(true);

                        articulosExtraidos.add(art);
                        categoriasConDatos.add(normCat);
                    }
                }

                // Categorías vacías se crean con stock 0
                for (String cat : CATEGORIAS_VALIDAS) {
                    if (!categoriasConDatos.contains(cat)) {
                        Articulo base = new Articulo();
                        base.setCategoria(cat);
                        base.setArticulo(cat.substring(0, 1).toUpperCase() + cat.substring(1).toLowerCase());
                        base.setReferencia(cat.substring(0, Math.min(3, cat.length())) + "-000");
                        base.setStockActual(0.0);
                        base.setStockMinimo(0.0);
                        base.setUnidadMedida("HILOS".equals(cat) ? "BOBINAS" : (Arrays.asList("TEJIDO", "ENTRETELA", "FORRO").contains(cat) ? "METROS" : "UNIDADES"));
                        base.setActivo(true);
                        articulosExtraidos.add(base);
                    }
                }

                // Guardar en Firestore por lotes (Batches)
                FirebaseFirestore db = FirebaseFirestore.getInstance();
                WriteBatch batch = db.batch();
                int batchSize = 0;

                for (Articulo a : articulosExtraidos) {
                    batch.set(db.collection("articulos").document(), a);
                    batchSize++;
                    if (batchSize >= 400) {
                        Tasks.await(batch.commit());
                        batch = db.batch();
                        batchSize = 0;
                    }
                }

                if (batchSize > 0) {
                    Tasks.await(batch.commit());
                }

                callback.onSuccess(articulosExtraidos.size());

            } catch (Exception e) {
                callback.onError("Error al procesar archivo Excel: " + e.getLocalizedMessage());
            }
        }).start();
    }

    private static String getCellText(Row row, Integer colIdx) {
        if (colIdx == null || colIdx < 0 || colIdx >= row.getCellCount()) return "";
        Cell c = row.getCell(colIdx);
        return c != null && c.getText() != null ? c.getText().trim() : "";
    }

    private static Double parseNumero(String txt) {
        if (txt == null || txt.isEmpty()) return null;
        Matcher m = Pattern.compile("(\\d+(?:[.,]\\d+)?)").matcher(txt);
        if (m.find()) {
            try {
                return Double.parseDouble(m.group(1).replace(",", "."));
            } catch (NumberFormatException ignored) {}
        }
        return null;
    }

    private static String extraerTextoRestante(String txt) {
        if (txt == null || txt.isEmpty()) return "";
        return txt.replaceAll("(\\d+(?:[.,]\\d+)?)", "").replaceAll("[\\s,/-]+", " ").trim();
    }

    private static Integer parseInt(String txt) {
        Double d = parseNumero(txt);
        return d != null ? d.intValue() : null;
    }

    // ==========================================
    // COMPARTIR ARCHIVO GENERADO
    // ==========================================

    public static void compartirArchivo(Context context, File archivo, String mimeType) {
        Uri uri = FileProvider.getUriForFile(context, context.getPackageName() + ".fileprovider", archivo);

        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType(mimeType);
        intent.putExtra(Intent.EXTRA_STREAM, uri);
        intent.putExtra(Intent.EXTRA_SUBJECT, "Inventario Textil - " + archivo.getName());
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

        context.startActivity(Intent.createChooser(intent, "Compartir archivo de inventario"));
    }
}
