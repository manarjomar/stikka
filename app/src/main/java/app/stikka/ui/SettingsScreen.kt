package app.stikka.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.stikka.BuildConfig
import app.stikka.R
import app.stikka.domain.Updates
import app.stikka.domain.tr

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    vm: MainViewModel,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onLanguageChanged: () -> Unit,
) {
    val ctx = LocalContext.current
    val settings = vm.settings
    var publisher by remember { mutableStateOf(settings.publisher) }
    fun open(url: String) = runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(tr("Ajustes", "Settings")) },
                navigationIcon = {
                    IconButton(onClick = { settings.publisher = publisher.trim(); vm.settingsOpen = false }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, tr("Volver", "Back"))
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
                .padding(horizontal = 16.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Cabecera «Acerca de»
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.large)
                    .background(Brand.Gradient)
                    .padding(22.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        painterResource(R.mipmap.ic_launcher_foreground),
                        contentDescription = null,
                        modifier = Modifier.size(84.dp).clip(MaterialTheme.shapes.medium).background(Color.White.copy(alpha = 0.15f)),
                    )
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text("Stikka", style = MaterialTheme.typography.headlineMedium, color = Color.White)
                        Text(tr("Versión ${BuildConfig.VERSION_NAME}", "Version ${BuildConfig.VERSION_NAME}"), color = Color.White.copy(alpha = 0.92f))
                        Text(tr("Hecha por manarjomar", "Made by manarjomar"), style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.85f))
                    }
                }
            }

            Section(tr("Actualizaciones", "Updates")) {
                Item(
                    Icons.Rounded.SystemUpdate,
                    tr("Buscar actualizaciones", "Check for updates"),
                    tr("Comprueba si hay una versión nueva en GitHub", "Checks GitHub for a new version"),
                    onClick = { vm.checkUpdates(manual = true) },
                    trailing = { if (vm.checkingUpdate) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp) },
                )
                Item(
                    Icons.Rounded.NewReleases,
                    tr("Comprobar automáticamente", "Check automatically"),
                    tr("Como mucho una vez al día", "At most once a day"),
                    trailing = { Switch(checked = settings.autoUpdateCheck, onCheckedChange = settings::updateAutoUpdate) },
                )
                Item(
                    Icons.AutoMirrored.Rounded.OpenInNew,
                    tr("Novedades de cada versión", "What's new in each version"),
                    onClick = { open("${Updates.REPO_URL}/releases") },
                )
            }

            Section(tr("Apariencia", "Appearance")) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Palette, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(16.dp))
                        Text(tr("Tema", "Theme"), style = MaterialTheme.typography.bodyLarge)
                    }
                    Spacer(Modifier.height(10.dp))
                    val themes = listOf("system" to tr("Sistema", "System"), "light" to tr("Claro", "Light"), "dark" to tr("Oscuro", "Dark"))
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        themes.forEachIndexed { i, (key, label) ->
                            SegmentedButton(
                                selected = settings.theme == key,
                                onClick = { settings.updateTheme(key) },
                                shape = SegmentedButtonDefaults.itemShape(i, themes.size),
                            ) { Text(label) }
                        }
                    }
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    Item(
                        Icons.Rounded.Palette,
                        tr("Colores de tu fondo de pantalla", "Wallpaper colours"),
                        tr("Material You en lugar de los colores de Stikka", "Material You instead of Stikka's colours"),
                        trailing = { Switch(checked = settings.dynamicColor, onCheckedChange = settings::updateDynamic) },
                    )
                }
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Translate, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(16.dp))
                        Text(tr("Idioma", "Language"), style = MaterialTheme.typography.bodyLarge)
                    }
                    Spacer(Modifier.height(10.dp))
                    val langs = listOf("system" to tr("Sistema", "System"), "es" to "Español", "en" to "English")
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        langs.forEachIndexed { i, (key, label) ->
                            SegmentedButton(
                                selected = settings.language == key,
                                onClick = {
                                    if (settings.language != key) {
                                        settings.updateLanguage(key)
                                        onLanguageChanged()
                                    }
                                },
                                shape = SegmentedButtonDefaults.itemShape(i, langs.size),
                            ) { Text(label) }
                        }
                    }
                }
            }

            Section(tr("Paquetes", "Packs")) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Person, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(16.dp))
                        Text(tr("Autor por defecto", "Default author"), style = MaterialTheme.typography.bodyLarge)
                    }
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = publisher,
                        onValueChange = { publisher = it.take(128); settings.publisher = it.take(128).trim() },
                        placeholder = { Text("manarjomar") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            Section(tr("Copia de seguridad", "Backup")) {
                Item(
                    Icons.Rounded.Backup,
                    tr("Guardar copia", "Save backup"),
                    tr("Todos los stickers, paquetes y decoraciones en un archivo", "All stickers, packs and decorations in one file"),
                    onClick = onExport,
                )
                Item(
                    Icons.Rounded.Restore,
                    tr("Restaurar o importar", "Restore or import"),
                    tr("Una copia de Stikka o un paquete .stikka que te hayan pasado. No borra nada.", "A Stikka backup or a .stikka pack someone sent you. Nothing is deleted."),
                    onClick = onImport,
                )
            }

            Section(tr("Acerca de", "About")) {
                Item(
                    Icons.Rounded.Lock,
                    tr("Privacidad", "Privacy"),
                    tr(
                        "Tus fotos y stickers no salen del móvil. Sin anuncios, sin cuentas y sin rastreo. Solo se conecta para buscar actualizaciones en GitHub; el modelo de recorte lo descarga Google Play Services.",
                        "Your photos and stickers never leave your phone. No ads, no accounts, no tracking. It only connects to check for updates on GitHub; the cutout model is downloaded by Google Play Services.",
                    ),
                )
                Item(Icons.Rounded.Code, tr("Código fuente", "Source code"), "github.com/${Updates.REPO}", onClick = { open(Updates.REPO_URL) })
                Text(
                    tr(
                        "Recorte con Google ML Kit (Subject Segmentation). Hecha con Kotlin, Jetpack Compose y Material 3. Formato de paquetes de WhatsApp según su ejemplo oficial.\n\n© manarjomar. Todos los derechos reservados.",
                        "Cutout by Google ML Kit (Subject Segmentation). Built with Kotlin, Jetpack Compose and Material 3. WhatsApp pack format per their official sample.\n\n© manarjomar. All rights reserved.",
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Start,
                    modifier = Modifier.padding(16.dp),
                )
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 8.dp, bottom = 6.dp),
        )
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.medium) {
            Column { content() }
        }
    }
}

@Composable
private fun Item(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = subtitle?.let { { Text(it) } },
        leadingContent = { Icon(icon, null, tint = MaterialTheme.colorScheme.primary) },
        trailingContent = trailing,
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier,
    )
}
