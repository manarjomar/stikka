package app.stikka.domain

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Movie
import android.graphics.Paint
import android.graphics.RectF
import android.media.MediaMetadataRetriever
import android.net.Uri
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Origen de un sticker animado: un vídeo o un GIF. */
class AnimSource(val uri: Uri, val isGif: Boolean, val durationMs: Long, val width: Int, val height: Int)

object AnimFrames {
    /** WhatsApp: animados de 10 s como máximo. Dejamos margen. */
    const val MAX_WINDOW_MS = 8000L
    /** Límite de fotogramas para no agotar la memoria. */
    const val MAX_FRAMES = 48

    fun open(context: Context, uri: Uri): AnimSource? {
        val mime = context.contentResolver.getType(uri).orEmpty()
        return if (mime == "image/gif") openGif(context, uri) else openVideo(context, uri)
    }

    @Suppress("DEPRECATION")
    private fun openGif(context: Context, uri: Uri): AnimSource? {
        val movie = context.contentResolver.openInputStream(uri)?.use { Movie.decodeStream(it) } ?: return null
        val d = movie.duration().toLong().takeIf { it > 0 } ?: 1000L
        return AnimSource(uri, true, d, movie.width(), movie.height())
    }

    private fun openVideo(context: Context, uri: Uri): AnimSource? {
        val r = MediaMetadataRetriever()
        return try {
            r.setDataSource(context, uri)
            val d = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: return null
            var w = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            var h = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            val rot = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            if (rot == 90 || rot == 270) { val t = w; w = h; h = t }
            AnimSource(uri, false, d, w, h)
        } catch (e: Exception) {
            null
        } finally {
            runCatching { r.release() }
        }
    }

    /** Instantes (ms) de cada fotograma del tramo elegido. */
    fun times(startMs: Long, endMs: Long, fps: Int): List<Long> {
        val step = 1000.0 / fps
        val n = min(MAX_FRAMES, max(1, ((endMs - startMs) / step).toInt()))
        return List(n) { i -> startMs + (i * step).roundToInt() }
    }

    /**
     * Saca los fotogramas ya colocados en el lienzo de 512 px.
     * [square]: recorta al centro en cuadrado; si no, encaja la imagen entera con margen.
     */
    fun extract(
        context: Context,
        src: AnimSource,
        times: List<Long>,
        square: Boolean,
        onProgress: (Float) -> Unit,
    ): List<Bitmap> {
        val out = ArrayList<Bitmap>(times.size)
        if (src.isGif) {
            @Suppress("DEPRECATION")
            val movie = context.contentResolver.openInputStream(src.uri)?.use { Movie.decodeStream(it) } ?: return emptyList()
            val w = max(1, movie.width())
            val h = max(1, movie.height())
            times.forEachIndexed { i, t ->
                val raw = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                @Suppress("DEPRECATION")
                movie.setTime((t % max(1, movie.duration())).toInt())
                @Suppress("DEPRECATION")
                movie.draw(Canvas(raw), 0f, 0f)
                out += layout(raw, square)
                raw.recycle()
                onProgress((i + 1f) / times.size)
            }
        } else {
            val r = MediaMetadataRetriever()
            try {
                r.setDataSource(context, src.uri)
                val scale = 640f / max(1, max(src.width, src.height))
                val tw = max(1, (src.width * min(1f, scale)).roundToInt())
                val th = max(1, (src.height * min(1f, scale)).roundToInt())
                times.forEachIndexed { i, t ->
                    val raw = r.getScaledFrameAtTime(t * 1000, MediaMetadataRetriever.OPTION_CLOSEST, tw, th)
                        ?: r.getFrameAtTime(t * 1000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    if (raw != null) {
                        out += layout(raw, square)
                        raw.recycle()
                    }
                    onProgress((i + 1f) / times.size)
                }
            } finally {
                runCatching { r.release() }
            }
        }
        return out
    }

    private fun layout(raw: Bitmap, square: Boolean): Bitmap {
        val size = StickerSpec.SIZE
        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        if (square) {
            val side = min(raw.width, raw.height)
            val left = (raw.width - side) / 2
            val top = (raw.height - side) / 2
            c.drawBitmap(raw, android.graphics.Rect(left, top, left + side, top + side), RectF(0f, 0f, size.toFloat(), size.toFloat()), paint)
        } else {
            val fit = StickerMath.fitInto(raw.width, raw.height)
            c.drawBitmap(raw, null, RectF(fit.left, fit.top, fit.left + fit.width, fit.top + fit.height), paint)
        }
        return out
    }

    /** Quita el fondo de un fotograma con el recorte inteligente (sin moverlo de sitio). */
    suspend fun removeBackground(frame: Bitmap): Bitmap {
        val mask = Images.confidenceToAlpha(Cutout.mask(frame))
        return Images.applyMask(frame, mask)
    }

    /**
     * Codifica el sticker animado por debajo de [maxBytes]: baja la calidad y, si no basta,
     * usa uno de cada dos fotogramas (manteniendo la duración total).
     */
    fun encode(frames: List<Bitmap>, frameMs: Int, maxBytes: Int = 500 * 1024, onProgress: (Float) -> Unit): ByteArray {
        var current = frames
        var duration = frameMs
        while (true) {
            var q = 80
            while (q >= 25) {
                val stills = current.mapIndexed { i, f ->
                    onProgress((i + 1f) / current.size)
                    Images.encodeWebpQuality(f, q)
                }
                val anim = WebpMuxer.animate(stills, StickerSpec.SIZE, StickerSpec.SIZE, duration)
                if (anim.size <= maxBytes) return anim
                q -= 15
            }
            if (current.size <= 2) {
                // Último recurso: calidad mínima.
                return WebpMuxer.animate(current.map { Images.encodeWebpQuality(it, 10) }, StickerSpec.SIZE, StickerSpec.SIZE, duration)
            }
            current = current.filterIndexed { i, _ -> i % 2 == 0 }
            duration *= 2
        }
    }
}
