package iecxiv.cronmaster

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
private fun ServerChoice(profile: ServerProfile,active: Boolean,enabled: Boolean,onRename: (() -> Unit)?=null,select: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical=6.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        Column(Modifier.weight(1f)) {
            Text(profile.name,style=MaterialTheme.typography.titleSmall,maxLines=1,overflow=TextOverflow.Ellipsis)
            Text(profile.url,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant,maxLines=2,overflow=TextOverflow.Ellipsis)
        }
        if(active) Icon(Icons.Default.CheckCircle,stringResource(R.string.server_active),tint=MaterialTheme.colorScheme.primary)
        else OutlinedButton(onClick=select,enabled=enabled,contentPadding=PaddingValues(horizontal=12.dp)) { Text(stringResource(R.string.use_server)) }
        if(onRename!=null) IconButton(onClick=onRename,enabled=enabled) {
            Icon(Icons.Default.Edit,stringResource(R.string.edit_session_name_description,profile.name))
        }
    }
}

@Composable
internal fun ServerSettingsCard(vm: MainViewModel,onAddServer: () -> Unit) {
    val enabled=!vm.busy && !vm.restoring
    var renaming by remember { mutableStateOf<ServerProfile?>(null) }
    CronmasterCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.servers),style=MaterialTheme.typography.titleMedium)
            if(vm.serverProfiles.isEmpty()) Text(stringResource(R.string.no_saved_servers),style=MaterialTheme.typography.bodySmall)
            vm.serverProfiles.forEach { profile ->
                key(profile.id) {
                    ServerChoice(profile,vm.connected && profile.id==vm.activeServerId,enabled,onRename={renaming=profile}) { vm.switchServer(profile.id) }
                }
            }
            OutlinedButton(onClick=onAddServer,enabled=enabled,modifier=Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Add,null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.add_server))
            }
            Text(stringResource(R.string.session_names_hint),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    renaming?.let { profile -> RenameSessionDialog(vm,profile) {renaming=null} }
}

@Composable
private fun RenameSessionDialog(vm: MainViewModel,profile: ServerProfile,close: () -> Unit) {
    var name by remember(profile.id) { mutableStateOf(if(profile.name==profile.url) "" else profile.name) }
    var error by remember(profile.id) { mutableStateOf("") }
    val enabled=!vm.busy && !vm.restoring
    AlertDialog(onDismissRequest={if(enabled) close()},title={Text(stringResource(R.string.edit_session_name))},text={
        Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text(profile.url,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(name,{if(it.length<=64) name=it},label={Text(stringResource(R.string.session_name_optional))},
                singleLine=true,enabled=enabled,modifier=Modifier.fillMaxWidth())
            Text(stringResource(R.string.session_names_hint),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            if(error.isNotBlank()) Text(error,color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)
            if(vm.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        }
    },confirmButton={TextButton(onClick={error="";vm.renameSession(profile.id,name,done=close,failed={error=it})},enabled=enabled) {Text(stringResource(R.string.save))}},
        dismissButton={TextButton(onClick=close,enabled=enabled) {Text(stringResource(R.string.cancel))}})
}

@Composable
internal fun ServerSwitcherDialog(vm: MainViewModel,close: () -> Unit) {
    AlertDialog(onDismissRequest=close,title={Text(stringResource(R.string.switch_server))},text={
        Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.switch_server_warning),style=MaterialTheme.typography.bodySmall)
            LazyColumn(Modifier.fillMaxWidth().heightIn(max=360.dp)) {
                items(vm.serverProfiles,key={it.id}) { profile ->
                    ServerChoice(profile,vm.connected && profile.id==vm.activeServerId,!vm.busy && !vm.restoring) { vm.switchServer(profile.id,close) }
                }
            }
        }
    },confirmButton={TextButton(onClick=close) {Text(stringResource(R.string.close))}})
}

