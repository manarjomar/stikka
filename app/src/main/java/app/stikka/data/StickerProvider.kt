package app.stikka.data

import android.content.ContentProvider
import android.content.ContentValues
import android.content.res.AssetFileDescriptor
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import app.stikka.BuildConfig
import app.stikka.StikkaApp
import java.io.File

/**
 * Proveedor que lee WhatsApp para mostrar nuestros paquetes. Sigue el formato de la app de ejemplo oficial:
 * - `metadata` y `metadata/<pack>`: datos de los paquetes.
 * - `stickers/<pack>`: lista de stickers con sus emojis.
 * - `stickers_asset/<pack>/<archivo>`: los archivos (stickers `.webp` e icono `tray.png`).
 */
class StickerProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

    private val app get() = context?.applicationContext as StikkaApp

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor {
        val seg = uri.pathSegments
        val cursor = when {
            seg.size == 1 && seg[0] == METADATA -> packsCursor(app.packs.list())
            seg.size == 2 && seg[0] == METADATA -> packsCursor(app.packs.list().filter { it.id == seg[1] })
            seg.size == 2 && seg[0] == STICKERS -> stickersCursor(seg[1])
            else -> throw IllegalArgumentException("Unknown URI: $uri")
        }
        context?.contentResolver?.let { cursor.setNotificationUri(it, uri) }
        return cursor
    }

    private fun packsCursor(packs: List<Pack>): Cursor {
        val c = MatrixCursor(
            arrayOf(
                "sticker_pack_identifier", "sticker_pack_name", "sticker_pack_publisher", "sticker_pack_icon",
                "android_play_store_link", "ios_app_download_link", "sticker_pack_publisher_email",
                "sticker_pack_publisher_website", "sticker_pack_privacy_policy_website",
                "sticker_pack_license_agreement_website", "image_data_version",
                "whatsapp_will_not_cache_stickers", "animated_sticker_pack",
            )
        )
        packs.filter { it.ready }.forEach { p ->
            c.addRow(arrayOf<Any>(p.id, p.name, p.publisher, TRAY, "", "", "", "", "", "", p.version.toString(), 0, if (p.animated) 1 else 0))
        }
        return c
    }

    private fun stickersCursor(packId: String): Cursor {
        val c = MatrixCursor(arrayOf("sticker_file_name", "sticker_emoji", "sticker_accessibility_text"))
        val pack = app.packs.get(packId) ?: return c
        pack.stickerIds.forEach { id ->
            val sticker = app.store.get(id) ?: return@forEach
            val meta = app.store.loadMeta(sticker)
            c.addRow(arrayOf<Any?>("$id.webp", meta.whatsappEmojis.joinToString(","), null))
        }
        return c
    }

    override fun openAssetFile(uri: Uri, mode: String): AssetFileDescriptor? {
        val seg = uri.pathSegments
        if (seg.size != 3 || seg[0] != STICKERS_ASSET) return null
        val pack = app.packs.get(seg[1]) ?: return null
        val name = seg[2]
        val file: File = if (name == TRAY) {
            app.packs.ensureTray(pack)
        } else {
            val id = name.removeSuffix(".webp")
            if (id !in pack.stickerIds) return null
            app.store.get(id)?.file ?: return null
        }
        if (!file.exists()) return null
        val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        return AssetFileDescriptor(pfd, 0, file.length())
    }

    override fun getType(uri: Uri): String {
        val seg = uri.pathSegments
        return when {
            seg.size == 1 && seg[0] == METADATA -> "vnd.android.cursor.dir/vnd.$AUTHORITY.$METADATA"
            seg.size == 2 && seg[0] == METADATA -> "vnd.android.cursor.item/vnd.$AUTHORITY.$METADATA"
            seg.size == 2 && seg[0] == STICKERS -> "vnd.android.cursor.dir/vnd.$AUTHORITY.$STICKERS"
            seg.size == 3 && seg[0] == STICKERS_ASSET && seg[2] == TRAY -> "image/png"
            seg.size == 3 && seg[0] == STICKERS_ASSET -> "image/webp"
            else -> throw IllegalArgumentException("Unknown URI: $uri")
        }
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = throw UnsupportedOperationException()
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = throw UnsupportedOperationException()
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int =
        throw UnsupportedOperationException()

    companion object {
        val AUTHORITY = "${BuildConfig.APPLICATION_ID}.stickercontentprovider"
        const val METADATA = "metadata"
        const val STICKERS = "stickers"
        const val STICKERS_ASSET = "stickers_asset"
        const val TRAY = "tray.png"
    }
}
