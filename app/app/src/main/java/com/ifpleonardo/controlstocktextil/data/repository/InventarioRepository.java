package com.ifpleonardo.controlstocktextil.data.repository;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.ifpleonardo.controlstocktextil.data.model.Articulo;
import com.ifpleonardo.controlstocktextil.data.model.Usuario;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Repositorio de Inventario y Concurrencia de Stock Textil.
 * 
 * Garantías de integridad y seguridad:
 * 1. Transacciones atómicas ACID (db.runTransaction):
 *    - Toda alteración de stock (entradas, salidas, préstamos, devoluciones, ajustes) se
 *      ejecuta dentro de una transacción en Firestore para prevenir condiciones de carrera
 *      si varias profesoras escanean o registran el mismo material simultáneamente.
 * 2. Validación estricta de stock negativo:
 *    - Se comprueba en el servidor que stockActual >= cantidad solicitada antes de autorizar
 *      cualquier salida o préstamo; si no hay suficientes existencias, la transacción aborta.
 * 3. Trazabilidad inmutable:
 *    - Cada transacción genera en el mismo paso atómico un registro en la colección /movimientos
 *      con ID de usuaria, nombre, tipo, cantidad, stock resultante y timestamp de servidor.
 * 4. Desactivación lógica:
 *    - Los artículos no se borran físicamente (activo = false) para no romper históricos de movimientos.
 */
public class InventarioRepository {

    private static InventarioRepository instance;
    private final FirebaseFirestore db;
    private ListenerRegistration articulosListener;

    private final MutableLiveData<List<Articulo>> articulosLiveData = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<String> errorLiveData = new MutableLiveData<>();
    private final MutableLiveData<Boolean> cargandoLiveData = new MutableLiveData<>(false);

    private InventarioRepository() {
        this.db = FirebaseFirestore.getInstance();
    }

    public static synchronized InventarioRepository getInstance() {
        if (instance == null) {
            instance = new InventarioRepository();
        }
        return instance;
    }

    public LiveData<List<Articulo>> getArticulosLiveData() {
        return articulosLiveData;
    }

    public LiveData<String> getErrorLiveData() {
        return errorLiveData;
    }

    public LiveData<Boolean> getCargandoLiveData() {
        return cargandoLiveData;
    }

    /**
     * Inicia la sincronización en tiempo real de los artículos activos del catálogo.
     */
    public void iniciarEscuchaInventario() {
        if (articulosListener != null) {
            return;
        }

        cargandoLiveData.postValue(true);

        Query query = db.collection("articulos")
                .whereEqualTo("activo", true);

        articulosListener = query.addSnapshotListener((value, error) -> {
            cargandoLiveData.postValue(false);

            if (error != null) {
                // Si el usuario acaba de cerrar sesión, ignorar el error de permisos residual
                if (com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser() == null) {
                    return;
                }
                errorLiveData.postValue("Error al sincronizar inventario: " + error.getLocalizedMessage());
                return;
            }

            if (value != null) {
                List<Articulo> lista = new ArrayList<>();
                for (DocumentSnapshot doc : value.getDocuments()) {
                    Articulo articulo = doc.toObject(Articulo.class);
                    if (articulo != null) {
                        articulo.setId(doc.getId());
                        lista.add(articulo);
                    }
                }

                Collections.sort(lista, (a1, a2) -> {
                    String cat1 = a1.getCategoria() != null ? a1.getCategoria() : "";
                    String cat2 = a2.getCategoria() != null ? a2.getCategoria() : "";
                    int catComp = cat1.compareToIgnoreCase(cat2);
                    if (catComp != 0)
                        return catComp;
                    return a1.getNombreCompleto().compareToIgnoreCase(a2.getNombreCompleto());
                });

                articulosLiveData.postValue(lista);
            }
        });
    }

    public void detenerEscuchaInventario() {
        if (articulosListener != null) {
            articulosListener.remove();
            articulosListener = null;
        }
        cargandoLiveData.postValue(false);
        errorLiveData.postValue(null);
    }

    // ==========================================
    // TRANSACCIÓN ATÓMICA DE STOCK (CONCURRENCIA)
    // ==========================================

    public interface TransactionCallback {
        void onSuccess(double nuevoStock);

        void onError(String error);
    }

