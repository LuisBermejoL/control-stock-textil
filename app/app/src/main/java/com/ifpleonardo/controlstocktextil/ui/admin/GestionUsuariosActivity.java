package com.ifpleonardo.controlstocktextil.ui.admin;

import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.snackbar.Snackbar;
import com.ifpleonardo.controlstocktextil.data.model.Usuario;
import com.ifpleonardo.controlstocktextil.data.repository.AuthRepository;
import com.ifpleonardo.controlstocktextil.data.repository.UsuarioRepository;
import com.ifpleonardo.controlstocktextil.databinding.ActivityGestionUsuariosBinding;

/**
 * Actividad administrativa para la visualización y gestión del censo de profesoras.
 * Permite modificar roles o suspender cuentas de docentes jubiladas o trasladadas.
 */
public class GestionUsuariosActivity extends AppCompatActivity {

    private ActivityGestionUsuariosBinding binding;
    private UsuarioAdapter adapter;
    private UsuarioRepository usuarioRepository;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Usuario usuario = AuthRepository.getInstance().getUsuarioActualLiveData().getValue();
        if (usuario == null || !usuario.isAdmin()) {
            Toast.makeText(this, "Acceso restringido a administradores", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        binding = ActivityGestionUsuariosBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setSupportActionBar(binding.toolbarUsuarios);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        usuarioRepository = UsuarioRepository.getInstance();

        setupRecyclerView();
        setupObservadores();
        setupListeners();

        usuarioRepository.iniciarEscuchaUsuarios();
    }

    private void setupRecyclerView() {
        adapter = new UsuarioAdapter(usuario -> {
            EditarUsuarioDialogFragment dialog = EditarUsuarioDialogFragment.newInstance(usuario);
            dialog.show(getSupportFragmentManager(), "EditarUsuarioDialogFragment");
        });

        binding.rvUsuarios.setLayoutManager(new LinearLayoutManager(this));
        binding.rvUsuarios.setAdapter(adapter);
    }

    private void setupObservadores() {
        usuarioRepository.getCargandoLiveData().observe(this, cargando -> {
            binding.progressBarUsuarios.setVisibility(cargando ? View.VISIBLE : View.GONE);
        });

        usuarioRepository.getErrorLiveData().observe(this, error -> {
            if (error != null && !error.isEmpty()) {
                Snackbar.make(binding.coordinatorUsuarios, error, Snackbar.LENGTH_LONG).show();
            }
        });

        usuarioRepository.getUsuariosLiveData().observe(this, usuarios -> {
            adapter.setUsuarios(usuarios);
            boolean vacio = usuarios == null || usuarios.isEmpty();
            binding.llVacioUsuarios.setVisibility(vacio ? View.VISIBLE : View.GONE);
            binding.rvUsuarios.setVisibility(vacio ? View.GONE : View.VISIBLE);
        });
    }

    private void setupListeners() {
        // La creación de cuentas se realiza de forma autónoma desde el registro de la pantalla de Login
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
        usuarioRepository.detenerEscuchaUsuarios();
    }
}
