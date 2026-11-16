package app.stikka.ui

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.stikka.StikkaApp
import app.stikka.data.Sticker
import app.stikka.domain.Cutout
import app.stikka.domain.Decor
import app.stikka.domain.DecorEditor
import app.stikka.domain.DecorRenderer
import app.stikka.domain.EditTarget
import app.stikka.domain.Images
import app.stikka.domain.MaskEditor
import app.stikka.domain.StickerMath
import app.stikka.domain.tr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class Stage { LOADING, DOWNLOADING_MODEL, CUTTING }

/** Estado de la pantalla de recorte. */
sealed interface CutState {
    data class Working(val stage: Stage, val original: Bitmap? = null) : CutState

    /**
     * Recorte listo. [base] es el recorte de 512 px sin decorar y [sticker] el resultado final
     * con la decoración aplicada.
     */
    data class Ready(
        val original: Bitmap,
        val mask: FloatArray,
        val base: Bitmap,
        val decor: Decor,
        val sticker: Bitmap,
        val autoCut: Boolean,
    ) : CutState

    data class NoSubject(val original: Bitmap) : CutState
    data class Failed(val message: String, val original: Bitmap? = null) : CutState
}

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val store = StikkaApp.instance.store

    private val _stickers = MutableStateFlow(store.list())
    val stickers: StateFlow<List<Sticker>> = _stickers

    var cut by mutableStateOf<CutState?>(null)
        private set
    var viewing by mutableStateOf<Sticker?>(null)
        private set
    var saving by mutableStateOf(false)
        private set
    var message by mutableStateOf<String?>(null)
    var editor by mutableStateOf<MaskEditor?>(null)
        private set
    var decorator by mutableStateOf<DecorEditor?>(null)
        private set

    private var job: Job? = null

    // ---------- Nuevo sticker ----------

    fun startCutout(uri: Uri) {
        job?.cancel()
        viewing = null
        editor = null
        decorator = null
        cut = CutState.Working(Stage.LOADING)
        job = viewModelScope.launch {
            var original: Bitmap? = null
            try {
                original = withContext(Dispatchers.IO) { Images.load(getApplication(), uri) }
                cut = CutState.Working(Stage.CUTTING, original)
                Cutout.ensureModel(getApplication()) { cut = CutState.Working(Stage.DOWNLOADING_MODEL, original) }
                cut = CutState.Working(Stage.CUTTING, original)
                val mask = Images.confidenceToAlpha(Cutout.confidenceMask(original))
                if (StickerMath.coverage(mask) < 0.002f) {
                    cut = CutState.NoSubject(original)
                    return@launch
                }
                cut = build(original, mask, autoCut = true, decor = Decor())
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Throwable) {
                cut = CutState.Failed(
                    if (original == null) {
                        tr("No se ha podido abrir la imagen.", "The image couldn't be opened.")
                    } else {
                        tr(
                            "No se ha podido hacer el recorte. La primera vez necesitas conexión para descargar el modelo, y Google Play Services debe estar al día. También puedes usar la foto entera y recortarla tú con el lazo o el rectángulo.",
                            "The cutout failed. The first time you need a connection to download the model, and Google Play Services must be up to date. You can also use the whole photo and cut it yourself with the lasso or rectangle.",
                        )
                    },
                    original,
                )
            }
        }
    }

    /** Usa la imagen completa, sin recortar. */
    fun useWholeImage() {
        val (original, decor) = when (val c = cut) {
            is CutState.NoSubject -> c.original to Decor()
            is CutState.Failed -> (c.original ?: return) to Decor()
            is CutState.Ready -> c.original to c.decor
            else -> return
        }
        job?.cancel()
        job = viewModelScope.launch {
            cut = build(original, Images.fullMask(original), autoCut = false, decor = decor)
        }
    }

    private suspend fun build(original: Bitmap, mask: FloatArray, autoCut: Boolean, decor: Decor): CutState.Ready =
        withContext(Dispatchers.Default) {
            val base = Images.renderSticker(Images.applyMask(original, mask), mask)
            CutState.Ready(original, mask, base, decor, DecorRenderer.render(base, decor), autoCut)
        }

    fun saveCut() {
        val ready = cut as? CutState.Ready ?: return
        if (saving) return
        saving = true
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    store.save(
                        webp = Images.encodeWebp(ready.sticker),
                        basePng = Images.encodePng(ready.base),
                        decorJson = ready.decor.toJson(),
                        sourceJpeg = Images.encodeJpeg(ready.original),
                        maskPng = Images.encodeMask(ready.mask, ready.original.width, ready.original.height),
                    )
                }
                refresh()
                cut = null
                message = tr("Sticker guardado", "Sticker saved")
            } catch (e: Throwable) {
                message = tr("No se ha podido guardar", "Couldn't save it")
            } finally {
                saving = false
            }
        }
    }

    fun closeCut() {
        job?.cancel()
        cut = null
    }

    // ---------- Editor de recorte ----------

    fun editCut() {
        val ready = cut as? CutState.Ready ?: return
        editor = MaskEditor(ready.original, ready.mask, EditTarget.NewCut)
    }

    fun editSticker(sticker: Sticker) {
        if (!sticker.editable || saving) return
        viewModelScope.launch {
            val loaded = withContext(Dispatchers.IO) {
                runCatching {
                    val opts = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 }
                    val src = BitmapFactory.decodeFile(sticker.sourceFile.absolutePath, opts)!!
                    val maskBmp = BitmapFactory.decodeFile(sticker.maskFile.absolutePath, opts)!!
                    src to Images.decodeMask(maskBmp, src.width, src.height)
                }.getOrNull()
            }
            if (loaded == null) {
                message = tr("No se puede retocar este sticker", "This sticker can't be edited")
            } else {
                editor = MaskEditor(loaded.first, loaded.second, EditTarget.Saved(sticker.id))
            }
        }
    }

    fun closeEditor() { editor = null }

    fun finishEditor() {
        val e = editor ?: return
        if (saving) return
        val mask = e.maskFloats()
        when (val t = e.target) {
            EditTarget.NewCut -> viewModelScope.launch {
                val decor = (cut as? CutState.Ready)?.decor ?: Decor()
                cut = build(e.source, mask, autoCut = true, decor = decor)
                editor = null
            }
            is EditTarget.Saved -> persist(
                ok = tr("Recorte actualizado", "Cutout updated"),
                done = { editor = null },
            ) {
                val sticker = store.get(t.stickerId) ?: return@persist
                val base = Images.renderSticker(Images.applyMask(e.source, mask), mask)
                val decor = store.loadDecor(sticker)
                store.write(
                    sticker,
                    webp = Images.encodeWebp(DecorRenderer.render(base, decor)),
                    basePng = Images.encodePng(base),
                    maskPng = Images.encodeMask(mask, e.width, e.height),
                )
            }
        }
    }

    // ---------- Decoración (textos, emojis, borde) ----------

    fun decorateCut() {
        val ready = cut as? CutState.Ready ?: return
        decorator = DecorEditor(ready.base, ready.decor, EditTarget.NewCut)
    }

    fun decorateSticker(sticker: Sticker) {
        if (saving) return
        viewModelScope.launch {
            val loaded = withContext(Dispatchers.IO) {
                runCatching { store.loadBase(sticker)!! to store.loadDecor(sticker) }.getOrNull()
            }
            if (loaded == null) {
                message = tr("No se puede abrir este sticker", "This sticker can't be opened")
            } else {
                decorator = DecorEditor(loaded.first, loaded.second, EditTarget.Saved(sticker.id))
            }
        }
    }

    fun closeDecorator() { decorator = null }

    fun finishDecorator() {
        val d = decorator ?: return
        if (saving) return
        val decor = d.decor
        when (val t = d.target) {
            EditTarget.NewCut -> viewModelScope.launch {
                val ready = cut as? CutState.Ready
                if (ready != null) {
                    val sticker = withContext(Dispatchers.Default) { DecorRenderer.render(ready.base, decor) }
                    cut = ready.copy(decor = decor, sticker = sticker)
                }
                decorator = null
            }
            is EditTarget.Saved -> persist(
                ok = tr("Sticker actualizado", "Sticker updated"),
                done = { decorator = null },
            ) {
                val sticker = store.get(t.stickerId) ?: return@persist
                store.write(
                    sticker,
                    webp = Images.encodeWebp(DecorRenderer.render(d.base, decor)),
                    basePng = if (sticker.baseFile.exists()) null else Images.encodePng(d.base),
                    decorJson = decor.toJson(),
                )
            }
        }
    }

    /** Guarda cambios de un sticker existente en segundo plano y refresca la vista. */
    private fun persist(ok: String, done: () -> Unit, work: suspend () -> Unit) {
        saving = true
        viewModelScope.launch {
            try {
                withContext(Dispatchers.Default) { work() }
                refresh()
                viewing = viewing?.let { store.get(it.id) }
                done()
                message = ok
            } catch (x: Throwable) {
                message = tr("No se ha podido guardar", "Couldn't save it")
            } finally {
                saving = false
            }
        }
    }

    // ---------- Biblioteca ----------

    fun open(sticker: Sticker) { viewing = sticker }
    fun closeViewer() { viewing = null }

    fun delete(sticker: Sticker) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { store.delete(sticker) }
            if (viewing?.id == sticker.id) viewing = null
            refresh()
            message = tr("Sticker eliminado", "Sticker deleted")
        }
    }

    private suspend fun refresh() {
        _stickers.value = withContext(Dispatchers.IO) { store.list() }
    }
}
