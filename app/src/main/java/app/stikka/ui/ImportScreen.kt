package app.stikka.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.stikka.data.WaFile
import app.stikka.data.WhatsAppImport
import app.stikka.domain.tr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportScreen(vm: MainViewModel, onPickFolder: () -> Unit) {
    val files = vm.importFiles
    val progress = vm.importProgress
    val selectable = files.orEmpty().count { it.name !in vm.importedOrigins }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(tr("Importar de WhatsApp", "Import from WhatsApp")) },
                navigationIcon = { IconButton(onClick = vm::closeImport) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, tr("Volver", "Back")) } },
                actions = {
                    if (vm.importTree != null) {
                        IconButton(onClick = onPickFolder) { Icon(Icons.Rounded.FolderOpen, tr("Cambiar carpeta", "Change folder")) }
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent),
            )
        },
        bottomBar = {
            if (files != null && files.isNotEmpty()) {
                Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
                    Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp)) {
                        if (progress != null) {
                            Text(tr("Importando ${progress.first} de ${progress.second}…", "Importing ${progress.first} of ${progress.second}…"))
                            Spacer(Modifier.height(8.dp))
                            LinearProgressIndicator(
                                progress = { progress.first / progress.second.toFloat().coerceAtLeast(1f) },
                                modifier = Modifier.fillMaxWidth().clip(CircleShape),
                            )
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                val all = vm.importSelected.size == selectable && selectable > 0
                                TextButton(onClick = { vm.selectAllImport(!all) }, enabled = selectable > 0) {
                                    Text(if (all) tr("Ninguno", "None") else tr("Todos", "All"))
                                }
                                Spacer(Modifier.weight(1f))
                                Button(onClick = vm::importSelectedFiles, enabled = vm.importSelected.isNotEmpty()) {
                                    Icon(Icons.Rounded.Download, null, Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(tr("Importar (${vm.importSelected.size})", "Import (${vm.importSelected.size})"))
                                }
                            }
                        }
                    }
                }
            }
        },
    ) { padding ->
        when {
            vm.importTree == null -> ImportIntro(padding, onPickFolder)
            vm.importLoading || files == null -> Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(12.dp))
                    Text(tr("Buscando stickers…", "Looking for stickers…"))
                }
            }
            files.isEmpty() -> Column(
                Modifier.padding(padding).fillMaxSize().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    tr(
                        "No he encontrado stickers en esa carpeta. Elige «WhatsApp Stickers» dentro de Android › media › com.whatsapp › WhatsApp › Media.",
                        "No stickers found in that folder. Pick “WhatsApp Stickers” inside Android › media › com.whatsapp › WhatsApp › Media.",
                    ),
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(16.dp))
                OutlinedButton(onClick = onPickFolder) { Text(tr("Elegir otra carpeta", "Pick another folder")) }
            }
            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(88.dp),
                contentPadding = PaddingValues(
                    start = 16.dp, end = 16.dp,
                    top = padding.calculateTopPadding() + 4.dp,
                    bottom = padding.calculateBottomPadding() + 16.dp,
                ),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(files, key = { it.name }) { f ->
                    val imported = f.name in vm.importedOrigins
                    val sel = f.name in vm.importSelected
                    Box(
                        Modifier
                            .aspectRatio(1f)
                            .clip(MaterialTheme.shapes.medium)
                            .background(if (sel) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer)
                            .then(if (sel) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.medium) else Modifier)
                            .clickable(enabled = !imported && progress == null) { vm.toggleImport(f.name) },
                    ) {
                        WaThumb(f, Modifier.fillMaxSize().padding(6.dp).alpha(if (imported) 0.35f else 1f))
                        if (sel || imported) {
                            Icon(
                                Icons.Rounded.CheckCircle,
                                null,
                                tint = if (imported) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(20.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WaThumb(file: WaFile, modifier: Modifier) {
    val ctx = LocalContext.current
    val bmp by produceState<Bitmap?>(null, file.uri) {
        value = withContext(Dispatchers.IO) { WhatsAppImport.thumbnail(ctx, file) }
    }
    bmp?.let { Image(it.asImageBitmap(), null, modifier, contentScale = ContentScale.Fit) }
}

@Composable
private fun ImportIntro(padding: PaddingValues, onPickFolder: () -> Unit) {
    Column(
        Modifier
            .padding(padding)
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BrandBadge(Icons.Rounded.Folder, size = 84.dp, iconSize = 40.dp)
        Spacer(Modifier.height(18.dp))
        Text(tr("Trae tus stickers de WhatsApp", "Bring your WhatsApp stickers"), style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(10.dp))
        Text(
            tr(
                "WhatsApp guarda en el móvil los stickers que envías y recibes. Android solo deja leerlos si eliges tú esa carpeta una vez.",
                "WhatsApp keeps the stickers you send and receive on your phone. Android only lets apps read them if you pick that folder once.",
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(tr("1. Pulsa «Elegir carpeta».", "1. Tap “Pick folder”."))
                Text(tr("2. Se abrirá en «WhatsApp Stickers». Si no, ve a Android › media › com.whatsapp › WhatsApp › Media › WhatsApp Stickers.", "2. It opens at “WhatsApp Stickers”. If not, go to Android › media › com.whatsapp › WhatsApp › Media › WhatsApp Stickers."))
                Text(tr("3. Pulsa «Usar esta carpeta» y después «Permitir».", "3. Tap “Use this folder” and then “Allow”."))
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(
            tr(
                "Los paquetes de WhatsApp no se pueden leer tal cual (es información privada de WhatsApp), pero aquí podrás ordenarlos en paquetes nuevos.",
                "WhatsApp's own packs can't be read as such (that's private WhatsApp data), but here you can sort them into new packs.",
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onPickFolder, modifier = Modifier.fillMaxWidth().height(56.dp)) {
            Icon(Icons.Rounded.FolderOpen, null)
            Spacer(Modifier.width(10.dp))
            Text(tr("Elegir carpeta", "Pick folder"), style = MaterialTheme.typography.titleMedium)
        }
    }
}
