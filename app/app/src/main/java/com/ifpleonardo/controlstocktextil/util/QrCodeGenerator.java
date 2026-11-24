package com.ifpleonardo.controlstocktextil.util;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.ifpleonardo.controlstocktextil.data.model.Articulo;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Generador de Códigos QR, Etiquetas de Almacén y Hojas de Etiquetas en formato PDF.
 * 
 * Capacidades:
 * 1. Codificación estándar QR con ZXing:
 *    - Codifica la referencia única del artículo textil (o su ID de Firestore si no tiene referencia).
 *    - Margen mínimo y codificación UTF-8 para garantizar legibilidad rápida desde cualquier distancia.
 * 2. Diseño de tarjetas visuales de estantería:
 *    - Dibuja sobre Canvas con Paint suavizado (ANTI_ALIAS_FLAG) los datos clave: Referencia,
 *      Nombre, Categoría, Ubicación de almacén (almacén + estantería) y Unidad de medida.
 * 3. Hojas de impresión A4 multi-página:
 *    - Organiza las etiquetas en una cuadrícula de 2 columnas x 4 filas (8 etiquetas por página A4)
 *      aprovechando papel autoadhesivo troquelado estándar para almacenes e institutos.
 * 4. Distribución segura:
 *    - Exporta a través de FileProvider con permisos de lectura efímeros para enviar a imprimir
 *      vía Google Cloud Print, servicios Mopria, WhatsApp o guardar en almacenamiento externo.
 */
public class QrCodeGenerator {

