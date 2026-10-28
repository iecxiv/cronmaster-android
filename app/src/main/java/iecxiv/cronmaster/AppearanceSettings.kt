package iecxiv.cronmaster

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@Composable
internal fun AppearanceSettings(appearance: AppAppearanceViewModel) {
    var editedName by remember(appearance.displayName) { mutableStateOf(appearance.displayName) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) appearance.importIcon(uri)
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (appearance.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        CronmasterCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Nombre de la aplicación", style = MaterialTheme.typography.titleMedium)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    OutlinedTextField(editedName, { if (it.length <= 64) editedName = it }, singleLine = true,
                        label = { Text("Nombre visible") }, modifier = Modifier.weight(1f))
                    IconButton(onClick = { appearance.resetName() }, enabled = !appearance.busy) {
                        Icon(Icons.Default.Refresh, "Restaurar nombre Cronmaster")
                    }
                    Button(onClick = { appearance.saveName(editedName) },
                        enabled = !appearance.busy && editedName.trim().ifBlank { AppAppearanceViewModel.DEFAULT_NAME } != appearance.displayName,
                        contentPadding = PaddingValues(horizontal = 10.dp)) { Text("Guardar") }
                }
            }
        }
        CronmasterCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Icono de la aplicación", style = MaterialTheme.typography.titleMedium)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CronmasterBrandIcon(appearance.iconBitmap, Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)))
                    Spacer(Modifier.width(12.dp))
                    Text(if (appearance.iconBitmap == null) "Icono original" else "Icono personalizado", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                    IconButton(onClick = { appearance.resetIcon() }, enabled = !appearance.busy && appearance.iconBitmap != null) {
                        Icon(Icons.Default.Refresh, "Restaurar icono original")
                    }
                }
                OutlinedButton(onClick = { picker.launch("image/*") }, enabled = !appearance.busy, modifier = Modifier.fillMaxWidth()) {
                    Text(if (appearance.iconBitmap == null) "Elegir imagen" else "Cambiar imagen")
                }
                Text("Imagen de hasta 10 MiB. Se adapta al centro de un cuadrado y se guarda dentro de la app.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedButton(onClick = { appearance.addToLauncher() }, enabled = !appearance.busy, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Home, null); Spacer(Modifier.width(8.dp)); Text("Añadir al launcher")
                }
                Text("Personaliza el header y un acceso directo. No sustituye el nombre ni el icono originales del cajón de aplicaciones. Si ya existe el acceso personalizado, se actualiza.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        CronmasterCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Ocultar botón de ajustes", modifier = Modifier.weight(1f))
                    Switch(appearance.hideSettingsIcon, { appearance.saveHideSettingsIcon(it) })
                }
                Text("Abre los ajustes tocando el icono de Cronmaster del header. El botón de refrescar seguirá visible.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (appearance.notice.isNotBlank()) Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.medium) {
            Row(Modifier.fillMaxWidth().padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(appearance.notice, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                IconButton(onClick = { appearance.clearNotice() }) { Icon(Icons.Default.Close, "Descartar aviso de apariencia") }
            }
        }
    }
}

@Composable
internal fun AppearanceSettingsDialog(appearance: AppAppearanceViewModel, close: () -> Unit) {
    Dialog(onDismissRequest = close, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.windowInsetsPadding(WindowInsets.safeDrawing).fillMaxWidth(.96f).widthIn(max = 960.dp).fillMaxHeight(.92f),
            shape = MaterialTheme.shapes.extraLarge, tonalElevation = 6.dp) {
            Column(Modifier.fillMaxSize().padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Apariencia", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    IconButton(onClick = close) { Icon(Icons.Default.Close, "Cerrar ajustes de apariencia") }
                }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(top = 8.dp)) { AppearanceSettings(appearance) }
            }
        }
    }
}
