package iecxiv.cronmaster

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal val LocalCardActionRowScope = staticCompositionLocalOf<RowScope?> { null }

@Composable
internal fun CardActions(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        val rowScope = this
        CompositionLocalProvider(LocalCardActionRowScope provides rowScope) {
            content(rowScope)
        }
    }
}

private fun compactActionLabel(label: String): String = when (label) {
    "Ejecutar tarea" -> "Ejecutar"
    "Editar tarea", "Editar script" -> "Editar"
    "Pausar tarea" -> "Pausar"
    "Reanudar tarea" -> "Reanudar"
    "Historial de logs" -> "Logs"
    "Clonar tarea", "Clonar script" -> "Clonar"
    "Alternar logging" -> "Logging"
    "Guardar backup de tarea" -> "Backup"
    "Eliminar tarea", "Eliminar script", "Eliminar backup", "Eliminar log" -> "Eliminar"
    "Programar script" -> "Programar"
    "Copiar contenido" -> "Copiar"
    "Backup de todas las tareas" -> "Copiar todo"
    "Restaurar todos los backups" -> "Restaurar todo"
    "Restaurar backup" -> "Restaurar"
    "Cambiar acceso" -> "Acceso"
    "Cerrar sesión" -> "Cerrar sesión"
    "Eliminar todos los logs" -> "Borrar todo"
    "Abrir log en terminal" -> "Ver log"
    else -> label
}

@Composable
internal fun RowScope.CompactCardAction(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    tint: Color,
    onClick: () -> Unit
) {
    val color = if (enabled) tint else tint.copy(alpha = tint.alpha * 0.35f)
    Column(
        modifier = Modifier
            .weight(1f, fill = false)
            .widthIn(max = 44.dp)
            .heightIn(min = 52.dp)
            .clip(MaterialTheme.shapes.small)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = label }
            .padding(horizontal = 0.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
        Spacer(Modifier.height(2.dp))
        Text(
            text = compactActionLabel(label),
            color = color,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, lineHeight = 10.sp),
            textAlign = TextAlign.Center,
            minLines = 2,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth().clearAndSetSemantics { }
        )
    }
}
