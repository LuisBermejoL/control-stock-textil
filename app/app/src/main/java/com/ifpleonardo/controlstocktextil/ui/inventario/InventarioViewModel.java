package com.ifpleonardo.controlstocktextil.ui.inventario;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.ifpleonardo.controlstocktextil.data.model.Articulo;
import com.ifpleonardo.controlstocktextil.data.model.Usuario;
import com.ifpleonardo.controlstocktextil.data.repository.AuthRepository;
import com.ifpleonardo.controlstocktextil.data.repository.InventarioRepository;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;

/**
 * ViewModel del catálogo de inventario (Patrón MVVM).
 * 
 * Centraliza la lógica de filtrado reactivo combinando múltiples fuentes mediante MediatorLiveData:
 * - Lista cruda de Firestore desde InventarioRepository.
 * - Categoría seleccionada por Chip.
 * - Texto de búsqueda ingresado en el SearchView.
 * - Filtro booleano de artículos bajo mínimos de seguridad.
 */
public class InventarioViewModel extends ViewModel {

    private final InventarioRepository inventarioRepository;
    private final AuthRepository authRepository;

    private final MutableLiveData<String> categoriaFiltro = new MutableLiveData<>("TODAS");
    private final MutableLiveData<String> textoBusqueda = new MutableLiveData<>("");
    private final MutableLiveData<Boolean> soloBajoMinimos = new MutableLiveData<>(false);

    private final MediatorLiveData<List<Articulo>> articulosFiltrados = new MediatorLiveData<>();

    public InventarioViewModel() {
        this.inventarioRepository = InventarioRepository.getInstance();
        this.authRepository = AuthRepository.getInstance();

        // Conectar fuentes al MediatorLiveData
        articulosFiltrados.addSource(inventarioRepository.getArticulosLiveData(), articulos -> aplicarFiltros());
        articulosFiltrados.addSource(categoriaFiltro, cat -> aplicarFiltros());
        articulosFiltrados.addSource(textoBusqueda, txt -> aplicarFiltros());
        articulosFiltrados.addSource(soloBajoMinimos, bajo -> aplicarFiltros());

        // Iniciar escucha en tiempo real
        inventarioRepository.iniciarEscuchaInventario();
    }

    public LiveData<List<Articulo>> getArticulosFiltrados() {
        return articulosFiltrados;
    }

    public LiveData<Boolean> getCargando() {
        return inventarioRepository.getCargandoLiveData();
    }

    public LiveData<String> getError() {
        return inventarioRepository.getErrorLiveData();
    }

    public LiveData<Usuario> getUsuarioActual() {
        return authRepository.getUsuarioActualLiveData();
    }

    public void setCategoria(String categoria) {
        categoriaFiltro.setValue(categoria != null ? categoria : "TODAS");
    }

    public void setBusqueda(String texto) {
        textoBusqueda.setValue(texto != null ? texto : "");
    }

    public void setSoloBajoMinimos(boolean activado) {
        soloBajoMinimos.setValue(activado);
    }

    private void aplicarFiltros() {
        List<Articulo> listaOriginal = inventarioRepository.getArticulosLiveData().getValue();
        if (listaOriginal == null) {
            articulosFiltrados.setValue(new ArrayList<>());
            return;
        }

        String cat = categoriaFiltro.getValue();
        String query = limpiarTexto(textoBusqueda.getValue());
        boolean filtrarBajoMinimos = Boolean.TRUE.equals(soloBajoMinimos.getValue());

        List<Articulo> resultado = new ArrayList<>();

        for (Articulo a : listaOriginal) {
            // Filtro bajo mínimos / agotados
            if (filtrarBajoMinimos) {
                if (!a.isBajoMinimos() && a.getStockActual() > 0) {
                    continue;
                }
            }

            // Filtro por categoría
            if (cat != null && !cat.equalsIgnoreCase("TODAS")) {
                if (a.getCategoria() == null || !a.getCategoria().equalsIgnoreCase(cat)) {
                    continue;
                }
            }

            // Filtro por texto de búsqueda
            if (query != null && !query.isEmpty()) {
                String searchable = limpiarTexto(
                        (a.getArticulo() != null ? a.getArticulo() : "") + " " +
                        (a.getReferencia() != null ? a.getReferencia() : "") + " " +
                        (a.getColor() != null ? a.getColor() : "") + " " +
                        (a.getProveedor() != null ? a.getProveedor() : "") + " " +
                        (a.getColorProveedor() != null ? a.getColorProveedor() : "") + " " +
                        (a.getSubtipo() != null ? a.getSubtipo() : "") + " " +
                        (a.getComposicion() != null ? a.getComposicion() : "")
                );

                if (!searchable.contains(query)) {
                    continue;
                }
            }

            resultado.add(a);
        }

        articulosFiltrados.setValue(resultado);
    }

    private String limpiarTexto(String input) {
        if (input == null) return "";
        String normalized = Normalizer.normalize(input, Normalizer.Form.NFD);
        return normalized.replaceAll("\\p{InCombiningDiacriticalMarks}+", "").toLowerCase().trim();
    }

    public void cerrarSesion() {
        inventarioRepository.detenerEscuchaInventario();
        authRepository.logout();
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        inventarioRepository.detenerEscuchaInventario();
    }
}
