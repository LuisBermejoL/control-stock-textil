package com.ifpleonardo.controlstocktextil.ui.detalle;

import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import com.bumptech.glide.Glide;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.ifpleonardo.controlstocktextil.data.model.Articulo;
import com.ifpleonardo.controlstocktextil.data.model.Ubicacion;
import com.ifpleonardo.controlstocktextil.data.repository.InventarioRepository;
import com.ifpleonardo.controlstocktextil.databinding.ActivityEditarArticuloBinding;
import com.ifpleonardo.controlstocktextil.util.CloudinaryUploader;
import com.ifpleonardo.controlstocktextil.util.ImageCompressor;

import java.io.File;

/**
 * Formulario para el alta y modificación técnica de artículos textiles.
 * 
 * Acciones:
 * 1. Validación de campos obligatorios (Categoría, Artículo, Unidad de medida).
 * 2. Asignación de ubicación en almacén (módulo y estantería).
 * 3. Incorporación o reemplazo de la fotografía del material.
 * 4. Guardado seguro en Cloud Firestore mediante InventarioRepository.
 */
public class EditarArticuloActivity extends AppCompatActivity {

    public static final String EXTRA_ARTICULO = "extra_articulo";

    private ActivityEditarArticuloBinding binding;
    private Articulo articulo;
    private boolean esNuevo = true;
    private String imagenUrlSubida = "";
    private Uri cameraTempUri;

    private static final String[] CATEGORIAS = {
            "HILOS", "ENTRETELA", "TEJIDO", "FORRO",
            "BOTONES", "CREMALLERAS", "LIGUETA", "PERCHAS",
            "BOLSAS", "ETIQUETAS"
    };

    private static final String[] UNIDADES = {
            "METROS", "BOBINAS", "UNIDADES"
    };

