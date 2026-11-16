package app.stikka.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Collections
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.stikka.data.Pack
import app.stikka.data.Sticker
import app.stikka.domain.tr

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    vm: MainViewModel,
    stickers: List<Sticker>,
    packs: List<Pack>,
    snackbar: SnackbarHostState,
    onGallery: () -> Unit,
    onCamera: () -> Unit,
) {
    val selecting = vm.selection.isNotEmpty()
    var packSheet by remember { mutableStateOf(false) }
    var newPackFor by remember { mutableStateOf<Set<String>?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    val grid = rememberLazyGridState()
    val heroGone by remember { derivedStateOf { grid.firstVisibleItemIndex > 0 } }

    Scaffold(
        topBar = {
            if (selecting) {
                TopAppBar(
                    title = { Text(tr("${vm.selection.size} seleccionados", "${vm.selection.size} selected")) },
                    navigationIcon = { IconButton(onClick = vm::clearSelection) { Icon(Icons.Rounded.Close, tr("Cancelar", "Cancel")) } },
                    actions = {
                        IconButton(onClick = { packSheet = true }) {
                            Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, tr("Añadir a paquete", "Add to pack"))
                        }
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(Icons.Rounded.DeleteOutline, tr("Eliminar", "Delete"))
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                )
            }
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = vm.tab == 0,
                    onClick = { vm.tab = 0 },
                    icon = { Icon(Icons.Rounded.Collections, null) },
                    label = { Text(tr("Stickers", "Stickers")) },
                )
                NavigationBarItem(
                    selected = vm.tab == 1,
                    onClick = { vm.tab = 1; vm.clearSelection(); vm.refreshAdded() },
                    icon = { Icon(Icons.Rounded.Inventory2, null) },
                    label = { Text(tr("Paquetes", "Packs")) },
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            if (vm.tab == 0) {
                AnimatedVisibility(visible = heroGone && !selecting, enter = scaleIn(), exit = scaleOut()) {
                    ExtendedFloatingActionButton(
                        onClick = onGallery,
                        icon = { Icon(Icons.Rounded.AutoAwesome, null) },
                        text = { Text(tr("Nuevo sticker", "New sticker")) },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            } else {
                ExtendedFloatingActionButton(
                    onClick = { newPackFor = emptySet() },
                    icon = { Icon(Icons.Rounded.Add, null) },
                    text = { Text(tr("Nuevo paquete", "New pack")) },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                )
            }
        },
    ) { padding ->
        if (vm.tab == 0) {
            StickersTab(vm, stickers, padding, grid, onGallery, onCamera)
        } else {
            PacksTab(vm, packs, padding)
        }
    }

    if (packSheet) {
        PackPickerSheet(
            packs = packs,
            onDismiss = { packSheet = false },
            onPick = { p -> packSheet = false; vm.addToPack(p.id, vm.selection) },
            onNew = { packSheet = false; newPackFor = vm.selection },
        )
    }
    newPackFor?.let { ids ->
        PackDialog(
            title = tr("Nuevo paquete", "New pack"),
            initialName = "",
            initialPublisher = vm.lastPublisher,
            onDismiss = { newPackFor = null },
            onConfirm = { name, publisher ->
                newPackFor = null
                vm.createPack(name, publisher, ids)
            },
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            icon = { Icon(Icons.Rounded.DeleteOutline, null) },
            title = { Text(tr("¿Eliminar ${vm.selection.size} stickers?", "Delete ${vm.selection.size} stickers?")) },
            text = { Text(tr("También se quitarán de sus paquetes. No se puede deshacer.", "They'll also be removed from their packs. This can't be undone.")) },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; vm.deleteSelected() }) {
                    Text(tr("Eliminar", "Delete"), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(tr("Cancelar", "Cancel")) } },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StickersTab(
    vm: MainViewModel,
    stickers: List<Sticker>,
    padding: PaddingValues,
    grid: LazyGridState,
    onGallery: () -> Unit,
    onCamera: () -> Unit,
) {
    LazyVerticalGrid(
        state = grid,
        columns = GridCells.Adaptive(104.dp),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = padding.calculateTopPadding() + 12.dp,
            bottom = padding.calculateBottomPadding() + 96.dp,
        ),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) { Hero(onGallery, onCamera) }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Surface(
                onClick = vm::openImport,
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.tertiaryContainer,
            ) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Download, null, tint = MaterialTheme.colorScheme.onTertiaryContainer)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            tr("Importar de WhatsApp", "Import from WhatsApp"),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                        )
                        Text(
                            tr("Trae los stickers de tus chats para ordenarlos en paquetes", "Bring the stickers from your chats and sort them into packs"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.85f),
                        )
                    }
                }
            }
        }
        if (stickers.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) { EmptyState() }
        } else {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp, start = 4.dp)) {
                    Text(tr("Mis stickers", "My stickers"), style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.width(10.dp))
                    CountBadge(stickers.size)
                    Spacer(Modifier.weight(1f))
                    Text(
                        tr("Mantén pulsado para elegir varios", "Long-press to select"),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(stickers, key = { it.id }) { sticker ->
                val selected = sticker.id in vm.selection
                Box(
                    Modifier
                        .animateItem()
                        .clip(MaterialTheme.shapes.medium)
                        .background(
                            if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                        )
                        .then(
                            if (selected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.medium) else Modifier,
                        )
                        .combinedClickable(
                            onClick = { if (vm.selection.isNotEmpty()) vm.toggleSelect(sticker.id) else vm.open(sticker) },
                            onLongClick = { vm.toggleSelect(sticker.id) },
                        ),
                ) {
                    StickerImage(sticker, Modifier.fillMaxWidth().aspectRatio(1f).padding(10.dp))
                    if (sticker.id in vm.animatedIds) AnimatedBadge(Modifier.align(Alignment.TopStart).padding(6.dp))
                    if (selected) {
                        Icon(
                            Icons.Rounded.CheckCircle,
                            null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).size(22.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CountBadge(n: Int) {
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
        Text(
            n.toString(),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun Hero(onGallery: () -> Unit, onCamera: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(Brand.Gradient)
            .padding(22.dp),
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = CircleShape, color = Color.White.copy(alpha = 0.22f)) {
                    Icon(Icons.Rounded.ContentCut, null, tint = Color.White, modifier = Modifier.padding(8.dp).size(20.dp))
                }
                Spacer(Modifier.width(10.dp))
                Text("Stikka", style = MaterialTheme.typography.headlineLarge, color = Color.White)
            }
            Spacer(Modifier.height(10.dp))
            Text(
                tr(
                    "Convierte cualquier foto en un sticker. El recorte se hace solo y en tu móvil.",
                    "Turn any photo into a sticker. The cutout is automatic and stays on your phone.",
                ),
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.92f),
            )
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onGallery,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Brand.Raspberry),
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
                ) {
                    Icon(Icons.Rounded.PhotoLibrary, null, Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(tr("Galería", "Gallery"), fontWeight = FontWeight.Bold)
                }
                FilledTonalButton(
                    onClick = onCamera,
                    colors = ButtonDefaults.filledTonalButtonColors(containerColor = Color.White.copy(alpha = 0.22f), contentColor = Color.White),
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
                ) {
                    Icon(Icons.Rounded.PhotoCamera, null, Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(tr("Cámara", "Camera"), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun EmptyState() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth().padding(top = 24.dp, start = 12.dp, end = 12.dp),
    ) {
        BrandBadge(Icons.Rounded.AutoAwesome, size = 84.dp, iconSize = 40.dp, modifier = Modifier.pulse())
        Spacer(Modifier.height(18.dp))
        Text(tr("Aún no tienes stickers", "No stickers yet"), style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(6.dp))
        Text(
            tr("Crea el primero en tres pasos:", "Make your first one in three steps:"),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(18.dp))
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Step(Icons.Rounded.PhotoLibrary, tr("Elige una foto o haz una nueva", "Pick a photo or take a new one"))
                Step(Icons.Rounded.ContentCut, tr("Stikka recorta el personaje u objeto", "Stikka cuts out the person or object"))
                Step(Icons.Rounded.IosShare, tr("Decóralo, guárdalo y añádelo a WhatsApp", "Decorate it, save it and add it to WhatsApp"))
            }
        }
    }
}

@Composable
private fun Step(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.padding(8.dp).size(20.dp))
        }
        Spacer(Modifier.width(14.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PackPickerSheet(packs: List<Pack>, onDismiss: () -> Unit, onPick: (Pack) -> Unit, onNew: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(bottom = 24.dp)) {
            Text(
                tr("Añadir a un paquete", "Add to a pack"),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            ListItem(
                headlineContent = { Text(tr("Nuevo paquete", "New pack"), fontWeight = FontWeight.SemiBold) },
                leadingContent = { BrandBadge(Icons.Rounded.Add, size = 40.dp, iconSize = 22.dp) },
                modifier = Modifier.clickable(onClick = onNew),
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            )
            packs.forEach { p ->
                ListItem(
                    headlineContent = { Text(p.name) },
                    supportingContent = { Text("${p.count}/${Pack.MAX_STICKERS} · ${p.publisher}") },
                    leadingContent = { PackIcon(p, 40.dp) },
                    modifier = Modifier.clickable { onPick(p) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
            }
        }
    }
}

@Composable
fun PackDialog(
    title: String,
    initialName: String,
    initialPublisher: String,
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    var publisher by remember { mutableStateOf(initialPublisher) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Inventory2, null) },
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(128) },
                    label = { Text(tr("Nombre del paquete", "Pack name")) },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = publisher,
                    onValueChange = { publisher = it.take(128) },
                    label = { Text(tr("Autor", "Author")) },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name, publisher) }, enabled = name.isNotBlank() && publisher.isNotBlank()) {
                Text(tr("Aceptar", "OK"))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Cancelar", "Cancel")) } },
    )
}
