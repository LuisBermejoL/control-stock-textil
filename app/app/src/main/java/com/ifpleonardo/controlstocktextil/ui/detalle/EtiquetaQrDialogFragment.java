package com.ifpleonardo.controlstocktextil.ui.detalle;

import android.app.Dialog;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.ifpleonardo.controlstocktextil.data.model.Articulo;
import com.ifpleonardo.controlstocktextil.databinding.DialogEtiquetaQrBinding;
import com.ifpleonardo.controlstocktextil.util.QrCodeGenerator;

import java.io.File;

/**
 * Diálogo modal para la previsualización e impresión de la etiqueta individual QR de un artículo.
 */
public class EtiquetaQrDialogFragment extends DialogFragment {

    private static final String ARG_ARTICULO = "arg_articulo";
    private DialogEtiquetaQrBinding binding;
    private Articulo articulo;
    private Bitmap labelBitmap;

    public static EtiquetaQrDialogFragment newInstance(Articulo articulo) {
        EtiquetaQrDialogFragment fragment = new EtiquetaQrDialogFragment();
        Bundle args = new Bundle();
        args.putSerializable(ARG_ARTICULO, articulo);
        fragment.setArguments(args);
        return fragment;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        binding = DialogEtiquetaQrBinding.inflate(LayoutInflater.from(requireContext()));

        if (getArguments() != null) {
            articulo = (Articulo) getArguments().getSerializable(ARG_ARTICULO);
        }

        if (articulo != null) {
            labelBitmap = QrCodeGenerator.generarEtiquetaArticuloBitmap(articulo);
            if (labelBitmap != null) {
                binding.ivEtiquetaQrPreview.setImageBitmap(labelBitmap);
            }
        }

        binding.btnCerrarQr.setOnClickListener(v -> dismiss());

        binding.btnCompartirImprimirQr.setOnClickListener(v -> {
            if (labelBitmap == null) return;
            try {
                String safeRef = articulo.getReferencia() != null ? articulo.getReferencia().replaceAll("[^a-zA-Z0-9_-]", "_") : "articulo";
                File file = QrCodeGenerator.guardarEtiquetaComoImagen(requireContext(), labelBitmap, "Etiqueta_" + safeRef);
                QrCodeGenerator.compartirImagenEtiqueta(requireContext(), file);
            } catch (Exception e) {
                Toast.makeText(requireContext(), "Error al preparar imagen: " + e.getLocalizedMessage(), Toast.LENGTH_SHORT).show();
            }
        });

        return new MaterialAlertDialogBuilder(requireContext())
                .setView(binding.getRoot())
                .create();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}