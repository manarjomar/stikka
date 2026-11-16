package app.stikka.domain

import org.json.JSONArray
import org.json.JSONObject

enum class LayerKind { TEXT, EMOJI }

enum class LayerFont { BOLD, CLASSIC, SERIF, MONO, SCRIPT }

/** Un texto o emoji colocado sobre el sticker. Coordenadas en el lienzo de 512 px (centro de la capa). */
data class Layer(
    val id: Long,
    val kind: LayerKind,
    val text: String,
    val x: Float = 256f,
    val y: Float = 256f,
    val scale: Float = 1f,
    val rotation: Float = 0f,
    val color: Int = 0xFFFFFFFF.toInt(),
    val font: LayerFont = LayerFont.BOLD,
    val stroke: Boolean = true,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("kind", kind.name)
        .put("text", text)
        .put("x", x.toDouble())
        .put("y", y.toDouble())
        .put("scale", scale.toDouble())
        .put("rotation", rotation.toDouble())
        .put("color", color)
        .put("font", font.name)
        .put("stroke", stroke)

    companion object {
        fun fromJson(o: JSONObject) = Layer(
            id = o.optLong("id", System.nanoTime()),
            kind = runCatching { LayerKind.valueOf(o.getString("kind")) }.getOrDefault(LayerKind.TEXT),
            text = o.optString("text"),
            x = o.optDouble("x", 256.0).toFloat(),
            y = o.optDouble("y", 256.0).toFloat(),
            scale = o.optDouble("scale", 1.0).toFloat(),
            rotation = o.optDouble("rotation", 0.0).toFloat(),
            color = o.optInt("color", 0xFFFFFFFF.toInt()),
            font = runCatching { LayerFont.valueOf(o.getString("font")) }.getOrDefault(LayerFont.BOLD),
            stroke = o.optBoolean("stroke", true),
        )
    }
}

/** Decoración de un sticker: borde alrededor del recorte y capas de texto/emoji. */
data class Decor(
    val outline: Boolean = false,
    val outlineColor: Int = 0xFFFFFFFF.toInt(),
    val outlineWidth: Float = 10f,
    val layers: List<Layer> = emptyList(),
) {
    val isEmpty get() = !outline && layers.isEmpty()

    fun toJson(): String = JSONObject()
        .put("outline", outline)
        .put("outlineColor", outlineColor)
        .put("outlineWidth", outlineWidth.toDouble())
        .put("layers", JSONArray().apply { layers.forEach { put(it.toJson()) } })
        .toString()

    companion object {
        fun fromJson(json: String?): Decor {
            if (json.isNullOrBlank()) return Decor()
            return runCatching {
                val o = JSONObject(json)
                val arr = o.optJSONArray("layers") ?: JSONArray()
                Decor(
                    outline = o.optBoolean("outline", false),
                    outlineColor = o.optInt("outlineColor", 0xFFFFFFFF.toInt()),
                    outlineWidth = o.optDouble("outlineWidth", 10.0).toFloat(),
                    layers = (0 until arr.length()).map { Layer.fromJson(arr.getJSONObject(it)) },
                )
            }.getOrDefault(Decor())
        }
    }
}
