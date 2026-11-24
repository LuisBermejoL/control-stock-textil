package com.ifpleonardo.controlstocktextil.ui.inventario;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.chip.Chip;
import com.ifpleonardo.controlstocktextil.R;
import com.ifpleonardo.controlstocktextil.data.model.Usuario;
import com.ifpleonardo.controlstocktextil.data.repository.AuthRepository;
import com.ifpleonardo.controlstocktextil.databinding.FragmentInventarioBinding;
import com.ifpleonardo.controlstocktextil.ui.detalle.DetalleArticuloActivity;
import com.ifpleonardo.controlstocktextil.ui.detalle.EditarArticuloActivity;

/**
 * Pestaña principal de Inventario Textil en tiempo real.
 * 
 * Funcionalidades:
 * 1. Buscador interactivo por nombre, referencia, color o proveedor.
 * 2. Barra de Chips por categoría textil ("Todas", "HILOS", "TEJIDO", "ENTRETELA", etc.) y filtro de artículos "Bajo mínimos".
 * 3. Botón flotante FAB para creación de nuevos artículos (visible únicamente para rol ADMIN).
 */
public class InventarioFragment extends Fragment {

    private FragmentInventarioBinding binding;
    private InventarioViewModel viewModel;
    private ArticuloAdapter adapter;
    private Usuario usuarioActual;

    public static InventarioFragment newInstance() {
        return new InventarioFragment();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentInventarioBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        viewModel = new ViewModelProvider(requireActivity()).get(InventarioViewModel.class);

        setupRecyclerView();
        setupBuscador();
        setupChips();
        setupObservadores();

        usuarioActual = AuthRepository.getInstance().getUsuarioActualLiveData().getValue();
        if (usuarioActual != null && usuarioActual.isAdmin()) {
            binding.fabNuevoArticulo.setVisibility(View.VISIBLE);
        } else {
            binding.fabNuevoArticulo.setVisibility(View.GONE);
        }

        binding.fabNuevoArticulo.setOnClickListener(v -> {
            startActivity(new Intent(requireContext(), EditarArticuloActivity.class));
        });
    }

    private void setupRecyclerView() {
        adapter = new ArticuloAdapter(articulo -> {
            Intent intent = new Intent(requireContext(), DetalleArticuloActivity.class);
            intent.putExtra(DetalleArticuloActivity.EXTRA_ARTICULO, articulo);
            startActivity(intent);
        });

        binding.rvArticulos.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvArticulos.setAdapter(adapter);
    }

    private void setupBuscador() {
        binding.etBuscar.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                viewModel.setBusqueda(s != null ? s.toString() : "");
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private void setupChips() {
        binding.chipGroupCategorias.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) {
                binding.chipTodas.setChecked(true);
                viewModel.setCategoria("Todas");
                viewModel.setSoloBajoMinimos(false);
            } else {
                int selectedId = checkedIds.get(0);
                Chip selectedChip = group.findViewById(selectedId);
                if (selectedChip != null) {
                    if (selectedId == R.id.chipBajoMinimos) {
                        viewModel.setSoloBajoMinimos(true);
                        viewModel.setCategoria("Todas");
                    } else {
                        viewModel.setSoloBajoMinimos(false);
                        viewModel.setCategoria(selectedChip.getText().toString());
                    }
                }
            }
        });
    }

    private void setupObservadores() {
        viewModel.getCargando().observe(getViewLifecycleOwner(), cargando -> {
            binding.progressBar.setVisibility(cargando ? View.VISIBLE : View.GONE);
        });

        viewModel.getArticulosFiltrados().observe(getViewLifecycleOwner(), articulos -> {
            adapter.setArticulos(articulos);
            boolean vacio = articulos == null || articulos.isEmpty();
            binding.llVacio.setVisibility(vacio ? View.VISIBLE : View.GONE);
            binding.rvArticulos.setVisibility(vacio ? View.GONE : View.VISIBLE);
        });

        AuthRepository.getInstance().getUsuarioActualLiveData().observe(getViewLifecycleOwner(), usuario -> {
            usuarioActual = usuario;
            if (binding != null) {
                binding.fabNuevoArticulo.setVisibility(usuario != null && usuario.isAdmin() ? View.VISIBLE : View.GONE);
            }
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}