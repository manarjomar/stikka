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
import android.content.Context
import android.content.Intent
import app.stikka.data.Pack
import app.stikka.data.Sticker
import app.stikka.data.StickerMeta
import app.stikka.data.WaFile
import app.stikka.data.WhatsAppImport
import app.stikka.domain.WebpInfo
import app.stikka.domain.WhatsApp
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
    private val packStore = StikkaApp.instance.packs
    private val prefs = app.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _packs = MutableStateFlow(packStore.list())
    val packs: StateFlow<List<Pack>> = _packs

    /** Pestaña de inicio: 0 = stickers, 1 = paquetes. */
    var tab by mutableStateOf(0)
    /** Stickers seleccionados con pulsación larga. */
    var selection by mutableStateOf<Set<String>>(emptySet())
        private set
    var openPackId by mutableStateOf<String?>(null)
        private set
    /** Paquete al que se están añadiendo stickers (pantalla de selección). */
    var pickerPackId by mutableStateOf<String?>(null)
        private set
    /** ¿Está cada paquete añadido en WhatsApp? (null = no se sabe) */
    var addedStatus by mutableStateOf<Map<String, Boolean?>>(emptyMap())
        private set
    var whatsappInstalled by mutableStateOf(true)
        private set
    var viewingMeta by mutableStateOf(StickerMeta())
        private set

    // Importación desde WhatsApp
    var importOpen by mutableStateOf(false)
        private set
    var importTree by mutableStateOf(prefs.getString("wa_tree", null)?.let { Uri.parse(it) })
        private set
    var importFiles by mutableStateOf<List<WaFile>?>(null)
        private set
    var importLoading by mutableStateOf(false)
        private set
    var importSelected by mutableStateOf<Set<String>>(emptySet())
        private set
    var importedOrigins by mutableStateOf<Set<String>>(emptySet())
        private set
    var importProgress by mutableStateOf<Pair<Int, Int>?>(null)
        private set

    val lastPublisher: String get() = prefs.getString("publisher", null).orEmpty()

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
                changedStickerId = sticker.id
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
                changedStickerId = sticker.id
                store.write(
                    sticker,
                    webp = Images.encodeWebp(DecorRenderer.render(d.base, decor)),
                    basePng = if (sticker.baseFile.exists()) null else Images.encodePng(d.base),
                    decorJson = decor.toJson(),
                )
            }
        }
    }

    private var changedStickerId: String? = null

    /** Guarda cambios de un sticker existente en segundo plano y refresca la vista. */
    private fun persist(ok: String, done: () -> Unit, work: suspend () -> Unit) {
        saving = true
        viewModelScope.launch {
            try {
                withContext(Dispatchers.Default) { work() }
                changedStickerId?.let { stickerChanged(it, deleted = false) }
                changedStickerId = null
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

    fun open(sticker: Sticker) {
        viewing = sticker
        viewingMeta = store.loadMeta(sticker)
    }
    fun closeViewer() { viewing = null }

    fun delete(sticker: Sticker) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { store.delete(sticker) }
            if (viewing?.id == sticker.id) viewing = null
            stickerChanged(sticker.id, deleted = true)
            refresh()
            message = tr("Sticker eliminado", "Sticker deleted")
        }
    }

    private suspend fun refresh() {
        _stickers.value = withContext(Dispatchers.IO) { store.list() }
        _packs.value = withContext(Dispatchers.IO) { packStore.list() }
    }

    private suspend fun stickerChanged(id: String, deleted: Boolean) {
        withContext(Dispatchers.IO) { packStore.onStickerChanged(id, deleted) }
        WhatsApp.notifyChanged(getApplication())
    }

    // ---------- Selección múltiple ----------

    fun toggleSelect(id: String) {
        selection = if (id in selection) selection - id else selection + id
    }

    fun clearSelection() { selection = emptySet() }

    fun deleteSelected() {
        val ids = selection
        selection = emptySet()
        viewModelScope.launch {
            ids.forEach { id ->
                withContext(Dispatchers.IO) { store.get(id)?.let { store.delete(it) } }
                stickerChanged(id, deleted = true)
            }
            refresh()
            message = tr("${ids.size} stickers eliminados", "${ids.size} stickers deleted")
        }
    }

    // ---------- Emojis para WhatsApp ----------

    fun setEmojis(sticker: Sticker, emojis: List<String>) {
        viewModelScope.launch {
            val meta = withContext(Dispatchers.IO) {
                val m = store.loadMeta(sticker).copy(emojis = emojis.take(3))
                store.saveMeta(sticker, m)
                m
            }
            viewingMeta = meta
            stickerChanged(sticker.id, deleted = false)
            refresh()
        }
    }

    // ---------- Paquetes ----------

    fun openPack(id: String) {
        openPackId = id
        refreshAdded()
    }

    fun closePack() { openPackId = null }

    /** Crea un paquete (opcionalmente con stickers) y lo abre. */
    fun createPack(name: String, publisher: String, stickerIds: Collection<String> = emptyList()) {
        if (_packs.value.size >= Pack.MAX_PACKS) {
            message = tr("WhatsApp admite como máximo ${Pack.MAX_PACKS} paquetes por app", "WhatsApp allows up to ${Pack.MAX_PACKS} packs per app")
            return
        }
        prefs.edit().putString("publisher", publisher).apply()
        viewModelScope.launch {
            val pack = withContext(Dispatchers.IO) { packStore.create(name.trim(), publisher.trim()) }
            refresh()
            if (stickerIds.isNotEmpty()) addToPack(pack.id, stickerIds) else {
                tab = 1
                openPack(pack.id)
            }
        }
    }

    fun renamePack(id: String, name: String, publisher: String) {
        prefs.edit().putString("publisher", publisher).apply()
        viewModelScope.launch {
            withContext(Dispatchers.IO) { packStore.update(id) { it.copy(name = name.trim().take(128), publisher = publisher.trim().take(128)) } }
            WhatsApp.notifyChanged(getApplication())
            refresh()
        }
    }

    fun deletePack(id: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { packStore.delete(id) }
            if (openPackId == id) openPackId = null
            WhatsApp.notifyChanged(getApplication())
            refresh()
            message = tr(
                "Paquete eliminado. Si estaba en WhatsApp, quítalo también desde allí.",
                "Pack deleted. If it was in WhatsApp, remove it there too.",
            )
        }
    }

    /** Añade stickers a un paquete respetando el máximo y el tipo (estático o animado). */
    fun addToPack(packId: String, ids: Collection<String>) {
        selection = emptySet()
        pickerPackId = null
        viewModelScope.launch {
            var skippedType = 0
            var skippedFull = 0
            var added = 0
            withContext(Dispatchers.IO) {
                val pack = packStore.get(packId) ?: return@withContext
                val types = ids.associateWith { id -> store.get(id)?.let { store.loadMeta(it).animated } }
                val valid = ids.filter { it !in pack.stickerIds && types[it] != null }
                val animated = if (pack.stickerIds.isEmpty()) (valid.firstOrNull()?.let { types[it] } ?: false) else pack.animated
                val sameType = valid.filter { types[it] == animated }
                skippedType = valid.size - sameType.size
                val room = Pack.MAX_STICKERS - pack.count
                val toAdd = sameType.take(room.coerceAtLeast(0))
                skippedFull = sameType.size - toAdd.size
                added = toAdd.size
                packStore.update(packId) { it.copy(stickerIds = it.stickerIds + toAdd, animated = animated) }
            }
            WhatsApp.notifyChanged(getApplication())
            refresh()
            tab = 1
            openPack(packId)
            message = buildString {
                append(tr("$added añadidos", "$added added"))
                if (skippedType > 0) append(tr(" · $skippedType no encajan (no se mezclan animados y estáticos)", " · $skippedType don't fit (animated and static can't be mixed)"))
                if (skippedFull > 0) append(tr(" · $skippedFull no caben (máximo ${Pack.MAX_STICKERS})", " · $skippedFull don't fit (max ${Pack.MAX_STICKERS})"))
            }
        }
    }

    fun removeFromPack(packId: String, stickerId: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { packStore.update(packId) { it.copy(stickerIds = it.stickerIds - stickerId) } }
            WhatsApp.notifyChanged(getApplication())
            refresh()
        }
    }

    fun setTray(packId: String, stickerId: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { packStore.update(packId) { it.copy(trayId = stickerId) } }
            WhatsApp.notifyChanged(getApplication())
            refresh()
            message = tr("Icono del paquete cambiado", "Pack icon changed")
        }
    }

    fun openPicker(packId: String) { pickerPackId = packId }
    fun closePicker() { pickerPackId = null }

    /** Comprueba qué paquetes están ya en WhatsApp. */
    fun refreshAdded() {
        viewModelScope.launch {
            val ctx: Context = getApplication()
            val result = withContext(Dispatchers.IO) {
                whatsappInstalled = WhatsApp.installed(ctx).isNotEmpty()
                _packs.value.associate { it.id to WhatsApp.isAdded(ctx, it.id) }
            }
            addedStatus = result
        }
    }

    /** Prepara el intent para añadir el paquete a WhatsApp (null si no se puede). */
    fun addToWhatsAppIntent(pack: Pack): Intent? {
        if (!pack.ready) {
            message = tr(
                "Un paquete necesita entre ${Pack.MIN_STICKERS} y ${Pack.MAX_STICKERS} stickers",
                "A pack needs ${Pack.MIN_STICKERS} to ${Pack.MAX_STICKERS} stickers",
            )
            return null
        }
        packStore.ensureTray(pack)
        val intent = WhatsApp.addIntent(getApplication(), pack)
        if (intent == null) message = tr("WhatsApp no está instalada", "WhatsApp isn't installed")
        return intent
    }

    fun onAddResult(ok: Boolean, validationError: String?) {
        if (validationError != null) {
            message = tr("WhatsApp no ha aceptado el paquete: ", "WhatsApp rejected the pack: ") + validationError
        } else if (ok) {
            message = tr("¡Paquete añadido a WhatsApp!", "Pack added to WhatsApp!")
        }
        refreshAdded()
    }

    // ---------- Importar desde WhatsApp ----------

    fun openImport() {
        importOpen = true
        importSelected = emptySet()
        importTree?.let { loadImport(it) }
    }

    fun closeImport() {
        importOpen = false
        importFiles = null
        importSelected = emptySet()
    }

    fun chooseImportFolder(uri: Uri) {
        val ctx: Context = getApplication()
        runCatching {
            ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        prefs.edit().putString("wa_tree", uri.toString()).apply()
        importTree = uri
        loadImport(uri)
    }

    private fun loadImport(tree: Uri) {
        importLoading = true
        viewModelScope.launch {
            val ctx: Context = getApplication()
            val files = withContext(Dispatchers.IO) { runCatching { WhatsAppImport.list(ctx, tree) }.getOrNull() }
            importedOrigins = withContext(Dispatchers.IO) { store.importedOrigins() }
            importLoading = false
            if (files == null) {
                importTree = null
                prefs.edit().remove("wa_tree").apply()
                message = tr("No se puede leer esa carpeta. Elígela de nuevo.", "That folder can't be read. Pick it again.")
            }
            importFiles = files
        }
    }

    fun toggleImport(name: String) {
        importSelected = if (name in importSelected) importSelected - name else importSelected + name
    }

    fun selectAllImport(all: Boolean) {
        importSelected = if (all) (importFiles.orEmpty().map { it.name } - importedOrigins).toSet() else emptySet()
    }

    fun importSelectedFiles() {
        val files = importFiles.orEmpty().filter { it.name in importSelected }
        if (files.isEmpty() || importProgress != null) return
        viewModelScope.launch {
            val ctx: Context = getApplication()
            var ok = 0
            importProgress = 0 to files.size
            files.forEachIndexed { i, f ->
                val done = withContext(Dispatchers.Default) { runCatching { importOne(ctx, f) }.getOrDefault(false) }
                if (done) ok++
                importProgress = (i + 1) to files.size
            }
            importProgress = null
            importSelected = emptySet()
            importedOrigins = withContext(Dispatchers.IO) { store.importedOrigins() }
            refresh()
            message = tr("$ok stickers importados", "$ok stickers imported")
        }
    }

    private fun importOne(ctx: Context, f: WaFile): Boolean {
        val bytes = WhatsAppImport.readBytes(ctx, f) ?: return false
        val animated = WebpInfo.isAnimated(bytes)
        if (animated) {
            if (bytes.size > 500 * 1024) return false
            store.save(bytes, null, Decor().toJson(), meta = StickerMeta(animated = true, origin = f.name))
            return true
        }
        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.copy(Bitmap.Config.ARGB_8888, false) ?: return false
        val base = Images.fitTo512(bmp)
        val webp = if (WebpInfo.isWebp(bytes) && bmp.width == 512 && bmp.height == 512 && bytes.size <= 100 * 1024) bytes
        else Images.encodeWebp(base)
        store.save(webp, Images.encodePng(base), Decor().toJson(), meta = StickerMeta(origin = f.name))
        return true
    }
}
