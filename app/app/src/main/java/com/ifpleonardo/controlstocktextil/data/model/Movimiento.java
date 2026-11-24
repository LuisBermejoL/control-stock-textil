package com.ifpleonardo.controlstocktextil.data.model;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentId;
import com.google.firebase.firestore.ServerTimestamp;

import java.io.Serializable;
import java.util.Date;

/**
 * Modelo de datos representativo de una operación en el Historial de Movimientos de Almacén.
 * 
 * Inmutabilidad y Auditoría:
 * Cada entrada, salida, consumo, préstamo, devolución o ajuste genera una instancia de este
 * modelo dentro de una transacción atómica. Por reglas de seguridad en Firestore, este registro
 * no puede ser modificado ni eliminado jamás, garantizando la trazabilidad de inventario.
 */
public class Movimiento implements Serializable {

    @DocumentId
    private String id;                       // ID del movimiento generado en Firestore
    private String articuloId;               // ID del artículo afectado
    private String articuloNombre;           // Nombre completo del artículo en el momento del movimiento
    private String categoria;                // Categoría del material
    private String tipo;                     // ENTRADA, SALIDA_CONSUMO, PRESTAMO, DEVOLUCION, AJUSTE
    private Double cantidad;                 // Cantidad de material implicada en la operación
    private Double stockResultante;          // Saldo exacto de existencias tras aplicar la transacción
    private String usuarioId;                // UID de la profesora o administradora que ejecutó la acción
    private String usuarioNombre;            // Nombre de la usuaria responsable para visualización rápida

    @ServerTimestamp
    private Date fecha;                      // Marca de tiempo fidedigna generada por el servidor de Google
    private String observaciones;            // Motivo del movimiento o notas explicativas

    /**
     * Constructor vacío obligatorio para la serialización y deserialización de Firestore.
     */
    public Movimiento() {
    }

    public Movimiento(String articuloId, String articuloNombre, String categoria, String tipo,
                      Double cantidad, Double stockResultante, String usuarioId, String usuarioNombre,
                      String observaciones) {
        this.articuloId = articuloId;
        this.articuloNombre = articuloNombre;
        this.categoria = categoria;
        this.tipo = tipo;
        this.cantidad = cantidad;
        this.stockResultante = stockResultante;
        this.usuarioId = usuarioId;
        this.usuarioNombre = usuarioNombre;
        this.observaciones = observaciones;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getArticuloId() {
        return articuloId;
    }

    public void setArticuloId(String articuloId) {
        this.articuloId = articuloId;
    }

    public String getArticuloNombre() {
        return articuloNombre;
    }

    public void setArticuloNombre(String articuloNombre) {
        this.articuloNombre = articuloNombre;
    }

    public String getCategoria() {
        return categoria;
    }

    public void setCategoria(String categoria) {
        this.categoria = categoria;
    }

    public String getTipo() {
        return tipo;
    }

    public void setTipo(String tipo) {
        this.tipo = tipo;
    }

    public Double getCantidad() {
        return cantidad;
    }

    public void setCantidad(Double cantidad) {
        this.cantidad = cantidad;
    }

    public Double getStockResultante() {
        return stockResultante;
    }

    public void setStockResultante(Double stockResultante) {
        this.stockResultante = stockResultante;
    }

    public String getUsuarioId() {
        return usuarioId;
    }

    public void setUsuarioId(String usuarioId) {
        this.usuarioId = usuarioId;
    }

    public String getUsuarioNombre() {
        return usuarioNombre;
    }

    public void setUsuarioNombre(String usuarioNombre) {
        this.usuarioNombre = usuarioNombre;
    }

    public Date getFecha() {
        return fecha;
    }

    public void setFecha(Date fecha) {
        this.fecha = fecha;
    }

    public String getObservaciones() {
        return observaciones;
    }

    public void setObservaciones(String observaciones) {
        this.observaciones = observaciones;
    }
}
