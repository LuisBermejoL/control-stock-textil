package com.ifpleonardo.controlstocktextil.ui.admin;

import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.ifpleonardo.controlstocktextil.data.model.Usuario;
import com.ifpleonardo.controlstocktextil.data.repository.UsuarioRepository;
import com.ifpleonardo.controlstocktextil.databinding.DialogCrearUsuarioBinding;

/**
 * Diálogo modal para la creación manual de usuarias por parte de la administradora.
 * 
 * Valida nombre, correo institucional y contraseña mínima de 6 caracteres.
 * Utiliza una instancia secundaria de FirebaseApp para no cerrar la sesión de la jefa de departamento.
 */
public class CrearUsuarioDialogFragment extends DialogFragment {

    private DialogCrearUsuarioBinding binding;
    private static final String[] ROLES = {"LECTOR", "PROFESOR/A", "ADMIN"};

    public static CrearUsuarioDialogFragment newInstance() {
        return new CrearUsuarioDialogFragment();
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        binding = DialogCrearUsuarioBinding.inflate(LayoutInflater.from(requireContext()));

        ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_dropdown_item_1line, ROLES);
        binding.actvNuevoRol.setAdapter(adapter);
        binding.actvNuevoRol.setText(ROLES[0], false);

        setupListeners();

        return new MaterialAlertDialogBuilder(requireContext())
                .setView(binding.getRoot())
                .create();
    }

    private void setupListeners() {
        binding.btnCancelarCrear.setOnClickListener(v -> dismiss());

        binding.btnGuardarNuevoUsuario.setOnClickListener(v -> {
            String nombre = binding.etNuevoNombre.getText() != null ? binding.etNuevoNombre.getText().toString().trim() : "";
            String email = binding.etNuevoEmail.getText() != null ? binding.etNuevoEmail.getText().toString().trim() : "";
            String password = binding.etNuevoPassword.getText() != null ? binding.etNuevoPassword.getText().toString() : "";
            String rolSeleccionado = binding.actvNuevoRol.getText().toString().trim();
            String rol = Usuario.mapRolVisualARolFirestore(rolSeleccionado);

            if (nombre.isEmpty()) {
                binding.tilNuevoNombre.setError("Introduce el nombre completo");
                return;
            } else {
                binding.tilNuevoNombre.setError(null);
            }

            if (email.isEmpty()) {
                binding.tilNuevoEmail.setError("Introduce el correo institucional");
                return;
            } else {
                binding.tilNuevoEmail.setError(null);
            }

            if (password.length() < 6) {
                binding.tilNuevoPassword.setError("La contraseña debe tener al menos 6 caracteres");
                return;
            } else {
                binding.tilNuevoPassword.setError(null);
            }

            binding.progressCrearUsuario.setVisibility(View.VISIBLE);
            binding.btnGuardarNuevoUsuario.setEnabled(false);
            binding.btnCancelarCrear.setEnabled(false);

            UsuarioRepository.getInstance().registrarNuevaProfesora(
                    requireContext(),
                    nombre,
                    email,
                    password,
                    rol,
                    new UsuarioRepository.OperationCallback() {
                        @Override
                        public void onSuccess() {
                            if (getContext() == null) return;
                            Toast.makeText(getContext(), "Usuario creado con éxito", Toast.LENGTH_SHORT).show();
                            dismiss();
                        }

                        @Override
                        public void onError(String error) {
                            if (getContext() == null) return;
                            binding.progressCrearUsuario.setVisibility(View.GONE);
                            binding.btnGuardarNuevoUsuario.setEnabled(true);
                            binding.btnCancelarCrear.setEnabled(true);
                            Toast.makeText(getContext(), "Error: " + error, Toast.LENGTH_LONG).show();
                        }
                    }
            );
        });
    }
}
