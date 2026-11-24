package com.ifpleonardo.controlstocktextil.data.repository;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.ifpleonardo.controlstocktextil.data.model.Usuario;

/**
 * Repositorio central de autenticación y control de sesiones.
 * 
 * Responsabilidades de seguridad:
 * 1. Control estricto de sesiones mediante Firebase Authentication (Email y Contraseña).
 * 2. Validación y carga del perfil de usuario desde Cloud Firestore (/usuarios/{uid}).
 * 3. Verificación de cuenta activa (bloquea el acceso a usuarias dadas de baja lógica).
 * 4. Registro seguro de profesoras:
 *    - Asigna obligatoriamente el rol 'PROFESORA' y 'activo = true'.
 *    - Rollback automático (elimina la cuenta de Auth) si falla la escritura en Firestore.
 *    - Auto-recuperación de cuentas huérfanas producidas por cortes de conexión previos.
 * 5. Cierre seguro de sesión (logout): desconecta todos los listeners reactivos de Firestore
 *    antes de invalidar el token de autenticación para evitar excepciones de permisos.
 */
public class AuthRepository {

    private static AuthRepository instance;
    private final FirebaseAuth auth;
    private final FirebaseFirestore db;

    // LiveData reactivo que expone el usuario logueado actualmente a toda la aplicación
    private final MutableLiveData<Usuario> usuarioActualLiveData = new MutableLiveData<>();

    private AuthRepository() {
        this.auth = FirebaseAuth.getInstance();
        this.db = FirebaseFirestore.getInstance();
    }

    /**
     * Acceso Singleton sincronizado para garantizar una única instancia en toda la app.
     */
    public static synchronized AuthRepository getInstance() {
        if (instance == null) {
            instance = new AuthRepository();
        }
        return instance;
    }

    /**
     * Comprueba si existe una sesión válida almacenada localmente en el dispositivo.
     */
    public boolean isUserLoggedIn() {
        return auth.getCurrentUser() != null;
    }

    /**
     * Obtiene el UID de Firebase Authentication del usuario actual (o null si no hay sesión).
     */
    public String getCurrentUid() {
        FirebaseUser user = auth.getCurrentUser();
        return user != null ? user.getUid() : null;
    }

    /**
     * Devuelve el LiveData del perfil del usuario actual para observación en la UI (MVVM).
     */
    public LiveData<Usuario> getUsuarioActualLiveData() {
        return usuarioActualLiveData;
    }

    /**
     * Interfaz de callback para operaciones asíncronas de autenticación.
     */
    public interface AuthCallback {
        void onSuccess(Usuario usuario);
        void onError(String error);
    }

    /**
     * Inicia sesión con correo y contraseña.
     * Al autenticar en Firebase Auth, descarga inmediatamente el perfil desde Firestore
     * y comprueba si la cuenta sigue activa antes de conceder acceso a la app.
     */
    public void login(String email, String password, AuthCallback callback) {
        if (email == null || email.trim().isEmpty() || password == null || password.trim().isEmpty()) {
            callback.onError("Por favor, introduce email y contraseña.");
            return;
        }

        auth.signInWithEmailAndPassword(email.trim(), password)
                .addOnSuccessListener(authResult -> {
                    FirebaseUser firebaseUser = authResult.getUser();
                    if (firebaseUser != null) {
                        cargarPerfilUsuario(firebaseUser.getUid(), callback);
                    } else {
                        callback.onError("Error al obtener la sesión del usuario.");
                    }
                })
                .addOnFailureListener(e -> callback.onError("Credenciales incorrectas: " + e.getLocalizedMessage()));
    }

