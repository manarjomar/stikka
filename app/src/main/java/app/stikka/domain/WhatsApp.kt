package app.stikka.domain

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import app.stikka.data.Pack
import app.stikka.data.StickerProvider

/** Integración con WhatsApp (normal y Business). */
object WhatsApp {
    const val CONSUMER = "com.whatsapp"
    const val BUSINESS = "com.whatsapp.w4b"

    fun installed(context: Context): List<String> = listOf(CONSUMER, BUSINESS).filter { isInstalled(context, it) }

    private fun isInstalled(context: Context, pkg: String): Boolean = try {
        context.packageManager.getApplicationInfo(pkg, 0).enabled
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }

    /** ¿Está el paquete añadido en todas las WhatsApp instaladas? Null si no se puede saber. */
    fun isAdded(context: Context, packId: String): Boolean? {
        val apps = installed(context)
        if (apps.isEmpty()) return null
        val results = apps.map { pkg -> addedIn(context, pkg, packId) }
        if (results.any { it == null }) return results.filterNotNull().let { if (it.isEmpty()) null else it.all { r -> r } }
        return results.all { it == true }
    }

    private fun addedIn(context: Context, pkg: String, packId: String): Boolean? = try {
        val authority = "$pkg.provider.sticker_whitelist_check"
        if (context.packageManager.resolveContentProvider(authority, PackageManager.GET_META_DATA) == null) {
            null
        } else {
            val uri = Uri.Builder().scheme(ContentResolver.SCHEME_CONTENT).authority(authority)
                .appendPath("is_whitelisted")
                .appendQueryParameter("authority", StickerProvider.AUTHORITY)
                .appendQueryParameter("identifier", packId)
                .build()
            context.contentResolver.query(uri, null, null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getInt(c.getColumnIndexOrThrow("result")) == 1 else null
            }
        }
    } catch (e: Exception) {
        null
    }

    /** Intent oficial para añadir un paquete. Con las dos WhatsApp instaladas se deja elegir. */
    fun addIntent(context: Context, pack: Pack): Intent? {
        val apps = installed(context)
        if (apps.isEmpty()) return null
        val intent = Intent("com.whatsapp.intent.action.ENABLE_STICKER_PACK").apply {
            putExtra("sticker_pack_id", pack.id)
            putExtra("sticker_pack_authority", StickerProvider.AUTHORITY)
            putExtra("sticker_pack_name", pack.name)
        }
        return if (apps.size == 1) {
            intent.setPackage(apps[0])
        } else {
            Intent.createChooser(intent, tr("Añadir a…", "Add to…"))
        }
    }

    /** Avisa a WhatsApp de que los paquetes han cambiado. */
    fun notifyChanged(context: Context) {
        runCatching {
            context.contentResolver.notifyChange(Uri.parse("content://${StickerProvider.AUTHORITY}/${StickerProvider.METADATA}"), null)
        }
    }
}
