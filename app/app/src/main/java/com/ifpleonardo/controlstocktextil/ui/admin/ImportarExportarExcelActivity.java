package com.ifpleonardo.controlstocktextil.ui.admin;

import android.net.Uri;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.ifpleonardo.controlstocktextil.data.model.Articulo;
import com.ifpleonardo.controlstocktextil.data.model.Usuario;
import com.ifpleonardo.controlstocktextil.data.repository.AuthRepository;
import com.ifpleonardo.controlstocktextil.data.repository.InventarioRepository;
import com.ifpleonardo.controlstocktextil.databinding.ActivityImportExportExcelBinding;
import com.ifpleonardo.controlstocktextil.util.ExcelManager;

import java.io.File;
import java.io.InputStream;
import java.util.List;

/**
 * Actividad para la exportación e importación directa de datos en hojas de cálculo Excel y CSV.
 */
public class ImportarExportarExcelActivity extends AppCompatActivity {

    private ActivityImportExportExcelBinding binding;

    private final ActivityResultLauncher<String> filePickerLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) {
                    procesarArchivoExcelSeleccionado(uri);
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityImportExportExcelBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setSupportActionBar(binding.toolbarImportExport);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        setupListeners();

        Usuario usuario = AuthRepository.getInstance().getUsuarioActualLiveData().getValue();
        boolean esAdmin = usuario != null && usuario.isAdmin();
        binding.btnImportarExcel.setVisibility(esAdmin ? View.VISIBLE : View.GONE);
    }

    private void setupListeners() {
        binding.btnExportarExcel.setOnClickListener(v -> exportarInventario(true));
        binding.btnExportarCsv.setOnClickListener(v -> exportarInventario(false));
        binding.btnImportarExcel.setOnClickListener(v ->
                filePickerLauncher.launch("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
    }

    private void exportarInventario(boolean formatoExcel) {
        List<Articulo> articulos = InventarioRepository.getInstance().getArticulosLiveData().getValue();
        if (articulos == null || articulos.isEmpty()) {
            Toast.makeText(this, "No hay artículos en el inventario para exportar", Toast.LENGTH_SHORT).show();
            return;
        }

        mostrarCargando("Generando archivo...");

        if (formatoExcel) {
            ExcelManager.exportarExcel(this, articulos, new ExcelManager.ExportCallback() {
                @Override
                public void onSuccess(File archivo) {
                    runOnUiThread(() -> {
                        ocultarCargando();
                        mostrarOpcionesArchivoGenerado(archivo,
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
                    });
                }

                @Override
                public void onError(String error) {
                    runOnUiThread(() -> {
                        ocultarCargando();
                        Toast.makeText(ImportarExportarExcelActivity.this, error, Toast.LENGTH_LONG).show();
                    });
                }
            });
        } else {
            ExcelManager.exportarCsv(this, articulos, new ExcelManager.ExportCallback() {
                @Override
                public void onSuccess(File archivo) {
                    runOnUiThread(() -> {
                        ocultarCargando();
                        mostrarOpcionesArchivoGenerado(archivo, "text/csv");
                    });
                }

                @Override
                public void onError(String error) {
                    runOnUiThread(() -> {
                        ocultarCargando();
                        Toast.makeText(ImportarExportarExcelActivity.this, error, Toast.LENGTH_LONG).show();
                    });
                }
            });
        }
    }

    private void mostrarOpcionesArchivoGenerado(File archivo, String mimeType) {
        android.view.View dialogView = getLayoutInflater()
                .inflate(com.ifpleonardo.controlstocktextil.R.layout.dialog_archivo_listo, null);

        android.widget.TextView tvMensaje = dialogView.findViewById(com.ifpleonardo.controlstocktextil.R.id.tvArchivoNombreMensaje);
        tvMensaje.setText("¿Qué deseas hacer con \"" + archivo.getName() + "\"?");

        AlertDialog dialog = new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setView(dialogView)
                .create();

        dialogView.findViewById(com.ifpleonardo.controlstocktextil.R.id.btnGuardarEnDescargas).setOnClickListener(v -> {
            dialog.dismiss();
            mostrarCargando("Guardando en Descargas...");
            ExcelManager.guardarEnDescargas(this, archivo, mimeType, new ExcelManager.ExportCallback() {
                @Override
                public void onSuccess(File guardado) {
                    runOnUiThread(() -> {
                        ocultarCargando();
                        Toast.makeText(ImportarExportarExcelActivity.this,
                                "Guardado en la carpeta Descargas: " + archivo.getName(),
                                Toast.LENGTH_LONG).show();
                    });
                }

                @Override
                public void onError(String error) {
                    runOnUiThread(() -> {
                        ocultarCargando();
                        Toast.makeText(ImportarExportarExcelActivity.this, error, Toast.LENGTH_LONG).show();
                    });
                }
            });
        });

        dialogView.findViewById(com.ifpleonardo.controlstocktextil.R.id.btnCompartirArchivo).setOnClickListener(v -> {
            dialog.dismiss();
            ExcelManager.compartirArchivo(ImportarExportarExcelActivity.this, archivo, mimeType);
        });

        dialogView.findViewById(com.ifpleonardo.controlstocktextil.R.id.btnCerrarDialogoArchivo).setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }

    private void procesarArchivoExcelSeleccionado(Uri uri) {
        Usuario u = AuthRepository.getInstance().getUsuarioActualLiveData().getValue();
        if (u == null || !u.isAdmin()) {
            Toast.makeText(this, "Solo el rol ADMIN puede importar Excel", Toast.LENGTH_SHORT).show();
            return;
        }
        String errorValidacion = ExcelManager.validarArchivoImportacion(this, uri);
        if (errorValidacion != null) {
            Toast.makeText(this, errorValidacion, Toast.LENGTH_LONG).show();
            return;
        }
        mostrarCargando("Procesando y subiendo artículos del Excel...");

        try {
            InputStream is = getContentResolver().openInputStream(uri);
            if (is == null) {
                ocultarCargando();
                Toast.makeText(this, "No se pudo leer el archivo seleccionado", Toast.LENGTH_SHORT).show();
                return;
            }

            ExcelManager.importarExcel(this, is, new ExcelManager.ImportCallback() {
                @Override
                public void onSuccess(int totalArticulos) {
                    runOnUiThread(() -> {
                        ocultarCargando();
                        new AlertDialog.Builder(ImportarExportarExcelActivity.this)
                                .setTitle("Importación completada")
                                .setMessage("Se han procesado y guardado correctamente " + totalArticulos + " artículos en el catálogo.")
                                .setPositiveButton("Aceptar", null)
                                .show();
                    });
                }

                @Override
                public void onError(String error) {
                    runOnUiThread(() -> {
                        ocultarCargando();
                        new AlertDialog.Builder(ImportarExportarExcelActivity.this)
                                .setTitle("Error de importación")
                                .setMessage(error)
                                .setPositiveButton("Entendido", null)
                                .show();
                    });
                }
            });

        } catch (Exception e) {
            ocultarCargando();
            Toast.makeText(this, "Error al abrir archivo: " + e.getLocalizedMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void mostrarCargando(String mensaje) {
        binding.progressExcel.setVisibility(View.VISIBLE);
        binding.tvEstadoExcel.setVisibility(View.VISIBLE);
        binding.tvEstadoExcel.setText(mensaje);
        binding.btnExportarExcel.setEnabled(false);
        binding.btnExportarCsv.setEnabled(false);
        binding.btnImportarExcel.setEnabled(false);
    }

    private void ocultarCargando() {
        binding.progressExcel.setVisibility(View.GONE);
        binding.tvEstadoExcel.setVisibility(View.GONE);
        binding.btnExportarExcel.setEnabled(true);
        binding.btnExportarCsv.setEnabled(true);
        binding.btnImportarExcel.setEnabled(true);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
