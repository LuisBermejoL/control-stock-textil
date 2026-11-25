package com.ifpleonardo.controlstocktextil.ui.admin;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.ifpleonardo.controlstocktextil.data.model.Usuario;
import com.ifpleonardo.controlstocktextil.databinding.ItemUsuarioBinding;

import java.util.ArrayList;
import java.util.List;

/**
 * Adaptador de RecyclerView para el listado de profesoras y administradoras del centro.
 * Muestra el nombre, correo institucional, rol (ADMIN / PROFESORA) y estado operativo (Activa / Inactiva).
 */
public class UsuarioAdapter extends RecyclerView.Adapter<UsuarioAdapter.UsuarioViewHolder> {

    public interface OnUsuarioClickListener {
        void onUsuarioClick(Usuario usuario);
    }

    private final List<Usuario> usuarios = new ArrayList<>();
    private final OnUsuarioClickListener listener;

    public UsuarioAdapter(OnUsuarioClickListener listener) {
        this.listener = listener;
    }

    public void setUsuarios(List<Usuario> nuevaLista) {
        this.usuarios.clear();
        if (nuevaLista != null) {
            this.usuarios.addAll(nuevaLista);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public UsuarioViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemUsuarioBinding binding = ItemUsuarioBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false
        );
        return new UsuarioViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull UsuarioViewHolder holder, int position) {
        holder.bind(usuarios.get(position));
    }

    @Override
    public int getItemCount() {
        return usuarios.size();
    }

    class UsuarioViewHolder extends RecyclerView.ViewHolder {

        private final ItemUsuarioBinding binding;

        public UsuarioViewHolder(ItemUsuarioBinding binding) {
            super(binding.getRoot());
            this.binding = binding;

            binding.getRoot().setOnClickListener(v -> {
                int pos = getAdapterPosition();
                if (pos != RecyclerView.NO_POSITION && listener != null) {
                    listener.onUsuarioClick(usuarios.get(pos));
                }
            });
        }

        public void bind(Usuario u) {
            String nombre = u.getNombre() != null ? u.getNombre() : "Sin nombre";
            binding.tvNombreUsuario.setText(nombre);
            binding.tvEmailUsuario.setText(u.getEmail() != null ? u.getEmail() : "");

            // Inicial del avatar
            String inicial = !nombre.isEmpty() ? String.valueOf(nombre.charAt(0)).toUpperCase() : "U";
            binding.tvAvatarInicial.setText(inicial);

            // Badge de Rol
            String rolVisual = u.getRolVisual();
            binding.tvRolBadge.setText(rolVisual);

            // Estado (Activo / Inactivo)
            if (u.isActivo()) {
                binding.tvEstadoBadge.setText("🟢 Activo");
                binding.tvEstadoBadge.setTextColor(ContextCompat.getColor(binding.getRoot().getContext(), android.R.color.holo_green_dark));
                binding.getRoot().setAlpha(1.0f);
            } else {
                binding.tvEstadoBadge.setText("⚪ Inactivo (Baja)");
                binding.tvEstadoBadge.setTextColor(Color.GRAY);
                binding.getRoot().setAlpha(0.6f);
            }
        }
    }
}
