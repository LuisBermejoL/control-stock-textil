package com.ifpleonardo.controlstocktextil.data.repository;

import android.content.Context;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.ifpleonardo.controlstocktextil.data.model.Usuario;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Repositorio para la administración y consulta de las cuentas de usuarias/profesoras.
 * 
 * Principios de seguridad aplicados:
 * 1. Acceso restringido al rol ADMIN: Solo la jefa de departamento puede activar la escucha
 *    completa de profesoras o editar roles y estados.
 * 2. Desactivación lógica (Soft Delete): Las profesoras nunca se eliminan físicamente de la base
 *    de datos para garantizar la integridad referencial y auditoría del historial de movimientos.
 * 3. Filtrado de errores residuales en cierre de sesión: Ignora desconexiones cuando auth.getCurrentUser() es null.
 */
public class UsuarioRepository {

    private static UsuarioRepository instance;
    private final FirebaseFirestore db;
    private ListenerRegistration usuariosListener;

    private final MutableLiveData<List<Usuario>> usuariosLiveData = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<String> errorLiveData = new MutableLiveData<>();
    private final MutableLiveData<Boolean> cargandoLiveData = new MutableLiveData<>(false);

    public interface OperationCallback {
        void onSuccess();
        void onError(String error);
    }

    private UsuarioRepository() {
        this.db = FirebaseFirestore.getInstance();
    }

    public static synchronized UsuarioRepository getInstance() {
        if (instance == null) {
            instance = new UsuarioRepository();
        }
        return instance;
    }

    public LiveData<List<Usuario>> getUsuariosLiveData() {
        return usuariosLiveData;
    }

    public LiveData<String> getErrorLiveData() {
        return errorLiveData;
    }

    public LiveData<Boolean> getCargandoLiveData() {
        return cargandoLiveData;
    }

    /**
     * Inicia la escucha en tiempo real de la colección /usuarios.
     * Solo debe invocarse cuando un usuario con rol ADMIN accede a la pestaña de profesoras.
     */
    public void iniciarEscuchaUsuarios() {
        if (usuariosListener != null) return;
        if (FirebaseAuth.getInstance().getCurrentUser() == null) return;

        cargandoLiveData.postValue(true);

        usuariosListener = db.collection("usuarios").addSnapshotListener((value, error) -> {
            cargandoLiveData.postValue(false);

            if (error != null) {
                // Si el usuario acaba de cerrar sesión, ignorar el error de permisos residual
                if (FirebaseAuth.getInstance().getCurrentUser() == null) {
                    return;
                }
                errorLiveData.postValue("Error al sincronizar profesoras: " + error.getLocalizedMessage());
                return;
            }

            if (value != null) {
                List<Usuario> lista = new ArrayList<>();
                for (DocumentSnapshot doc : value.getDocuments()) {
                    Usuario u = doc.toObject(Usuario.class);
                    if (u != null) {
                        u.setId(doc.getId());
                        lista.add(u);
                    }
                }

                // Ordenar: primero las activas, y luego alfabéticamente por nombre
                Collections.sort(lista, (u1, u2) -> {
                    if (u1.isActivo() != u2.isActivo()) {
                        return u1.isActivo() ? -1 : 1;
                    }
                    String n1 = u1.getNombre() != null ? u1.getNombre() : "";
                    String n2 = u2.getNombre() != null ? u2.getNombre() : "";
                    return n1.compareToIgnoreCase(n2);
                });

                usuariosLiveData.postValue(lista);
            }
        });
    }

    public void detenerEscuchaUsuarios() {
        if (usuariosListener != null) {
            usuariosListener.remove();
            usuariosListener = null;
        }
        cargandoLiveData.postValue(false);
        errorLiveData.postValue(null);
    }

    /**
     * Crea una nueva profesora en Firebase Auth y Firestore sin cerrar la sesión de la administradora.
     */
    public void registrarNuevaProfesora(Context context, String nombre, String email, String password,
                                       String rol, OperationCallback callback) {
        try {
            FirebaseOptions options = FirebaseApp.getInstance().getOptions();
            FirebaseApp secondaryApp;
            try {
                secondaryApp = FirebaseApp.getInstance("secondary_auth_app");
            } catch (IllegalStateException e) {
                secondaryApp = FirebaseApp.initializeApp(context.getApplicationContext(), options, "secondary_auth_app");
            }

            FirebaseAuth secondaryAuth = FirebaseAuth.getInstance(secondaryApp);

            secondaryAuth.createUserWithEmailAndPassword(email.trim(), password)
                    .addOnSuccessListener(authResult -> {
                        if (authResult.getUser() != null) {
                            String newUid = authResult.getUser().getUid();

                            Usuario nuevo = new Usuario(newUid, nombre.trim(), email.trim(), rol, true);
                            db.collection("usuarios").document(newUid)
                                    .set(nuevo)
                                    .addOnSuccessListener(aVoid -> {
                                        secondaryAuth.signOut();
                                        callback.onSuccess();
                                    })
                                    .addOnFailureListener(e -> {
                                        // Evitar cuenta huérfana en Auth si falla la escritura en Firestore
                                        authResult.getUser().delete()
                                                .addOnCompleteListener(task -> secondaryAuth.signOut());
                                        callback.onError("Error al guardar perfil en Firestore: " + e.getLocalizedMessage());
                                    });
                        } else {
                            callback.onError("No se pudo obtener el UID de la nueva usuaria.");
                        }
                    })
                    .addOnFailureListener(e -> callback.onError("Error de autenticación: " + e.getLocalizedMessage()));

        } catch (Exception e) {
            callback.onError("Error general al registrar usuaria: " + e.getLocalizedMessage());
        }
    }

    public void actualizarDatosProfesora(String uid, String nuevoNombre, String nuevoRol, OperationCallback callback) {
        db.collection("usuarios").document(uid)
                .update("nombre", nuevoNombre.trim(), "rol", nuevoRol)
                .addOnSuccessListener(aVoid -> callback.onSuccess())
                .addOnFailureListener(e -> callback.onError(e.getLocalizedMessage()));
    }

    /**
     * Borrado lógico: Cambia 'activo' a false para que no pueda entrar sin corromper el histórico.
     */
    public void cambiarEstadoActivo(String uid, boolean nuevoEstado, OperationCallback callback) {
        db.collection("usuarios").document(uid)
                .update("activo", nuevoEstado)
                .addOnSuccessListener(aVoid -> callback.onSuccess())
                .addOnFailureListener(e -> callback.onError(e.getLocalizedMessage()));
    }
}
