package app.stikka.ui

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.stikka.StikkaApp
import app.stikka.data.Pack
import app.stikka.data.Sticker
import app.stikka.domain.tr

/** Icono de un paquete: su sticker de bandeja sobre fondo suave. */
@Composable
fun PackIcon(pack: Pack, size: Dp) {
    val sticker = remember(pack.effectiveTrayId, pack.version) { pack.effectiveTrayId?.let { StikkaApp.instance.store.get(it) } }
    Box(
        Modifier
            .size(size)
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        if (sticker != null) {
            StickerImage(sticker, Modifier.fillMaxSize().padding(size / 12))
        } else {
            Icon(Icons.Rounded.Inventory2, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun StatusChip(pack: Pack, added: Boolean?) {
    val (text, container, content) = when {
        !pack.ready -> Triple(
            if (pack.count < Pack.MIN_STICKERS) tr("Faltan ${Pack.MIN_STICKERS - pack.count}", "${Pack.MIN_STICKERS - pack.count} more needed")
            else tr("Demasiados", "Too many"),
            MaterialTheme.colorScheme.surfaceContainerHighest,
            MaterialTheme.colorScheme.onSurfaceVariant,
        )
        added == true -> Triple(tr("En WhatsApp", "In WhatsApp"), Color(0xFFD7F5DD), Color(0xFF0B5A1E))
        else -> Triple(tr("Listo para añadir", "Ready to add"), MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer)
    }
    Surface(shape = CircleShape, color = container) {
        Text(text, style = MaterialTheme.typography.labelMedium, color = content, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
    }
}

@Composable
fun PacksTab(vm: MainViewModel, packs: List<Pack>, padding: PaddingValues) {
    if (packs.isEmpty()) {
        Column(
            Modifier.padding(padding).fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            BrandBadge(Icons.Rounded.Inventory2, size = 84.dp, iconSize = 40.dp, modifier = Modifier.pulse())
            Spacer(Modifier.height(18.dp))
            Text(tr("Aún no tienes paquetes", "No packs yet"), style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            Text(
                tr(
                    "Crea un paquete y añádele entre 3 y 30 stickers. Después, un toque y lo tienes en WhatsApp.",
                    "Create a pack and add 3 to 30 stickers. Then one tap and it's in WhatsApp.",
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        return
    }
    LazyColumn(
        contentPadding = PaddingValues(
            start = 16.dp, end = 16.dp,
            top = padding.calculateTopPadding() + 16.dp,
            bottom = padding.calculateBottomPadding() + 96.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)) {
                Text(tr("Mis paquetes", "My packs"), style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.width(10.dp))
                CountBadge(packs.size)
                Spacer(Modifier.weight(1f))
                Text("máx. ${Pack.MAX_PACKS}".let { tr(it, "max ${Pack.MAX_PACKS}") }, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        items(packs, key = { it.id }) { pack ->
            Surface(
                onClick = { vm.openPack(pack.id) },
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PackIcon(pack, 56.dp)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(pack.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                "${pack.publisher} · ${pack.count}/${Pack.MAX_STICKERS}" + if (pack.animated) tr(" · animados", " · animated") else "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        StatusChip(pack, vm.addedStatus[pack.id])
                    }
                    if (pack.count > 0) {
                        Spacer(Modifier.height(12.dp))
                        val store = StikkaApp.instance.store
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            pack.stickerIds.take(6).forEach { id ->
                                val s = remember(id, pack.version) { store.get(id) }
                                Box(
                                    Modifier
                                        .weight(1f)
                                        .aspectRatio(1f)
                                        .clip(MaterialTheme.shapes.small)
                                        .background(MaterialTheme.colorScheme.surfaceContainer),
                                ) { s?.let { StickerImage(it, Modifier.fillMaxSize().padding(4.dp)) } }
                            }
                            repeat((6 - pack.count).coerceAtLeast(0)) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                    LinearProgressIndicator(
                        progress = { (pack.count / Pack.MAX_STICKERS.toFloat()).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp).clip(CircleShape),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PackScreen(
    vm: MainViewModel,
    pack: Pack,
    stickers: List<Sticker>,
    onAddToWhatsApp: (Pack) -> Unit,
    onShare: (Pack) -> Unit,
) {
    var ordering by remember { mutableStateOf(false) }
    var newOrder by remember { mutableStateOf<List<String>>(emptyList()) }
    var menu by remember { mutableStateOf(false) }
    var rename by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var actionsFor by remember { mutableStateOf<Sticker?>(null) }
    val byId = remember(stickers) { stickers.associateBy { it.id } }
    val inPack = pack.stickerIds.mapNotNull { byId[it] }
    val added = vm.addedStatus[pack.id]

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        if (ordering) tr("Toca en el orden que quieras", "Tap in the order you want") else pack.name,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    if (ordering) {
                        IconButton(onClick = { ordering = false }) { Icon(Icons.Rounded.Close, tr("Cancelar", "Cancel")) }
                    } else {
                        IconButton(onClick = vm::closePack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, tr("Volver", "Back")) }
                    }
                },
                actions = {
                    if (ordering) {
                        TextButton(onClick = { ordering = false; vm.reorderPack(pack.id, newOrder) }, enabled = newOrder.isNotEmpty()) {
                            Text(tr("Guardar", "Save"))
                        }
                        return@CenterAlignedTopAppBar
                    }
                    IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, null) }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text(tr("Ordenar stickers", "Reorder stickers")) },
                            leadingIcon = { Icon(Icons.Rounded.SwapVert, null) },
                            onClick = { menu = false; newOrder = emptyList(); ordering = true },
                            enabled = pack.count > 1,
                        )
                        DropdownMenuItem(
                            text = { Text(tr("Compartir paquete (.stikka)", "Share pack (.stikka)")) },
                            leadingIcon = { Icon(Icons.Rounded.Share, null) },
                            onClick = { menu = false; onShare(pack) },
                            enabled = pack.count > 0,
                        )
                        DropdownMenuItem(
                            text = { Text(tr("Cambiar nombre o autor", "Rename")) },
                            leadingIcon = { Icon(Icons.Rounded.Edit, null) },
                            onClick = { menu = false; rename = true },
                        )
                        DropdownMenuItem(
                            text = { Text(tr("Eliminar paquete", "Delete pack")) },
                            leadingIcon = { Icon(Icons.Rounded.DeleteOutline, null) },
                            onClick = { menu = false; confirmDelete = true },
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent),
            )
        },
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(96.dp),
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp,
                top = padding.calculateTopPadding() + 4.dp,
                bottom = padding.calculateBottomPadding() + 24.dp,
            ),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.large)
                        .background(Brand.Gradient)
                        .padding(20.dp),
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.clip(MaterialTheme.shapes.small).background(Color.White.copy(alpha = 0.9f))) {
                                PackIcon(pack, 64.dp)
                            }
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(pack.name, style = MaterialTheme.typography.titleLarge, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text(pack.publisher, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.9f))
                                Text(
                                    tr("${pack.count} de ${Pack.MAX_STICKERS} stickers", "${pack.count} of ${Pack.MAX_STICKERS} stickers") +
                                        (if (pack.animated) tr(" · animados", " · animated") else "") +
                                        " · ${(inPack.sumOf { it.sizeBytes } + 1023) / 1024} KB",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color.White.copy(alpha = 0.9f),
                                )
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                        Button(
                            onClick = { onAddToWhatsApp(pack) },
                            enabled = pack.ready,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.White,
                                contentColor = Brand.Raspberry,
                                disabledContainerColor = Color.White.copy(alpha = 0.35f),
                                disabledContentColor = Color.White,
                            ),
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                        ) {
                            Icon(if (added == true) Icons.Rounded.CheckCircle else Icons.Rounded.Add, null)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                when {
                                    !pack.ready -> tr("Necesita de 3 a 30 stickers", "Needs 3 to 30 stickers")
                                    added == true -> tr("Ya está en WhatsApp · volver a añadir", "In WhatsApp · add again")
                                    else -> tr("Añadir a WhatsApp", "Add to WhatsApp")
                                },
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        if (added == true) {
                            Text(
                                tr("Los cambios que hagas aquí se actualizan solos en WhatsApp.", "Changes you make here update in WhatsApp automatically."),
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.9f),
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                        if (!vm.whatsappInstalled) {
                            Text(
                                tr("No encuentro WhatsApp en este móvil.", "WhatsApp isn't installed on this phone."),
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                    }
                }
            }
            val oversized = inPack.filter { it.sizeBytes > (if (it.id in vm.animatedIds) 500 else 100) * 1024L }
            if (oversized.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Surface(color = MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.medium) {
                        Text(
                            tr(
                                "${oversized.size} stickers pasan del límite de WhatsApp (100 KB estáticos, 500 KB animados) y están marcados con «!». Ábrelos y guarda de nuevo, o quítalos.",
                                "${oversized.size} stickers exceed WhatsApp's limit (100 KB static, 500 KB animated) and are marked with “!”. Open and save them again, or remove them.",
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(14.dp),
                        )
                    }
                }
            }
            val animCount = inPack.count { it.id in vm.animatedIds }
            if (animCount in 1 until inPack.size) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Surface(color = MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.medium) {
                        Text(
                            tr(
                                "Este paquete mezcla $animCount animados (GIF) y ${inPack.size - animCount} estáticos, y WhatsApp no lo aceptará. Quita los que sobren o corrige su tipo desde cada sticker.",
                                "This pack mixes $animCount animated (GIF) and ${inPack.size - animCount} static stickers, which WhatsApp won't accept. Remove the extra ones or fix their type from each sticker.",
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(14.dp),
                        )
                    }
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                    Text(tr("Stickers del paquete", "Stickers in this pack"), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    FilledTonalButton(onClick = { vm.openPicker(pack.id) }, enabled = pack.count < Pack.MAX_STICKERS) {
                        Icon(Icons.Rounded.Add, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(tr("Añadir", "Add"))
                    }
                }
            }
            val shown = if (ordering) inPack else inPack
            items(shown, key = { it.id }) { s ->
                val isTray = pack.effectiveTrayId == s.id
                val pos = newOrder.indexOf(s.id)
                Box(
                    Modifier
                        .aspectRatio(1f)
                        .clip(MaterialTheme.shapes.medium)
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                        .then(if (isTray) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.medium) else Modifier)
                        .clickable {
                            if (ordering) newOrder = if (pos >= 0) newOrder - s.id else newOrder + s.id
                            else actionsFor = s
                        },
                ) {
                    StickerImage(s, Modifier.fillMaxSize().padding(8.dp).alpha(if (ordering && pos < 0) 0.5f else 1f))
                    if (s.id in vm.animatedIds) AnimatedBadge(Modifier.align(Alignment.TopStart).padding(4.dp))
                    if (s.sizeBytes > (if (s.id in vm.animatedIds) 500 else 100) * 1024L) {
                        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.error, modifier = Modifier.align(Alignment.BottomStart).padding(4.dp)) {
                            Text("!", color = MaterialTheme.colorScheme.onError, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 7.dp))
                        }
                    }
                    if (ordering && pos >= 0) {
                        Box(
                            Modifier.align(Alignment.Center).size(36.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center,
                        ) { Text("${pos + 1}", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold) }
                    }
                    if (isTray) {
                        Icon(Icons.Rounded.Star, tr("Icono", "Icon"), tint = MaterialTheme.colorScheme.primary, modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(18.dp))
                    }
                }
            }
            if (inPack.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        tr("Pulsa «Añadir» para elegir stickers de tu biblioteca.", "Tap “Add” to pick stickers from your library."),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 24.dp),
                    )
                }
            }
        }
    }

    actionsFor?.let { s ->
        StickerActionsSheet(
            onDismiss = { actionsFor = null },
            onTray = { actionsFor = null; vm.setTray(pack.id, s.id) },
            onRemove = { actionsFor = null; vm.removeFromPack(pack.id, s.id) },
            onOpen = { actionsFor = null; vm.open(s) },
        )
    }
    if (rename) {
        PackDialog(
            title = tr("Editar paquete", "Edit pack"),
            initialName = pack.name,
            initialPublisher = pack.publisher,
            onDismiss = { rename = false },
            onConfirm = { n, p -> rename = false; vm.renamePack(pack.id, n, p) },
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            icon = { Icon(Icons.Rounded.DeleteOutline, null) },
            title = { Text(tr("¿Eliminar «${pack.name}»?", "Delete “${pack.name}”?")) },
            text = { Text(tr("Los stickers se quedan en tu biblioteca. Si el paquete estaba en WhatsApp, quítalo también desde allí.", "The stickers stay in your library. If the pack was in WhatsApp, remove it there too.")) },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; vm.deletePack(pack.id) }) {
                    Text(tr("Eliminar", "Delete"), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(tr("Cancelar", "Cancel")) } },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StickerActionsSheet(onDismiss: () -> Unit, onTray: () -> Unit, onRemove: () -> Unit, onOpen: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(bottom = 24.dp)) {
            ListItem(
                headlineContent = { Text(tr("Usar como icono del paquete", "Use as pack icon")) },
                leadingContent = { Icon(Icons.Rounded.Image, null) },
                modifier = Modifier.clickable(onClick = onTray),
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            )
            ListItem(
                headlineContent = { Text(tr("Ver y editar sticker", "View and edit sticker")) },
                leadingContent = { Icon(Icons.Rounded.Visibility, null) },
                modifier = Modifier.clickable(onClick = onOpen),
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            )
            ListItem(
                headlineContent = { Text(tr("Quitar del paquete", "Remove from pack")) },
                leadingContent = { Icon(Icons.Rounded.RemoveCircleOutline, null, tint = MaterialTheme.colorScheme.error) },
                modifier = Modifier.clickable(onClick = onRemove),
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            )
        }
    }
}

