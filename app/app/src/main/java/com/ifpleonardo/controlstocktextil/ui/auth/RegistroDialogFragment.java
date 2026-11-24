package com.ifpleonardo.controlstocktextil.ui.auth;

import android.app.Dialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.ifpleonardo.controlstocktextil.MainActivity;
import com.ifpleonardo.controlstocktextil.data.model.Usuario;
import com.ifpleonardo.controlstocktextil.data.repository.AuthRepository;
import com.ifpleonardo.controlstocktextil.databinding.DialogRegistroProfesoraBinding;

/**
 * Diálogo modal para el autoregistro de profesoras desde la pantalla de login.
 * 
 * Medidas de seguridad y usabilidad:
 * 1. Asigna incondicionalmente el rol 'PROFESORA' y 'activo = true'.
 * 2. Validación local de coincidencia y longitud mínima de contraseña (>= 6 caracteres).
 * 3. Alertas enriquecidas con tarjeta de aviso en el formulario y diálogo modal explicativo.
 * 4. Soporte para recuperación automática de cuentas huérfanas en caso de fallos de red previos.
 */
public class RegistroDialogFragment extends DialogFragment {

    private DialogRegistroProfesoraBinding binding;

    public static RegistroDialogFragment newInstance() {
        return new RegistroDialogFragment();
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        binding = DialogRegistroProfesoraBinding.inflate(LayoutInflater.from(requireContext()));

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireContext())
                .setView(binding.getRoot());

        binding.btnCancelarRegistro.setOnClickListener(v -> dismiss());

        binding.btnConfirmarRegistro.setOnClickListener(v -> ejecutarRegistro());

        TextWatcher clearErrorWatcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (binding != null && binding.cardErrorRegistro.getVisibility() == View.VISIBLE) {
                    binding.cardErrorRegistro.setVisibility(View.GONE);
                }
            }
            @Override
            public void afterTextChanged(Editable s) {}
        };

        binding.etRegistroNombre.addTextChangedListener(clearErrorWatcher);
        binding.etRegistroEmail.addTextChangedListener(clearErrorWatcher);
        binding.etRegistroPassword.addTextChangedListener(clearErrorWatcher);
        binding.etRegistroConfirmPassword.addTextChangedListener(clearErrorWatcher);

        return builder.create();
    }

    private void ejecutarRegistro() {
        if (binding == null) return;
        binding.cardErrorRegistro.setVisibility(View.GONE);

        String nombre = binding.etRegistroNombre.getText() != null ? binding.etRegistroNombre.getText().toString().trim() : "";
        String email = binding.etRegistroEmail.getText() != null ? binding.etRegistroEmail.getText().toString().trim() : "";
        String password = binding.etRegistroPassword.getText() != null ? binding.etRegistroPassword.getText().toString() : "";
        String confirmPassword = binding.etRegistroConfirmPassword.getText() != null ? binding.etRegistroConfirmPassword.getText().toString() : "";

        if (nombre.isEmpty()) {
            binding.tilRegistroNombre.setError("Introduce tu nombre y apellidos");
            return;
        } else {
            binding.tilRegistroNombre.setError(null);
        }

        if (email.isEmpty()) {
            binding.tilRegistroEmail.setError("Introduce tu correo electrónico");
            return;
        } else {
            binding.tilRegistroEmail.setError(null);
        }

        if (password.length() < 6) {
            binding.tilRegistroPassword.setError("La contraseña debe tener al menos 6 caracteres");
            return;
        } else {
            binding.tilRegistroPassword.setError(null);
        }

        if (!password.equals(confirmPassword)) {
            binding.tilRegistroConfirmPassword.setError("Las contraseñas no coinciden");
            return;
        } else {
            binding.tilRegistroConfirmPassword.setError(null);
        }

        setLoading(true);

        AuthRepository.getInstance().registrarProfesora(nombre, email, password, new AuthRepository.AuthCallback() {
            @Override
            public void onSuccess(Usuario usuario) {
                if (!isAdded()) return;
                setLoading(false);
                Toast.makeText(requireContext(), "Cuenta creada con éxito. Bienvenida, " + usuario.getNombre(), Toast.LENGTH_LONG).show();
                dismiss();

                Intent intent = new Intent(requireActivity(), MainActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
                requireActivity().finish();
            }

            @Override
            public void onError(String error) {
                if (!isAdded()) return;
                setLoading(false);

                if (binding != null) {
                    binding.cardErrorRegistro.setVisibility(View.VISIBLE);
                    binding.tvErrorRegistro.setText(error);
                }

                new MaterialAlertDialogBuilder(requireContext())
                        .setTitle("Aviso de Registro")
                        .setMessage(error)
                        .setPositiveButton("Aceptar", null)
                        .show();
            }
        });
    }

    private void setLoading(boolean loading) {
        binding.pbRegistro.setVisibility(loading ? View.VISIBLE : View.GONE);
        binding.btnConfirmarRegistro.setEnabled(!loading);
        binding.btnCancelarRegistro.setEnabled(!loading);
        binding.etRegistroNombre.setEnabled(!loading);
        binding.etRegistroEmail.setEnabled(!loading);
        binding.etRegistroPassword.setEnabled(!loading);
        binding.etRegistroConfirmPassword.setEnabled(!loading);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}