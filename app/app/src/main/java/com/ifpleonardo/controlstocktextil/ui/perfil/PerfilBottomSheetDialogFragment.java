package com.ifpleonardo.controlstocktextil.ui.perfil;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;

import com.bumptech.glide.Glide;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.ifpleonardo.controlstocktextil.R;
import com.ifpleonardo.controlstocktextil.data.model.Usuario;
import com.ifpleonardo.controlstocktextil.data.repository.AuthRepository;
import com.ifpleonardo.controlstocktextil.databinding.DialogPerfilUsuarioBinding;
import com.ifpleonardo.controlstocktextil.ui.auth.LoginActivity;
import com.ifpleonardo.controlstocktextil.util.CloudinaryUploader;

import java.io.File;

/**
 * Panel inferior (BottomSheet) de perfil de usuario.
 * 
 * Funcionalidades:
 * 1. Muestra nombre, email institucional, rol activo y foto de perfil (o donut
 * por defecto).
 * 2. Permite editar el nombre propio y cambiar la contraseña de acceso.
 * 3. Permite subir o actualizar la foto de perfil con compresión local y
 * Cloudinary CDN.
 * 4. Cierre seguro de sesión (logout) con confirmación previa y limpieza de
 * listeners.
 */
public class PerfilBottomSheetDialogFragment extends BottomSheetDialogFragment {

    private DialogPerfilUsuarioBinding binding;
    private Uri cameraTempUri;
    private File cameraTempFile;

