package com.ifpleonardo.controlstocktextil.data.model;

import java.io.Serializable;

/**
 * Objeto de valor que representa la ubicación física de un material en el centro educativo.
 * 
 * Estructura jerárquica:
 * - Almacén: sala, taller o zona (ej: "M2-S", "Taller Principal", "Armario A").
 * - Estantería: módulo, balda o nicho específico (ej: "2-1", "Baldas 3", "Caja 4").
 */
public class Ubicacion implements Serializable {

    private String almacen;                  // Identificador de sala o almacén
    private String estanteria;                // Módulo o número de estantería

    /**
     * Constructor vacío obligatorio para Firestore.
     */
    public Ubicacion() {
        this.almacen = "";
        this.estanteria = "";
    }

    public Ubicacion(String almacen, String estanteria) {
        this.almacen = almacen != null ? almacen : "";
        this.estanteria = estanteria != null ? estanteria : "";
    }

    public String getAlmacen() {
        return almacen;
    }

    public void setAlmacen(String almacen) {
        this.almacen = almacen;
    }

    public String getEstanteria() {
        return estanteria;
    }

    public void setEstanteria(String estanteria) {
        this.estanteria = estanteria;
    }

    public String getFormatoCompleto() {
        if ((almacen == null || almacen.trim().isEmpty()) && (estanteria == null || estanteria.trim().isEmpty())) {
            return "Sin ubicación asignada";
        }
        if (almacen != null && !almacen.trim().isEmpty() && (estanteria == null || estanteria.trim().isEmpty())) {
            return "Almacén: " + almacen;
        }
        if ((almacen == null || almacen.trim().isEmpty()) && estanteria != null && !estanteria.trim().isEmpty()) {
            return "Estantería: " + estanteria;
        }
        return "Almacén " + almacen + " · Estantería " + estanteria;
    }
}
