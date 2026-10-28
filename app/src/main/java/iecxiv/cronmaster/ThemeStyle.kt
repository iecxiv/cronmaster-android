package iecxiv.cronmaster

import android.app.Application
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel

internal enum class ThemeStyle(val label: String) {
    CRONMASTER("Cronmaster (principal)"),
    MESHFABRIC("MeshFabric");

    companion object {
        fun fromPreference(value: String?): ThemeStyle = values().firstOrNull { it.name == value } ?: CRONMASTER
    }
}

class ThemeStyleViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("appearance", 0)
    internal var style by mutableStateOf(ThemeStyle.fromPreference(prefs.getString("theme_style", null)))
        private set
    internal fun choose(value: ThemeStyle) {
        if (style == value) return
        prefs.edit().putString("theme_style", value.name).apply()
        style = value
    }
}

internal val LocalThemeStyle = staticCompositionLocalOf { ThemeStyle.CRONMASTER }
private val DefaultTypography = Typography()
private val DefaultShapes = Shapes()
private val MeshTypography = DefaultTypography.copy(
    titleLarge = DefaultTypography.titleLarge.copy(fontWeight = FontWeight.Bold),
    titleMedium = DefaultTypography.titleMedium.copy(fontWeight = FontWeight.Bold),
    titleSmall = DefaultTypography.titleSmall.copy(fontWeight = FontWeight.Bold)
)
private val CronmasterDark = darkColorScheme(
    primary = Color(0xFF77D8C7), secondary = Color(0xFFA8C7FA),
    background = Color(0xFF0C1320), surface = Color(0xFF121C2C)
)
private val CronmasterLight = lightColorScheme(
    primary = Color(0xFF006B5D), secondary = Color(0xFF385F98),
    background = Color(0xFFF4F7FB), surface = Color.White
)
// Explicit palette from iecxiv/meshfabric ui/theme/Color.kt and Theme.kt.
private val MeshDark = darkColorScheme(
    primary = Color(0xFF60A5FA),
    onPrimary = Color(0xFF0F172A),
    primaryContainer = Color(0xFF1E3A5F),
    onPrimaryContainer = Color(0xFFDBEAFE),
    secondary = Color(0xFF38BDF8),
    onSecondary = Color(0xFF0C4A6E),
    background = Color(0xFF111621),
    onBackground = Color(0xFFF1F5F9),
    surface = Color(0xFF181E2A),
    onSurface = Color(0xFFF1F5F9),
    surfaceVariant = Color(0xFF202838),
    onSurfaceVariant = Color(0xFF94A3B8),
    surfaceContainer = Color(0xFF181E2A),
    surfaceContainerHigh = Color(0xFF283246),
    surfaceContainerHighest = Color(0xFF303C54),
    outline = Color(0xFF334155),
    outlineVariant = Color(0xFF334155)
)
private val MeshLight = lightColorScheme(primary = Color(0xFF0061A4), secondary = Color(0xFF00A896))

@Composable
internal fun CronmasterTheme(style: ThemeStyle, dark: Boolean, content: @Composable () -> Unit) {
    val colors = when (style) {
        ThemeStyle.CRONMASTER -> if (dark) CronmasterDark else CronmasterLight
        ThemeStyle.MESHFABRIC -> if (dark) MeshDark else MeshLight
    }
    CompositionLocalProvider(LocalThemeStyle provides style) {
        MaterialTheme(colorScheme = colors,
            typography = if (style == ThemeStyle.MESHFABRIC) MeshTypography else DefaultTypography,
            shapes = DefaultShapes, content = content)
    }
}

@Composable
internal fun CronmasterCard(
    modifier: Modifier = Modifier,
    shape: Shape = CardDefaults.shape,
    colors: CardColors? = null,
    elevation: CardElevation? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val mesh = LocalThemeStyle.current == ThemeStyle.MESHFABRIC
    Card(modifier = modifier,
        shape = if (mesh) MaterialTheme.shapes.medium else shape,
        colors = colors ?: if (mesh) CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant) else CardDefaults.cardColors(),
        elevation = elevation ?: if (mesh) CardDefaults.cardElevation(defaultElevation = 2.dp) else CardDefaults.cardElevation(),
        content = content)
}

@Composable
internal fun ThemeStyleSettings(themes: ThemeStyleViewModel) {
    var expanded by remember { mutableStateOf(false) }
    CronmasterCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Tema visual", style = MaterialTheme.typography.titleMedium)
            Box {
                OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(themes.style.label, modifier = Modifier.weight(1f))
                        Icon(Icons.Default.ArrowDropDown, "Elegir tema visual")
                    }
                }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    ThemeStyle.values().forEach { option ->
                        DropdownMenuItem(text = { Text(option.label) }, onClick = {
                            themes.choose(option)
                            expanded = false
                        }, trailingIcon = { if (option == themes.style) Icon(Icons.Default.Check, "Tema seleccionado") })
                    }
                }
            }
            Text("Cronmaster conserva el estilo original. MeshFabric adapta sus colores, tarjetas y títulos. El modo claro/oscuro se configura por separado.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
