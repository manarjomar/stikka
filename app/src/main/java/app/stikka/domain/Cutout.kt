package app.stikka.domain

import android.content.Context
import android.graphics.Bitmap
import com.google.android.gms.common.moduleinstall.ModuleInstall
import com.google.android.gms.common.moduleinstall.ModuleInstallRequest
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentation
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenter
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions
import kotlinx.coroutines.delay
import kotlinx.coroutines.tasks.await

/**
 * Recorte inteligente con ML Kit Subject Segmentation.
 * Todo ocurre en el móvil: la foto no sale del teléfono.
 */
object Cutout {

    private val segmenter: SubjectSegmenter by lazy {
        SubjectSegmentation.getClient(
            SubjectSegmenterOptions.Builder()
                .enableForegroundConfidenceMask()
                .build()
        )
    }

    /**
     * Se asegura de que el modelo está en el móvil. Si hay que descargarlo, avisa con [onDownloading]
     * y espera (la primera vez puede tardar unos segundos).
     */
    suspend fun ensureModel(context: Context, onDownloading: () -> Unit) {
        val client = ModuleInstall.getClient(context)
        if (client.areModulesAvailable(segmenter).await().areModulesAvailable()) return
        onDownloading()
        client.installModules(ModuleInstallRequest.newBuilder().addApi(segmenter).build()).await()
        repeat(120) {
            if (client.areModulesAvailable(segmenter).await().areModulesAvailable()) return
            delay(1000)
        }
        throw IllegalStateException("model download timeout")
    }

    /** Devuelve la confianza (0..1) de que cada píxel pertenece al sujeto. */
    suspend fun confidenceMask(bitmap: Bitmap): FloatArray {
        val result = segmenter.process(InputImage.fromBitmap(bitmap, 0)).await()
        val buffer = result.foregroundConfidenceMask
            ?: throw IllegalStateException("no mask")
        buffer.rewind()
        val mask = FloatArray(bitmap.width * bitmap.height)
        buffer.get(mask, 0, minOf(mask.size, buffer.remaining()))
        return mask
    }
}
