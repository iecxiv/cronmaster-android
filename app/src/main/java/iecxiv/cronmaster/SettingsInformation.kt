package iecxiv.cronmaster

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@Composable
internal fun SettingsInformationCard() {
    CronmasterCard(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = .4f))) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
            Icon(Icons.Default.Info, "Información", tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Información", style = MaterialTheme.typography.titleSmall)
                Text("Verde: última ejecución correcta. Rojo: fallo. Gris: sin resultado verificable o logging desactivado. La pausa se muestra por separado.", style = MaterialTheme.typography.bodySmall)
                Text("La sesión se restaura al abrir la app. El servidor puede revocar el acceso; la app no puede impedirlo. OIDC no está implementado.", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Suppress("DEPRECATION")
@Composable
internal fun SettingsVersionFooter() {
    val context = LocalContext.current
    val versionName = remember(context) {
        try { context.packageManager.getPackageInfo(context.packageName, 0).versionName?.takeIf { it.isNotBlank() } }
        catch (_: Exception) { null }
    }
    Column(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(versionName?.let { "Versión $it" } ?: "Versión no disponible", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
