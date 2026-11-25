package com.ifpleonardo.controlstocktextil.ui.inventario;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.ifpleonardo.controlstocktextil.data.model.Articulo;
import com.ifpleonardo.controlstocktextil.databinding.ItemArticuloBinding;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

/**
 * Adaptador de RecyclerView para el catálogo principal de artículos textiles.
 * Muestra el nombre, referencia, categoría, ubicación física y una píldora visual de stock
 * con alerta roja de "Agotado" o naranja de "Bajo mínimos".
 */
public class ArticuloAdapter extends RecyclerView.Adapter<ArticuloAdapter.ArticuloViewHolder> {

    public interface OnArticuloClickListener {
        void onArticuloClick(Articulo articulo);
    }

    private final List<Articulo> articulos = new ArrayList<>();
    private final OnArticuloClickListener listener;
    private final DecimalFormat decimalFormat = new DecimalFormat("#.##");

    public ArticuloAdapter(OnArticuloClickListener listener) {
        this.listener = listener;
    }

    public void setArticulos(List<Articulo> nuevaLista) {
        this.articulos.clear();
        if (nuevaLista != null) {
            this.articulos.addAll(nuevaLista);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ArticuloViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemArticuloBinding binding = ItemArticuloBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false
        );
        return new ArticuloViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ArticuloViewHolder holder, int position) {
        holder.bind(articulos.get(position));
    }

    @Override
    public int getItemCount() {
        return articulos.size();
    }

    class ArticuloViewHolder extends RecyclerView.ViewHolder {

        private final ItemArticuloBinding binding;

        public ArticuloViewHolder(ItemArticuloBinding binding) {
            super(binding.getRoot());
            this.binding = binding;

            binding.getRoot().setOnClickListener(v -> {
                int pos = getAdapterPosition();
                if (pos != RecyclerView.NO_POSITION && listener != null) {
                    listener.onArticuloClick(articulos.get(pos));
                }
            });
        }

        public void bind(Articulo a) {
            binding.chipCategoria.setText(a.getCategoria() != null ? a.getCategoria() : "GENERAL");
            binding.tvNombre.setText(a.getNombreCompleto());

            // Construir texto de referencia y proveedor
            StringBuilder refBuilder = new StringBuilder();
            if (a.getReferencia() != null && !a.getReferencia().trim().isEmpty()) {
                refBuilder.append("Ref: ").append(a.getReferencia());
            }
            if (a.getProveedor() != null && !a.getProveedor().trim().isEmpty()) {
                if (refBuilder.length() > 0) refBuilder.append(" · ");
                refBuilder.append(a.getProveedor());
            }
            if (a.getColorProveedor() != null && !a.getColorProveedor().trim().isEmpty()) {
                refBuilder.append(" (").append(a.getColorProveedor()).append(")");
            }
            binding.tvReferencia.setText(refBuilder.toString());

            // Formatear Stock con singular/plural automático
            double stock = a.getStockActual();
            String unidad = a.getUnidadMedida() != null ? a.getUnidadMedida() : "";
            String stockStr = Articulo.formatearStockConUnidad(stock, unidad, decimalFormat);
            binding.tvStockPill.setText(stockStr);

            // Cargar imagen con Glide si existe
            if (a.getImagenUrl() != null && !a.getImagenUrl().trim().isEmpty()) {
                binding.ivArticuloFoto.setVisibility(View.VISIBLE);
                com.bumptech.glide.Glide.with(binding.ivArticuloFoto.getContext())
                        .load(a.getImagenUrl())
                        .centerCrop()
                        .placeholder(android.R.drawable.ic_menu_gallery)
                        .into(binding.ivArticuloFoto);
            } else {
                binding.ivArticuloFoto.setVisibility(View.GONE);
            }

            // Ubicación
            if (a.getUbicacion() != null) {
                binding.tvUbicacion.setText("📍 " + a.getUbicacion().getFormatoCompleto());
            } else {
                binding.tvUbicacion.setText("📍 Sin ubicación");
            }

            // Indicador de alerta / bajo mínimos / agotado
            if (stock <= 0) {
                binding.tvBajoMinimos.setVisibility(View.VISIBLE);
                binding.tvBajoMinimos.setText("❌ Agotado");
                binding.tvStockPill.setTextColor(Color.RED);
            } else if (a.isBajoMinimos()) {
                binding.tvBajoMinimos.setVisibility(View.VISIBLE);
                binding.tvBajoMinimos.setText("⚠️ Bajo mínimos");
                binding.tvStockPill.setTextColor(ContextCompat.getColor(binding.getRoot().getContext(), android.R.color.holo_orange_dark));
            } else {
                binding.tvBajoMinimos.setVisibility(View.GONE);
                binding.tvStockPill.setTextColor(ContextCompat.getColor(binding.getRoot().getContext(), android.R.color.holo_green_dark));
            }
        }
    }
}
