package app.stikka.domain

import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

enum class Tool { BRUSH, LASSO, RECT }

/** Qué hace una forma (lazo o rectángulo) con la máscara. */
enum class ShapeOp {
    /** La forma pasa a ser el sticker: dentro todo, fuera nada. */
    CUT,
    /** Conserva el recorte solo dentro de la forma. */
    KEEP,
    /** Añade lo de dentro. */
    ADD,
    /** Quita lo de dentro. */
    REMOVE,
}

/** Qué se está retocando: el recorte recién hecho o un sticker ya guardado. */
sealed interface EditTarget {
    data object NewCut : EditTarget
    data class Saved(val stickerId: String) : EditTarget
}

/**
 * Sesión del editor de máscara. La máscara es la transparencia final (0 = fuera, 255 = dentro).
 * Mantiene una imagen compuesta que se actualiza solo en la zona pintada, para que vaya fluido.
 */
class MaskEditor(val source: Bitmap, initial: FloatArray, val target: EditTarget) {
    val width = source.width
    val height = source.height
    private val src = IntArray(width * height).also { source.getPixels(it, 0, width, 0, 0, width, height) }
    private val mask = ByteArray(width * height) { (initial[it].coerceIn(0f, 1f) * 255f).roundToInt().toByte() }
    private val startMask = mask.copyOf()

    val composite: Bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

    /** Cambia cada vez que la imagen compuesta cambia, para redibujar. */
    var frame by mutableIntStateOf(0)
        private set
    var erase by mutableStateOf(true)
    var tool by mutableStateOf(Tool.BRUSH)
    var shapeOp by mutableStateOf(ShapeOp.CUT)
    var squareLock by mutableStateOf(false)
    var brushDp by mutableFloatStateOf(36f)
    var canUndo by mutableStateOf(false)
        private set
    var canRedo by mutableStateOf(false)
        private set

    /** Vista de máscara: lo eliminado se ve en rojo en lugar de transparente. */
    private val overlayState = mutableStateOf(false)
    var overlay: Boolean
        get() = overlayState.value
        set(value) {
            overlayState.value = value
            refresh(0, 0, width, height)
        }

    private val undo = ArrayDeque<ByteArray>()
    private val redo = ArrayDeque<ByteArray>()

    init {
        refresh(0, 0, width, height)
    }

    fun beginStroke() {
        undo.addLast(mask.copyOf())
        if (undo.size > MAX_UNDO) undo.removeFirst()
        redo.clear()
        flags()
    }

    /** Anula el trazo en curso (por ejemplo, al poner el segundo dedo para hacer zoom). */
    fun cancelStroke() {
        val last = undo.removeLastOrNull() ?: return
        last.copyInto(mask)
        refresh(0, 0, width, height)
        flags()
    }

    fun undo() {
        val prev = undo.removeLastOrNull() ?: return
        redo.addLast(mask.copyOf())
        prev.copyInto(mask)
        refresh(0, 0, width, height)
        flags()
    }

    fun redo() {
        val next = redo.removeLastOrNull() ?: return
        undo.addLast(mask.copyOf())
        next.copyInto(mask)
        refresh(0, 0, width, height)
        flags()
    }

    /** Vuelve al recorte con el que se abrió el editor. */
    fun reset() {
        beginStroke()
        startMask.copyInto(mask)
        refresh(0, 0, width, height)
    }

    /** Pinta una línea de toques entre dos puntos (en píxeles de la imagen). */
    fun line(x0: Float, y0: Float, x1: Float, y1: Float, radius: Float) {
        val r = max(0.75f, radius)
        val dist = hypot(x1 - x0, y1 - y0)
        val steps = max(1, ceil(dist / (r * 0.3f)).toInt())
        var minX = Int.MAX_VALUE
        var minY = Int.MAX_VALUE
        var maxX = Int.MIN_VALUE
        var maxY = Int.MIN_VALUE
        for (i in 0..steps) {
            val t = i.toFloat() / steps
            val cx = x0 + (x1 - x0) * t
            val cy = y0 + (y1 - y0) * t
            val box = dab(cx, cy, r) ?: continue
            minX = min(minX, box[0]); minY = min(minY, box[1])
            maxX = max(maxX, box[2]); maxY = max(maxY, box[3])
        }
        if (maxX > minX && maxY > minY) refresh(minX, minY, maxX, maxY)
    }

