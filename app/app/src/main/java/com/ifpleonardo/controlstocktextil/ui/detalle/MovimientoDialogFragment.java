package com.ifpleonardo.controlstocktextil.ui.detalle;

import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.ifpleonardo.controlstocktextil.R;
import com.ifpleonardo.controlstocktextil.data.model.Articulo;
import com.ifpleonardo.controlstocktextil.data.repository.InventarioRepository;
import com.ifpleonardo.controlstocktextil.databinding.DialogMovimientoBinding;

import java.text.DecimalFormat;

/**
 * Diálogo interactivo para registrar movimientos de almacén sobre un artículo.
 * 
 * Operaciones:
 * - ENTRADA: Aumento de existencias por recepción de pedido o reposición.
 * - SALIDA_CONSUMO: Consumo directo en talleres y confección de prendas.
 * - PRESTAMO: Cesión temporal de fornituras o herramientas a alumnado/profesorado.
 * - DEVOLUCION: Reintegro de material previamente prestado.
 * - AJUSTE: Regularización manual de inventario físico (solo administradoras).
 */
public class MovimientoDialogFragment extends DialogFragment {

    public interface OnMovimientoRealizadoListener {
        void onMovimientoCompletado(double nuevoStock);
    }

    private static final String ARG_ARTICULO = "arg_articulo";
    private Articulo articulo;
    private DialogMovimientoBinding binding;
    private OnMovimientoRealizadoListener listener;
    private final DecimalFormat decimalFormat = new DecimalFormat("#.##");

    public static MovimientoDialogFragment newInstance(Articulo articulo) {
        MovimientoDialogFragment fragment = new MovimientoDialogFragment();
        Bundle args = new Bundle();
        args.putSerializable(ARG_ARTICULO, articulo);
        fragment.setArguments(args);
        return fragment;
    }

    public void setOnMovimientoRealizadoListener(OnMovimientoRealizadoListener listener) {
        this.listener = listener;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            articulo = (Articulo) getArguments().getSerializable(ARG_ARTICULO);
        }
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        binding = DialogMovimientoBinding.inflate(LayoutInflater.from(requireContext()));

        if (articulo != null) {
            String unidad = articulo.getUnidadMedida() != null ? articulo.getUnidadMedida() : "";
            binding.tvStockActualDialogo.setText("Stock actual: " + decimalFormat.format(articulo.getStockActual()) + " " + unidad);
            binding.tilCantidad.setHint("Cantidad (" + unidad + ")");
        }

        setupListeners();

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireContext())
                .setView(binding.getRoot());

        return builder.create();
    }

    private void setupListeners() {
        binding.btnCancelar.setOnClickListener(v -> dismiss());

        binding.btnGuardarMovimiento.setOnClickListener(v -> {
            String cantidadStr = binding.etCantidad.getText() != null ? binding.etCantidad.getText().toString().trim() : "";
            if (cantidadStr.isEmpty()) {
                binding.tilCantidad.setError("Introduce la cantidad");
                return;
            }

            double cantidad;
            try {
                cantidad = Double.parseDouble(cantidadStr.replace(",", "."));
            } catch (NumberFormatException e) {
                binding.tilCantidad.setError("Cantidad numérica inválida");
                return;
            }

            if (cantidad <= 0 && !getSelectedTipo().equals("AJUSTE")) {
                binding.tilCantidad.setError("La cantidad debe ser mayor a 0");
                return;
            } else {
                binding.tilCantidad.setError(null);
            }

            String observaciones = binding.etObservaciones.getText() != null ? binding.etObservaciones.getText().toString().trim() : "";
            String tipo = getSelectedTipo();

            // Bloquear interfaz mientras procesa
            binding.progressBarMovimiento.setVisibility(View.VISIBLE);
            binding.btnGuardarMovimiento.setEnabled(false);
            binding.btnCancelar.setEnabled(false);

            InventarioRepository.getInstance().registrarMovimiento(
                    articulo.getId(),
                    tipo,
                    cantidad,
                    observaciones,
                    new InventarioRepository.TransactionCallback() {
                        @Override
                        public void onSuccess(double nuevoStock) {
                            if (getContext() == null) return;
                            Toast.makeText(getContext(), "Movimiento registrado con éxito. Nuevo stock: " + decimalFormat.format(nuevoStock), Toast.LENGTH_SHORT).show();
                            if (listener != null) {
                                listener.onMovimientoCompletado(nuevoStock);
                            }
                            dismiss();
                        }

                        @Override
                        public void onError(String error) {
                            if (getContext() == null) return;
                            binding.progressBarMovimiento.setVisibility(View.GONE);
                            binding.btnGuardarMovimiento.setEnabled(true);
                            binding.btnCancelar.setEnabled(true);
                            Toast.makeText(getContext(), "Error: " + error, Toast.LENGTH_LONG).show();
                        }
                    }
            );
        });
    }

    private String getSelectedTipo() {
        int checkedId = binding.chipGroupTipo.getCheckedChipId();
        if (checkedId == R.id.chipEntrada) return "ENTRADA";
        if (checkedId == R.id.chipPrestamo) return "PRESTAMO";
        if (checkedId == R.id.chipDevolucion) return "DEVOLUCION";
        if (checkedId == R.id.chipAjuste) return "AJUSTE";
        return "SALIDA_CONSUMO";
    }
}
