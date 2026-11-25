package com.ifpleonardo.controlstocktextil.ui.admin;

import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.ifpleonardo.controlstocktextil.data.model.Usuario;
import com.ifpleonardo.controlstocktextil.data.repository.UsuarioRepository;
import com.ifpleonardo.controlstocktextil.databinding.DialogEditarUsuarioBinding;

/**
 * Diálogo modal para la edición y baja lógica de una profesora seleccionada.
 * 
 * Permite a la administradora:
 * 1. Modificar nombre y rol (PROFESORA / ADMIN).
 * 2. Dar de baja lógica (desactivar cuenta) sin borrar los datos físicos para preservar el historial.
 * 3. Reactivar cuentas en caso de incorporación o sustitución docente.
 */
public class EditarUsuarioDialogFragment extends DialogFragment {

    private static final String ARG_USUARIO = "arg_usuario";
    private DialogEditarUsuarioBinding binding;
    private Usuario usuario;
    private static final String[] ROLES = {"LECTOR", "PROFESOR/A", "ADMIN"};

    public static EditarUsuarioDialogFragment newInstance(Usuario usuario) {
        EditarUsuarioDialogFragment fragment = new EditarUsuarioDialogFragment();
        Bundle args = new Bundle();
        args.putSerializable(ARG_USUARIO, usuario);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            usuario = (Usuario) getArguments().getSerializable(ARG_USUARIO);
        }
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        binding = DialogEditarUsuarioBinding.inflate(LayoutInflater.from(requireContext()));

        if (usuario != null) {
            binding.tvEditarEmailFijo.setText(usuario.getEmail());
            binding.etEditarNombre.setText(usuario.getNombre());

            ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_dropdown_item_1line, ROLES);
            binding.actvEditarRol.setAdapter(adapter);
            binding.actvEditarRol.setText(usuario.getRolVisual(), false);

            actualizarBotonEstado();
        }

        setupListeners();

        return new MaterialAlertDialogBuilder(requireContext())
                .setView(binding.getRoot())
                .create();
    }

    private void actualizarBotonEstado() {
        if (usuario.isActivo()) {
            binding.btnCambiarEstadoCuenta.setText("Desactivar Cuenta (Baja)");
            binding.btnCambiarEstadoCuenta.setIconResource(android.R.drawable.ic_delete);
        } else {
            binding.btnCambiarEstadoCuenta.setText("Reactivar Cuenta de Acceso");
            binding.btnCambiarEstadoCuenta.setIconResource(android.R.drawable.ic_input_add);
        }
    }

    private void setupListeners() {
        binding.btnCancelarEditar.setOnClickListener(v -> dismiss());

        binding.btnGuardarEditarUsuario.setOnClickListener(v -> {
            String nuevoNombre = binding.etEditarNombre.getText() != null ? binding.etEditarNombre.getText().toString().trim() : "";
            String nuevoRolSeleccionado = binding.actvEditarRol.getText().toString().trim();
            String nuevoRol = Usuario.mapRolVisualARolFirestore(nuevoRolSeleccionado);

            if (nuevoNombre.isEmpty()) {
                binding.tilEditarNombre.setError("Introduce el nombre completo");
                return;
            }

            binding.progressEditarUsuario.setVisibility(View.VISIBLE);
            binding.btnGuardarEditarUsuario.setEnabled(false);

            UsuarioRepository.getInstance().actualizarDatosProfesora(
                    usuario.getId(),
                    nuevoNombre,
                    nuevoRol,
                    new UsuarioRepository.OperationCallback() {
                        @Override
                        public void onSuccess() {
                            if (getContext() == null) return;
                            Toast.makeText(getContext(), "Datos actualizados correctamente", Toast.LENGTH_SHORT).show();
                            dismiss();
                        }

                        @Override
                        public void onError(String error) {
                            if (getContext() == null) return;
                            binding.progressEditarUsuario.setVisibility(View.GONE);
                            binding.btnGuardarEditarUsuario.setEnabled(true);
                            Toast.makeText(getContext(), "Error: " + error, Toast.LENGTH_LONG).show();
                        }
                    }
            );
        });

        binding.btnCambiarEstadoCuenta.setOnClickListener(v -> {
            boolean nuevoEstado = !usuario.isActivo();
            String accion = nuevoEstado ? "reactivar" : "desactivar";
            String explicacion = nuevoEstado
                    ? "¿Deseas volver a habilitar el acceso a esta cuenta?"
                    : "¿Deseas desactivar esta cuenta? El usuario ya no podrá iniciar sesión, pero todo su historial de movimientos pasados se conservará intacto.";

            new AlertDialog.Builder(requireContext())
                    .setTitle("Confirmar " + accion)
                    .setMessage(explicacion)
                    .setPositiveButton("Sí, " + accion, (dialog, which) -> {
                        binding.progressEditarUsuario.setVisibility(View.VISIBLE);
                        UsuarioRepository.getInstance().cambiarEstadoActivo(
                                usuario.getId(),
                                nuevoEstado,
                                new UsuarioRepository.OperationCallback() {
                                    @Override
                                    public void onSuccess() {
                                        if (getContext() == null) return;
                                        Toast.makeText(getContext(), "Cuenta " + (nuevoEstado ? "reactivada" : "desactivada") + " con éxito", Toast.LENGTH_SHORT).show();
                                        dismiss();
                                    }

                                    @Override
                                    public void onError(String error) {
                                        if (getContext() == null) return;
                                        binding.progressEditarUsuario.setVisibility(View.GONE);
                                        Toast.makeText(getContext(), "Error: " + error, Toast.LENGTH_LONG).show();
                                    }
                                }
                        );
                    })
                    .setNegativeButton("Cancelar", null)
                    .show();
        });
    }
}
