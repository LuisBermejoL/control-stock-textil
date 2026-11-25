package com.ifpleonardo.controlstocktextil.ui.detalle;

import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import com.bumptech.glide.Glide;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.ifpleonardo.controlstocktextil.R;
import com.ifpleonardo.controlstocktextil.data.model.Articulo;
import com.ifpleonardo.controlstocktextil.data.model.Usuario;
import com.ifpleonardo.controlstocktextil.data.repository.AuthRepository;
import com.ifpleonardo.controlstocktextil.data.repository.InventarioRepository;
import com.ifpleonardo.controlstocktextil.databinding.ActivityDetalleArticuloBinding;
import com.ifpleonardo.controlstocktextil.util.CloudinaryUploader;

import java.io.File;
import java.text.DecimalFormat;

/**
 * Ficha técnica y de control de stock de un artículo seleccionado.
 * 
 * Funcionalidades:
 * 1. Sincronización en tiempo real con Firestore mediante snapshot listener sobre el documento.
 * 2. Visualización y captura de fotografía de tejido/fornitura vía cámara o galería con compresión local y Cloudinary.
 * 3. Ejecución de operaciones de stock (Entradas, Salidas de consumo, Préstamos y Devoluciones).
 * 4. Generación, impresión y compartición de la etiqueta QR individual de estantería.
 * 5. Acceso exclusivo a edición de datos técnicos o desactivación si la usuaria tiene rol ADMIN.
 */
public class DetalleArticuloActivity extends AppCompatActivity {

    public static final String EXTRA_ARTICULO = "extra_articulo";

    private ActivityDetalleArticuloBinding binding;
    private Articulo articulo;
    private ListenerRegistration articuloListener;
    private final DecimalFormat decimalFormat = new DecimalFormat("#.##");
    private boolean esAdmin = false;

    private Uri cameraTempUri;
    private File cameraTempFile;

