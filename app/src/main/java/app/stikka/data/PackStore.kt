package app.stikka.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File

/** Un paquete de stickers tal como se entrega a WhatsApp. */
data class Pack(
    val id: String,
    val name: String,
    val publisher: String,
    val stickerIds: List<String>,
    val trayId: String?,
    /** Sube con cada cambio para que WhatsApp vuelva a leer el paquete. */
    val version: Int = 1,
    val animated: Boolean = false,
) {
    val count get() = stickerIds.size
    val ready get() = count in MIN_STICKERS..MAX_STICKERS
    val effectiveTrayId get() = trayId?.takeIf { it in stickerIds } ?: stickerIds.firstOrNull()

    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("name", name)
        .put("publisher", publisher)
        .put("stickers", JSONArray(stickerIds))
        .put("tray", trayId ?: JSONObject.NULL)
        .put("version", version)
        .put("animated", animated)

    companion object {
        const val MIN_STICKERS = 3
        const val MAX_STICKERS = 30
        /** WhatsApp admite de 1 a 10 paquetes por app. */
        const val MAX_PACKS = 10

        fun fromJson(o: JSONObject): Pack {
            val arr = o.optJSONArray("stickers") ?: JSONArray()
            return Pack(
                id = o.getString("id"),
                name = o.optString("name"),
                publisher = o.optString("publisher"),
                stickerIds = (0 until arr.length()).map { arr.getString(it) },
                trayId = if (o.isNull("tray")) null else o.optString("tray").ifBlank { null },
                version = o.optInt("version", 1),
                animated = o.optBoolean("animated", false),
            )
        }
    }
}

/** Paquetes guardados en `packs.json` y sus iconos de bandeja en `packs/<id>/tray.png`. */
class PackStore(context: Context, private val stickers: StickerStore) {
    private val file = File(context.filesDir, "packs.json")
    private val trayRoot = File(context.filesDir, "packs").apply { mkdirs() }

    @Synchronized
    fun list(): List<Pack> = runCatching {
        val arr = JSONArray(file.readText())
        (0 until arr.length()).map { Pack.fromJson(arr.getJSONObject(it)) }
    }.getOrDefault(emptyList())

    fun get(id: String): Pack? = list().firstOrNull { it.id == id }

    @Synchronized
    private fun saveAll(packs: List<Pack>) {
        val tmp = File(file.parentFile, "packs.tmp")
        tmp.writeText(JSONArray().apply { packs.forEach { put(it.toJson()) } }.toString())
        tmp.renameTo(file)
    }

    @Synchronized
    fun create(name: String, publisher: String, animated: Boolean = false): Pack {
        val pack = Pack("pack_${System.currentTimeMillis()}", name.take(128), publisher.take(128), emptyList(), null, 1, animated)
        saveAll(list() + pack)
        return pack
    }

    /** Añade un paquete ya hecho (importado). */
    @Synchronized
    fun add(pack: Pack) {
        saveAll(list() + pack)
        refreshTray(pack)
    }

    /** Cambia un paquete, sube su versión y regenera el icono. */
    @Synchronized
    fun update(id: String, change: (Pack) -> Pack): Pack? {
        var result: Pack? = null
        saveAll(list().map {
            if (it.id == id) change(it).copy(version = it.version + 1).also { p -> result = p } else it
        })
        result?.let { refreshTray(it) }
        return result
    }

    @Synchronized
    fun delete(id: String) {
        saveAll(list().filterNot { it.id == id })
        File(trayRoot, id).deleteRecursively()
    }

    /** Un sticker ha cambiado o se ha borrado: actualiza los paquetes que lo contienen. */
    @Synchronized
    fun onStickerChanged(stickerId: String, deleted: Boolean) {
        list().filter { stickerId in it.stickerIds }.forEach { p ->
            update(p.id) { if (deleted) it.copy(stickerIds = it.stickerIds - stickerId) else it }
        }
    }

    /** Marca cada paquete como animado si contiene algún sticker animado. */
    @Synchronized
    fun recomputeTypes(isAnimated: (String) -> Boolean) {
        val packs = list()
        val fixed = packs.map { p -> p.copy(animated = p.stickerIds.any(isAnimated)) }
        if (fixed != packs) saveAll(fixed.map { f -> if (packs.first { it.id == f.id }.animated != f.animated) f.copy(version = f.version + 1) else f })
    }

    fun trayFile(pack: Pack): File = File(File(trayRoot, pack.id), "tray.png")

    /** Icono de bandeja: 96x96 PNG de menos de 50 KB, sacado del sticker elegido. */
    fun refreshTray(pack: Pack) {
        val out = trayFile(pack)
        val sticker = pack.effectiveTrayId?.let { stickers.get(it) }
        if (sticker == null) {
            out.delete()
            return
        }
        val src = BitmapFactory.decodeFile(sticker.file.absolutePath) ?: return
        val tray = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888)
        Canvas(tray).drawBitmap(src, null, RectF(0f, 0f, 96f, 96f), Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
        val bytes = ByteArrayOutputStream().also { tray.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
        out.parentFile?.mkdirs()
        out.writeBytes(bytes)
    }

    fun ensureTray(pack: Pack): File {
        val f = trayFile(pack)
        if (!f.exists()) refreshTray(pack)
        return f
    }
}
