package com.ifpleonardo.controlstocktextil.util;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

/**
 * Optimizador y compresor local de imágenes capturadas con la cámara o galería.
 * 
 * Medidas de estabilidad, privacidad y eficiencia:
 * 1. Prevención de Out-Of-Memory (OOM):
 *    - Decodifica inicialmente con inJustDecodeBounds = true para inspeccionar el tamaño
 *      en píxeles sin alojar el bitmap completo en la memoria RAM de Android.
 *    - Calcula inSampleSize por potencias de 2 para submuestrear imágenes pesadas.
 * 2. Corrección completa de orientación EXIF:
 *    - Soporta rotaciones (90, 180, 270) y espejos de cámara frontal (FLIP / TRANSPOSE / TRANSVERSE).
 * 3. Limpieza de metadatos y fondo transparente:
 *    - Elimina metadatos EXIF sensibles (GPS, modelo) al recomprimir como nuevo JPEG.
 *    - Aplana transparencias (PNG) sobre fondo blanco para evitar fondos negros en JPEG.
 * 4. Control adaptativo de peso y dimensiones:
 *    - Artículos: máx. 1080px y objetivo <= 220 KB.
 *    - Avatares de perfil: máx. 512px y objetivo <= 100 KB.
 * 5. Reciclaje explícito de Bitmaps con try/finally para liberar memoria nativa en cualquier ruta.
 */
public class ImageCompressor {

    private static final int MAX_DIMENSION_ARTICULO = 1080;
    private static final int MAX_BYTES_ARTICULO = 220 * 1024;

    private static final int MAX_DIMENSION_AVATAR = 512;
    private static final int MAX_BYTES_AVATAR = 100 * 1024;

    private static final int INITIAL_QUALITY = 82;
    private static final int MIN_QUALITY = 50;

    /**
     * Comprime una fotografía de artículo textil (máx. 1080px, <= 220 KB).
     */
    public static File comprimirImagen(Context context, Uri uri) throws Exception {
        return comprimirImagen(context, uri, MAX_DIMENSION_ARTICULO, MAX_BYTES_ARTICULO);
    }

    /**
     * Comprime una fotografía de perfil / avatar (máx. 512px, <= 100 KB).
     */
    public static File comprimirAvatar(Context context, Uri uri) throws Exception {
        return comprimirImagen(context, uri, MAX_DIMENSION_AVATAR, MAX_BYTES_AVATAR);
    }

