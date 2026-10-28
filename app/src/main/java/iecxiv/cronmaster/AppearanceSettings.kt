package iecxiv.cronmaster

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@Composable
internal fun SharedSettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surfaceVariant),
        elevation=CardDefaults.cardElevation(defaultElevation=0.dp),content=content)
}

@Composable
internal fun SettingsHeading(icon: ImageVector,title: String,large: Boolean=false) {
    Row(verticalAlignment=Alignment.CenterVertically) {
        Icon(icon,null,tint=MaterialTheme.colorScheme.primary,modifier=Modifier.size(if(large) 24.dp else 20.dp))
        Spacer(Modifier.width(if(large) 8.dp else 6.dp))
        Text(title,fontWeight=FontWeight.Bold,fontSize=if(large) 16.sp else 15.sp)
    }
}

@Composable
internal fun AppearanceSettings(appearance: AppAppearanceViewModel,includeSettingsButton: Boolean=true) {
    var editedName by remember(appearance.displayName) { mutableStateOf(appearance.displayName) }
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> if(uri!=null) appearance.importIcon(uri) }
    val nameLabel=stringResource(R.string.app_display_name)
    val saved=editedName.trim().ifBlank { AppAppearanceViewModel.DEFAULT_NAME }==appearance.displayName
    val custom=appearance.iconBitmap!=null
    Column(verticalArrangement=Arrangement.spacedBy(16.dp)) {
        if(appearance.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        SharedSettingsCard {
            Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                SettingsHeading(Icons.Default.Title,nameLabel)
                Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(editedName,{if(it.length<=64) editedName=it},singleLine=true,enabled=!appearance.busy,
                        placeholder={Text(AppAppearanceViewModel.DEFAULT_NAME)},
                        modifier=Modifier.weight(1f).semantics { contentDescription=nameLabel })
                    if(appearance.displayName!=AppAppearanceViewModel.DEFAULT_NAME || editedName!=AppAppearanceViewModel.DEFAULT_NAME) {
                        IconButton(onClick={appearance.resetName();editedName=AppAppearanceViewModel.DEFAULT_NAME},enabled=!appearance.busy) {
                            Icon(Icons.Default.Refresh,stringResource(R.string.restore_app_name),tint=MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Button(onClick={appearance.saveName(editedName)},enabled=!appearance.busy && !saved,contentPadding=PaddingValues(horizontal=12.dp)) {
                        Text(stringResource(if(saved) R.string.settings_updated else R.string.save),fontSize=13.sp)
                    }
                }
            }
        }
        SharedSettingsCard {
            Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                SettingsHeading(Icons.Default.Image,stringResource(R.string.app_icon))
                Row(verticalAlignment=Alignment.CenterVertically) {
                    CronmasterBrandIcon(appearance.iconBitmap,Modifier.size(44.dp).clip(CircleShape))
                    Spacer(Modifier.width(10.dp))
                    Text(stringResource(if(custom) R.string.custom_icon else R.string.settings_default_icon),
                        modifier=Modifier.weight(1f),style=MaterialTheme.typography.bodyMedium,fontWeight=FontWeight.Medium)
                    if(custom) IconButton(onClick={appearance.resetIcon()},enabled=!appearance.busy) {
                        Icon(Icons.Default.Refresh,stringResource(R.string.restore_original_icon),tint=MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Button(onClick={picker.launch("image/*")},enabled=!appearance.busy && !custom,contentPadding=PaddingValues(horizontal=12.dp)) {
                        Text(stringResource(if(custom) R.string.settings_icon_loaded else R.string.settings_load_image),fontSize=13.sp)
                    }
                }
                OutlinedButton(onClick={appearance.addToLauncher()},enabled=!appearance.busy,modifier=Modifier.fillMaxWidth(),
                    contentPadding=PaddingValues(vertical=4.dp,horizontal=12.dp)) {
                    Icon(Icons.Default.Home,null,Modifier.size(16.dp));Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.settings_launcher_button),fontSize=12.sp)
                }
            }
        }
        if(includeSettingsButton) {
            SettingsButtonVisibilityCard(appearance)
            AppearanceNotices(appearance)
        }
    }
}

@Composable
internal fun SettingsButtonVisibilityCard(appearance: AppAppearanceViewModel) {
    SharedSettingsCard {
        Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) {
                Row(Modifier.weight(1f),verticalAlignment=Alignment.CenterVertically) {
                    Icon(Icons.Default.Settings,null,tint=MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(stringResource(R.string.settings_hide_gear_title),fontWeight=FontWeight.Bold,fontSize=15.sp)
                        Text(stringResource(R.string.settings_hide_gear_description),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Switch(appearance.hideSettingsIcon,{appearance.saveHideSettingsIcon(it)})
            }
            Text(stringResource(R.string.settings_hide_gear_note),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun AppearanceNotices(appearance: AppAppearanceViewModel) {
    if(appearance.notice.isNotBlank()) Surface(color=MaterialTheme.colorScheme.secondaryContainer,shape=MaterialTheme.shapes.medium) {
        Row(Modifier.fillMaxWidth().padding(start=12.dp),verticalAlignment=Alignment.CenterVertically) {
            Text(appearance.notice,modifier=Modifier.weight(1f),style=MaterialTheme.typography.bodySmall)
            IconButton(onClick={appearance.clearNotice()}) {Icon(Icons.Default.Close,stringResource(R.string.dismiss_appearance_notice))}
        }
    }
}

@Composable
internal fun AppearanceSettingsDialog(appearance: AppAppearanceViewModel,close: () -> Unit) {
    Dialog(onDismissRequest=close,properties=DialogProperties(usePlatformDefaultWidth=false)) {
        Surface(Modifier.windowInsetsPadding(WindowInsets.safeDrawing).fillMaxWidth(.96f).widthIn(max=960.dp).fillMaxHeight(.92f),shape=MaterialTheme.shapes.extraLarge,tonalElevation=6.dp) {
            Column(Modifier.fillMaxSize().padding(16.dp)) {
                Row(verticalAlignment=Alignment.CenterVertically) {
                    Text(stringResource(R.string.appearance),style=MaterialTheme.typography.titleLarge,modifier=Modifier.weight(1f))
                    IconButton(onClick=close) {Icon(Icons.Default.Close,stringResource(R.string.close_appearance_settings))}
                }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(top=8.dp)) {AppearanceSettings(appearance)}
            }
        }
    }
}