@Composable
private fun ServerField(label: String,value: String,enabled: Boolean,secret: Boolean=false,change: (String) -> Unit) {
    OutlinedTextField(value,change,label={Text(label)},singleLine=true,enabled=enabled,modifier=Modifier.fillMaxWidth(),
        visualTransformation=if(secret) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions=KeyboardOptions(keyboardType=if(secret) KeyboardType.Password else KeyboardType.Text))
}

@Composable
internal fun AddServerDialog(vm: MainViewModel,close: () -> Unit) {
    var name by remember {mutableStateOf("")}
    var url by remember {mutableStateOf("")}
    var password by remember {mutableStateOf("")}
    var token by remember {mutableStateOf("")}
    var http by remember {mutableStateOf(false)}
    var error by remember {mutableStateOf("")}
    val enabled=!vm.busy && !vm.restoring
    Dialog(onDismissRequest={if(enabled) close()},properties=DialogProperties(usePlatformDefaultWidth=false)) {
        Surface(Modifier.windowInsetsPadding(WindowInsets.safeDrawing).fillMaxWidth(.96f).widthIn(max=960.dp).fillMaxHeight(.92f),shape=MaterialTheme.shapes.extraLarge,tonalElevation=6.dp) {
            Column(Modifier.fillMaxSize().padding(16.dp)) {
                Row(verticalAlignment=Alignment.CenterVertically) {
                    Text(stringResource(R.string.add_server),style=MaterialTheme.typography.titleLarge,modifier=Modifier.weight(1f))
                    IconButton(onClick=close,enabled=enabled) {Icon(Icons.Default.Close,stringResource(R.string.cancel_add_server))}
                }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.add_server_hint),style=MaterialTheme.typography.bodySmall)
                    ServerField(stringResource(R.string.session_name_optional),name,enabled) {if(it.length<=64) name=it}
                    ServerField(stringResource(R.string.server_url),url,enabled) {url=it}
                    ServerField(stringResource(R.string.password),password,enabled,true) {password=it}
                    ServerField(stringResource(R.string.api_key_alternative),token,enabled,true) {token=it}
                    Row(verticalAlignment=Alignment.CenterVertically) {
                        Checkbox(http,{http=it},enabled=enabled)
                        Text(stringResource(R.string.allow_http),style=MaterialTheme.typography.bodySmall)
                    }
                    if(http) Text(stringResource(R.string.http_warning),color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)
                    if(error.isNotBlank()) Text(error,color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)
                    if(vm.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                    Button(onClick={
                        error=""
                        vm.addServer(name,url,token,password,http,done=close,failed={error=it})
                    },enabled=enabled && url.isNotBlank() && (password.isNotBlank() || token.isNotBlank()),modifier=Modifier.fillMaxWidth()) {
                        Text(stringResource(if(vm.busy) R.string.validating_server else R.string.validate_save_use))
                    }
                }
            }
        }
    }
}

@Composable
internal fun ServerSettingsDialog(vm: MainViewModel,appearance: AppAppearanceViewModel,close: () -> Unit,onAddServer: () -> Unit) {
    val themes: ThemeStyleViewModel=viewModel()
    Dialog(onDismissRequest=close,properties=DialogProperties(usePlatformDefaultWidth=false)) {
        Surface(Modifier.windowInsetsPadding(WindowInsets.safeDrawing).fillMaxWidth(.96f).widthIn(max=960.dp).fillMaxHeight(.92f),shape=MaterialTheme.shapes.extraLarge,tonalElevation=6.dp) {
            Column(Modifier.fillMaxSize().padding(16.dp)) {
                Row(verticalAlignment=Alignment.CenterVertically) {
                    Text(stringResource(R.string.settings),style=MaterialTheme.typography.titleLarge,modifier=Modifier.weight(1f))
                    IconButton(onClick=close) {Icon(Icons.Default.Close,stringResource(R.string.close_settings))}
                }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(top=8.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                    ServerSettingsCard(vm,onAddServer)
                    ThemeStyleSettings(themes)
                    AppearanceSettings(appearance)
                    SettingsInformationCard()
                    SettingsVersionFooter()
                }
            }
        }
    }
}
