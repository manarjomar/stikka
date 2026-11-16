package app.stikka.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.core.content.FileProvider
import androidx.core.content.IntentCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.content.ActivityNotFoundException
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarHost
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import app.stikka.data.Pack
import app.stikka.data.Sticker
import app.stikka.data.WhatsAppImport
import app.stikka.domain.tr
import java.io.File

class MainActivity : ComponentActivity() {

    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            navigationBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) handleIntent(intent)

        setContent {
            StikkaTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    App()
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    /** Imagen compartida desde otra app: directa al recorte. */
    private fun handleIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND) return
        if (intent.type?.startsWith("image/") != true) return
        val uri = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java) ?: return
        vm.startCutout(uri)
    }

    private fun share(sticker: Sticker) {
        val uri = FileProvider.getUriForFile(this, "$packageName.files", sticker.file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "image/webp"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(send, tr("Compartir sticker", "Share sticker")))
    }

    private fun newCameraUri(): Uri {
        val dir = File(cacheDir, "camera").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val file = File(dir, "photo-${System.currentTimeMillis()}.jpg")
        return FileProvider.getUriForFile(this, "$packageName.files", file)
    }

    @androidx.compose.runtime.Composable
    private fun App() {
        val stickers by vm.stickers.collectAsStateWithLifecycle()
        val snackbar = remember { SnackbarHostState() }
        var cameraUri by rememberSaveable { mutableStateOf<Uri?>(null) }

        val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null) vm.startCutout(uri)
        }
        val takePhoto = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
            val uri = cameraUri
            if (ok && uri != null) vm.startCutout(uri)
        }
        val openGallery = {
            pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
        val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri != null) vm.setImportTree(uri)
        }
        val openFolderPicker = { pickFolder.launch(WhatsAppImport.initialUri()) }
        val addPack = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
            vm.onAddResult(res.resultCode == android.app.Activity.RESULT_OK, res.data?.getStringExtra("validation_error"))
        }
        val addToWhatsApp: (Pack) -> Unit = { p ->
            vm.addToWhatsAppIntent(p)?.let { intent ->
                try {
                    addPack.launch(intent)
                } catch (e: ActivityNotFoundException) {
                    vm.message = tr("No se ha podido abrir WhatsApp", "Couldn't open WhatsApp")
                }
            }
        }
        val packs by vm.packs.collectAsStateWithLifecycle()
        val openCamera = {
            val uri = newCameraUri()
            cameraUri = uri
            takePhoto.launch(uri)
        }

        LaunchedEffect(vm.message) {
            vm.message?.let {
                snackbar.showSnackbar(it)
                vm.message = null
            }
        }

        val cut = vm.cut
        val viewing = vm.viewing
        val editor = vm.editor
        val decorator = vm.decorator
        val openPack = vm.openPackId?.let { id -> packs.firstOrNull { it.id == id } }
        val pickerPack = vm.pickerPackId?.let { id -> packs.firstOrNull { it.id == id } }
        val screen = when {
            decorator != null -> "decor"
            editor != null -> "edit"
            cut != null -> "cut"
            viewing != null -> "view"
            vm.importOpen -> "import"
            pickerPack != null -> "picker"
            openPack != null -> "pack"
            else -> "home"
        }
        var newPackFor by remember { mutableStateOf<String?>(null) }

        BackHandler(enabled = screen != "home") {
            when {
                decorator != null -> vm.closeDecorator()
                editor != null -> vm.closeEditor()
                cut != null -> vm.closeCut()
                viewing != null -> vm.closeViewer()
                vm.importOpen -> vm.closeImport()
                pickerPack != null -> vm.closePicker()
                else -> vm.closePack()
            }
        }

        // Al volver a la app (por ejemplo, desde WhatsApp) se comprueba qué paquetes están añadidos.
        androidx.lifecycle.compose.LifecycleResumeEffect(Unit) {
            vm.refreshAdded()
            onPauseOrDispose { }
        }

        Box(Modifier.fillMaxSize()) {

        AnimatedContent(
            targetState = screen,
            transitionSpec = { (fadeIn() + scaleIn(initialScale = 0.96f)) togetherWith fadeOut() },
            label = "screen",
        ) { target ->
            when (target) {
                "decor" -> vm.decorator?.let { d ->
                    DecorScreen(
                        editor = d,
                        saving = vm.saving,
                        onClose = vm::closeDecorator,
                        onDone = vm::finishDecorator,
                    )
                }
                "edit" -> vm.editor?.let { e ->
                    MaskEditorScreen(
                        editor = e,
                        saving = vm.saving,
                        onClose = vm::closeEditor,
                        onDone = vm::finishEditor,
                    )
                }
                "cut" -> vm.cut?.let { state ->
                    CutoutScreen(
                        state = state,
                        saving = vm.saving,
                        onClose = vm::closeCut,
                        onSave = vm::saveCut,
                        onWholeImage = vm::useWholeImage,
                        onPickAnother = openGallery,
                        onEdit = vm::editCut,
                        onDecorate = vm::decorateCut,
                    )
                }
                "view" -> vm.viewing?.let { sticker ->
                    StickerScreen(
                        sticker = sticker,
                        onBack = vm::closeViewer,
                        onShare = ::share,
                        onDelete = vm::delete,
                        onEdit = vm::editSticker,
                        onDecorate = vm::decorateSticker,
                        meta = vm.viewingMeta,
                        packs = packs,
                        onEmojis = { vm.setEmojis(sticker, it) },
                        onAddToPack = { p -> vm.closeViewer(); vm.addToPack(p.id, listOf(sticker.id)) },
                        onNewPack = { newPackFor = sticker.id },
                    )
                }
                "import" -> ImportScreen(vm, onPickFolder = openFolderPicker)
                "picker" -> pickerPack?.let { PickerScreen(vm, it, stickers) }
                "pack" -> openPack?.let { PackScreen(vm, it, stickers, onAddToWhatsApp = addToWhatsApp) }
                else -> HomeScreen(
                    vm = vm,
                    stickers = stickers,
                    packs = packs,
                    snackbar = snackbar,
                    onGallery = openGallery,
                    onCamera = openCamera,
                )
            }
        }
        if (screen != "home") {
            SnackbarHost(
                snackbar,
                Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 72.dp),
            )
        }
        }

        newPackFor?.let { id ->
            PackDialog(
                title = tr("Nuevo paquete", "New pack"),
                initialName = "",
                initialPublisher = vm.lastPublisher,
                onDismiss = { newPackFor = null },
                onConfirm = { name, publisher ->
                    newPackFor = null
                    vm.closeViewer()
                    vm.createPack(name, publisher, listOf(id))
                },
            )
        }
    }
}
