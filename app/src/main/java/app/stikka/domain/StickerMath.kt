package app.stikka.domain

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Medidas que exige WhatsApp para un sticker estático. */
object StickerSpec {
    const val SIZE = 512
    const val PADDING = 16
    const val MAX_BYTES = 100 * 1024
}

/** Rectángulo de píxeles: [left, right) x [top, bottom). */
data class PixelBox(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width get() = right - left
    val height get() = bottom - top
}

/** Rectángulo con decimales, para dibujar. */
data class FloatBox(val left: Float, val top: Float, val width: Float, val height: Float)

object StickerMath {

    /** Convierte la confianza del modelo (0..1) en transparencia (0..255) con un borde suave. */
    fun alphaFor(confidence: Float, low: Float = 0.35f, high: Float = 0.65f): Int {
        if (confidence <= low) return 0
        if (confidence >= high) return 255
        val t = (confidence - low) / (high - low)
        val smooth = t * t * (3f - 2f * t)
        return (smooth * 255f).roundToInt().coerceIn(0, 255)
    }

    /** Caja mínima que contiene el sujeto. Null si la máscara está vacía. */
    fun maskBounds(mask: FloatArray, width: Int, height: Int, threshold: Float = 0.5f): PixelBox? {
        require(mask.size >= width * height) { "mask too small" }
        var minX = width
        var minY = height
        var maxX = -1
        var maxY = -1
        for (y in 0 until height) {
            val row = y * width
            for (x in 0 until width) {
                if (mask[row + x] >= threshold) {
                    if (x < minX) minX = x
                    if (x > maxX) maxX = x
                    if (y < minY) minY = y
                    if (y > maxY) maxY = y
                }
            }
        }
        if (maxX < 0) return null
        return PixelBox(minX, minY, maxX + 1, maxY + 1)
    }

    /** Amplía la caja un margen (proporcional) sin salirse de la imagen. */
    fun expand(box: PixelBox, width: Int, height: Int, fraction: Float = 0.04f): PixelBox {
        val margin = (max(box.width, box.height) * fraction).roundToInt()
        return PixelBox(
            max(0, box.left - margin),
            max(0, box.top - margin),
            min(width, box.right + margin),
            min(height, box.bottom + margin),
        )
    }

    /** Dónde dibujar una imagen de [srcW]x[srcH] centrada en el lienzo del sticker, sin deformarla. */
    fun fitInto(srcW: Int, srcH: Int, canvas: Int = StickerSpec.SIZE, padding: Int = StickerSpec.PADDING): FloatBox {
        require(srcW > 0 && srcH > 0) { "empty image" }
        val room = (canvas - 2 * padding).toFloat()
        val scale = min(room / srcW, room / srcH)
        val w = srcW * scale
        val h = srcH * scale
        return FloatBox((canvas - w) / 2f, (canvas - h) / 2f, w, h)
    }

    /** Proporción de la imagen que ocupa el sujeto (0..1). Sirve para avisar si no se ha encontrado nada. */
    fun coverage(mask: FloatArray, threshold: Float = 0.5f): Float {
        if (mask.isEmpty()) return 0f
        var n = 0
        for (v in mask) if (v >= threshold) n++
        return n.toFloat() / mask.size
    }
}