    private final ActivityResultLauncher<String> galleryLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) {
                    subirFotoACloudinary(uri, null);
                }
            });

    private final ActivityResultLauncher<Uri> cameraLauncher =
            registerForActivityResult(new ActivityResultContracts.TakePicture(), success -> {
                if (Boolean.TRUE.equals(success) && cameraTempUri != null) {
                    File temp = cameraTempFile;
                    cameraTempFile = null;
                    subirFotoACloudinary(cameraTempUri, temp);
                } else if (cameraTempFile != null) {
                    cameraTempFile.delete();
                    cameraTempFile = null;
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityDetalleArticuloBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setSupportActionBar(binding.toolbarDetalle);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        articulo = (Articulo) getIntent().getSerializableExtra(EXTRA_ARTICULO);
        if (articulo == null) {
            Toast.makeText(this, "No se encontró el artículo", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        Usuario usuario = AuthRepository.getInstance().getUsuarioActualLiveData().getValue();
        esAdmin = usuario != null && usuario.isAdmin();
        boolean puedeModificar = usuario != null && usuario.puedeModificarStock();

        setupListeners();
        aplicarPermisosUsuario(puedeModificar);
        mostrarDatosArticulo();
        iniciarEscuchaTiempoReal();

        AuthRepository.getInstance().getUsuarioActualLiveData().observe(this, u -> {
            if (u == null) {
                finish();
                return;
            }
            esAdmin = u.isAdmin();
            invalidateOptionsMenu();
            aplicarPermisosUsuario(u.puedeModificarStock());
        });
    }

    private void aplicarPermisosUsuario(boolean puedeModificar) {
        binding.fabRegistrarMovimiento.setVisibility(puedeModificar ? View.VISIBLE : View.GONE);
        binding.btnCambiarFoto.setVisibility(puedeModificar ? View.VISIBLE : View.GONE);
        binding.cardFotoArticulo.setClickable(puedeModificar);
        binding.cardFotoArticulo.setEnabled(puedeModificar);
    }

    private void iniciarEscuchaTiempoReal() {
        if (articulo.getId() == null) return;

        articuloListener = FirebaseFirestore.getInstance()
                .collection("articulos")
                .document(articulo.getId())
                .addSnapshotListener((snapshot, error) -> {
                    if (error != null || snapshot == null || !snapshot.exists()) {
                        return;
                    }
                    Articulo actualizado = snapshot.toObject(Articulo.class);
                    if (actualizado != null) {
                        actualizado.setId(snapshot.getId());
                        this.articulo = actualizado;
                        mostrarDatosArticulo();
                    }
                });
    }

    private void mostrarDatosArticulo() {
        binding.chipDetalleCategoria.setText(articulo.getCategoria() != null ? articulo.getCategoria() : "GENERAL");
        binding.tvDetalleNombre.setText(articulo.getNombreCompleto());
        binding.tvDetalleReferencia.setText("Referencia: " + (articulo.getReferencia() != null ? articulo.getReferencia() : "Sin referencia"));

        // Stock y Badge con singular/plural automático
        double stock = articulo.getStockActual();
        String unidad = articulo.getUnidadMedida() != null ? articulo.getUnidadMedida() : "";
        binding.tvDetalleStockPill.setText(Articulo.formatearStockConUnidad(stock, unidad, decimalFormat));

        if (stock <= 0) {
            binding.tvDetalleAlerta.setVisibility(View.VISIBLE);
            binding.tvDetalleAlerta.setText("❌ Stock Agotado (" + Articulo.formatearStockConUnidad(0, unidad, decimalFormat) + ")");
            binding.tvDetalleStockPill.setTextColor(Color.RED);
        } else if (articulo.isBajoMinimos()) {
            binding.tvDetalleAlerta.setVisibility(View.VISIBLE);
            binding.tvDetalleAlerta.setText("⚠️ Stock bajo mínimos (Mínimo: " + Articulo.formatearStockConUnidad(articulo.getStockMinimo(), unidad, decimalFormat) + ")");
            binding.tvDetalleStockPill.setTextColor(ContextCompat.getColor(this, android.R.color.holo_orange_dark));
        } else {
            binding.tvDetalleAlerta.setVisibility(View.GONE);
            binding.tvDetalleStockPill.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark));
        }

        // Ubicación
        if (articulo.getUbicacion() != null) {
            binding.tvDetalleUbicacion.setText(articulo.getUbicacion().getFormatoCompleto());
        } else {
            binding.tvDetalleUbicacion.setText("Sin ubicación asignada");
        }

        // Especificaciones Textiles
        binding.tvDetalleComposicion.setText("Composición: " + (articulo.getComposicion() != null && !articulo.getComposicion().isEmpty() ? articulo.getComposicion() : "No especificada"));

        String anchoStr = "No especificado";
        if (articulo.getAnchoUtil() != null) {
            anchoStr = decimalFormat.format(articulo.getAnchoUtil()) + " m";
            if (articulo.getFormatoAncho() != null && !articulo.getFormatoAncho().isEmpty()) {
                anchoStr += " (" + articulo.getFormatoAncho() + ")";
            }
        }
        binding.tvDetalleAncho.setText("Ancho útil: " + anchoStr);

        binding.tvDetalleRollos.setText("Nº de rollos/piezas: " + Articulo.formatearRollos(articulo.getNumRollos()));

        String precioStr = articulo.getPrecioPorMetro() != null ? decimalFormat.format(articulo.getPrecioPorMetro()) + " € / metro" : "No especificado";
        binding.tvDetallePrecio.setText("Precio: " + precioStr);

        // Proveedor y Códigos
        binding.tvDetalleProveedor.setText("Proveedor: " + (articulo.getProveedor() != null && !articulo.getProveedor().isEmpty() ? articulo.getProveedor() : "No especificado"));
        binding.tvDetalleRefProv.setText("Ref. Proveedor: " + (articulo.getReferenciaProveedor() != null && !articulo.getReferenciaProveedor().isEmpty() ? articulo.getReferenciaProveedor() : "No especificada"));

        String colorCompleto = (articulo.getColor() != null && !articulo.getColor().isEmpty() ? articulo.getColor() : "No especificado");
        if (articulo.getColorProveedor() != null && !articulo.getColorProveedor().isEmpty()) {
            colorCompleto += " · Código prov.: " + articulo.getColorProveedor();
        }
        binding.tvDetalleColor.setText("Color: " + colorCompleto);

        // Observaciones
        binding.tvDetalleObservaciones.setText(articulo.getObservaciones() != null && !articulo.getObservaciones().isEmpty() ? articulo.getObservaciones() : "Sin observaciones registradas.");

        mostrarFotoArticulo();
    }

    private void mostrarFotoArticulo() {
        if (articulo.getImagenUrl() != null && !articulo.getImagenUrl().trim().isEmpty()) {
            Glide.with(this)
                    .load(articulo.getImagenUrl())
                    .centerCrop()
                    .placeholder(android.R.drawable.ic_menu_gallery)
                    .into(binding.ivDetalleFoto);
            binding.btnCambiarFoto.setText("📸 Cambiar Foto");
        } else {
            binding.ivDetalleFoto.setImageResource(android.R.drawable.ic_menu_gallery);
            binding.btnCambiarFoto.setText("📸 Añadir Foto");
        }
    }

    private void mostrarDialogoSeleccionFoto() {
        Usuario usuarioActual = AuthRepository.getInstance().getUsuarioActualLiveData().getValue();
        if (usuarioActual == null || !usuarioActual.puedeModificarStock()) {
            Toast.makeText(this, "Modo solo lectura: necesitas rol Profesor/a para modificar fotos", Toast.LENGTH_SHORT).show();
            return;
        }
        boolean tieneFoto = articulo.getImagenUrl() != null && !articulo.getImagenUrl().trim().isEmpty();
        String[] opciones;
        if (tieneFoto) {
            opciones = new String[]{"📷 Tomar nueva foto con la Cámara", "🖼️ Seleccionar de la Galería", "🗑️ Quitar / Eliminar foto actual"};
        } else {
            opciones = new String[]{"📷 Tomar foto con la Cámara", "🖼️ Seleccionar de la Galería"};
        }

        new MaterialAlertDialogBuilder(this)
                .setTitle("Foto del Tejido / Producto")
                .setItems(opciones, (dialog, which) -> {
                    if (which == 0) {
                        lanzarCamara();
                    } else if (which == 1) {
                        galleryLauncher.launch("image/*");
                    } else if (which == 2) {
                        eliminarFotoArticulo();
                    }
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void eliminarFotoArticulo() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Eliminar fotografía")
                .setMessage("¿Estás segura de que deseas quitar la foto de este artículo?")
                .setPositiveButton("Sí, eliminar", (dialog, which) -> {
                    articulo.setImagenUrl("");
                    mostrarFotoArticulo();
                    if (articulo.getId() != null) {
                        FirebaseFirestore.getInstance().collection("articulos")
                                .document(articulo.getId())
                                .update("imagenUrl", "")
                                .addOnSuccessListener(aVoid -> Toast.makeText(this, "Foto eliminada con éxito", Toast.LENGTH_SHORT).show())
                                .addOnFailureListener(e -> Toast.makeText(this, "Error al quitar foto: " + e.getLocalizedMessage(), Toast.LENGTH_SHORT).show());
                    }
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void lanzarCamara() {
        try {
            File tempFile = new File(getCacheDir(), "cam_" + System.currentTimeMillis() + ".jpg");
            cameraTempFile = tempFile;
            cameraTempUri = FileProvider.getUriForFile(
                    this, getPackageName() + ".fileprovider", tempFile);
            cameraLauncher.launch(cameraTempUri);
        } catch (Exception e) {
            Toast.makeText(this, "Error al abrir cámara: " + e.getLocalizedMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void subirFotoACloudinary(Uri uri, @Nullable File archivoCamaraTemporal) {
        binding.pbSubiendoFoto.setVisibility(View.VISIBLE);
        binding.btnCambiarFoto.setEnabled(false);

        CloudinaryUploader.comprimirYSubir(getApplicationContext(), uri, archivoCamaraTemporal, false, new CloudinaryUploader.UploadCallback() {
            @Override
            public void onSuccess(String imageUrl) {
                if (isFinishing() || isDestroyed()) return;
                binding.pbSubiendoFoto.setVisibility(View.GONE);
                binding.btnCambiarFoto.setEnabled(true);
                articulo.setImagenUrl(imageUrl);
                mostrarFotoArticulo();

                if (articulo.getId() != null) {
                    FirebaseFirestore.getInstance().collection("articulos")
                            .document(articulo.getId())
                            .update("imagenUrl", imageUrl);
                }
                Toast.makeText(DetalleArticuloActivity.this, "Foto guardada con éxito", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onError(String error) {
                if (isFinishing() || isDestroyed()) return;
                binding.pbSubiendoFoto.setVisibility(View.GONE);
                binding.btnCambiarFoto.setEnabled(true);
                Toast.makeText(DetalleArticuloActivity.this, error, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void setupListeners() {
        binding.btnCambiarFoto.setOnClickListener(v -> mostrarDialogoSeleccionFoto());
        binding.cardFotoArticulo.setOnClickListener(v -> mostrarDialogoSeleccionFoto());

        binding.btnVerEtiquetaQr.setOnClickListener(v -> {
            EtiquetaQrDialogFragment dialog = EtiquetaQrDialogFragment.newInstance(articulo);
            dialog.show(getSupportFragmentManager(), "EtiquetaQrDialogFragment");
        });

        binding.fabRegistrarMovimiento.setOnClickListener(v -> {
            MovimientoDialogFragment dialog = MovimientoDialogFragment.newInstance(articulo);
            dialog.setOnMovimientoRealizadoListener(nuevoStock -> {
                articulo.setStockActual(nuevoStock);
                mostrarDatosArticulo();
            });
            dialog.show(getSupportFragmentManager(), "MovimientoDialogFragment");
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_detalle_articulo, menu);
        if (!esAdmin) {
            MenuItem editItem = menu.findItem(R.id.action_editar_articulo);
            if (editItem != null) editItem.setVisible(false);
            MenuItem deleteItem = menu.findItem(R.id.action_desactivar_articulo);
            if (deleteItem != null) deleteItem.setVisible(false);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        } else if (item.getItemId() == R.id.action_ver_qr) {
            EtiquetaQrDialogFragment dialog = EtiquetaQrDialogFragment.newInstance(articulo);
            dialog.show(getSupportFragmentManager(), "EtiquetaQrDialogFragment");
            return true;
        } else if (item.getItemId() == R.id.action_editar_articulo) {
            Intent intent = new Intent(this, EditarArticuloActivity.class);
            intent.putExtra(EditarArticuloActivity.EXTRA_ARTICULO, articulo);
            startActivity(intent);
            return true;
        } else if (item.getItemId() == R.id.action_desactivar_articulo) {
            confirmarDesactivacion();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void confirmarDesactivacion() {
        new AlertDialog.Builder(this)
                .setTitle("Desactivar artículo")
                .setMessage("¿Estás segura de que deseas desactivar este artículo del inventario activo?")
                .setPositiveButton("Sí, desactivar", (dialog, which) -> {
                    InventarioRepository.getInstance().desactivarArticulo(articulo.getId(), new InventarioRepository.OperationCallback() {
                        @Override
                        public void onSuccess() {
                            Toast.makeText(DetalleArticuloActivity.this, "Artículo desactivado correctamente", Toast.LENGTH_SHORT).show();
                            finish();
                        }

                        @Override
                        public void onError(String error) {
                            Toast.makeText(DetalleArticuloActivity.this, "Error al desactivar: " + error, Toast.LENGTH_LONG).show();
                        }
                    });
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (articuloListener != null) {
            articuloListener.remove();
        }
    }
}