/**
 * Elegir stickers de la biblioteca para añadirlos a un paquete.
 * Los que ya están en el paquete se ven deshabilitados; los de otro tipo (animado/estático), atenuados.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PickerScreen(vm: MainViewModel, pack: Pack, stickers: List<Sticker>) {
    var chosen by remember { mutableStateOf<List<String>>(emptyList()) }
    val room = Pack.MAX_STICKERS - pack.count
    // Tipo del paquete: el de sus stickers o, si está vacío, el del primero que se elija.
    val packType: Boolean? = pack.stickerIds.firstOrNull()?.let { it in vm.animatedIds }
        ?: chosen.firstOrNull()?.let { it in vm.animatedIds }
    val sorted = remember(stickers, pack.stickerIds) { stickers.sortedBy { it.id in pack.stickerIds } }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(tr("Añadir a «${pack.name}»", "Add to “${pack.name}”"), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = vm::closePicker) { Icon(Icons.Rounded.Close, tr("Cancelar", "Cancel")) } },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent),
            )
        },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
                Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp)) {
                    if (chosen.size > room) {
                        Text(
                            tr(
                                "WhatsApp admite 30 stickers por paquete: caben $room. Con los ${chosen.size - room} restantes te propondré crear otro paquete.",
                                "WhatsApp allows 30 stickers per pack: $room fit. I'll offer to create another pack with the other ${chosen.size - room}.",
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            tr("${chosen.size} elegidos · caben $room", "${chosen.size} chosen · room for $room"),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        Button(onClick = { vm.addToPack(pack.id, chosen) }, enabled = chosen.isNotEmpty()) {
                            Text(tr("Añadir (${chosen.size})", "Add (${chosen.size})"))
                        }
                    }
                }
            }
        },
    ) { padding ->
        if (stickers.isEmpty()) {
            Box(Modifier.padding(padding).fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text(
                    tr("Tu biblioteca está vacía. Crea o importa stickers.", "Your library is empty. Create or import stickers."),
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@Scaffold
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(96.dp),
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp,
                top = padding.calculateTopPadding() + 4.dp,
                bottom = padding.calculateBottomPadding() + 16.dp,
            ),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AnimatedBadge()
                        Spacer(Modifier.width(6.dp))
                        Text(tr("animado", "animated"), style = MaterialTheme.typography.labelMedium)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.CheckCircle, null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(tr("ya en el paquete", "already in pack"), style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
            items(sorted, key = { it.id }) { s ->
                val inPack = s.id in pack.stickerIds
                val anim = s.id in vm.animatedIds
                val otherType = !inPack && packType != null && anim != packType
                val sel = s.id in chosen
                Box(
                    Modifier
                        .aspectRatio(1f)
                        .clip(MaterialTheme.shapes.medium)
                        .background(if (sel) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer)
                        .then(if (sel) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.medium) else Modifier)
                        .clickable(enabled = !inPack) {
                            when {
                                sel -> chosen = chosen - s.id
                                otherType -> vm.message = tr(
                                    "No se mezclan animados y estáticos. Si este está mal marcado, ábrelo y cambia su tipo.",
                                    "Animated and static can't be mixed. If this one is wrong, open it and change its type.",
                                )
                                else -> chosen = chosen + s.id
                            }
                        },
                ) {
                    StickerImage(
                        s,
                        Modifier
                            .fillMaxSize()
                            .padding(8.dp)
                            .alpha(if (inPack) 0.3f else if (otherType) 0.45f else 1f),
                    )
                    if (anim) AnimatedBadge(Modifier.align(Alignment.TopStart).padding(4.dp))
                    if (inPack) {
                        Icon(Icons.Rounded.CheckCircle, tr("Ya en el paquete", "Already in pack"), tint = MaterialTheme.colorScheme.outline, modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(20.dp))
                    } else if (sel) {
                        Icon(Icons.Rounded.CheckCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(22.dp))
                    }
                }
            }
        }
    }
}
