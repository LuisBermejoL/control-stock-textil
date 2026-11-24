package com.ifpleonardo.controlstocktextil.ui.admin;

import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.ifpleonardo.controlstocktextil.data.model.Articulo;
import com.ifpleonardo.controlstocktextil.data.model.Usuario;
import com.ifpleonardo.controlstocktextil.data.repository.AuthRepository;
import com.ifpleonardo.controlstocktextil.data.repository.InventarioRepository;
import com.ifpleonardo.controlstocktextil.databinding.FragmentExcelBinding;
import com.ifpleonardo.controlstocktextil.util.ExcelManager;
import com.ifpleonardo.controlstocktextil.util.QrCodeGenerator;

import java.io.File;
import java.io.InputStream;
import java.util.List;

/**
 * Pestaña central de carga y descarga de inventario y generación de etiquetas QR.
 * 
 * Funcionalidades:
 * 1. Exportación completa del catálogo a hoja de cálculo .xlsx (FastExcel) o .csv (UTF-8 con BOM).
 * 2. Importación masiva de Excel para actualización masiva del catálogo (restringida a administradoras).
 * 3. Generación automática del cuaderno PDF en tamaño A4 con etiquetas troqueladas (2x4 por página)
 *    para identificar rollos de tela y estanterías del almacén.
 */
public class ExcelFragment extends Fragment {

    private FragmentExcelBinding binding;

    private final ActivityResultLauncher<String> filePickerLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) {
                    procesarArchivoExcelSeleccionado(uri);
                }
            });

    public static ExcelFragment newInstance() {
        return new ExcelFragment();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentExcelBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        Usuario usuario = AuthRepository.getInstance().getUsuarioActualLiveData().getValue();
        boolean esAdmin = usuario != null && usuario.isAdmin();
        binding.cardImportar.setVisibility(esAdmin ? View.VISIBLE : View.GONE);

        setupListeners();
    }

    private void setupListeners() {
        binding.btnGenerarPdfEtiquetas.setOnClickListener(v -> generarPdfEtiquetas());
        binding.btnExportarExcel.setOnClickListener(v -> exportarInventario(true));
        binding.btnExportarCsv.setOnClickListener(v -> exportarInventario(false));
        binding.btnImportarExcel.setOnClickListener(v -> filePickerLauncher.launch("*/*"));
    }

    private void generarPdfEtiquetas() {
        List<Articulo> articulos = InventarioRepository.getInstance().getArticulosLiveData().getValue();
        if (articulos == null || articulos.isEmpty()) {
            Toast.makeText(requireContext(), "No hay artículos en el inventario para generar etiquetas", Toast.LENGTH_SHORT).show();
            return;
        }

        setLoading(true);
        new Thread(() -> {
            try {
                File pdfFile = QrCodeGenerator.generarPdfEtiquetasAlmacen(requireContext(), articulos);
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        setLoading(false);
                        QrCodeGenerator.compartirOImprimirPdf(requireContext(), pdfFile);
                    });
                }
            } catch (Exception e) {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        setLoading(false);
                        Toast.makeText(requireContext(), "Error al generar PDF: " + e.getLocalizedMessage(), Toast.LENGTH_LONG).show();
                    });
                }
            }
        }).start();
    }

    private void exportarInventario(boolean formatoExcel) {
        List<Articulo> articulos = InventarioRepository.getInstance().getArticulosLiveData().getValue();
        if (articulos == null || articulos.isEmpty()) {
            Toast.makeText(requireContext(), "No hay artículos en el inventario para exportar", Toast.LENGTH_SHORT).show();
            return;
        }

        setLoading(true);

        if (formatoExcel) {
            ExcelManager.exportarExcel(requireContext(), articulos, new ExcelManager.ExportCallback() {
                @Override
                public void onSuccess(File archivo) {
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            setLoading(false);
                            ExcelManager.compartirArchivo(requireContext(), archivo,
                                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
                        });
                    }
                }

                @Override
                public void onError(String error) {
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            setLoading(false);
                            Toast.makeText(requireContext(), error, Toast.LENGTH_LONG).show();
                        });
                    }
                }
            });
        } else {
            ExcelManager.exportarCsv(requireContext(), articulos, new ExcelManager.ExportCallback() {
                @Override
                public void onSuccess(File archivo) {
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            setLoading(false);
                            ExcelManager.compartirArchivo(requireContext(), archivo, "text/csv");
                        });
                    }
                }

                @Override
                public void onError(String error) {
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            setLoading(false);
                            Toast.makeText(requireContext(), error, Toast.LENGTH_LONG).show();
                        });
                    }
                }
            });
        }
    }

    private void procesarArchivoExcelSeleccionado(Uri uri) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Confirmar importación de Excel")
                .setMessage("Se procesarán las hojas del archivo para sincronizar el catálogo. Los artículos existentes se actualizarán y los nuevos se registrarán. ¿Deseas continuar?")
                .setPositiveButton("Sí, importar", (dialog, which) -> ejecutarCargaExcel(uri))
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void ejecutarCargaExcel(Uri uri) {
        setLoading(true);

        try {
            InputStream is = requireContext().getContentResolver().openInputStream(uri);
            if (is == null) {
                setLoading(false);
                Toast.makeText(requireContext(), "No se pudo leer el archivo seleccionado", Toast.LENGTH_SHORT).show();
                return;
            }

            ExcelManager.importarExcel(requireContext(), is, new ExcelManager.ImportCallback() {
                @Override
                public void onSuccess(int totalArticulos) {
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            setLoading(false);
                            new MaterialAlertDialogBuilder(requireContext())
                                    .setTitle("Importación completada")
                                    .setMessage("Se han sincronizado correctamente " + totalArticulos + " artículos en el catálogo.")
                                    .setPositiveButton("Aceptar", null)
                                    .show();
                        });
                    }
                }

                @Override
                public void onError(String error) {
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            setLoading(false);
                            Toast.makeText(requireContext(), "Error al importar: " + error, Toast.LENGTH_LONG).show();
                        });
                    }
                }
            });
        } catch (Exception e) {
            setLoading(false);
            Toast.makeText(requireContext(), "Error: " + e.getLocalizedMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void setLoading(boolean loading) {
        if (binding == null) return;
        binding.pbExcel.setVisibility(loading ? View.VISIBLE : View.GONE);
        binding.btnGenerarPdfEtiquetas.setEnabled(!loading);
        binding.btnExportarExcel.setEnabled(!loading);
        binding.btnExportarCsv.setEnabled(!loading);
        binding.btnImportarExcel.setEnabled(!loading);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}