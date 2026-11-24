package com.ifpleonardo.controlstocktextil.ui.historial;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.ifpleonardo.controlstocktextil.R;
import com.ifpleonardo.controlstocktextil.data.model.Movimiento;
import com.ifpleonardo.controlstocktextil.databinding.FragmentHistorialBinding;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;

/**
 * Pestaña de Historial de Movimientos integrada en el BottomNavigationView principal.
 * Conexión en tiempo real ordenada cronológicamente (más recientes primero).
 */
public class HistorialFragment extends Fragment {

    private FragmentHistorialBinding binding;
    private MovimientoAdapter adapter;
    private ListenerRegistration movimientosListener;

    private final List<Movimiento> listaCompleta = new ArrayList<>();
    private String tipoFiltro = "TODOS";
    private String textoFiltro = "";

    public static HistorialFragment newInstance() {
        return new HistorialFragment();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentHistorialBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        setupRecyclerView();
        setupBuscador();
        setupChips();
        iniciarEscuchaMovimientos();
    }

    private void setupRecyclerView() {
        adapter = new MovimientoAdapter();
        binding.rvHistorial.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvHistorial.setAdapter(adapter);
    }

    private void setupBuscador() {
        binding.etBuscarHistorial.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                textoFiltro = s != null ? s.toString().trim() : "";
                aplicarFiltros();
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private void setupChips() {
        binding.chipGroupTipoHistorial.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) {
                binding.chipTipoTodos.setChecked(true);
                tipoFiltro = "TODOS";
            } else {
                int id = checkedIds.get(0);
                if (id == R.id.chipTipoSalidas) tipoFiltro = "SALIDA_CONSUMO";
                else if (id == R.id.chipTipoEntradas) tipoFiltro = "ENTRADA";
                else if (id == R.id.chipTipoPrestamos) tipoFiltro = "PRESTAMO";
                else if (id == R.id.chipTipoDevoluciones) tipoFiltro = "DEVOLUCION";
                else if (id == R.id.chipTipoAjustes) tipoFiltro = "AJUSTE";
                else tipoFiltro = "TODOS";
            }
            aplicarFiltros();
        });
    }

    private void iniciarEscuchaMovimientos() {
        binding.progressHistorial.setVisibility(View.VISIBLE);

        movimientosListener = FirebaseFirestore.getInstance().collection("movimientos")
                .orderBy("fecha", Query.Direction.DESCENDING)
                .limit(200)
                .addSnapshotListener((value, error) -> {
                    if (binding == null) return;
                    binding.progressHistorial.setVisibility(View.GONE);

                    if (error != null || value == null) {
                        return;
                    }

                    listaCompleta.clear();
                    for (DocumentSnapshot doc : value.getDocuments()) {
                        Movimiento mov = doc.toObject(Movimiento.class);
                        if (mov != null) {
                            mov.setId(doc.getId());
                            listaCompleta.add(mov);
                        }
                    }

                    aplicarFiltros();
                });
    }

    private void aplicarFiltros() {
        List<Movimiento> filtrados = new ArrayList<>();
        String busquedaNormalizada = normalizar(textoFiltro);

        for (Movimiento mov : listaCompleta) {
            boolean coincideTipo = tipoFiltro.equals("TODOS") || tipoFiltro.equalsIgnoreCase(mov.getTipo());
            if (!coincideTipo) continue;

            boolean coincideTexto = true;
            if (!busquedaNormalizada.isEmpty()) {
                String articuloNorm = normalizar(mov.getArticuloNombre());
                String usuarioNorm = normalizar(mov.getUsuarioNombre());
                String obsNorm = normalizar(mov.getObservaciones());

                coincideTexto = articuloNorm.contains(busquedaNormalizada)
                        || usuarioNorm.contains(busquedaNormalizada)
                        || obsNorm.contains(busquedaNormalizada);
            }

            if (coincideTexto) {
                filtrados.add(mov);
            }
        }

        adapter.setMovimientos(filtrados);
        boolean vacio = filtrados.isEmpty();
        if (binding != null) {
            binding.llVacioHistorial.setVisibility(vacio ? View.VISIBLE : View.GONE);
            binding.rvHistorial.setVisibility(vacio ? View.GONE : View.VISIBLE);
        }
    }

    private String normalizar(String input) {
        if (input == null) return "";
        return Normalizer.normalize(input.trim().toLowerCase(), Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (movimientosListener != null) {
            movimientosListener.remove();
        }
        binding = null;
    }
}