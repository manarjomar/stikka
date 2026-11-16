package app.stikka.domain

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ImageDecoder
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import android.os.Build
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

object Images {

    /** Abre una imagen ya girada según su EXIF y reducida para que el recorte vaya rápido. */
    fun load(context: Context, uri: Uri, maxSide: Int = 1600): Bitmap {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        val bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val w = info.size.width
            val h = info.size.height
            val scale = min(1f, maxSide.toFloat() / max(w, h))
            if (scale < 1f) {
                decoder.setTargetSize(
                    (w * scale).roundToInt().coerceAtLeast(1),
                    (h * scale).roundToInt().coerceAtLeast(1),
                )
            }
        }
        return if (bitmap.config == Bitmap.Config.ARGB_8888) bitmap else bitmap.copy(Bitmap.Config.ARGB_8888, false)
    }

    /** Pasa la confianza del modelo a transparencia final (0..1) con borde suave. */
    fun confidenceToAlpha(confidence: FloatArray): FloatArray =
        FloatArray(confidence.size) { StickerMath.alphaFor(confidence[it]) / 255f }

    /** Aplica la máscara (transparencia 0..1): lo que no es sujeto queda transparente. */
    fun applyMask(source: Bitmap, mask: FloatArray): Bitmap {
        val w = source.width
        val h = source.height
        val px = IntArray(w * h)
        source.getPixels(px, 0, w, 0, 0, w, h)
        for (i in px.indices) {
            val a = (mask[i].coerceIn(0f, 1f) * 255f).roundToInt()
            px[i] = (px[i] and 0x00FFFFFF) or (a shl 24)
        }
        return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
    }

    /** Máscara llena: toda la imagen es sujeto. */
    fun fullMask(source: Bitmap): FloatArray = FloatArray(source.width * source.height) { 1f }

    /** Sticker de 512x512: recorta al sujeto, lo centra y deja un pequeño margen transparente. */
    fun renderSticker(cutout: Bitmap, mask: FloatArray): Bitmap {
        val w = cutout.width
        val h = cutout.height
        val bounds = StickerMath.maskBounds(mask, w, h, 0.4f)
            ?.let { StickerMath.expand(it, w, h, 0.02f) }
            ?: PixelBox(0, 0, w, h)
        var crop = Bitmap.createBitmap(cutout, bounds.left, bounds.top, bounds.width, bounds.height)

        // Reducción por pasos para que los bordes no salgan dentados.
        val target = StickerSpec.SIZE - 2 * StickerSpec.PADDING
        while (max(crop.width, crop.height) > target * 2) {
            crop = Bitmap.createScaledBitmap(crop, max(1, crop.width / 2), max(1, crop.height / 2), true)
        }

        val fit = StickerMath.fitInto(crop.width, crop.height)
        val out = Bitmap.createBitmap(StickerSpec.SIZE, StickerSpec.SIZE, Bitmap.Config.ARGB_8888)
        Canvas(out).drawBitmap(
            crop,
            null,
            RectF(fit.left, fit.top, fit.left + fit.width, fit.top + fit.height),
            Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG),
        )
        return out
    }

    /** WebP con transparencia por debajo del límite de WhatsApp (100 KB). */
    fun encodeWebp(bitmap: Bitmap, maxBytes: Int = StickerSpec.MAX_BYTES): ByteArray {
        val format = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Bitmap.CompressFormat.WEBP_LOSSY
        } else {
            @Suppress("DEPRECATION")
            Bitmap.CompressFormat.WEBP
        }
        var quality = 95
        var bytes: ByteArray
        do {
            val out = ByteArrayOutputStream()
            bitmap.compress(format, quality, out)
            bytes = out.toByteArray()
            quality -= 7
        } while (bytes.size > maxBytes && quality >= 20)
        return bytes
    }

    fun encodeJpeg(bitmap: Bitmap, quality: Int = 90): ByteArray =
        ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.JPEG, quality, it) }.toByteArray()

    /** Guarda la máscara como PNG en escala de grises, para poder retocarla más adelante. */
    fun encodeMask(mask: FloatArray, width: Int, height: Int): ByteArray {
        val px = IntArray(width * height) {
            val g = (mask[it].coerceIn(0f, 1f) * 255f).roundToInt()
            (0xFF shl 24) or (g shl 16) or (g shl 8) or g
        }
        val bmp = Bitmap.createBitmap(px, width, height, Bitmap.Config.ARGB_8888)
        return ByteArrayOutputStream().also { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
    }

    /** Lee una máscara guardada con [encodeMask]. */
    fun decodeMask(bitmap: Bitmap, width: Int, height: Int): FloatArray {
        val m = if (bitmap.width == width && bitmap.height == height) bitmap
        else Bitmap.createScaledBitmap(bitmap, width, height, true)
        val px = IntArray(width * height)
        m.getPixels(px, 0, width, 0, 0, width, height)
        return FloatArray(px.size) { (px[it] shr 16 and 0xFF) / 255f }
    }
}
