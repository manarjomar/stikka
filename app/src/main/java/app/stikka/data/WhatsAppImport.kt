package app.stikka.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.DocumentsContract

/** Un sticker encontrado en la carpeta de WhatsApp. */
data class WaFile(val uri: Uri, val name: String, val modified: Long, val size: Long)

/**
 * Lee los stickers que WhatsApp guarda en `Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Stickers`.
 * Android solo deja leerla si el usuario elige la carpeta una vez (selector del sistema).
 */
object WhatsAppImport {
    private const val EXTERNAL = "com.android.externalstorage.documents"

    /** Dónde abrir el selector: la carpeta de stickers de WhatsApp. */
    fun initialUri(business: Boolean = false): Uri {
        val path = if (business) {
            "primary:Android/media/com.whatsapp.w4b/WhatsApp Business/Media/WhatsApp Business Stickers"
        } else {
            "primary:Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Stickers"
        }
        return DocumentsContract.buildDocumentUri(EXTERNAL, path)
    }

    private val passThrough = setOf("com.whatsapp", "com.whatsapp.w4b", "whatsapp", "whatsapp business", "media", "android")

    fun list(context: Context, tree: Uri): List<WaFile> {
        val rootId = DocumentsContract.getTreeDocumentId(tree)
        val out = mutableListOf<WaFile>()
        walk(context, tree, rootId, 0, out)
        return out.distinctBy { it.name }.sortedByDescending { it.modified }
    }

    private fun walk(context: Context, tree: Uri, docId: String, depth: Int, out: MutableList<WaFile>) {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, docId)
        val cols = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
            DocumentsContract.Document.COLUMN_SIZE,
        )
        val dirs = mutableListOf<Pair<String, String>>()
        context.contentResolver.query(children, cols, null, null, null)?.use { c ->
            while (c.moveToNext()) {
                val id = c.getString(0) ?: continue
                val name = c.getString(1) ?: continue
                val mime = c.getString(2).orEmpty()
                if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                    dirs += id to name
                } else if (name.endsWith(".webp", ignoreCase = true)) {
                    out += WaFile(
                        DocumentsContract.buildDocumentUriUsingTree(tree, id),
                        name,
                        if (c.isNull(3)) 0L else c.getLong(3),
                        if (c.isNull(4)) 0L else c.getLong(4),
                    )
                }
            }
        }
        if (depth >= 5) return
        dirs.forEach { (id, name) ->
            val n = name.lowercase()
            if (n in passThrough || n.contains("sticker")) walk(context, tree, id, depth + 1, out)
        }
    }

    fun readBytes(context: Context, file: WaFile): ByteArray? =
        runCatching { context.contentResolver.openInputStream(file.uri)?.use { it.readBytes() } }.getOrNull()

    /** Miniatura pequeña para la cuadrícula. */
    fun thumbnail(context: Context, file: WaFile, size: Int = 160): Bitmap? = runCatching {
        val bytes = readBytes(context, file) ?: return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= size) sample *= 2
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
    }.getOrNull()
}
