package com.ifpleonardo.controlstocktextil.ui.auth;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.ifpleonardo.controlstocktextil.data.model.Usuario;
import com.ifpleonardo.controlstocktextil.data.repository.AuthRepository;

/**
 * ViewModel para la pantalla de inicio de sesión (Patrón MVVM).
 * Gestiona el estado reactivo del formulario (cargando, errores, usuario autenticado).
 */
public class LoginViewModel extends ViewModel {

    private final AuthRepository authRepository;

    private final MutableLiveData<Boolean> cargandoLiveData = new MutableLiveData<>(false);
    private final MutableLiveData<String> errorLiveData = new MutableLiveData<>();
    private final MutableLiveData<Usuario> usuarioLogueadoLiveData = new MutableLiveData<>();

    public LoginViewModel() {
        this.authRepository = AuthRepository.getInstance();
    }

    public LiveData<Boolean> getCargando() {
        return cargandoLiveData;
    }

    public LiveData<String> getError() {
        return errorLiveData;
    }

    public LiveData<Usuario> getUsuarioLogueado() {
        return usuarioLogueadoLiveData;
    }

    public boolean estaLogueado() {
        return authRepository.isUserLoggedIn();
    }

    public void verificarSesionExistente() {
        if (estaLogueado()) {
            cargandoLiveData.setValue(true);
            authRepository.cargarPerfilUsuario(authRepository.getCurrentUid(), new AuthRepository.AuthCallback() {
                @Override
                public void onSuccess(Usuario usuario) {
                    cargandoLiveData.setValue(false);
                    usuarioLogueadoLiveData.setValue(usuario);
                }

                @Override
                public void onError(String error) {
                    cargandoLiveData.setValue(false);
                    errorLiveData.setValue(error);
                }
            });
        }
    }

    public void iniciarSesion(String email, String password) {
        cargandoLiveData.setValue(true);
        errorLiveData.setValue(null);

        authRepository.login(email, password, new AuthRepository.AuthCallback() {
            @Override
            public void onSuccess(Usuario usuario) {
                cargandoLiveData.setValue(false);
                usuarioLogueadoLiveData.setValue(usuario);
            }

            @Override
            public void onError(String error) {
                cargandoLiveData.setValue(false);
                errorLiveData.setValue(error);
            }
        });
    }

    public void registrarProfesora(String nombre, String email, String password) {
        cargandoLiveData.setValue(true);
        errorLiveData.setValue(null);

        authRepository.registrarProfesora(nombre, email, password, new AuthRepository.AuthCallback() {
            @Override
            public void onSuccess(Usuario usuario) {
                cargandoLiveData.setValue(false);
                usuarioLogueadoLiveData.setValue(usuario);
            }

            @Override
            public void onError(String error) {
                cargandoLiveData.setValue(false);
                errorLiveData.setValue(error);
            }
        });
    }
}