    public void registrarMovimiento(String articuloId, String tipo, double cantidad,
            String observaciones, TransactionCallback callback) {
        if (articuloId == null || articuloId.trim().isEmpty()) {
            callback.onError("ID de artículo inválido");
            return;
        }

        DocumentReference articuloRef = db.collection("articulos").document(articuloId);
        DocumentReference movimientoRef = db.collection("movimientos").document();

        db.runTransaction(transaction -> {
            DocumentSnapshot snapshot = transaction.get(articuloRef);
            if (!snapshot.exists()) {
                throw new FirebaseFirestoreException("El artículo no existe",
                        FirebaseFirestoreException.Code.NOT_FOUND);
            }

            Articulo articulo = snapshot.toObject(Articulo.class);
            if (articulo == null) {
                throw new FirebaseFirestoreException("Error al parsear el artículo",
                        FirebaseFirestoreException.Code.INTERNAL);
            }

            double stockActual = articulo.getStockActual();
            double nuevoStock;

            switch (tipo) {
                case "ENTRADA":
                case "DEVOLUCION":
                    nuevoStock = stockActual + cantidad;
                    break;
                case "SALIDA_CONSUMO":
                case "PRESTAMO":
                    if (stockActual < cantidad) {
                        throw new FirebaseFirestoreException(
                                "Stock insuficiente. Stock actual: " + stockActual + ", solicitado: " + cantidad,
                                FirebaseFirestoreException.Code.ABORTED);
                    }
                    nuevoStock = stockActual - cantidad;
                    break;
                case "AJUSTE":
                    nuevoStock = cantidad;
                    if (nuevoStock < 0) {
                        throw new FirebaseFirestoreException("El stock no puede ser negativo",
                                FirebaseFirestoreException.Code.ABORTED);
                    }
                    break;
                default:
                    throw new FirebaseFirestoreException("Tipo de movimiento desconocido: " + tipo,
                            FirebaseFirestoreException.Code.INVALID_ARGUMENT);
            }

            // 1. Actualizar el stock en el documento del artículo
            transaction.update(articuloRef, "stockActual", nuevoStock);

            // 2. Registrar el movimiento inmutable
            Usuario usuario = AuthRepository.getInstance().getUsuarioActualLiveData().getValue();
            String usuarioId = usuario != null ? usuario.getId() : AuthRepository.getInstance().getCurrentUid();
            String usuarioNombre = usuario != null ? usuario.getNombre() : "Profesora";

            Map<String, Object> movimientoData = new HashMap<>();
            movimientoData.put("articuloId", articuloId);
            movimientoData.put("articuloNombre", articulo.getNombreCompleto());
            movimientoData.put("categoria", articulo.getCategoria());
            movimientoData.put("tipo", tipo);
            movimientoData.put("cantidad", cantidad);
            movimientoData.put("stockResultante", nuevoStock);
            movimientoData.put("usuarioId", usuarioId);
            movimientoData.put("usuarioNombre", usuarioNombre);
            movimientoData.put("fecha", FieldValue.serverTimestamp());
            movimientoData.put("observaciones", observaciones != null ? observaciones.trim() : "");

            transaction.set(movimientoRef, movimientoData);

            return nuevoStock;
        }).addOnSuccessListener(callback::onSuccess)
                .addOnFailureListener(e -> callback.onError(e.getLocalizedMessage()));
    }

    // ==========================================
    // ALTA, EDICIÓN Y BORRADO LÓGICO DE ARTÍCULOS
    // ==========================================

    public interface OperationCallback {
        void onSuccess();

        void onError(String error);
    }

    public void guardarArticulo(Articulo articulo, OperationCallback callback) {
        DocumentReference ref;
        if (articulo.getId() != null && !articulo.getId().trim().isEmpty()) {
            ref = db.collection("articulos").document(articulo.getId());
        } else {
            ref = db.collection("articulos").document();
        }

        ref.set(articulo)
                .addOnSuccessListener(aVoid -> callback.onSuccess())
                .addOnFailureListener(e -> callback.onError(e.getLocalizedMessage()));
    }

    public void desactivarArticulo(String articuloId, OperationCallback callback) {
        db.collection("articulos").document(articuloId)
                .update("activo", false)
                .addOnSuccessListener(aVoid -> callback.onSuccess())
                .addOnFailureListener(e -> callback.onError(e.getLocalizedMessage()));
    }
}
