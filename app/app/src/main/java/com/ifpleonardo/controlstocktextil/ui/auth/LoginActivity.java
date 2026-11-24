package com.ifpleonardo.controlstocktextil.ui.auth;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.snackbar.Snackbar;
import com.ifpleonardo.controlstocktextil.MainActivity;
import com.ifpleonardo.controlstocktextil.databinding.ActivityLoginBinding;

/**
 * Pantalla de inicio de sesión y puerta de entrada principal a la aplicación.
 * 
 * Flujo:
 * 1. Comprobación automática de sesión existente al iniciar.
 * 2. Validación de formato de campos de correo y contraseña.
 * 3. Acceso al diálogo de autoregistro de nuevas profesoras.
 * 4. Redirección a MainActivity al autenticar satisfactoriamente con banderas CLEAR_TASK.
 */
public class LoginActivity extends AppCompatActivity {

    private ActivityLoginBinding binding;
    private LoginViewModel viewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityLoginBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        viewModel = new ViewModelProvider(this).get(LoginViewModel.class);

        setupObservadores();
        setupListeners();

        // Si ya hay sesión guardada en el dispositivo, verificarla automáticamente
        viewModel.verificarSesionExistente();
    }

    private void setupObservadores() {
        viewModel.getCargando().observe(this, cargando -> {
            binding.progressBar.setVisibility(cargando ? View.VISIBLE : View.GONE);
            binding.btnLogin.setEnabled(!cargando);
            binding.etEmail.setEnabled(!cargando);
            binding.etPassword.setEnabled(!cargando);
        });

        viewModel.getError().observe(this, error -> {
            if (error != null && !error.isEmpty()) {
                Snackbar.make(binding.coordinatorLayout, error, Snackbar.LENGTH_LONG).show();
            }
        });

        viewModel.getUsuarioLogueado().observe(this, usuario -> {
            if (usuario != null) {
                Toast.makeText(this, "Bienvenida, " + usuario.getNombre(), Toast.LENGTH_SHORT).show();
                Intent intent = new Intent(LoginActivity.this, MainActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
                finish();
            }
        });
    }

    private void setupListeners() {
        binding.btnLogin.setOnClickListener(v -> {
            String email = binding.etEmail.getText() != null ? binding.etEmail.getText().toString().trim() : "";
            String password = binding.etPassword.getText() != null ? binding.etPassword.getText().toString() : "";

            if (email.isEmpty()) {
                binding.tilEmail.setError("Introduce el correo electrónico");
                return;
            } else {
                binding.tilEmail.setError(null);
            }

            if (password.isEmpty()) {
                binding.tilPassword.setError("Introduce la contraseña");
                return;
            } else {
                binding.tilPassword.setError(null);
            }

            viewModel.iniciarSesion(email, password);
        });

        binding.btnAbrirRegistro.setOnClickListener(v -> {
            RegistroDialogFragment dialog = RegistroDialogFragment.newInstance();
            dialog.show(getSupportFragmentManager(), "RegistroDialogFragment");
        });
    }
}
