package com.ifpleonardo.controlstocktextil;

import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import com.ifpleonardo.controlstocktextil.data.model.Usuario;
import com.ifpleonardo.controlstocktextil.data.repository.AuthRepository;
import com.ifpleonardo.controlstocktextil.databinding.ActivityMainBinding;
import com.ifpleonardo.controlstocktextil.ui.admin.ExcelFragment;
import com.ifpleonardo.controlstocktextil.ui.admin.ProfesorasFragment;
import com.ifpleonardo.controlstocktextil.ui.auth.LoginActivity;
import com.ifpleonardo.controlstocktextil.ui.historial.HistorialFragment;
import com.ifpleonardo.controlstocktextil.ui.inventario.InventarioFragment;
import com.ifpleonardo.controlstocktextil.ui.perfil.PerfilBottomSheetDialogFragment;
import com.ifpleonardo.controlstocktextil.ui.scanner.ScannerActivity;

/**
 * Pantalla principal y contenedor de navegación de la aplicación.
 * 
 * Arquitectura y seguridad:
 * 1. Control de acceso reactivo (RBAC):
 *    - Observa el LiveData del usuario autenticado.
 *    - Si el rol es 'ADMIN', habilita las 4 pestañas: Inventario, Historial, Excel/QR y Gestión de Profesoras.
 *    - Si el rol es 'PROFESORA', oculta de la interfaz la pestaña de gestión de profesoras.
 * 2. Carga perezosa (Lazy loading):
 *    - ProfesorasFragment solo se crea e inicializa si la usuaria es ADMIN y pulsa la pestaña correspondiente,
 *      evitando consultas y listeners de Firestore no autorizados en cuentas de profesoras.
 * 3. Diseño inmersivo Material Design 3 Edge-to-Edge:
 *    - Soporte completo para barras del sistema mediante WindowInsetsCompat.
 */
public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;
    private Usuario usuarioActual;

    private Fragment fragmentInventario;
    private Fragment fragmentHistorial;
    private Fragment fragmentExcel;
    private Fragment fragmentProfesoras;
    private Fragment fragmentActivo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Control de autenticación inicial
        if (!AuthRepository.getInstance().isUserLoggedIn()) {
            redirigirALogin();
            return;
        }

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        WindowCompat.enableEdgeToEdge(getWindow());

        ViewCompat.setOnApplyWindowInsetsListener(binding.mainCoordinator, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, 0);
            return insets;
        });

        setSupportActionBar(binding.topAppBar);

        setupFragmentos();
        setupBottomNavigation();
        setupObservadorUsuario();
    }

    private void setupFragmentos() {
        FragmentManager fm = getSupportFragmentManager();

        fragmentInventario = InventarioFragment.newInstance();
        fragmentHistorial = HistorialFragment.newInstance();
        fragmentExcel = ExcelFragment.newInstance();

        fm.beginTransaction()
                .add(R.id.fragmentContainer, fragmentExcel, "excel").hide(fragmentExcel)
                .add(R.id.fragmentContainer, fragmentHistorial, "historial").hide(fragmentHistorial)
                .add(R.id.fragmentContainer, fragmentInventario, "inventario")
                .commit();

        fragmentActivo = fragmentInventario;
        actualizarTitulosTopBar(R.id.nav_inventario);
    }

    private void setupBottomNavigation() {
        binding.bottomNavigation.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            Fragment targetFragment = null;

            if (id == R.id.nav_inventario) {
                targetFragment = fragmentInventario;
            } else if (id == R.id.nav_historial) {
                targetFragment = fragmentHistorial;
            } else if (id == R.id.nav_excel) {
                targetFragment = fragmentExcel;
            } else if (id == R.id.nav_profesoras) {
                if (usuarioActual != null && usuarioActual.isAdmin()) {
                    if (fragmentProfesoras == null) {
                        fragmentProfesoras = ProfesorasFragment.newInstance();
                        getSupportFragmentManager().beginTransaction()
                                .add(R.id.fragmentContainer, fragmentProfesoras, "profesoras")
                                .commit();
                    }
                    targetFragment = fragmentProfesoras;
                } else {
                    return false;
                }
            }

            if (targetFragment != null && targetFragment != fragmentActivo) {
                getSupportFragmentManager().beginTransaction()
                        .hide(fragmentActivo)
                        .show(targetFragment)
                        .commit();
                fragmentActivo = targetFragment;
                actualizarTitulosTopBar(id);
                return true;
            }
            return false;
        });
    }

    private void actualizarTitulosTopBar(int navItemId) {
        if (binding.topAppBar == null)
            return;

        if (navItemId == R.id.nav_inventario) {
            binding.topAppBar.setTitle("Inventario Textil");
            binding.topAppBar.setSubtitle("Almacén FP Confección");
        } else if (navItemId == R.id.nav_historial) {
            binding.topAppBar.setTitle("Historial de Movimientos");
            binding.topAppBar.setSubtitle("Registro inmutable de almacén");
        } else if (navItemId == R.id.nav_excel) {
            binding.topAppBar.setTitle("Excel y Etiquetas QR");
            binding.topAppBar.setSubtitle("Carga masiva y códigos de estantería");
        } else if (navItemId == R.id.nav_profesoras) {
            binding.topAppBar.setTitle("Gestión de Profesoras");
            binding.topAppBar.setSubtitle("Administración de accesos");
        }
    }

    private void setupObservadorUsuario() {
        AuthRepository.getInstance().getUsuarioActualLiveData().observe(this, usuario -> {
            this.usuarioActual = usuario;
            if (usuario == null) {
                redirigirALogin();
                return;
            }

            // Si es profesora, ocultar la pestaña de profesoras de la barra inferior
            MenuItem itemProfesoras = binding.bottomNavigation.getMenu().findItem(R.id.nav_profesoras);
            if (itemProfesoras != null) {
                itemProfesoras.setVisible(usuario.isAdmin());
            }

            // Si estaba en la pestaña de profesoras y ya no es admin, regresar a inventario y detener escucha
            if (!usuario.isAdmin()) {
                com.ifpleonardo.controlstocktextil.data.repository.UsuarioRepository.getInstance().detenerEscuchaUsuarios();
                if (fragmentActivo == fragmentProfesoras) {
                    binding.bottomNavigation.setSelectedItemId(R.id.nav_inventario);
                }
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_scanner) {
            startActivity(new Intent(this, ScannerActivity.class));
            return true;
        } else if (id == R.id.action_perfil) {
            PerfilBottomSheetDialogFragment dialog = PerfilBottomSheetDialogFragment.newInstance();
            dialog.show(getSupportFragmentManager(), "PerfilBottomSheetDialogFragment");
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void redirigirALogin() {
        Intent intent = new Intent(MainActivity.this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}