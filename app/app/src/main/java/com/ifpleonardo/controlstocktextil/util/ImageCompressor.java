package com.ifpleonardo.controlstocktextil.util;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

/**
 * Optimizador y compresor local de imágenes capturadas con la cámara o galería.
 * 
 * Medidas de estabilidad y eficiencia:
 * 1. Prevención de Out-Of-Memory (OOM):
 *    - Decodifica inicialmente con inJustDecodeBounds = true para inspeccionar el tamaño
 *      en píxeles sin alojar el bitmap completo en la memoria RAM de Android.
 *    - Calcula inSampleSize por potencias de 2 para submuestrear imágenes pesadas (ej. 48MP/108MP).
 * 2. Corrección de orientación EXIF:
 *    - Detecta la orientación nativa del sensor de la cámara y aplica la rotación geométrica
 *      adecuada para evitar que las fotos de tejidos se muestren tumbadas o invertidas.
 * 3. Restricción de dimensiones y peso:
 *    - Limita el lado mayor a 1080px (suficiente para alta nitidez en fichas de rollos y fornituras)
 *      y comprime en JPEG con calidad 80, reduciendo el archivo de ~8 MB a solo ~100-150 KB.
 * 4. Reciclaje explícito de Bitmaps:
 *    - Llama a bitmap.recycle() inmediatamente después del escalado para liberar memoria nativa.
 */
public class ImageCompressor {

    private static final int MAX_DIMENSION = 1080;
    private static final int COMPRESSION_QUALITY = 80;

    /**
     * Comprime una imagen desde una Uri (cámara o galería) reduciendo sus dimensiones
     * y peso a ~100-150 KB sin perder nitidez de la textura textil.
     */
    public static File comprimirImagen(Context context, Uri uri) throws Exception {
        // 1. Obtener orientación EXIF si está disponible
        int rotacion = 0;
        try (InputStream is = context.getContentResolver().openInputStream(uri)) {
            if (is != null) {
                ExifInterface exif = new ExifInterface(is);
                int orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
                if (orientation == ExifInterface.ORIENTATION_ROTATE_90) rotacion = 90;
                else if (orientation == ExifInterface.ORIENTATION_ROTATE_180) rotacion = 180;
                else if (orientation == ExifInterface.ORIENTATION_ROTATE_270) rotacion = 270;
            }
        } catch (Exception ignored) {}

        // 2. Decodificar solo los límites para calcular escala y no agotar la memoria RAM
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        try (InputStream is = context.getContentResolver().openInputStream(uri)) {
            BitmapFactory.decodeStream(is, null, options);
        }

        int anchoOriginal = options.outWidth;
        int altoOriginal = options.outHeight;

        int inSampleSize = 1;
        while ((anchoOriginal / inSampleSize) > MAX_DIMENSION || (altoOriginal / inSampleSize) > MAX_DIMENSION) {
            inSampleSize *= 2;
        }

        // 3. Decodificar imagen con muestreo eficiente
        options.inJustDecodeBounds = false;
        options.inSampleSize = inSampleSize;

        Bitmap bitmap;
        try (InputStream is = context.getContentResolver().openInputStream(uri)) {
            bitmap = BitmapFactory.decodeStream(is, null, options);
        }

        if (bitmap == null) {
            throw new Exception("No se pudo decodificar la imagen seleccionada.");
        }

        // 4. Aplicar rotación y escalado final si es necesario
        Matrix matrix = new Matrix();
        if (rotacion != 0) {
            matrix.postRotate(rotacion);
        }

        float scale = Math.min((float) MAX_DIMENSION / bitmap.getWidth(), (float) MAX_DIMENSION / bitmap.getHeight());
        if (scale < 1.0f) {
            matrix.postScale(scale, scale);
        }

        Bitmap bitmapFinal = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
        if (bitmapFinal != bitmap) {
            bitmap.recycle();
        }

        // 5. Guardar en archivo temporal comprimido en JPEG al 80%
        File archivoSalida = new File(context.getCacheDir(), "foto_comprimida_" + System.currentTimeMillis() + ".jpg");
        try (FileOutputStream fos = new FileOutputStream(archivoSalida)) {
            bitmapFinal.compress(Bitmap.CompressFormat.JPEG, COMPRESSION_QUALITY, fos);
            fos.flush();
        } finally {
            bitmapFinal.recycle();
        }

        return archivoSalida;
    }
}