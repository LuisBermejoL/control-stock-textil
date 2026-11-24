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
    }

    private void setupListeners() {
        binding.btnExportarExcel.setOnClickListener(v -> exportarInventario(true));
        binding.btnExportarCsv.setOnClickListener(v -> exportarInventario(false));
        binding.btnImportarExcel.setOnClickListener(v -> filePickerLauncher.launch("*/*"));
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
                        ExcelManager.compartirArchivo(ImportarExportarExcelActivity.this, archivo,
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
                        ExcelManager.compartirArchivo(ImportarExportarExcelActivity.this, archivo, "text/csv");
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

    private void procesarArchivoExcelSeleccionado(Uri uri) {
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
