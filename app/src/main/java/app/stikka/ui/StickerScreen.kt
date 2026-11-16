package app.stikka.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Brush
import androidx.compose.material.icons.rounded.EmojiEmotions
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.PhotoSizeSelectActual
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Straighten
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.material3.SegmentedButton
import app.stikka.data.Pack
import app.stikka.data.Sticker
import app.stikka.data.StickerMeta
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.ui.unit.sp
import app.stikka.domain.tr
import java.text.DateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StickerScreen(
    sticker: Sticker,
    onBack: () -> Unit,
    onShare: (Sticker) -> Unit,
    onDelete: (Sticker) -> Unit,
    onEdit: (Sticker) -> Unit,
    onDecorate: (Sticker) -> Unit,
    meta: StickerMeta,
    packs: List<Pack>,
    onEmojis: (List<String>) -> Unit,
    onAddToPack: (Pack) -> Unit,
    onNewPack: () -> Unit,
    onSetType: (Boolean) -> Unit,
    onDuplicate: () -> Unit,
) {
    var confirm by remember { mutableStateOf(false) }
    var emojiPicker by remember { mutableStateOf(false) }
    var packSheet by remember { mutableStateOf(false) }
    val inPacks = packs.filter { sticker.id in it.stickerIds }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(tr("Sticker", "Sticker")) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, tr("Volver", "Back"))
                    }
                },
                actions = {
                    IconButton(onClick = onDuplicate) {
                        Icon(Icons.Rounded.ContentCopy, tr("Duplicar", "Duplicate"))
                    }
                    IconButton(onClick = { confirm = true }) {
                        Icon(Icons.Rounded.DeleteOutline, tr("Eliminar", "Delete"))
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent),
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding(),
        ) {
            Spacer(Modifier.height(8.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(MaterialTheme.shapes.large)
                    .checkerboard(),
                contentAlignment = Alignment.Center,
            ) {
                if (meta.animated) {
                    AnimatedSticker(sticker, Modifier.fillMaxSize().padding(12.dp))
                } else {
                    StickerImage(sticker, Modifier.fillMaxSize().padding(12.dp))
                }
            }
            Spacer(Modifier.height(16.dp))
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(vertical = 6.dp)) {
                    InfoRow(
                        Icons.Rounded.CalendarToday,
                        tr("Creado", "Created"),
                        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, Locale.getDefault())
                            .format(Date(sticker.created)),
                    )
                    InfoRow(Icons.Rounded.Straighten, tr("Tamaño", "Size"), "512 × 512 px")
                    InfoRow(
                        Icons.Rounded.PhotoSizeSelectActual,
                        tr("Archivo", "File"),
                        "WebP · ${(sticker.sizeBytes + 1023) / 1024} KB",
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
            Button(onClick = { onShare(sticker) }, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                Icon(Icons.Rounded.Share, null)
                Spacer(Modifier.width(10.dp))
                Text(tr("Compartir", "Share"), style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                if (sticker.editable) {
                    FilledTonalButton(onClick = { onEdit(sticker) }, modifier = Modifier.weight(1f).height(52.dp)) {
                        Icon(Icons.Rounded.Brush, null, Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(tr("Recorte", "Cutout"))
                    }
                }
                if (!meta.animated) {
                    FilledTonalButton(onClick = { onDecorate(sticker) }, modifier = Modifier.weight(1f).height(52.dp)) {
                        Icon(Icons.Rounded.EmojiEmotions, null, Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(tr("Decorar", "Decorate"))
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            FilledTonalButton(onClick = { packSheet = true }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, null, Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(tr("Añadir a un paquete", "Add to a pack"))
            }
            Spacer(Modifier.height(16.dp))
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(tr("Emojis para WhatsApp", "Emojis for WhatsApp"), style = MaterialTheme.typography.titleMedium)
                            Text(
                                tr("WhatsApp los usa para sugerir el sticker (1 a 3)", "WhatsApp uses them to suggest the sticker (1 to 3)"),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        TextButton(onClick = { emojiPicker = true }) { Text(tr("Cambiar", "Change")) }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        meta.whatsappEmojis.forEach { e ->
                            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHighest) {
                                Text(e, fontSize = 24.sp, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
                            }
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    Text(tr("Tipo", "Type"), style = MaterialTheme.typography.titleMedium)
                    Text(
                        tr("Corrígelo si está mal: los paquetes no mezclan animados y estáticos.", "Fix it if it's wrong: packs can't mix animated and static."),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    androidx.compose.material3.SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        SegmentedButton(
                            selected = !meta.animated,
                            onClick = { if (meta.animated) onSetType(false) },
                            shape = androidx.compose.material3.SegmentedButtonDefaults.itemShape(0, 2),
                        ) { Text(tr("Estático", "Static")) }
                        SegmentedButton(
                            selected = meta.animated,
                            onClick = { if (!meta.animated) onSetType(true) },
                            shape = androidx.compose.material3.SegmentedButtonDefaults.itemShape(1, 2),
                        ) { Text(tr("Animado (GIF)", "Animated (GIF)")) }
                    }
                    if (inPacks.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            tr("En: ", "In: ") + inPacks.joinToString { it.name },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            OutlinedButton(onClick = { confirm = true }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Icon(Icons.Rounded.DeleteOutline, null)
                Spacer(Modifier.width(10.dp))
                Text(tr("Eliminar", "Delete"))
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (emojiPicker) {
        EmojiTagsSheet(
            initial = meta.emojis,
            onDismiss = { emojiPicker = false },
            onSave = { emojiPicker = false; onEmojis(it) },
        )
    }
    if (packSheet) {
        PackPickerSheet(
            packs = packs.filter { sticker.id !in it.stickerIds },
            onDismiss = { packSheet = false },
            onPick = { packSheet = false; onAddToPack(it) },
            onNew = { packSheet = false; onNewPack() },
        )
    }

    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            icon = { Icon(Icons.Rounded.DeleteOutline, null) },
            title = { Text(tr("¿Eliminar este sticker?", "Delete this sticker?")) },
            text = { Text(tr("No se puede deshacer.", "This can't be undone.")) },
            confirmButton = {
                TextButton(onClick = { confirm = false; onDelete(sticker) }) {
                    Text(tr("Eliminar", "Delete"), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirm = false }) { Text(tr("Cancelar", "Cancel")) }
            },
        )
    }
}

@Composable
private fun InfoRow(icon: ImageVector, label: String, value: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(14.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Muestra un sticker animado moviéndose. */
@Composable
fun AnimatedSticker(sticker: Sticker, modifier: Modifier = Modifier) {
    androidx.compose.ui.viewinterop.AndroidView(
        factory = { ctx ->
            android.widget.ImageView(ctx).apply { scaleType = android.widget.ImageView.ScaleType.FIT_CENTER }
        },
        update = { view ->
            runCatching {
                val d = android.graphics.ImageDecoder.decodeDrawable(android.graphics.ImageDecoder.createSource(sticker.file))
                view.setImageDrawable(d)
                (d as? android.graphics.drawable.AnimatedImageDrawable)?.start()
            }
        },
        modifier = modifier,
    )
}

/** Elegir de 1 a 3 emojis para que WhatsApp sugiera el sticker. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmojiTagsSheet(initial: List<String>, onDismiss: () -> Unit, onSave: (List<String>) -> Unit) {
    var chosen by remember { mutableStateOf(initial.take(3)) }
    androidx.compose.material3.ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(tr("Emojis del sticker", "Sticker emojis"), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                Button(onClick = { onSave(chosen) }, enabled = chosen.isNotEmpty()) { Text(tr("Guardar", "Save")) }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.height(48.dp)) {
                if (chosen.isEmpty()) {
                    Text(tr("Elige hasta 3", "Pick up to 3"), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.align(Alignment.CenterVertically))
                }
                chosen.forEach { e ->
                    Surface(onClick = { chosen = chosen - e }, shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                        Text("$e  ✕", fontSize = 20.sp, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                columns = androidx.compose.foundation.lazy.grid.GridCells.Adaptive(52.dp),
                modifier = Modifier.fillMaxWidth().height(380.dp),
            ) {
                items(EmojiList.size) { i ->
                    val e = EmojiList[i]
                    val sel = e in chosen
                    Box(
                        Modifier
                            .size(52.dp)
                            .clip(MaterialTheme.shapes.small)
                            .then(if (sel) Modifier.background(MaterialTheme.colorScheme.primaryContainer) else Modifier)
                            .clickable {
                                chosen = if (sel) chosen - e else if (chosen.size < 3) chosen + e else chosen
                            },
                        contentAlignment = Alignment.Center,
                    ) { Text(e, fontSize = 28.sp) }
                }
            }
            Spacer(Modifier.navigationBarsPadding().height(16.dp))
        }
    }
}