    public void cargarPerfilUsuario(String uid, AuthCallback callback) {
        db.collection("usuarios").document(uid).get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        Usuario usuario = documentSnapshot.toObject(Usuario.class);
                        if (usuario != null) {
                            usuario.setId(documentSnapshot.getId());

                            // Validación de cuenta activa (jubilación/desactivación)
                            if (!usuario.isActivo()) {
                                auth.signOut();
                                usuarioActualLiveData.postValue(null);
                                callback.onError("Esta cuenta ha sido desactivada por administración.");
                                return;
                            }

                            usuarioActualLiveData.postValue(usuario);
                            callback.onSuccess(usuario);
                        } else {
                            callback.onError("El perfil del usuario no tiene un formato válido.");
                        }
                    } else {
                        // Si el usuario no tiene documento en Firestore aún
                        auth.signOut();
                        callback.onError("El usuario no tiene un perfil asignado en el sistema.");
                    }
                })
                .addOnFailureListener(e -> callback.onError("Error al cargar datos del usuario: " + e.getLocalizedMessage()));
    }

    public void logout() {
        UsuarioRepository.getInstance().detenerEscuchaUsuarios();
        InventarioRepository.getInstance().detenerEscuchaInventario();
        auth.signOut();
        usuarioActualLiveData.postValue(null);
    }

    public void registrarProfesora(String nombre, String email, String password, AuthCallback callback) {
        if (nombre == null || nombre.trim().isEmpty() || email == null || email.trim().isEmpty() || password == null || password.trim().isEmpty()) {
            callback.onError("Por favor, completa todos los campos.");
            return;
        }

        if (password.length() < 6) {
            callback.onError("La contraseña debe tener al menos 6 caracteres.");
            return;
        }

        String emailLimpio = email.trim();
        String passLimpio = password;
        String nombreLimpio = nombre.trim();

        auth.createUserWithEmailAndPassword(emailLimpio, passLimpio)
                .addOnSuccessListener(authResult -> {
                    FirebaseUser firebaseUser = authResult.getUser();
                    if (firebaseUser != null) {
                        crearDocumentoProfesora(firebaseUser, nombreLimpio, emailLimpio, true, callback);
                    } else {
                        callback.onError("No se pudo obtener el usuario registrado.");
                    }
                })
                .addOnFailureListener(e -> {
                    if (e instanceof com.google.firebase.auth.FirebaseAuthUserCollisionException ||
                            (e.getMessage() != null && e.getMessage().toLowerCase().contains("already in use"))) {
                        // El correo ya existe en Auth. Comprobar si era una cuenta huérfana de un intento previo
                        recuperarCuentaHuerfanaOReportar(emailLimpio, passLimpio, nombreLimpio, callback);
                    } else if (e instanceof com.google.firebase.auth.FirebaseAuthWeakPasswordException) {
                        callback.onError("La contraseña elegida es demasiado débil. Usa al menos 6 caracteres con letras y números.");
                    } else if (e instanceof com.google.firebase.auth.FirebaseAuthInvalidCredentialsException) {
                        callback.onError("El formato del correo electrónico no es válido.");
                    } else {
                        callback.onError("Error al registrar cuenta: " + e.getLocalizedMessage());
                    }
                });
    }

    private void crearDocumentoProfesora(FirebaseUser firebaseUser, String nombre, String email, boolean borrarAuthSiFalla, AuthCallback callback) {
        Usuario nuevoUsuario = new Usuario();
        nuevoUsuario.setId(firebaseUser.getUid());
        nuevoUsuario.setNombre(nombre);
        nuevoUsuario.setEmail(email);
        nuevoUsuario.setRol("PROFESORA");
        nuevoUsuario.setActivo(true);

        db.collection("usuarios").document(firebaseUser.getUid()).set(nuevoUsuario)
                .addOnSuccessListener(aVoid -> {
                    usuarioActualLiveData.postValue(nuevoUsuario);
                    callback.onSuccess(nuevoUsuario);
                })
                .addOnFailureListener(e -> {
                    if (borrarAuthSiFalla) {
                        // Limpiar usuario en Auth para evitar cuentas huérfanas
                        firebaseUser.delete();
                        auth.signOut();
                    }
                    String msg = e.getLocalizedMessage();
                    if (msg != null && (msg.contains("PERMISSION_DENIED") || msg.contains("Missing or insufficient permissions"))) {
                        callback.onError("Permiso denegado en la base de datos (PERMISSION_DENIED).\n\n" +
                                "Es necesario publicar las reglas de firestore.rules en la consola de Firebase (Firestore Database > pestaña Reglas > Publicar).");
                    } else {
                        callback.onError("Error al guardar perfil de profesora: " + msg);
                    }
                });
    }

    private void recuperarCuentaHuerfanaOReportar(String email, String password, String nombre, AuthCallback callback) {
        auth.signInWithEmailAndPassword(email, password)
                .addOnSuccessListener(authResult -> {
                    FirebaseUser fUser = authResult.getUser();
                    if (fUser != null) {
                        db.collection("usuarios").document(fUser.getUid()).get()
                                .addOnSuccessListener(doc -> {
                                    if (doc.exists()) {
                                        // La cuenta ya existe completamente en Firestore
                                        Usuario u = doc.toObject(Usuario.class);
                                        if (u != null) {
                                            u.setId(doc.getId());
                                            usuarioActualLiveData.postValue(u);
                                            callback.onSuccess(u);
                                        } else {
                                            auth.signOut();
                                            callback.onError("Ya existe una cuenta con este correo. Inicia sesión normalmente.");
                                        }
                                    } else {
                                        // Cuenta huérfana de un intento anterior: crear el documento ahora
                                        crearDocumentoProfesora(fUser, nombre, email, false, callback);
                                    }
                                })
                                .addOnFailureListener(e -> {
                                    auth.signOut();
                                    callback.onError("Error al verificar perfil existente: " + e.getLocalizedMessage());
                                });
                    } else {
                        callback.onError("Este correo ya está registrado en el sistema. Inicia sesión en la pantalla de login.");
                    }
                })
                .addOnFailureListener(e -> {
                    callback.onError("Este correo electrónico ya está registrado con otra contraseña.\n\n" +
                            "Si ya te habías registrado, introduce tu contraseña habitual en la pantalla de login.");
                });
    }

    public interface OperationCallback {
        void onSuccess();
        void onError(String error);
    }

    public void actualizarPerfil(String nuevoNombre, String nuevoPassword, OperationCallback callback) {
        FirebaseUser fUser = auth.getCurrentUser();
        if (fUser == null) {
            callback.onError("No hay sesión activa.");
            return;
        }

        if (nuevoNombre == null || nuevoNombre.trim().isEmpty()) {
            callback.onError("El nombre no puede estar vacío.");
            return;
        }

        db.collection("usuarios").document(fUser.getUid())
                .update("nombre", nuevoNombre.trim())
                .addOnSuccessListener(aVoid -> {
                    Usuario actual = usuarioActualLiveData.getValue();
                    if (actual != null) {
                        actual.setNombre(nuevoNombre.trim());
                        usuarioActualLiveData.postValue(actual);
                    }

                    if (nuevoPassword != null && !nuevoPassword.trim().isEmpty()) {
                        if (nuevoPassword.length() < 6) {
                            callback.onError("El nombre se actualizó, pero la contraseña debe tener al menos 6 caracteres.");
                            return;
                        }
                        fUser.updatePassword(nuevoPassword)
                                .addOnSuccessListener(aVoid1 -> callback.onSuccess())
                                .addOnFailureListener(e -> callback.onError("Nombre actualizado, pero error en contraseña: " + e.getLocalizedMessage()));
                    } else {
                        callback.onSuccess();
                    }
                })
                .addOnFailureListener(e -> callback.onError("Error al actualizar perfil: " + e.getLocalizedMessage()));
    }
}
