package com.ifpleonardo.controlstocktextil.data.model;

import com.google.firebase.firestore.DocumentId;

import java.io.Serializable;

/**
 * Modelo de datos representativo de una usuaria del sistema (profesora o administradora).
 * 
 * Políticas de autorización:
 * - rol "ADMIN": Jefa de departamento con privilegios completos de gestión de personal, altas,
 *   bajas y exportaciones/importaciones masivas de Excel.
 * - rol "PROFESORA": Docente con acceso al catálogo en tiempo real, registro de movimientos,
 *   etiquetas QR y consulta del historial.
 * - activo: Bandera booleana de baja lógica. Si es false, se deniega el inicio de sesión.
 */
public class Usuario implements Serializable {

    @DocumentId
    private String id;                       // UID de Firebase Authentication coincidente con el documento
    private String nombre;                   // Nombre y apellidos de la docente
    private String email;                    // Correo electrónico institucional
    private String rol;                      // Rol de autorización: "ADMIN" o "PROFESORA"
    private boolean activo;                  // Estado operativo de la cuenta (true = habilitada)
    private String fotoUrl;                  // URL de la fotografía de perfil alojada en Cloudinary

    /**
     * Constructor vacío obligatorio para Firestore.
     */
    public Usuario() {
    }

    public Usuario(String id, String nombre, String email, String rol, boolean activo) {
        this(id, nombre, email, rol, activo, null);
    }

    public Usuario(String id, String nombre, String email, String rol, boolean activo, String fotoUrl) {
        this.id = id;
        this.nombre = nombre;
        this.email = email;
        this.rol = rol;
        this.activo = activo;
        this.fotoUrl = fotoUrl;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getRol() {
        return rol;
    }

    public void setRol(String rol) {
        this.rol = rol;
    }

    public boolean isActivo() {
        return activo;
    }

    public void setActivo(boolean activo) {
        this.activo = activo;
    }

    public boolean isAdmin() {
        return "ADMIN".equalsIgnoreCase(rol);
    }

    public boolean isProfesor() {
        return "PROFESORA".equalsIgnoreCase(rol)
                || "PROFESOR/A".equalsIgnoreCase(rol)
                || "PROFESOR".equalsIgnoreCase(rol);
    }

    public boolean isLector() {
        return !isAdmin() && !isProfesor();
    }

    /**
     * Solo ADMIN y PROFESOR/A pueden modificar stock o subir/editar fotos de artículos.
     * Los usuarios recién registrados (LECTOR) solo tienen permisos de lectura y descarga.
     */
    public boolean puedeModificarStock() {
        return isAdmin() || isProfesor();
    }

    public String getRolVisual() {
        if (isAdmin()) return "ADMIN";
        if (isProfesor()) return "PROFESOR/A";
        return "LECTOR";
    }

    public static String mapRolVisualARolFirestore(String rolVisual) {
        if (rolVisual == null) return "LECTOR";
        String norm = rolVisual.trim().toUpperCase();
        if ("ADMIN".equals(norm)) return "ADMIN";
        if ("PROFESOR/A".equals(norm) || "PROFESORA".equals(norm) || "PROFESOR".equals(norm)) return "PROFESORA";
        return "LECTOR";
    }

    public String getFotoUrl() {
        return fotoUrl;
    }

    public void setFotoUrl(String fotoUrl) {
        this.fotoUrl = fotoUrl;
    }
}
