package com.ifpleonardo.controlstocktextil.ui.historial;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.MenuItem;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.chip.Chip;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.ifpleonardo.controlstocktextil.R;
import com.ifpleonardo.controlstocktextil.data.model.Movimiento;
import com.ifpleonardo.controlstocktextil.databinding.ActivityHistorialBinding;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;

/**
 * Pantalla autónoma para consultar el histórico completo de movimientos de almacén.
 * Incluye filtrado en vivo por tipo de operación (Entradas, Salidas, Préstamos, Devoluciones, Ajustes)
 * y búsqueda insensible a acentos por artículo o docente responsable.
 */
public class HistorialActivity extends AppCompatActivity {

    private ActivityHistorialBinding binding;
    private MovimientoAdapter adapter;
    private ListenerRegistration movimientosListener;

    private final List<Movimiento> listaCompleta = new ArrayList<>();
    private String tipoFiltro = "TODOS";
    private String textoFiltro = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityHistorialBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setSupportActionBar(binding.toolbarHistorial);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        setupRecyclerView();
        setupBuscador();
        setupChips();
        iniciarEscuchaMovimientos();
    }

    private void setupRecyclerView() {
        adapter = new MovimientoAdapter();
        binding.rvHistorial.setLayoutManager(new LinearLayoutManager(this));
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
                    binding.progressHistorial.setVisibility(View.GONE);

                    if (error != null) {
                        Snackbar.make(binding.coordinatorHistorial, "Error al sincronizar movimientos: " + error.getLocalizedMessage(), Snackbar.LENGTH_LONG).show();
                        return;
                    }

                    if (value != null) {
                        listaCompleta.clear();
                        for (DocumentSnapshot doc : value.getDocuments()) {
                            Movimiento m = doc.toObject(Movimiento.class);
                            if (m != null) {
                                m.setId(doc.getId());
                                listaCompleta.add(m);
                            }
                        }
                        aplicarFiltros();
                    }
                });
    }

    private void aplicarFiltros() {
        List<Movimiento> filtrados = new ArrayList<>();
        String query = limpiarTexto(textoFiltro);

        for (Movimiento m : listaCompleta) {
            // Filtro por tipo
            if (!tipoFiltro.equals("TODOS")) {
                if (m.getTipo() == null || !m.getTipo().equalsIgnoreCase(tipoFiltro)) {
                    continue;
                }
            }

            // Filtro por texto
            if (!query.isEmpty()) {
                String searchable = limpiarTexto(
                        (m.getArticuloNombre() != null ? m.getArticuloNombre() : "") + " " +
                        (m.getUsuarioNombre() != null ? m.getUsuarioNombre() : "") + " " +
                        (m.getObservaciones() != null ? m.getObservaciones() : "")
                );
                if (!searchable.contains(query)) {
                    continue;
                }
            }

            filtrados.add(m);
        }

        adapter.setMovimientos(filtrados);
        boolean vacio = filtrados.isEmpty();
        binding.llVacioHistorial.setVisibility(vacio ? View.VISIBLE : View.GONE);
        binding.rvHistorial.setVisibility(vacio ? View.GONE : View.VISIBLE);
    }

    private String limpiarTexto(String input) {
        if (input == null) return "";
        String normalized = Normalizer.normalize(input, Normalizer.Form.NFD);
        return normalized.replaceAll("\\p{InCombiningDiacriticalMarks}+", "").toLowerCase().trim();
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (movimientosListener != null) {
            movimientosListener.remove();
        }
    }
}
