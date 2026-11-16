package app.stikka.domain

import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Sesión de decoración: capas de texto/emoji y borde sobre el recorte de 512 px. */
class DecorEditor(val base: Bitmap, initial: Decor, val target: EditTarget) {
    var decor by mutableStateOf(initial)
        private set
    var selectedId by mutableStateOf<Long?>(null)

    private var outlineKey: Decor? = null
    private var outlineCache: Bitmap = base

    val selected: Layer? get() = decor.layers.firstOrNull { it.id == selectedId }

    /** Recorte con borde, recalculado solo cuando cambia el borde. */
    fun outlinedBase(): Bitmap {
        val key = decor.baseKey
        if (key != outlineKey) {
            outlineCache = DecorRenderer.outlined(base, decor)
            outlineKey = key
        }
        return outlineCache
    }

    fun setOutline(enabled: Boolean = decor.outline, color: Int = decor.outlineColor, width: Float = decor.outlineWidth) {
        decor = decor.copy(outline = enabled, outlineColor = color, outlineWidth = width)
    }

    fun setBase(rotation: Int = decor.baseRotation, flip: Boolean = decor.baseFlip, scale: Float = decor.baseScale, shadow: Boolean = decor.shadow) {
        decor = decor.copy(baseRotation = rotation, baseFlip = flip, baseScale = scale, shadow = shadow)
    }

    fun addText(text: String) {
        val n = decor.layers.count { it.kind == LayerKind.TEXT }
        val layer = Layer(System.nanoTime(), LayerKind.TEXT, text, y = 430f - (n % 4) * 90f)
        decor = decor.copy(layers = decor.layers + layer)
        selectedId = layer.id
    }

    fun addEmoji(emoji: String) {
        val n = decor.layers.count { it.kind == LayerKind.EMOJI }
        val layer = Layer(
            System.nanoTime(), LayerKind.EMOJI, emoji,
            x = 400f - (n % 3) * 60f, y = 110f + (n % 3) * 50f, scale = 0.9f, rotation = 12f,
        )
        decor = decor.copy(layers = decor.layers + layer)
        selectedId = layer.id
    }

    fun update(id: Long, change: (Layer) -> Layer) {
        decor = decor.copy(layers = decor.layers.map { if (it.id == id) change(it) else it })
    }

    fun delete(id: Long) {
        decor = decor.copy(layers = decor.layers.filterNot { it.id == id })
        if (selectedId == id) selectedId = null
    }

    fun bringToFront(id: Long) {
        val l = decor.layers.firstOrNull { it.id == id } ?: return
        decor = decor.copy(layers = decor.layers.filterNot { it.id == id } + l)
    }

    fun duplicate(id: Long) {
        val l = decor.layers.firstOrNull { it.id == id } ?: return
        val copy = l.copy(id = System.nanoTime(), x = (l.x + 30f).coerceAtMost(500f), y = (l.y + 30f).coerceAtMost(500f))
        decor = decor.copy(layers = decor.layers + copy)
        selectedId = copy.id
    }

    /** Capa de más arriba bajo el punto (coordenadas del lienzo de 512). */
    fun hitTest(x: Float, y: Float): Long? =
        decor.layers.lastOrNull { DecorRenderer.hit(it, x, y) }?.id
}
