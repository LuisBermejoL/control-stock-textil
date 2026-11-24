package com.ifpleonardo.controlstocktextil.util;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Gestor de subida de imágenes a Cloudinary CDN (100% gratuito sin servidor intermedio).
 * 
 * Medidas de seguridad implementadas:
 * 1. Cero credenciales expuestas:
 *    - Se utiliza un Upload Preset de modo 'Unsigned' (sin firma), lo que evita completamente
 *      tener que almacenar la API Secret privada de Cloudinary en el código de la app Android.
 * 2. Conexión cifrada HTTPS:
 *    - La transferencia multipart/form-data viaja exclusivamente por TLS/HTTPS.
 * 3. Aislamiento y organización en carpetas:
 *    - Las fotos se confinan en el directorio virtual 'articulos_textil'.
 * 4. Control de tiempos de espera (Timeouts):
 *    - Connect timeout: 20s, Read timeout: 30s para evitar hilos zombis o consumo de batería.
 * 5. Ejecución en hilo secundario y retorno seguro a hilo principal (MainLooper).
 */
public class CloudinaryUploader {

    public static final String CLOUD_NAME = "ts4drobq";
    public static final String UPLOAD_PRESET = "ml_default";
    private static final String UPLOAD_URL = "https://api.cloudinary.com/v1_1/" + CLOUD_NAME + "/image/upload";

    public interface UploadCallback {
        void onSuccess(String imageUrl);
        void onError(String error);
    }

    /**
     * Sube un archivo de imagen comprimido directamente a Cloudinary sin servidor intermedio
     * usando el Upload Preset 'Unsigned'.
     */
    public static void subirImagen(Context context, File archivoImagen, UploadCallback callback) {
        Handler mainHandler = new Handler(Looper.getMainLooper());

        new Thread(() -> {
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

                // Campo: folder (organizar en carpeta articulos)
                dos.writeBytes(twoHyphens + boundary + lineEnd);
                dos.writeBytes("Content-Disposition: form-data; name=\"folder\"" + lineEnd + lineEnd);
                dos.writeBytes("articulos_textil" + lineEnd);

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
                    String secureUrl = json.getString("secure_url");
                    mainHandler.post(() -> callback.onSuccess(secureUrl));
                } else {
                    String errorMsg = "Error en Cloudinary (" + serverResponseCode + "): " + response.toString();
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
            }
        }).start();
    }
}