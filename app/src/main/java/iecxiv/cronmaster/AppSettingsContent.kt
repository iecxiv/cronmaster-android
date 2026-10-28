package iecxiv.cronmaster

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@Composable
internal fun AppSettingsContent(vm: MainViewModel,appearance: AppAppearanceViewModel,themes: ThemeStyleViewModel,display: DisplayModeViewModel,onAddServer: () -> Unit) {
    Column(verticalArrangement=Arrangement.spacedBy(16.dp)) {
        AppearanceSettings(appearance,includeSettingsButton=false)
        ApplicationThemeSettings(themes,display)
        SettingsButtonVisibilityCard(appearance)
        AppearanceNotices(appearance)
        ServerSettingsCard(vm,onAddServer)
        ActiveSessionSettings(vm)
        SharedSettingsCard {
            Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                SettingsHeading(Icons.Default.Tune,stringResource(R.string.settings_display_options),large=true)
                Row(verticalAlignment=Alignment.CenterVertically) {
                    Text(stringResource(R.string.minimal_view),modifier=Modifier.weight(1f))
                    Switch(vm.minimal,{vm.minimal=it;vm.saveAppearance()})
                }
                Row(verticalAlignment=Alignment.CenterVertically) {
                    Text(stringResource(R.string.readable_schedules),modifier=Modifier.weight(1f))
                    Switch(vm.human,{vm.human=it;vm.saveAppearance()})
                }
            }
        }
        SettingsInformationCard()
        SettingsVersionFooter()
    }
}

@Composable
private fun ActiveSessionSettings(vm: MainViewModel) {
    var logout by remember {mutableStateOf(false)}
    val enabled=!vm.busy && !vm.restoring
    if(vm.connected) SharedSettingsCard {
        Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            SettingsHeading(Icons.Default.Dns,stringResource(R.string.active_session),large=true)
            Text(vm.activeServerName.ifBlank {vm.url},style=MaterialTheme.typography.titleSmall)
            if(vm.activeServerName!=vm.url) Text(vm.url,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stringResource(R.string.encrypted_keystore_access),style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.primary)
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick={vm.editConnection()},enabled=enabled,modifier=Modifier.weight(1f)) {
                    Icon(Icons.Default.Edit,null,Modifier.size(18.dp));Spacer(Modifier.width(6.dp));Text(stringResource(R.string.change_access))
                }
                OutlinedButton(onClick={logout=true},enabled=enabled,modifier=Modifier.weight(1f)) {
                    Icon(Icons.Default.Logout,null,Modifier.size(18.dp));Spacer(Modifier.width(6.dp));Text(stringResource(R.string.logout))
                }
            }
        }
    }
    if(logout) AlertDialog(onDismissRequest={logout=false},title={Text(stringResource(R.string.logout))},
        text={Text(stringResource(R.string.logout_server_confirmation,vm.activeServerName.ifBlank {vm.url}))},
        confirmButton={TextButton(onClick={logout=false;vm.logout()},enabled=enabled) {Text(stringResource(R.string.logout))}},
        dismissButton={TextButton(onClick={logout=false}) {Text(stringResource(R.string.cancel))}})
}

@Composable
internal fun UnifiedSettingsDialog(vm: MainViewModel,appearance: AppAppearanceViewModel,themes: ThemeStyleViewModel,display: DisplayModeViewModel,close: () -> Unit,onAddServer: () -> Unit) {
    Dialog(onDismissRequest=close,properties=DialogProperties(usePlatformDefaultWidth=false)) {
        Surface(Modifier.windowInsetsPadding(WindowInsets.safeDrawing).fillMaxWidth(.96f).widthIn(max=960.dp).fillMaxHeight(.92f),shape=MaterialTheme.shapes.extraLarge,tonalElevation=6.dp) {
            Column(Modifier.fillMaxSize().padding(16.dp)) {
                Row(verticalAlignment=Alignment.CenterVertically) {
                    Text(stringResource(R.string.settings),style=MaterialTheme.typography.titleLarge,modifier=Modifier.weight(1f))
                    IconButton(onClick=close) {Icon(Icons.Default.Close,stringResource(R.string.close_settings))}
                }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(top=8.dp)) {
                    AppSettingsContent(vm,appearance,themes,display,onAddServer)
                }
            }
        }
    }
}
