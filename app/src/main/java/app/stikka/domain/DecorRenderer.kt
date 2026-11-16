package app.stikka.domain

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.RectF
import android.graphics.Typeface
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/** Dibuja el borde y las capas de texto/emoji. Lo usan la vista previa y el sticker final. */
object DecorRenderer {

    const val TEXT_SIZE = 64f
    const val EMOJI_SIZE = 128f

    /** Recorte con giro/espejo/tamaño, borde y sombra (todo menos las capas). */
    fun outlined(base: Bitmap, decor: Decor): Bitmap {
        var b = transformed(base, decor)
        if (decor.outline && decor.outlineWidth > 0f) b = outline(b, decor.outlineColor, decor.outlineWidth)
        if (decor.shadow) b = shadow(b)
        return b
    }

    private fun transformed(base: Bitmap, decor: Decor): Bitmap {
        val rot = ((decor.baseRotation % 360) + 360) % 360
        if (rot == 0 && !decor.baseFlip && decor.baseScale == 1f) return base
        val out = Bitmap.createBitmap(base.width, base.height, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        val cx = base.width / 2f
        val cy = base.height / 2f
        c.translate(cx, cy)
        c.rotate(rot.toFloat())
        val s = decor.baseScale.coerceIn(0.3f, 1f)
        c.scale(if (decor.baseFlip) -s else s, s)
        c.drawBitmap(base, -cx, -cy, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
        return out
    }

    private fun outline(base: Bitmap, color: Int, w: Float): Bitmap {
        val out = Bitmap.createBitmap(base.width, base.height, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        val silhouette = Paint(Paint.FILTER_BITMAP_FLAG).apply {
            colorFilter = PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN)
        }
        for (ring in listOf(w, w * 0.7f, w * 0.4f)) {
            var a = 0
            while (a < 360) {
                val rad = Math.toRadians(a.toDouble())
                c.drawBitmap(base, (cos(rad) * ring).toFloat(), (sin(rad) * ring).toFloat(), silhouette)
                a += 12
            }
        }
        c.drawBitmap(base, 0f, 0f, null)
        return out
    }

    private fun shadow(base: Bitmap): Bitmap {
        val out = Bitmap.createBitmap(base.width, base.height, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        val alpha = base.extractAlpha()
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(110, 0, 0, 0)
            maskFilter = BlurMaskFilter(9f, BlurMaskFilter.Blur.NORMAL)
        }
        c.drawBitmap(alpha, 5f, 8f, p)
        c.drawBitmap(base, 0f, 0f, null)
        return out
    }

    /** Sticker final de 512 px: recorte (con borde) + capas. */
    fun render(base: Bitmap, decor: Decor): Bitmap {
        val outlined = outlined(base, decor)
        if (decor.layers.isEmpty()) return outlined
        val out = if (outlined === base) base.copy(Bitmap.Config.ARGB_8888, true) else outlined
        val c = Canvas(out)
        decor.layers.forEach { drawLayer(c, it) }
        return out
    }

    private fun typeface(font: LayerFont): Typeface = when (font) {
        LayerFont.BOLD -> Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        LayerFont.CLASSIC -> Typeface.create("sans-serif-black", Typeface.NORMAL)
        LayerFont.SERIF -> Typeface.create(Typeface.SERIF, Typeface.BOLD)
        LayerFont.MONO -> Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        LayerFont.SCRIPT -> Typeface.create("cursive", Typeface.BOLD)
    }

    private fun fillPaint(layer: Layer) = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
        textAlign = Paint.Align.CENTER
        if (layer.kind == LayerKind.TEXT) {
            textSize = TEXT_SIZE
            typeface = typeface(layer.font)
            color = layer.color
        } else {
            textSize = EMOJI_SIZE
            color = Color.BLACK
        }
    }

    private fun strokeColor(color: Int): Int {
        val lum = (0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color)) / 255.0
        return if (lum > 0.6) Color.BLACK else Color.WHITE
    }

    private fun lines(layer: Layer) = layer.text.ifEmpty { " " }.split('\n')

    /** Caja de la capa en sus coordenadas locales (centrada en 0,0, sin escala ni giro). */
    fun localBounds(layer: Layer): RectF {
        val p = fillPaint(layer)
        val ls = lines(layer)
        val w = ls.maxOf { p.measureText(it) }
        val h = p.fontSpacing * ls.size
        val pad = when {
            layer.kind == LayerKind.TEXT && layer.bubble -> p.textSize * 0.35f
            layer.kind == LayerKind.TEXT && layer.stroke -> p.textSize * 0.12f
            else -> 4f
        }
        return RectF(-w / 2 - pad, -h / 2 - pad, w / 2 + pad, h / 2 + pad)
    }

    fun drawLayer(canvas: Canvas, layer: Layer) {
        val fill = fillPaint(layer)
        val ls = lines(layer)
        val fm = fill.fontMetrics
        val lineH = fill.fontSpacing
        val total = lineH * ls.size
        canvas.save()
        canvas.translate(layer.x, layer.y)
        canvas.rotate(layer.rotation)
        canvas.scale(layer.scale, layer.scale)
        if (layer.kind == LayerKind.TEXT && layer.bubble) {
            val b = localBounds(layer)
            val bubble = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = strokeColor(layer.color) }
            val r = fill.textSize * 0.45f
            canvas.drawRoundRect(b, r, r, bubble)
        }
        val stroke = if (layer.kind == LayerKind.TEXT && layer.stroke && !layer.bubble) {
            Paint(fill).apply {
                style = Paint.Style.STROKE
                strokeWidth = max(4f, fill.textSize * 0.16f)
                strokeJoin = Paint.Join.ROUND
                color = strokeColor(layer.color)
            }
        } else null
        ls.forEachIndexed { i, line ->
            val baseline = -total / 2f + i * lineH - fm.ascent
            stroke?.let { canvas.drawText(line, 0f, baseline, it) }
            canvas.drawText(line, 0f, baseline, fill)
        }
        canvas.restore()
    }

    /** ¿El punto (en coordenadas del lienzo) cae dentro de la capa? */
    fun hit(layer: Layer, px: Float, py: Float, slop: Float = 12f): Boolean {
        val dx = px - layer.x
        val dy = py - layer.y
        val rad = Math.toRadians(-layer.rotation.toDouble())
        val lx = ((dx * cos(rad) - dy * sin(rad)) / layer.scale).toFloat()
        val ly = ((dx * sin(rad) + dy * cos(rad)) / layer.scale).toFloat()
        val b = localBounds(layer)
        val s = slop / layer.scale
        return lx >= b.left - s && lx <= b.right + s && ly >= b.top - s && ly <= b.bottom + s
    }
}
