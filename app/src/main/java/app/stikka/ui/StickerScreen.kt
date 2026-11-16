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
import app.stikka.data.Sticker
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
) {
    var confirm by remember { mutableStateOf(false) }

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
                StickerImage(sticker, Modifier.fillMaxSize().padding(12.dp))
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
                FilledTonalButton(onClick = { onDecorate(sticker) }, modifier = Modifier.weight(1f).height(52.dp)) {
                    Icon(Icons.Rounded.EmojiEmotions, null, Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(tr("Decorar", "Decorate"))
                }
            }
            Spacer(Modifier.height(10.dp))
            OutlinedButton(onClick = { confirm = true }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Icon(Icons.Rounded.DeleteOutline, null)
                Spacer(Modifier.width(10.dp))
                Text(tr("Eliminar", "Delete"))
            }
            Spacer(Modifier.height(16.dp))
            Text(
                tr(
                    "Próximamente: paquetes para añadir a WhatsApp e importar tus stickers de WhatsApp.",
                    "Coming soon: packs to add to WhatsApp and importing your WhatsApp stickers.",
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(24.dp))
        }
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