    /**
     * Genera un Bitmap en blanco y negro con el código QR del texto especificado.
     */
    public static Bitmap generarQrBitmap(String texto, int ancho, int alto) {
        if (texto == null || texto.trim().isEmpty()) {
            texto = "SIN_REFERENCIA";
        }

        try {
            QRCodeWriter writer = new QRCodeWriter();
            Map<EncodeHintType, Object> hints = new HashMap<>();
            hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
            hints.put(EncodeHintType.MARGIN, 1);

            BitMatrix bitMatrix = writer.encode(texto, BarcodeFormat.QR_CODE, ancho, alto, hints);
            Bitmap bitmap = Bitmap.createBitmap(ancho, alto, Bitmap.Config.ARGB_8888);

            for (int x = 0; x < ancho; x++) {
                for (int y = 0; y < alto; y++) {
                    bitmap.setPixel(x, y, bitMatrix.get(x, y) ? Color.BLACK : Color.WHITE);
                }
            }
            return bitmap;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Genera un Bitmap con la tarjeta / etiqueta de almacén lista para imprimir
     * o pegar en la estantería / rollo de tela.
     */
    public static Bitmap generarEtiquetaArticuloBitmap(Articulo articulo) {
        int ancho = 750;
        int alto = 450;
        Bitmap labelBitmap = Bitmap.createBitmap(ancho, alto, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(labelBitmap);

        // Fondo Blanco
        Paint paintFondo = new Paint();
        paintFondo.setColor(Color.WHITE);
        paintFondo.setStyle(Paint.Style.FILL);
        canvas.drawRect(0, 0, ancho, alto, paintFondo);

        // Borde exterior
        Paint paintBorde = new Paint();
        paintBorde.setColor(Color.DKGRAY);
        paintBorde.setStyle(Paint.Style.STROKE);
        paintBorde.setStrokeWidth(4);
        canvas.drawRoundRect(new RectF(8, 8, ancho - 8, alto - 8), 16, 16, paintBorde);

        // Barra superior de encabezado
        Paint paintHeader = new Paint();
        paintHeader.setColor(Color.rgb(33, 150, 243)); // Azul institucional
        paintHeader.setStyle(Paint.Style.FILL);
        canvas.drawRect(8, 8, ancho - 8, 56, paintHeader);

        // Texto Encabezado
        Paint paintHeaderText = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintHeaderText.setColor(Color.WHITE);
        paintHeaderText.setTextSize(20);
        paintHeaderText.setFakeBoldText(true);
        canvas.drawText("ALMACÉN TEXTIL FP · IES LEONARDO DA VINCI", 24, 38, paintHeaderText);

        // Código QR
        String contenidoQr = articulo.getReferencia() != null && !articulo.getReferencia().trim().isEmpty()
                ? articulo.getReferencia().trim()
                : (articulo.getId() != null ? articulo.getId() : "SIN_ID");

        int qrSize = 340;
        Bitmap qrBitmap = generarQrBitmap(contenidoQr, qrSize, qrSize);
        if (qrBitmap != null) {
            canvas.drawBitmap(qrBitmap, 24, 76, null);
        }

        // Textos del artículo en la mitad derecha
        int leftText = 380;
        Paint paintRef = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintRef.setColor(Color.BLACK);
        paintRef.setTextSize(26);
        paintRef.setFakeBoldText(true);
        canvas.drawText("REF: " + (articulo.getReferencia() != null ? articulo.getReferencia() : "S/R"), leftText, 110, paintRef);

        Paint paintNombre = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintNombre.setColor(Color.DKGRAY);
        paintNombre.setTextSize(22);
        paintNombre.setFakeBoldText(true);
        String nombre = articulo.getNombreCompleto();
        if (nombre.length() > 24) nombre = nombre.substring(0, 22) + "...";
        canvas.drawText(nombre, leftText, 155, paintNombre);

        Paint paintCategoria = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintCategoria.setColor(Color.rgb(2, 136, 209));
        paintCategoria.setTextSize(20);
        paintCategoria.setFakeBoldText(true);
        canvas.drawText("Cat: " + (articulo.getCategoria() != null ? articulo.getCategoria() : "GENERAL"), leftText, 200, paintCategoria);

        Paint paintUbicacion = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintUbicacion.setColor(Color.rgb(198, 40, 40)); // Rojo indicativo
        paintUbicacion.setTextSize(22);
        paintUbicacion.setFakeBoldText(true);
        String ubicacionStr = "Sin Ubicación";
        if (articulo.getUbicacion() != null) {
            ubicacionStr = articulo.getUbicacion().getFormatoCompleto();
        }
        if (ubicacionStr.length() > 24) ubicacionStr = ubicacionStr.substring(0, 22) + "...";
        canvas.drawText("📍 " + ubicacionStr, leftText, 250, paintUbicacion);

        Paint paintDetalles = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintDetalles.setColor(Color.GRAY);
        paintDetalles.setTextSize(18);
        String medidaStr = "Unidad: " + (articulo.getUnidadMedida() != null ? articulo.getUnidadMedida() : "Uds");
        canvas.drawText(medidaStr, leftText, 295, paintDetalles);

        String provStr = "Prov: " + (articulo.getProveedor() != null && !articulo.getProveedor().isEmpty() ? articulo.getProveedor() : "General");
        if (provStr.length() > 26) provStr = provStr.substring(0, 24) + "...";
        canvas.drawText(provStr, leftText, 335, paintDetalles);

        // Pie de tarjeta
        Paint paintFooter = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintFooter.setColor(Color.LTGRAY);
        paintFooter.setTextSize(14);
        canvas.drawText("Escanea con la app para consultar o registrar movimientos", 24, 432, paintFooter);

        return labelBitmap;
    }

    /**
     * Guarda el bitmap de la etiqueta como archivo PNG en la caché de la app.
     */
    public static File guardarEtiquetaComoImagen(Context context, Bitmap bitmap, String nombreArchivo) throws IOException {
        File cacheDir = context.getCacheDir();
        File archivo = new File(cacheDir, nombreArchivo + ".png");
        try (FileOutputStream out = new FileOutputStream(archivo)) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
            out.flush();
        }
        return archivo;
    }

    /**
     * Comparte o envía a imprimir una imagen de etiqueta vía FileProvider.
     */
    public static void compartirImagenEtiqueta(Context context, File archivoImagen) {
        Uri uri = FileProvider.getUriForFile(context, context.getPackageName() + ".fileprovider", archivoImagen);
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("image/png");
        intent.putExtra(Intent.EXTRA_STREAM, uri);
        intent.putExtra(Intent.EXTRA_SUBJECT, "Etiqueta QR Almacén");
        intent.putExtra(Intent.EXTRA_TEXT, "Etiqueta QR para almacén textil FP.");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        context.startActivity(Intent.createChooser(intent, "Imprimir o compartir etiqueta"));
    }

    /**
     * Genera un documento PDF tamaño A4 con hojas de etiquetas para todas las estanterías / telas.
     * Cuadrícula estándar: 2 columnas x 4 filas = 8 etiquetas por hoja A4.
     */
    public static File generarPdfEtiquetasAlmacen(Context context, List<Articulo> articulos) throws IOException {
        int pageWidth = 595; // A4 pt
        int pageHeight = 842; // A4 pt
        int cols = 2;
        int rows = 4;
        int itemsPerPage = cols * rows;

        PdfDocument pdfDocument = new PdfDocument();

        int marginX = 20;
        int marginY = 30;
        int cardWidth = (pageWidth - (marginX * 2) - 10) / cols;
        int cardHeight = (pageHeight - (marginY * 2) - 15) / rows;

        int totalPages = (int) Math.ceil((double) articulos.size() / itemsPerPage);
        if (totalPages == 0) totalPages = 1;

        Paint paintBorder = new Paint();
        paintBorder.setStyle(Paint.Style.STROKE);
        paintBorder.setStrokeWidth(1);
        paintBorder.setColor(Color.LTGRAY);

        Paint paintHeader = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintHeader.setColor(Color.DKGRAY);
        paintHeader.setTextSize(8);
        paintHeader.setFakeBoldText(true);

        Paint paintRef = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintRef.setColor(Color.BLACK);
        paintRef.setTextSize(12);
        paintRef.setFakeBoldText(true);

        Paint paintNombre = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintNombre.setColor(Color.DKGRAY);
        paintNombre.setTextSize(10);
        paintNombre.setFakeBoldText(true);

        Paint paintUbicacion = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintUbicacion.setColor(Color.rgb(180, 20, 20));
        paintUbicacion.setTextSize(10);
        paintUbicacion.setFakeBoldText(true);

        Paint paintSub = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintSub.setColor(Color.GRAY);
        paintSub.setTextSize(8);

        for (int p = 0; p < totalPages; p++) {
            PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(pageWidth, pageHeight, p + 1).create();
            PdfDocument.Page page = pdfDocument.startPage(pageInfo);
            Canvas canvas = page.getCanvas();

            int startIndex = p * itemsPerPage;
            int endIndex = Math.min(startIndex + itemsPerPage, articulos.size());

            for (int i = startIndex; i < endIndex; i++) {
                int indexInPage = i - startIndex;
                int col = indexInPage % cols;
                int row = indexInPage / cols;

                int x = marginX + col * (cardWidth + 10);
                int y = marginY + row * (cardHeight + 5);

                Articulo art = articulos.get(i);

                // Dibujar marco de corte de etiqueta
                canvas.drawRoundRect(new RectF(x, y, x + cardWidth, y + cardHeight), 8, 8, paintBorder);

                // Encabezado
                canvas.drawText("FP TEXTIL · IES LEONARDO", x + 10, y + 15, paintHeader);

                // Generar QR pequeño (110x110)
                String contenidoQr = art.getReferencia() != null && !art.getReferencia().trim().isEmpty()
                        ? art.getReferencia().trim()
                        : (art.getId() != null ? art.getId() : "S/R");
                Bitmap qrBmp = generarQrBitmap(contenidoQr, 100, 100);
                if (qrBmp != null) {
                    canvas.drawBitmap(qrBmp, x + 8, y + 22, null);
                }

                // Textos a la derecha del QR
                int textX = x + 116;
                String refStr = "REF: " + (art.getReferencia() != null ? art.getReferencia() : "S/R");
                if (refStr.length() > 18) refStr = refStr.substring(0, 16) + "...";
                canvas.drawText(refStr, textX, y + 36, paintRef);

                String nomStr = art.getNombreCompleto();
                if (nomStr.length() > 20) nomStr = nomStr.substring(0, 18) + "...";
                canvas.drawText(nomStr, textX, y + 54, paintNombre);

                String ubiStr = "📍 " + (art.getUbicacion() != null ? art.getUbicacion().getFormatoCompleto() : "Sin Ubicación");
                if (ubiStr.length() > 20) ubiStr = ubiStr.substring(0, 18) + "...";
                canvas.drawText(ubiStr, textX, y + 74, paintUbicacion);

                String catStr = "Cat: " + (art.getCategoria() != null ? art.getCategoria() : "GENERAL") + " (" + (art.getUnidadMedida() != null ? art.getUnidadMedida() : "Uds") + ")";
                if (catStr.length() > 22) catStr = catStr.substring(0, 20) + "...";
                canvas.drawText(catStr, textX, y + 92, paintSub);

                String provStr = "Prov: " + (art.getProveedor() != null ? art.getProveedor() : "Gral");
                if (provStr.length() > 22) provStr = provStr.substring(0, 20) + "...";
                canvas.drawText(provStr, textX, y + 108, paintSub);
            }

            pdfDocument.finishPage(page);
        }

        File cacheDir = context.getCacheDir();
        File archivoPdf = new File(cacheDir, "Etiquetas_Estanterias_Textil.pdf");
        try (FileOutputStream out = new FileOutputStream(archivoPdf)) {
            pdfDocument.writeTo(out);
        } finally {
            pdfDocument.close();
        }

        return archivoPdf;
    }

    /**
     * Comparte o abre el archivo PDF generado para imprimirlo desde el visor del sistema.
     */
    public static void compartirOImprimirPdf(Context context, File archivoPdf) {
        Uri uri = FileProvider.getUriForFile(context, context.getPackageName() + ".fileprovider", archivoPdf);
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("application/pdf");
        intent.putExtra(Intent.EXTRA_STREAM, uri);
        intent.putExtra(Intent.EXTRA_SUBJECT, "Etiquetas QR Estanterías Almacén Textil");
        intent.putExtra(Intent.EXTRA_TEXT, "Hojas de etiquetas QR tamaño A4 para imprimir y colocar en las estanterías.");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        context.startActivity(Intent.createChooser(intent, "Imprimir etiquetas en PDF"));
    }
}