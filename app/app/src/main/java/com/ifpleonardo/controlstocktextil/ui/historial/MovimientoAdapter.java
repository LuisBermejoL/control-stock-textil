package com.ifpleonardo.controlstocktextil.ui.historial;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.ifpleonardo.controlstocktextil.data.model.Movimiento;
import com.ifpleonardo.controlstocktextil.databinding.ItemMovimientoBinding;

import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Adaptador de RecyclerView para representar los registros de movimientos en tarjetas visuales.
 * Asigna códigos de color semánticos por tipo de operación (Verde = Entrada/Devolución, Rojo = Salida/Consumo, Naranja = Préstamo).
 */
public class MovimientoAdapter extends RecyclerView.Adapter<MovimientoAdapter.MovimientoViewHolder> {

    private final List<Movimiento> movimientos = new ArrayList<>();
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault());
    private final DecimalFormat decimalFormat = new DecimalFormat("#.##");

    public void setMovimientos(List<Movimiento> nuevaLista) {
        this.movimientos.clear();
        if (nuevaLista != null) {
            this.movimientos.addAll(nuevaLista);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public MovimientoViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemMovimientoBinding binding = ItemMovimientoBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false
        );
        return new MovimientoViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull MovimientoViewHolder holder, int position) {
        holder.bind(movimientos.get(position));
    }

    @Override
    public int getItemCount() {
        return movimientos.size();
    }

    class MovimientoViewHolder extends RecyclerView.ViewHolder {

        private final ItemMovimientoBinding binding;

        public MovimientoViewHolder(ItemMovimientoBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        public void bind(Movimiento m) {
            String tipo = m.getTipo() != null ? m.getTipo() : "MOVIMIENTO";
            binding.tvTipoMovimiento.setText(formatearTipo(tipo));
            aplicarColorTipo(tipo);

            if (m.getFecha() != null) {
                binding.tvFechaMovimiento.setText(dateFormat.format(m.getFecha()));
            } else {
                binding.tvFechaMovimiento.setText("Reciente");
            }

            String cat = m.getCategoria() != null ? " (" + m.getCategoria() + ")" : "";
            binding.tvArticuloMovimiento.setText((m.getArticuloNombre() != null ? m.getArticuloNombre() : "Artículo") + cat);

            double cant = m.getCantidad() != null ? m.getCantidad() : 0.0;
            double res = m.getStockResultante() != null ? m.getStockResultante() : 0.0;
            String signo = ("SALIDA_CONSUMO".equals(tipo) || "PRESTAMO".equals(tipo)) ? "-" : "+";
            if ("AJUSTE".equals(tipo)) signo = "";

            binding.tvCantidadMovimiento.setText("Cantidad: " + signo + decimalFormat.format(cant) + " · Stock resultante: " + decimalFormat.format(res));

            binding.tvUsuarioMovimiento.setText("👤 Usuario: " + (m.getUsuarioNombre() != null ? m.getUsuarioNombre() : "Desconocido"));

            if (m.getObservaciones() != null && !m.getObservaciones().trim().isEmpty()) {
                binding.tvObservacionesMovimiento.setVisibility(View.VISIBLE);
                binding.tvObservacionesMovimiento.setText("📝 " + m.getObservaciones());
            } else {
                binding.tvObservacionesMovimiento.setVisibility(View.GONE);
            }
        }

        private String formatearTipo(String tipo) {
            switch (tipo) {
                case "SALIDA_CONSUMO": return "SALIDA (CONSUMO)";
                case "ENTRADA": return "ENTRADA (REPOSICIÓN)";
                case "PRESTAMO": return "PRÉSTAMO";
                case "DEVOLUCION": return "DEVOLUCIÓN";
                case "AJUSTE": return "AJUSTE RECUENTO";
                default: return tipo;
            }
        }

        private void aplicarColorTipo(String tipo) {
            switch (tipo) {
                case "SALIDA_CONSUMO":
                    binding.tvTipoMovimiento.setTextColor(Color.parseColor("#E65100")); // Naranja oscuro
                    break;
                case "ENTRADA":
                    binding.tvTipoMovimiento.setTextColor(Color.parseColor("#2E7D32")); // Verde oscuro
                    break;
                case "PRESTAMO":
                    binding.tvTipoMovimiento.setTextColor(Color.parseColor("#1565C0")); // Azul
                    break;
                case "DEVOLUCION":
                    binding.tvTipoMovimiento.setTextColor(Color.parseColor("#00838F")); // Cyan/Teal
                    break;
                case "AJUSTE":
                    binding.tvTipoMovimiento.setTextColor(Color.parseColor("#6A1B9A")); // Púrpura
                    break;
                default:
                    binding.tvTipoMovimiento.setTextColor(Color.GRAY);
                    break;
            }
        }
    }
}
