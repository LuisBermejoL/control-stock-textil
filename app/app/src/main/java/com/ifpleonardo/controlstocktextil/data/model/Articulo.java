package com.ifpleonardo.controlstocktextil.data.model;

import com.google.firebase.firestore.DocumentId;

import java.io.Serializable;

/**
 * Modelo de datos representativo de un Artículo en el inventario textil.
 * 
 * Soportado por Cloud Firestore como POJO serializable.
 * Representa consumibles continuos (telas, forros y entretelas en metros con decimales),
 * unidades discretas (hilos por bobina, botones, cremalleras, bolsas) y herramientas.
 */
public class Articulo implements Serializable {

    @DocumentId
    private String id;                       // ID único del documento en Cloud Firestore
    private String categoria;                // Categoría normalizada (HILOS, TEJIDO, BOTONES, etc.)
    private String articulo;                 // Nombre principal del artículo (ej: Torzal, Popelín)
    private String subtipo;                  // Subtipo o variante (ej: Fina, Elástica)
    private String referencia;               // Código o referencia interna del instituto
    private String proveedor;                // Nombre de la empresa o proveedor distribuidor
    private String referenciaProveedor;      // Código comercial asignado por el fabricante
    private String colorProveedor;           // Código o número de tintada del fabricante
    private String color;                    // Denominación común del color (ej: Marrón, Negro)
    private String composicion;              // Composición de la fibra textil (ej: 100% Poliéster)
    private Double anchoUtil;                // Ancho útil de la pieza en metros (ej: 1.50)
    private String formatoAncho;             // Formato de distribución (ej: Rollo, Doble ancho)
    private String unidadMedida;             // Unidad de conteo: METROS, BOBINAS o UNIDADES
    private Integer numRollos;               // Número de rollos o piezas disponibles en almacén
    private Double stockActual;              // Cantidad física actual disponible en almacén
    private Double stockMinimo;              // Umbral mínimo de existencias para disparo de alertas
    private Double precioPorMetro;           // Coste unitario en euros por metro o unidad
    private Ubicacion ubicacion;             // Objeto con el almacén y estantería de guardado
    private String observaciones;            // Notas adicionales o instrucciones pedagógicas
    private String imagenUrl;                // URL de la fotografía del tejido alojada en Cloudinary
    private boolean activo;                  // Estado de borrado lógico (true = visible en catálogo)

    /**
     * Constructor vacío obligatorio para la deserialización automática de Firestore.
     */
    public Articulo() {
        this.activo = true;
        this.stockMinimo = 0.0;
        this.stockActual = 0.0;
        this.numRollos = 0;
        this.ubicacion = new Ubicacion();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getCategoria() {
        return categoria;
    }

    public void setCategoria(String categoria) {
        this.categoria = categoria;
    }

    public String getArticulo() {
        return articulo;
    }

    public void setArticulo(String articulo) {
        this.articulo = articulo;
    }

    public String getSubtipo() {
        return subtipo;
    }

    public void setSubtipo(String subtipo) {
        this.subtipo = subtipo;
    }

    public String getReferencia() {
        return referencia;
    }

    public void setReferencia(String referencia) {
        this.referencia = referencia;
    }

    public String getProveedor() {
        return proveedor;
    }

    public void setProveedor(String proveedor) {
        this.proveedor = proveedor;
    }

    public String getReferenciaProveedor() {
        return referenciaProveedor;
    }

    public void setReferenciaProveedor(String referenciaProveedor) {
        this.referenciaProveedor = referenciaProveedor;
    }

    public String getColorProveedor() {
        return colorProveedor;
    }

    public void setColorProveedor(String colorProveedor) {
        this.colorProveedor = colorProveedor;
    }

    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        this.color = color;
    }

    public String getComposicion() {
        return composicion;
    }

    public void setComposicion(String composicion) {
        this.composicion = composicion;
    }

    public Double getAnchoUtil() {
        return anchoUtil;
    }

    public void setAnchoUtil(Double anchoUtil) {
        this.anchoUtil = anchoUtil;
    }

    public String getFormatoAncho() {
        return formatoAncho;
    }

    public void setFormatoAncho(String formatoAncho) {
        this.formatoAncho = formatoAncho;
    }

    public String getUnidadMedida() {
        return unidadMedida;
    }

    public void setUnidadMedida(String unidadMedida) {
        this.unidadMedida = unidadMedida;
    }

    public Integer getNumRollos() {
        return numRollos;
    }

    public void setNumRollos(Integer numRollos) {
        this.numRollos = numRollos;
    }

    public Double getStockActual() {
        return stockActual != null ? stockActual : 0.0;
    }

    public void setStockActual(Double stockActual) {
        this.stockActual = stockActual;
    }

    public Double getStockMinimo() {
        return stockMinimo != null ? stockMinimo : 0.0;
    }

    public void setStockMinimo(Double stockMinimo) {
        this.stockMinimo = stockMinimo;
    }

    public Double getPrecioPorMetro() {
        return precioPorMetro;
    }

    public void setPrecioPorMetro(Double precioPorMetro) {
        this.precioPorMetro = precioPorMetro;
    }

    public Ubicacion getUbicacion() {
        return ubicacion != null ? ubicacion : new Ubicacion();
    }

    public void setUbicacion(Ubicacion ubicacion) {
        this.ubicacion = ubicacion;
    }

    public String getObservaciones() {
        return observaciones;
    }

    public void setObservaciones(String observaciones) {
        this.observaciones = observaciones;
    }

    public String getImagenUrl() {
        return imagenUrl;
    }

    public void setImagenUrl(String imagenUrl) {
        this.imagenUrl = imagenUrl;
    }

    public boolean isActivo() {
        return activo;
    }

    public void setActivo(boolean activo) {
        this.activo = activo;
    }

    public boolean isBajoMinimos() {
        return stockActual != null && stockMinimo != null && stockMinimo > 0 && stockActual <= stockMinimo;
    }

    public String getNombreCompleto() {
        StringBuilder sb = new StringBuilder();
        if (articulo != null && !articulo.trim().isEmpty()) {
            sb.append(articulo);
        }
        if (subtipo != null && !subtipo.trim().isEmpty()) {
            if (sb.length() > 0) sb.append(" - ");
            sb.append(subtipo);
        }
        if (color != null && !color.trim().isEmpty()) {
            if (sb.length() > 0) sb.append(" (").append(color).append(")");
            else sb.append(color);
        }
        return sb.length() > 0 ? sb.toString() : (referencia != null ? referencia : "Sin nombre");
    }
}
