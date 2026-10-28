package iecxiv.cronmaster

import android.app.Application
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel

internal enum class DisplayMode {
    SYSTEM,LIGHT,DARK;
    companion object {
        fun fromPreference(value: String?,legacyDark: Boolean): DisplayMode =
            values().firstOrNull { it.name==value } ?: if(legacyDark) DARK else LIGHT
    }
}

class DisplayModeViewModel(app: Application): AndroidViewModel(app) {
    private val prefs=app.getSharedPreferences("appearance",0)
    internal var mode by mutableStateOf(DisplayMode.fromPreference(prefs.getString("display_mode",null),prefs.getBoolean("dark",true)))
        private set
    internal fun choose(value: DisplayMode) {
        val editor=prefs.edit().putString("display_mode",value.name)
        if(value!=DisplayMode.SYSTEM) editor.putBoolean("dark",value==DisplayMode.DARK)
        editor.apply()
        mode=value
    }
}

@Composable
internal fun SettingsAppTheme(style: ThemeStyle,mode: DisplayMode,content: @Composable () -> Unit) {
    val dark=when(mode) {
        DisplayMode.SYSTEM -> isSystemInDarkTheme()
        DisplayMode.LIGHT -> false
        DisplayMode.DARK -> true
    }
    CronmasterTheme(style,dark,content)
}

@Composable
internal fun ApplicationThemeSettings(themes: ThemeStyleViewModel,display: DisplayModeViewModel) {
    var expanded by remember {mutableStateOf(false)}
    SharedSettingsCard {
        Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            SettingsHeading(Icons.Default.Palette,stringResource(R.string.settings_app_theme_title),large=true)
            Text(stringResource(R.string.settings_style_label),style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
            Box {
                OutlinedButton(onClick={expanded=true},modifier=Modifier.fillMaxWidth()) {
                    Text(stringResource(if(themes.style==ThemeStyle.CRONMASTER) R.string.theme_cronmaster else R.string.theme_meshfabric),modifier=Modifier.weight(1f))
                    Icon(Icons.Default.ArrowDropDown,stringResource(R.string.choose_visual_theme))
                }
                DropdownMenu(expanded,{expanded=false}) {
                    ThemeStyle.values().forEach { option ->
                        DropdownMenuItem(text={Text(stringResource(if(option==ThemeStyle.CRONMASTER) R.string.theme_cronmaster else R.string.theme_meshfabric))},
                            onClick={themes.choose(option);expanded=false},trailingIcon={if(option==themes.style) Icon(Icons.Default.Check,stringResource(R.string.selected_theme))})
                    }
                }
            }
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                DisplayModeOption(stringResource(R.string.theme_mode_system),Icons.Default.SettingsSuggest,display.mode==DisplayMode.SYSTEM,{display.choose(DisplayMode.SYSTEM)},Modifier.weight(1f))
                DisplayModeOption(stringResource(R.string.theme_mode_light),Icons.Default.LightMode,display.mode==DisplayMode.LIGHT,{display.choose(DisplayMode.LIGHT)},Modifier.weight(1f))
                DisplayModeOption(stringResource(R.string.theme_mode_dark),Icons.Default.DarkMode,display.mode==DisplayMode.DARK,{display.choose(DisplayMode.DARK)},Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun DisplayModeOption(title: String,icon: ImageVector,isSelected: Boolean,click: () -> Unit,modifier: Modifier) {
    val background=if(isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
    val foreground=if(isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
    val border=if(isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha=.5f)
    Surface(onClick=click,modifier=modifier.height(44.dp).semantics {selected=isSelected},shape=RoundedCornerShape(10.dp),
        color=background,contentColor=foreground,border=BorderStroke(if(isSelected) 2.dp else 1.dp,border)) {
        Row(Modifier.fillMaxSize().padding(horizontal=4.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.Center) {
            Icon(icon,null,Modifier.size(18.dp));Spacer(Modifier.width(4.dp))
            Text(title,fontSize=13.sp,fontWeight=if(isSelected) FontWeight.Bold else FontWeight.Medium,maxLines=1,overflow=TextOverflow.Ellipsis,modifier=Modifier.weight(1f,fill=false))
        }
    }
}