    /**
     * Comprime una imagen desde una Uri (cámara o galería) aplicando submuestreo OOM-safe,
     * corrección EXIF completa, aplanado de alpha y ajuste adaptativo de calidad JPEG.
     */
    public static File comprimirImagen(Context context, Uri uri, int maxDimension, int maxBytes) throws Exception {
        // 1. Obtener orientación EXIF completa si está disponible
        int orientation = ExifInterface.ORIENTATION_NORMAL;
        try (InputStream is = context.getContentResolver().openInputStream(uri)) {
            if (is != null) {
                ExifInterface exif = new ExifInterface(is);
                orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
            }
        } catch (Exception ignored) {}

        // 2. Decodificar solo los límites para calcular escala y no agotar la memoria RAM
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        try (InputStream is = context.getContentResolver().openInputStream(uri)) {
            if (is == null) {
                throw new Exception("No se pudo abrir la imagen seleccionada.");
            }
            BitmapFactory.decodeStream(is, null, options);
        }

        int anchoOriginal = options.outWidth;
        int altoOriginal = options.outHeight;
        if (anchoOriginal <= 0 || altoOriginal <= 0) {
            throw new Exception("El archivo seleccionado no es una imagen válida.");
        }

        int inSampleSize = 1;
        while ((anchoOriginal / inSampleSize) > maxDimension || (altoOriginal / inSampleSize) > maxDimension) {
            inSampleSize *= 2;
        }

        // 3. Decodificar imagen con muestreo eficiente
        options.inJustDecodeBounds = false;
        options.inSampleSize = inSampleSize;

        Bitmap bitmap = null;
        Bitmap bitmapFinal = null;
        Bitmap bitmapSinAlpha = null;

        try {
            try (InputStream is = context.getContentResolver().openInputStream(uri)) {
                if (is == null) {
                    throw new Exception("No se pudo leer la imagen seleccionada.");
                }
                bitmap = BitmapFactory.decodeStream(is, null, options);
            }

            if (bitmap == null) {
                throw new Exception("No se pudo decodificar la imagen seleccionada.");
            }

            // 4. Aplicar orientación EXIF completa + escalado final
            Matrix matrix = new Matrix();
            aplicarOrientacionExif(matrix, orientation);

            float scale = Math.min((float) maxDimension / bitmap.getWidth(), (float) maxDimension / bitmap.getHeight());
            if (scale < 1.0f) {
                matrix.postScale(scale, scale);
            }

            bitmapFinal = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
            if (bitmapFinal != bitmap) {
                bitmap.recycle();
                bitmap = null;
            }

            // 5. Aplanar transparencia (ej. PNG) sobre fondo blanco antes de pasar a JPEG
            Bitmap targetBitmap = bitmapFinal;
            if (bitmapFinal.hasAlpha()) {
                bitmapSinAlpha = Bitmap.createBitmap(bitmapFinal.getWidth(), bitmapFinal.getHeight(), Bitmap.Config.ARGB_8888);
                Canvas canvas = new Canvas(bitmapSinAlpha);
                canvas.drawColor(Color.WHITE);
                canvas.drawBitmap(bitmapFinal, 0, 0, null);
                targetBitmap = bitmapSinAlpha;
            }

            // 6. Comprimir en memoria ajustando calidad si excede el umbral máximo de bytes
            int quality = INITIAL_QUALITY;
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            targetBitmap.compress(Bitmap.CompressFormat.JPEG, quality, baos);
            while (baos.size() > maxBytes && quality > MIN_QUALITY) {
                baos.reset();
                quality -= 8;
                targetBitmap.compress(Bitmap.CompressFormat.JPEG, quality, baos);
            }

            // 7. Escribir resultado final a archivo temporal en caché
            File archivoSalida = new File(context.getCacheDir(), "foto_comprimida_" + System.currentTimeMillis() + ".jpg");
            try (FileOutputStream fos = new FileOutputStream(archivoSalida)) {
                baos.writeTo(fos);
                fos.flush();
            }

            return archivoSalida;
        } finally {
            if (bitmap != null && !bitmap.isRecycled()) bitmap.recycle();
            if (bitmapFinal != null && !bitmapFinal.isRecycled()) bitmapFinal.recycle();
            if (bitmapSinAlpha != null && !bitmapSinAlpha.isRecycled()) bitmapSinAlpha.recycle();
        }
    }

    private static void aplicarOrientacionExif(Matrix matrix, int orientation) {
        switch (orientation) {
            case ExifInterface.ORIENTATION_FLIP_HORIZONTAL:
                matrix.postScale(-1, 1);
                break;
            case ExifInterface.ORIENTATION_ROTATE_180:
                matrix.postRotate(180);
                break;
            case ExifInterface.ORIENTATION_FLIP_VERTICAL:
                matrix.postRotate(180);
                matrix.postScale(-1, 1);
                break;
            case ExifInterface.ORIENTATION_TRANSPOSE:
                matrix.postRotate(90);
                matrix.postScale(-1, 1);
                break;
            case ExifInterface.ORIENTATION_ROTATE_90:
                matrix.postRotate(90);
                break;
            case ExifInterface.ORIENTATION_TRANSVERSE:
                matrix.postRotate(270);
                matrix.postScale(-1, 1);
                break;
            case ExifInterface.ORIENTATION_ROTATE_270:
                matrix.postRotate(270);
                break;
            default:
                break;
        }
    }
}
