package com.ifpleonardo.controlstocktextil.ui.scanner;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.mlkit.vision.barcode.BarcodeScanner;
import com.google.mlkit.vision.barcode.BarcodeScanning;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.common.InputImage;
import com.ifpleonardo.controlstocktextil.data.model.Articulo;
import com.ifpleonardo.controlstocktextil.databinding.ActivityScannerBinding;
import com.ifpleonardo.controlstocktextil.ui.detalle.DetalleArticuloActivity;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Escáner de Códigos QR y Códigos de Barras en tiempo real.
 * 
 * Tecnologías y rendimiento:
 * 1. CameraX: Gestión del ciclo de vida de la cámara en Android con selector de cámara trasera.
 * 2. Google ML Kit Barcode Scanning: Detección ultrarrápida en el hilo de análisis secundario.
 * 3. Búsqueda inteligente: Resuelve por ID directo de documento o por referencia interna del artículo.
 * 4. Control de linterna / flash para entornos de almacén con baja iluminación.
 */
public class ScannerActivity extends AppCompatActivity {

    private ActivityScannerBinding binding;
    private ExecutorService cameraExecutor;
    private Camera camera;
    private boolean linternaEncendida = false;
    private boolean procesandoCodigo = false;

    private final ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    iniciarCamara();
                } else {
                    Toast.makeText(this, "Se necesita permiso de cámara para escanear", Toast.LENGTH_LONG).show();
                    finish();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityScannerBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setSupportActionBar(binding.toolbarScanner);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        cameraExecutor = Executors.newSingleThreadExecutor();

        setupListeners();
        verificarPermisoCamara();
    }

    private void setupListeners() {
        binding.fabFlash.setOnClickListener(v -> {
            if (camera != null && camera.getCameraInfo().hasFlashUnit()) {
                linternaEncendida = !linternaEncendida;
                camera.getCameraControl().enableTorch(linternaEncendida);
                binding.fabFlash.setImageDrawable(ContextCompat.getDrawable(this,
                        linternaEncendida ? android.R.drawable.ic_menu_close_clear_cancel : android.R.drawable.ic_menu_camera));
            }
        });
    }

    private void verificarPermisoCamara() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            iniciarCamara();
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA);
        }
    }

    private void iniciarCamara() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();

                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(binding.previewView.getSurfaceProvider());

                ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();

                BarcodeScanner scanner = BarcodeScanning.getClient();

                imageAnalysis.setAnalyzer(cameraExecutor, imageProxy -> procesarImagen(imageProxy, scanner));

                CameraSelector cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;

                cameraProvider.unbindAll();
                camera = cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis);

            } catch (ExecutionException | InterruptedException e) {
                Toast.makeText(this, "Error al iniciar cámara: " + e.getLocalizedMessage(), Toast.LENGTH_SHORT).show();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    @SuppressLint("UnsafeOptInUsageError")
    private void procesarImagen(ImageProxy imageProxy, BarcodeScanner scanner) {
        if (procesandoCodigo) {
            imageProxy.close();
            return;
        }

        if (imageProxy.getImage() != null) {
            InputImage image = InputImage.fromMediaImage(
                    imageProxy.getImage(),
                    imageProxy.getImageInfo().getRotationDegrees()
            );

            scanner.process(image)
                    .addOnSuccessListener(barcodes -> {
                        for (Barcode barcode : barcodes) {
                            String rawValue = barcode.getRawValue();
                            if (rawValue != null && !rawValue.trim().isEmpty() && !procesandoCodigo) {
                                procesandoCodigo = true;
                                runOnUiThread(() -> buscarArticuloPorCodigo(rawValue.trim()));
                                break;
                            }
                        }
                    })
                    .addOnCompleteListener(task -> imageProxy.close());
        } else {
            imageProxy.close();
        }
    }

    private void buscarArticuloPorCodigo(String codigo) {
        binding.progressScanner.setVisibility(View.VISIBLE);

        FirebaseFirestore db = FirebaseFirestore.getInstance();

        // 1. Intentar buscar por documento directo (ID)
        db.collection("articulos").document(codigo).get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        Articulo articulo = documentSnapshot.toObject(Articulo.class);
                        if (articulo != null) {
                            articulo.setId(documentSnapshot.getId());
                            abrirFichaArticulo(articulo);
                            return;
                        }
                    }
                    // 2. Si no es un ID directo, buscar por campo "referencia"
                    buscarPorReferencia(codigo);
                })
                .addOnFailureListener(e -> buscarPorReferencia(codigo));
    }

    private void buscarPorReferencia(String referencia) {
        FirebaseFirestore.getInstance().collection("articulos")
                .whereEqualTo("referencia", referencia)
                .whereEqualTo("activo", true)
                .limit(1)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    binding.progressScanner.setVisibility(View.GONE);
                    if (!queryDocumentSnapshots.isEmpty()) {
                        DocumentSnapshot doc = queryDocumentSnapshots.getDocuments().get(0);
                        Articulo articulo = doc.toObject(Articulo.class);
                        if (articulo != null) {
                            articulo.setId(doc.getId());
                            abrirFichaArticulo(articulo);
                            return;
                        }
                    }

                    // No encontrado
                    mostrarDialogoNoEncontrado(referencia);
                })
                .addOnFailureListener(e -> {
                    binding.progressScanner.setVisibility(View.GONE);
                    mostrarDialogoNoEncontrado(referencia);
                });
    }

    private void abrirFichaArticulo(Articulo articulo) {
        Intent intent = new Intent(this, DetalleArticuloActivity.class);
        intent.putExtra(DetalleArticuloActivity.EXTRA_ARTICULO, articulo);
        startActivity(intent);
        finish();
    }

    private void mostrarDialogoNoEncontrado(String codigo) {
        new AlertDialog.Builder(this)
                .setTitle("Artículo no encontrado")
                .setMessage("No se encontró ningún artículo registrado con el código o referencia:\n\n\"" + codigo + "\"")
                .setPositiveButton("Escanear otro", (dialog, which) -> procesandoCodigo = false)
                .setNegativeButton("Volver", (dialog, which) -> finish())
                .setCancelable(false)
                .show();
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
        if (cameraExecutor != null) {
            cameraExecutor.shutdown();
        }
    }
}