    private final ActivityResultLauncher<String> galleryLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) {
                    subirFotoACloudinary(uri);
                }
            });

    private final ActivityResultLauncher<Uri> cameraLauncher =
            registerForActivityResult(new ActivityResultContracts.TakePicture(), success -> {
                if (Boolean.TRUE.equals(success) && cameraTempUri != null) {
                    subirFotoACloudinary(cameraTempUri);
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityEditarArticuloBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setSupportActionBar(binding.toolbarEditar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        articulo = (Articulo) getIntent().getSerializableExtra(EXTRA_ARTICULO);
        if (articulo == null) {
            articulo = new Articulo();
            esNuevo = true;
            setTitle("Nuevo Artículo");
        } else {
            esNuevo = false;
            setTitle("Editar Artículo");
            imagenUrlSubida = articulo.getImagenUrl() != null ? articulo.getImagenUrl() : "";
        }

        setupDropdowns();
        poblarDatos();
        mostrarFoto();
        setupListeners();
    }

    private void setupDropdowns() {
        ArrayAdapter<String> catAdapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, CATEGORIAS);
        binding.actvCategoria.setAdapter(catAdapter);

        ArrayAdapter<String> uniAdapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, UNIDADES);
        binding.actvUnidadMedida.setAdapter(uniAdapter);
    }

    private void poblarDatos() {
        if (!esNuevo) {
            binding.actvCategoria.setText(articulo.getCategoria() != null ? articulo.getCategoria() : "", false);
            binding.etArticulo.setText(articulo.getArticulo() != null ? articulo.getArticulo() : "");
            binding.etSubtipo.setText(articulo.getSubtipo() != null ? articulo.getSubtipo() : "");
            binding.etReferencia.setText(articulo.getReferencia() != null ? articulo.getReferencia() : "");
            binding.etStockActual.setText(String.valueOf(articulo.getStockActual()));
            binding.etStockMinimo.setText(String.valueOf(articulo.getStockMinimo()));
            binding.actvUnidadMedida.setText(articulo.getUnidadMedida() != null ? articulo.getUnidadMedida() : "METROS", false);

            if (articulo.getUbicacion() != null) {
                binding.etAlmacen.setText(articulo.getUbicacion().getAlmacen());
                binding.etEstanteria.setText(articulo.getUbicacion().getEstanteria());
            }

            binding.etProveedor.setText(articulo.getProveedor() != null ? articulo.getProveedor() : "");
            binding.etColor.setText(articulo.getColor() != null ? articulo.getColor() : "");
            binding.etColorProveedor.setText(articulo.getColorProveedor() != null ? articulo.getColorProveedor() : "");
            binding.etComposicion.setText(articulo.getComposicion() != null ? articulo.getComposicion() : "");
            binding.etObservaciones.setText(articulo.getObservaciones() != null ? articulo.getObservaciones() : "");
        } else {
            binding.actvCategoria.setText(CATEGORIAS[0], false);
            binding.actvUnidadMedida.setText("METROS", false);
            binding.etStockActual.setText("0");
            binding.etStockMinimo.setText("0");
        }
    }

    private void mostrarFoto() {
        if (imagenUrlSubida != null && !imagenUrlSubida.trim().isEmpty()) {
            Glide.with(this)
                    .load(imagenUrlSubida)
                    .centerCrop()
                    .placeholder(android.R.drawable.ic_menu_gallery)
                    .into(binding.ivEditarFoto);
            binding.btnCambiarFotoEditar.setText("📸 Cambiar Foto");
        } else {
            binding.ivEditarFoto.setImageResource(android.R.drawable.ic_menu_gallery);
            binding.btnCambiarFotoEditar.setText("📸 Añadir Foto");
        }
    }

    private void mostrarDialogoSeleccionFoto() {
        String[] opciones = {"📷 Tomar foto con la Cámara", "🖼️ Seleccionar de la Galería"};
        new MaterialAlertDialogBuilder(this)
                .setTitle("Foto del Tejido / Producto")
                .setItems(opciones, (dialog, which) -> {
                    if (which == 0) {
                        lanzarCamara();
                    } else {
                        galleryLauncher.launch("image/*");
                    }
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void lanzarCamara() {
        try {
            File tempFile = new File(getCacheDir(), "cam_edit_" + System.currentTimeMillis() + ".jpg");
            cameraTempUri = FileProvider.getUriForFile(
                    this, getPackageName() + ".fileprovider", tempFile);
            cameraLauncher.launch(cameraTempUri);
        } catch (Exception e) {
            Toast.makeText(this, "Error al abrir la cámara: " + e.getLocalizedMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void subirFotoACloudinary(Uri uri) {
        binding.pbSubiendoFotoEditar.setVisibility(View.VISIBLE);
        binding.btnCambiarFotoEditar.setEnabled(false);

        new Thread(() -> {
            try {
                File archivoComprimido = ImageCompressor.comprimirImagen(EditarArticuloActivity.this, uri);
                CloudinaryUploader.subirImagen(EditarArticuloActivity.this, archivoComprimido, new CloudinaryUploader.UploadCallback() {
                    @Override
                    public void onSuccess(String imageUrl) {
                        runOnUiThread(() -> {
                            binding.pbSubiendoFotoEditar.setVisibility(View.GONE);
                            binding.btnCambiarFotoEditar.setEnabled(true);
                            imagenUrlSubida = imageUrl;
                            mostrarFoto();
                            Toast.makeText(EditarArticuloActivity.this, "Foto subida con éxito", Toast.LENGTH_SHORT).show();
                        });
                    }

                    @Override
                    public void onError(String error) {
                        runOnUiThread(() -> {
                            binding.pbSubiendoFotoEditar.setVisibility(View.GONE);
                            binding.btnCambiarFotoEditar.setEnabled(true);
                            Toast.makeText(EditarArticuloActivity.this, "Error al subir: " + error, Toast.LENGTH_LONG).show();
                        });
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    binding.pbSubiendoFotoEditar.setVisibility(View.GONE);
                    binding.btnCambiarFotoEditar.setEnabled(true);
                    Toast.makeText(EditarArticuloActivity.this, "Error de compresión: " + e.getLocalizedMessage(), Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    private void setupListeners() {
        binding.btnCambiarFotoEditar.setOnClickListener(v -> mostrarDialogoSeleccionFoto());
        binding.cardFotoEditar.setOnClickListener(v -> mostrarDialogoSeleccionFoto());
        binding.btnGuardar.setOnClickListener(v -> guardarArticulo());
    }

    private void guardarArticulo() {
        String categoria = binding.actvCategoria.getText().toString().trim();
        String nombre = binding.etArticulo.getText() != null ? binding.etArticulo.getText().toString().trim() : "";
        String referencia = binding.etReferencia.getText() != null ? binding.etReferencia.getText().toString().trim() : "";

        if (nombre.isEmpty()) {
            binding.tilArticulo.setError("Introduce el nombre del artículo");
            return;
        } else {
            binding.tilArticulo.setError(null);
        }

        if (referencia.isEmpty()) {
            binding.tilReferencia.setError("Introduce la referencia");
            return;
        } else {
            binding.tilReferencia.setError(null);
        }

        double stockActual = 0.0;
        try {
            String stockStr = binding.etStockActual.getText() != null ? binding.etStockActual.getText().toString().trim() : "0";
            stockActual = Double.parseDouble(stockStr.replace(",", "."));
        } catch (NumberFormatException ignored) {}

        double stockMinimo = 0.0;
        try {
            String minStr = binding.etStockMinimo.getText() != null ? binding.etStockMinimo.getText().toString().trim() : "0";
            stockMinimo = Double.parseDouble(minStr.replace(",", "."));
        } catch (NumberFormatException ignored) {}

        String subtipo = binding.etSubtipo.getText() != null ? binding.etSubtipo.getText().toString().trim() : "";
        String unidadMedida = binding.actvUnidadMedida.getText().toString().trim();
        String almacen = binding.etAlmacen.getText() != null ? binding.etAlmacen.getText().toString().trim() : "";
        String estanteria = binding.etEstanteria.getText() != null ? binding.etEstanteria.getText().toString().trim() : "";
        String proveedor = binding.etProveedor.getText() != null ? binding.etProveedor.getText().toString().trim() : "";
        String color = binding.etColor.getText() != null ? binding.etColor.getText().toString().trim() : "";
        String colorProv = binding.etColorProveedor.getText() != null ? binding.etColorProveedor.getText().toString().trim() : "";
        String composicion = binding.etComposicion.getText() != null ? binding.etComposicion.getText().toString().trim() : "";
        String observaciones = binding.etObservaciones.getText() != null ? binding.etObservaciones.getText().toString().trim() : "";

        articulo.setCategoria(categoria);
        articulo.setArticulo(nombre);
        articulo.setSubtipo(subtipo);
        articulo.setReferencia(referencia);
        articulo.setStockActual(stockActual);
        articulo.setStockMinimo(stockMinimo);
        articulo.setUnidadMedida(unidadMedida);
        articulo.setUbicacion(new Ubicacion(almacen, estanteria));
        articulo.setProveedor(proveedor);
        articulo.setColor(color);
        articulo.setColorProveedor(colorProv);
        articulo.setComposicion(composicion);
        articulo.setObservaciones(observaciones);
        articulo.setImagenUrl(imagenUrlSubida);
        articulo.setActivo(true);

        binding.btnGuardar.setEnabled(false);

        InventarioRepository.getInstance().guardarArticulo(articulo, new InventarioRepository.OperationCallback() {
            @Override
            public void onSuccess() {
                Toast.makeText(EditarArticuloActivity.this, "Artículo guardado con éxito", Toast.LENGTH_SHORT).show();
                finish();
            }

            @Override
            public void onError(String error) {
                binding.btnGuardar.setEnabled(true);
                Toast.makeText(EditarArticuloActivity.this, "Error al guardar: " + error, Toast.LENGTH_LONG).show();
            }
        });
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}
