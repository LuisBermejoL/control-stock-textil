package com.ifpleonardo.controlstocktextil.ui.perfil;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.ifpleonardo.controlstocktextil.data.model.Usuario;
import com.ifpleonardo.controlstocktextil.data.repository.AuthRepository;
import com.ifpleonardo.controlstocktextil.databinding.DialogPerfilUsuarioBinding;
import com.ifpleonardo.controlstocktextil.ui.auth.LoginActivity;

/**
 * Panel inferior (BottomSheet) de perfil de usuario.
 * 
 * Funcionalidades:
 * 1. Muestra nombre, email institucional y rol activo.
 * 2. Permite editar el nombre propio y cambiar la contraseña de acceso.
 * 3. Cierre seguro de sesión (logout) con confirmación previa y limpieza de listeners.
 */
public class PerfilBottomSheetDialogFragment extends BottomSheetDialogFragment {

    private DialogPerfilUsuarioBinding binding;

    public static PerfilBottomSheetDialogFragment newInstance() {
        return new PerfilBottomSheetDialogFragment();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = DialogPerfilUsuarioBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        Usuario usuario = AuthRepository.getInstance().getUsuarioActualLiveData().getValue();
        if (usuario != null) {
            binding.tvPerfilNombre.setText(usuario.getNombre());
            binding.tvPerfilEmail.setText(usuario.getEmail());
            binding.chipPerfilRol.setText(usuario.getRol() != null ? usuario.getRol().toUpperCase() : "PROFESORA");
        }

        binding.btnEditarPerfil.setOnClickListener(v -> mostrarDialogoEditarPerfil());

        binding.btnCerrarSesion.setOnClickListener(v -> confirmarCerrarSesion());
    }

    private void mostrarDialogoEditarPerfil() {
        Usuario usuario = AuthRepository.getInstance().getUsuarioActualLiveData().getValue();
        if (usuario == null) return;

        LinearLayout layout = new LinearLayout(requireContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(48, 24, 48, 16);

        TextInputLayout tilNombre = new TextInputLayout(requireContext(), null, com.google.android.material.R.style.Widget_Material3_TextInputLayout_OutlinedBox);
        tilNombre.setHint("Nombre y Apellidos");
        TextInputEditText etNombre = new TextInputEditText(tilNombre.getContext());
        etNombre.setText(usuario.getNombre());
        tilNombre.addView(etNombre);
        layout.addView(tilNombre);

        TextInputLayout tilPassword = new TextInputLayout(requireContext(), null, com.google.android.material.R.style.Widget_Material3_TextInputLayout_OutlinedBox);
        tilPassword.setHint("Nueva contraseña (dejar vacío para no cambiar)");
        tilPassword.setPasswordVisibilityToggleEnabled(true);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = 24;
        tilPassword.setLayoutParams(lp);
        TextInputEditText etPassword = new TextInputEditText(tilPassword.getContext());
        etPassword.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        tilPassword.addView(etPassword);
        layout.addView(tilPassword);

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Editar Perfil")
                .setView(layout)
                .setPositiveButton("Guardar", (dialog, which) -> {
                    String nuevoNombre = etNombre.getText() != null ? etNombre.getText().toString().trim() : "";
                    String nuevaPassword = etPassword.getText() != null ? etPassword.getText().toString() : "";

                    if (nuevoNombre.isEmpty()) {
                        Toast.makeText(requireContext(), "El nombre no puede estar vacío", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    AuthRepository.getInstance().actualizarPerfil(nuevoNombre, nuevaPassword, new AuthRepository.OperationCallback() {
                        @Override
                        public void onSuccess() {
                            Toast.makeText(requireContext(), "Perfil actualizado correctamente", Toast.LENGTH_SHORT).show();
                            if (binding != null) {
                                binding.tvPerfilNombre.setText(nuevoNombre);
                            }
                        }

                        @Override
                        public void onError(String error) {
                            Toast.makeText(requireContext(), "Error: " + error, Toast.LENGTH_LONG).show();
                        }
                    });
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void confirmarCerrarSesion() {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Cerrar sesión")
                .setMessage("¿Estás segura de que deseas salir?")
                .setPositiveButton("Sí, salir", (dialog, which) -> {
                    dismiss();
                    AuthRepository.getInstance().logout();
                    Intent intent = new Intent(requireActivity(), LoginActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    requireActivity().finish();
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}