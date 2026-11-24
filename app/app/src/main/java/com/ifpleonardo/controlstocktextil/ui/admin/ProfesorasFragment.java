package com.ifpleonardo.controlstocktextil.ui.admin;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.snackbar.Snackbar;
import com.ifpleonardo.controlstocktextil.data.model.Usuario;
import com.ifpleonardo.controlstocktextil.data.repository.AuthRepository;
import com.ifpleonardo.controlstocktextil.data.repository.UsuarioRepository;
import com.ifpleonardo.controlstocktextil.databinding.FragmentProfesorasBinding;

public class ProfesorasFragment extends Fragment {

    private FragmentProfesorasBinding binding;
    private UsuarioAdapter adapter;
    private UsuarioRepository usuarioRepository;

    public static ProfesorasFragment newInstance() {
        return new ProfesorasFragment();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentProfesorasBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        usuarioRepository = UsuarioRepository.getInstance();

        setupRecyclerView();
        setupObservadores();

        Usuario usuario = AuthRepository.getInstance().getUsuarioActualLiveData().getValue();
        if (usuario != null && usuario.isAdmin()) {
            usuarioRepository.iniciarEscuchaUsuarios();
        }
    }

    private void setupRecyclerView() {
        adapter = new UsuarioAdapter(usuario -> {
            EditarUsuarioDialogFragment dialog = EditarUsuarioDialogFragment.newInstance(usuario);
            dialog.show(getChildFragmentManager(), "EditarUsuarioDialogFragment");
        });

        binding.rvUsuarios.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvUsuarios.setAdapter(adapter);
    }

    private void setupObservadores() {
        AuthRepository.getInstance().getUsuarioActualLiveData().observe(getViewLifecycleOwner(), usuario -> {
            if (usuario != null && usuario.isAdmin()) {
                usuarioRepository.iniciarEscuchaUsuarios();
            } else {
                usuarioRepository.detenerEscuchaUsuarios();
            }
        });

        usuarioRepository.getCargandoLiveData().observe(getViewLifecycleOwner(), cargando -> {
            if (binding != null) {
                binding.progressBarUsuarios.setVisibility(cargando ? View.VISIBLE : View.GONE);
            }
        });

        usuarioRepository.getErrorLiveData().observe(getViewLifecycleOwner(), error -> {
            Usuario usuario = AuthRepository.getInstance().getUsuarioActualLiveData().getValue();
            if (error != null && !error.isEmpty() && getContext() != null && usuario != null && usuario.isAdmin()) {
                Toast.makeText(getContext(), error, Toast.LENGTH_LONG).show();
            }
        });

        usuarioRepository.getUsuariosLiveData().observe(getViewLifecycleOwner(), usuarios -> {
            if (binding != null) {
                adapter.setUsuarios(usuarios);
                boolean vacio = usuarios == null || usuarios.isEmpty();
                binding.llVacioUsuarios.setVisibility(vacio ? View.VISIBLE : View.GONE);
                binding.rvUsuarios.setVisibility(vacio ? View.GONE : View.VISIBLE);
            }
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        usuarioRepository.detenerEscuchaUsuarios();
        binding = null;
    }
}