    /** Aplica un polígono (coordenadas de imagen, pares x,y) según [shapeOp]. */
    fun applyPolygon(points: FloatArray) {
        val n = points.size / 2
        if (n < 3) return
        beginStroke()
        val xs = FloatArray(n)
        for (y in 0 until height) {
            val yc = y + 0.5f
            var count = 0
            for (i in 0 until n) {
                val j = (i + 1) % n
                val ax = points[2 * i]; val ay = points[2 * i + 1]
                val bx = points[2 * j]; val by = points[2 * j + 1]
                if ((ay <= yc && by > yc) || (by <= yc && ay > yc)) {
                    xs[count++] = ax + (yc - ay) / (by - ay) * (bx - ax)
                }
            }
            xs.sort(0, count)
            applyRow(y, xs, count)
        }
        refresh(0, 0, width, height)
    }

    /** Aplica un rectángulo (coordenadas de imagen) según [shapeOp]. */
    fun applyRect(x0: Float, y0: Float, x1: Float, y1: Float) {
        applyPolygon(floatArrayOf(x0, y0, x1, y0, x1, y1, x0, y1))
    }

    private fun applyRow(y: Int, xs: FloatArray, count: Int) {
        val row = y * width
        var k = 0
        for (x in 0 until width) {
            val xc = x + 0.5f
            while (k < count && xs[k] <= xc) k++
            val inside = k % 2 == 1
            val i = row + x
            when (shapeOp) {
                ShapeOp.CUT -> mask[i] = if (inside) 255.toByte() else 0
                ShapeOp.KEEP -> if (!inside) mask[i] = 0
                ShapeOp.ADD -> if (inside) mask[i] = 255.toByte()
                ShapeOp.REMOVE -> if (inside) mask[i] = 0
            }
        }
    }

    private fun dab(cx: Float, cy: Float, r: Float): IntArray? {
        val x0 = max(0, floor(cx - r).toInt())
        val y0 = max(0, floor(cy - r).toInt())
        val x1 = min(width, ceil(cx + r).toInt() + 1)
        val y1 = min(height, ceil(cy + r).toInt() + 1)
        if (x0 >= x1 || y0 >= y1) return null
        val hard = 0.55f
        for (y in y0 until y1) {
            val dy = y + 0.5f - cy
            val row = y * width
            for (x in x0 until x1) {
                val dx = x + 0.5f - cx
                val d = hypot(dx, dy) / r
                if (d >= 1f) continue
                val f = if (d <= hard) 1f else {
                    val t = 1f - (d - hard) / (1f - hard)
                    t * t * (3f - 2f * t)
                }
                val i = row + x
                val old = mask[i].toInt() and 0xFF
                val v = if (erase) min(old, ((1f - f) * 255f).roundToInt()) else max(old, (f * 255f).roundToInt())
                mask[i] = v.toByte()
            }
        }
        return intArrayOf(x0, y0, x1, y1)
    }

    private fun refresh(x0: Int, y0: Int, x1: Int, y1: Int) {
        val rw = x1 - x0
        val rh = y1 - y0
        if (rw <= 0 || rh <= 0) return
        val out = IntArray(rw * rh)
        for (y in 0 until rh) {
            val srcRow = (y0 + y) * width + x0
            val outRow = y * rw
            for (x in 0 until rw) {
                val p = src[srcRow + x]
                val m = mask[srcRow + x].toInt() and 0xFF
                out[outRow + x] = if (overlay) {
                    val t = (255 - m) / 255f * 0.65f
                    val r = ((p shr 16 and 0xFF) * (1 - t) + 255 * t).toInt()
                    val g = ((p shr 8 and 0xFF) * (1 - t) + 40 * t).toInt()
                    val b = ((p and 0xFF) * (1 - t) + 90 * t).toInt()
                    (0xFF shl 24) or (r shl 16) or (g shl 8) or b
                } else {
                    (p and 0x00FFFFFF) or (m shl 24)
                }
            }
        }
        composite.setPixels(out, 0, rw, x0, y0, rw, rh)
        frame++
    }

    private fun flags() {
        canUndo = undo.isNotEmpty()
        canRedo = redo.isNotEmpty()
    }

    fun maskFloats(): FloatArray = FloatArray(mask.size) { (mask[it].toInt() and 0xFF) / 255f }

    companion object {
        const val MAX_UNDO = 12
    }
}