    private final ActivityResultLauncher<String> galleryLauncher = registerForActivityResult(
            new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) {
                    subirFotoACloudinary(uri, null);
                }
            });

    private final ActivityResultLauncher<Uri> cameraLauncher = registerForActivityResult(
            new ActivityResultContracts.TakePicture(), success -> {
                if (Boolean.TRUE.equals(success) && cameraTempUri != null) {
                    File temp = cameraTempFile;
                    cameraTempFile = null;
                    subirFotoACloudinary(cameraTempUri, temp);
                } else if (cameraTempFile != null) {
                    cameraTempFile.delete();
                    cameraTempFile = null;
                }
            });

    public static PerfilBottomSheetDialogFragment newInstance() {
        return new PerfilBottomSheetDialogFragment();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        binding = DialogPerfilUsuarioBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        Usuario usuario = AuthRepository.getInstance().getUsuarioActualLiveData().getValue();
        if (usuario != null) {
            actualizarDatosUsuarioEnVista(usuario);
        }

        AuthRepository.getInstance().getUsuarioActualLiveData().observe(getViewLifecycleOwner(), u -> {
            if (u != null && binding != null) {
                actualizarDatosUsuarioEnVista(u);
            }
        });

        binding.cardPerfilAvatar.setOnClickListener(v -> mostrarDialogoSeleccionFoto());
        binding.btnCambiarFotoPerfil.setOnClickListener(v -> mostrarDialogoSeleccionFoto());
        binding.btnEditarPerfil.setOnClickListener(v -> mostrarDialogoEditarPerfil());
        binding.btnCerrarSesion.setOnClickListener(v -> confirmarCerrarSesion());
    }

    private void actualizarDatosUsuarioEnVista(Usuario usuario) {
        binding.tvPerfilNombre.setText(usuario.getNombre());
        binding.tvPerfilEmail.setText(usuario.getEmail());
        binding.chipPerfilRol.setText(usuario.getRolVisual());

        if (usuario.getFotoUrl() != null && !usuario.getFotoUrl().trim().isEmpty()) {
            Glide.with(this)
                    .load(usuario.getFotoUrl())
                    .circleCrop()
                    .placeholder(R.drawable.ic_donut_bitten)
                    .into(binding.ivPerfilAvatar);
            binding.btnCambiarFotoPerfil.setText("Cambiar foto de perfil");
        } else {
            binding.ivPerfilAvatar.setImageResource(R.drawable.ic_donut_bitten);
            binding.btnCambiarFotoPerfil.setText("Añadir foto de perfil");
        }
    }

    private void mostrarDialogoSeleccionFoto() {
        Usuario usuario = AuthRepository.getInstance().getUsuarioActualLiveData().getValue();
        boolean tieneFoto = usuario != null && usuario.getFotoUrl() != null && !usuario.getFotoUrl().trim().isEmpty();

        String[] opciones;
        if (tieneFoto) {
            opciones = new String[] { "📷 Tomar foto con la Cámara", "🖼️ Seleccionar de la Galería",
                    "🗑️ Eliminar foto actual" };
        } else {
            opciones = new String[] { "📷 Tomar foto con la Cámara", "🖼️ Seleccionar de la Galería" };
        }

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Foto de Perfil")
                .setItems(opciones, (dialog, which) -> {
                    if (which == 0) {
                        lanzarCamara();
                    } else if (which == 1) {
                        galleryLauncher.launch("image/*");
                    } else if (which == 2) {
                        eliminarFotoPerfil();
                    }
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void lanzarCamara() {
        try {
            File tempFile = new File(requireContext().getCacheDir(),
                    "cam_perfil_" + System.currentTimeMillis() + ".jpg");
            cameraTempFile = tempFile;
            cameraTempUri = FileProvider.getUriForFile(
                    requireContext(), requireContext().getPackageName() + ".fileprovider", tempFile);
            cameraLauncher.launch(cameraTempUri);
        } catch (Exception e) {
            Toast.makeText(requireContext(), "Error al abrir cámara: " + e.getLocalizedMessage(), Toast.LENGTH_SHORT)
                    .show();
        }
    }

    private void subirFotoACloudinary(Uri uri, @Nullable File archivoCamaraTemporal) {
        if (binding == null) {
            if (archivoCamaraTemporal != null && archivoCamaraTemporal.exists())
                archivoCamaraTemporal.delete();
            return;
        }
        binding.pbSubiendoFotoPerfil.setVisibility(View.VISIBLE);
        binding.btnCambiarFotoPerfil.setEnabled(false);

        CloudinaryUploader.comprimirYSubir(requireContext().getApplicationContext(), uri, archivoCamaraTemporal, true,
                new CloudinaryUploader.UploadCallback() {
                    @Override
                    public void onSuccess(String imageUrl) {
                        if (getActivity() == null)
                            return;
                        AuthRepository.getInstance().actualizarFotoPerfil(imageUrl,
                                new AuthRepository.OperationCallback() {
                                    @Override
                                    public void onSuccess() {
                                        if (binding != null) {
                                            binding.pbSubiendoFotoPerfil.setVisibility(View.GONE);
                                            binding.btnCambiarFotoPerfil.setEnabled(true);
                                        }
                                        if (isAdded() && getContext() != null) {
                                            Toast.makeText(getContext(), "Foto de perfil actualizada",
                                                    Toast.LENGTH_SHORT).show();
                                        }
                                    }

                                    @Override
                                    public void onError(String error) {
                                        if (binding != null) {
                                            binding.pbSubiendoFotoPerfil.setVisibility(View.GONE);
                                            binding.btnCambiarFotoPerfil.setEnabled(true);
                                        }
                                        if (isAdded() && getContext() != null) {
                                            Toast.makeText(getContext(), "Error al guardar foto: " + error,
                                                    Toast.LENGTH_LONG).show();
                                        }
                                    }
                                });
                    }

                    @Override
                    public void onError(String error) {
                        if (getActivity() == null)
                            return;
                        if (binding != null) {
                            binding.pbSubiendoFotoPerfil.setVisibility(View.GONE);
                            binding.btnCambiarFotoPerfil.setEnabled(true);
                        }
                        if (isAdded() && getContext() != null) {
                            Toast.makeText(getContext(), "Error al subir foto: " + error, Toast.LENGTH_LONG).show();
                        }
                    }
                });
    }

    private void eliminarFotoPerfil() {
        if (binding != null) {
            binding.pbSubiendoFotoPerfil.setVisibility(View.VISIBLE);
        }
        AuthRepository.getInstance().actualizarFotoPerfil("", new AuthRepository.OperationCallback() {
            @Override
            public void onSuccess() {
                if (binding != null) {
                    binding.pbSubiendoFotoPerfil.setVisibility(View.GONE);
                    binding.ivPerfilAvatar.setImageResource(R.drawable.ic_donut_bitten);
                    binding.btnCambiarFotoPerfil.setText("Añadir foto de perfil");
                }
                Toast.makeText(requireContext(), "Foto de perfil eliminada", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onError(String error) {
                if (binding != null) {
                    binding.pbSubiendoFotoPerfil.setVisibility(View.GONE);
                }
                Toast.makeText(requireContext(), "Error: " + error, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void mostrarDialogoEditarPerfil() {
        Usuario usuario = AuthRepository.getInstance().getUsuarioActualLiveData().getValue();
        if (usuario == null)
            return;

        LinearLayout layout = new LinearLayout(requireContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(48, 24, 48, 16);

        TextInputLayout tilNombre = new TextInputLayout(requireContext(), null,
                com.google.android.material.R.style.Widget_Material3_TextInputLayout_OutlinedBox);
        tilNombre.setHint("Nombre y Apellidos");
        TextInputEditText etNombre = new TextInputEditText(tilNombre.getContext());
        etNombre.setText(usuario.getNombre());
        tilNombre.addView(etNombre);
        layout.addView(tilNombre);

        TextInputLayout tilPassword = new TextInputLayout(requireContext(), null,
                com.google.android.material.R.style.Widget_Material3_TextInputLayout_OutlinedBox);
        tilPassword.setHint("Nueva contraseña");
        tilPassword.setPasswordVisibilityToggleEnabled(true);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = 24;
        tilPassword.setLayoutParams(lp);
        TextInputEditText etPassword = new TextInputEditText(tilPassword.getContext());
        etPassword.setInputType(
                android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
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

                    AuthRepository.getInstance().actualizarPerfil(nuevoNombre, nuevaPassword,
                            new AuthRepository.OperationCallback() {
                                @Override
                                public void onSuccess() {
                                    Toast.makeText(requireContext(), "Perfil actualizado correctamente",
                                            Toast.LENGTH_SHORT).show();
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
                .setMessage("¿Estás seguro/a de que deseas salir?")
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
