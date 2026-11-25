package com.ifpleonardo.controlstocktextil.util;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.Nullable;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Gestor de subida de imágenes a Cloudinary CDN (sin servidor intermedio).
 * 
 * Medidas de seguridad implementadas:
 * 1. Cero credenciales privadas expuestas:
 *    - Utiliza un Upload Preset de modo 'Unsigned' sin API Secret en el cliente.
 * 2. Conexión cifrada HTTPS con validación de origen en respuesta:
 *    - Transfiere vía HTTPS y valida que la URL resultante pertenezca a res.cloudinary.com.
 * 3. Separación por carpetas ('articulos_textil' y 'perfiles_textil').
 * 4. Limpieza automática del archivo temporal comprimido tras finalizar la subida.
 */
public class CloudinaryUploader {

    public static final String CLOUD_NAME = "ts4drobq";
    public static final String UPLOAD_PRESET = "ml_default";
    public static final String FOLDER_ARTICULOS = "articulos_textil";
    public static final String FOLDER_PERFILES = "perfiles_textil";
    private static final String UPLOAD_URL = "https://api.cloudinary.com/v1_1/" + CLOUD_NAME + "/image/upload";

    public interface UploadCallback {
        void onSuccess(String imageUrl);
        void onError(String error);
    }

    /**
     * Sube una fotografía de artículo a Cloudinary en la carpeta 'articulos_textil'.
     */
    public static void subirImagen(Context context, File archivoImagen, UploadCallback callback) {
        subirImagen(context, archivoImagen, FOLDER_ARTICULOS, callback);
    }

    /**
     * Comprime la imagen desde una Uri (aplicando límites de avatar o artículo), borra el fichero
     * temporal de cámara si existe, sube a Cloudinary y elimina el temporal comprimido al terminar.
     */
    public static void comprimirYSubir(Context context, Uri uriOrigen, @Nullable File archivoCamaraTemporal,
                                       boolean esAvatar, UploadCallback callback) {
        Handler mainHandler = new Handler(Looper.getMainLooper());
        new Thread(() -> {
            File archivoComprimido;
            try {
                archivoComprimido = esAvatar
                        ? ImageCompressor.comprimirAvatar(context, uriOrigen)
                        : ImageCompressor.comprimirImagen(context, uriOrigen);
            } catch (Exception e) {
                mainHandler.post(() -> callback.onError("Error de compresión: " + e.getLocalizedMessage()));
                return;
            } finally {
                if (archivoCamaraTemporal != null && archivoCamaraTemporal.exists()) {
                    archivoCamaraTemporal.delete();
                }
            }
            subirImagenSync(archivoComprimido, esAvatar ? FOLDER_PERFILES : FOLDER_ARTICULOS, mainHandler, callback);
        }).start();
    }

    /**
     * Sube un archivo de imagen comprimido a la carpeta especificada en Cloudinary
     * y elimina automáticamente el fichero comprimido temporal tras completar la petición.
     */
    public static void subirImagen(Context context, File archivoImagen, String folder, UploadCallback callback) {
        Handler mainHandler = new Handler(Looper.getMainLooper());
        String targetFolder = (folder != null && !folder.trim().isEmpty()) ? folder.trim() : FOLDER_ARTICULOS;
        new Thread(() -> subirImagenSync(archivoImagen, targetFolder, mainHandler, callback)).start();
    }

    private static void subirImagenSync(File archivoImagen, String targetFolder, Handler mainHandler, UploadCallback callback) {
            HttpURLConnection conn = null;
            DataOutputStream dos = null;
            String boundary = "*****" + System.currentTimeMillis() + "*****";
            String lineEnd = "\r\n";
            String twoHyphens = "--";

            try {
                URL url = new URL(UPLOAD_URL);
                conn = (HttpURLConnection) url.openConnection();
                conn.setDoInput(true);
                conn.setDoOutput(true);
                conn.setUseCaches(false);
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Connection", "Keep-Alive");
                conn.setRequestProperty("Content-Type", "multipart/form-data;boundary=" + boundary);
                conn.setConnectTimeout(20000);
                conn.setReadTimeout(30000);

                dos = new DataOutputStream(conn.getOutputStream());

                // Campo: upload_preset
                dos.writeBytes(twoHyphens + boundary + lineEnd);
                dos.writeBytes("Content-Disposition: form-data; name=\"upload_preset\"" + lineEnd + lineEnd);
                dos.writeBytes(UPLOAD_PRESET + lineEnd);

                // Campo: folder
                dos.writeBytes(twoHyphens + boundary + lineEnd);
                dos.writeBytes("Content-Disposition: form-data; name=\"folder\"" + lineEnd + lineEnd);
                dos.writeBytes(targetFolder + lineEnd);

                // Archivo binario
                dos.writeBytes(twoHyphens + boundary + lineEnd);
                dos.writeBytes("Content-Disposition: form-data; name=\"file\";filename=\"" + archivoImagen.getName() + "\"" + lineEnd);
                dos.writeBytes("Content-Type: image/jpeg" + lineEnd + lineEnd);

                try (FileInputStream fis = new FileInputStream(archivoImagen)) {
                    byte[] buffer = new byte[4096];
                    int bytesRead;
                    while ((bytesRead = fis.read(buffer)) != -1) {
                        dos.write(buffer, 0, bytesRead);
                    }
                }
                dos.writeBytes(lineEnd);
                dos.writeBytes(twoHyphens + boundary + twoHyphens + lineEnd);
                dos.flush();

                int serverResponseCode = conn.getResponseCode();
                BufferedReader reader;
                if (serverResponseCode >= 200 && serverResponseCode < 300) {
                    reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                } else {
                    reader = new BufferedReader(new InputStreamReader(conn.getErrorStream()));
                }

                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();

                if (serverResponseCode >= 200 && serverResponseCode < 300) {
                    JSONObject json = new JSONObject(response.toString());
                    String secureUrl = json.optString("secure_url", "");
                    if (!secureUrl.startsWith("https://res.cloudinary.com/")) {
                        mainHandler.post(() -> callback.onError("Respuesta inesperada del servidor de imágenes."));
                    } else {
                        mainHandler.post(() -> callback.onSuccess(secureUrl));
                    }
                } else {
                    String errorMsg = "Error en Cloudinary (" + serverResponseCode + "): " + response;
                    mainHandler.post(() -> callback.onError(errorMsg));
                }

            } catch (Exception e) {
                mainHandler.post(() -> callback.onError("Error al conectar con Cloudinary: " + e.getLocalizedMessage()));
            } finally {
                if (dos != null) {
                    try { dos.close(); } catch (Exception ignored) {}
                }
                if (conn != null) {
                    conn.disconnect();
                }
                if (archivoImagen != null && archivoImagen.exists()) {
                    archivoImagen.delete();
                }
            }
    }
}
