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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@Composable
private fun ServerChoice(profile: ServerProfile,active: Boolean,enabled: Boolean,select: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical=6.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        Column(Modifier.weight(1f)) {
            Text(profile.name,style=MaterialTheme.typography.titleSmall,maxLines=1,overflow=TextOverflow.Ellipsis)
            Text(profile.url,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant,maxLines=2,overflow=TextOverflow.Ellipsis)
        }
        if(active) Icon(Icons.Default.CheckCircle,"Servidor activo",tint=MaterialTheme.colorScheme.primary)
        else OutlinedButton(onClick=select,enabled=enabled,contentPadding=PaddingValues(horizontal=12.dp)) { Text("Usar") }
    }
}

@Composable
internal fun ServerSettingsCard(vm: MainViewModel,onAddServer: () -> Unit) {
    val enabled=!vm.busy && !vm.restoring
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text("Servidores",style=MaterialTheme.typography.titleMedium)
            if(vm.serverProfiles.isEmpty()) Text("No hay servidores guardados",style=MaterialTheme.typography.bodySmall)
            vm.serverProfiles.forEach { profile ->
                ServerChoice(profile,vm.connected && profile.id==vm.activeServerId,enabled) { vm.switchServer(profile.id) }
            }
            OutlinedButton(onClick=onAddServer,enabled=enabled,modifier=Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Add,null); Spacer(Modifier.width(8.dp)); Text("Añadir servidor")
            }
            Text("Una sesión activa a la vez. Las demás conservan su acceso cifrado. Para cambiar credenciales o cerrar una sesión, selecciona primero su servidor.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun ServerSwitcherDialog(vm: MainViewModel,close: () -> Unit) {
    AlertDialog(onDismissRequest=close,title={Text("Cambiar servidor")},text={
        Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text("Se cerrarán las vistas y se descartarán los borradores locales. Las tareas del servidor seguirán ejecutándose.",style=MaterialTheme.typography.bodySmall)
            LazyColumn(Modifier.fillMaxWidth().heightIn(max=360.dp)) {
                items(vm.serverProfiles,key={it.id}) { profile ->
                    ServerChoice(profile,vm.connected && profile.id==vm.activeServerId,!vm.busy && !vm.restoring) { vm.switchServer(profile.id,close) }
                }
            }
        }
    },confirmButton={TextButton(onClick=close) {Text("Cerrar")}})
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
                    Text("Añadir servidor",style=MaterialTheme.typography.titleLarge,modifier=Modifier.weight(1f))
                    IconButton(onClick=close,enabled=enabled) {Icon(Icons.Default.Close,"Cancelar alta de servidor")}
                }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                    Text("El servidor actual se conserva. El nuevo acceso se valida antes de guardarlo y seleccionarlo.",style=MaterialTheme.typography.bodySmall)
                    ServerField("Nombre (opcional)",name,enabled) {if(it.length<=64) name=it}
                    ServerField("URL del servidor",url,enabled) {url=it}
                    ServerField("Contraseña",password,enabled,true) {password=it}
                    ServerField("API key (alternativa)",token,enabled,true) {token=it}
                    Row(verticalAlignment=Alignment.CenterVertically) {
                        Checkbox(http,{http=it},enabled=enabled)
                        Text("Permitir HTTP sin cifrado",style=MaterialTheme.typography.bodySmall)
                    }
                    if(http) Text("HTTP expone el acceso en la red. Utiliza HTTPS siempre que sea posible.",color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)
                    if(error.isNotBlank()) Text(error,color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)
                    if(vm.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                    Button(onClick={
                        error=""
                        vm.addServer(name,url,token,password,http,done=close,failed={error=it})
                    },enabled=enabled && url.isNotBlank() && (password.isNotBlank() || token.isNotBlank()),modifier=Modifier.fillMaxWidth()) {
                        Text(if(vm.busy) "Validando servidor…" else "Validar, guardar y usar")
                    }
                }
            }
        }
    }
}

@Composable
internal fun ServerSettingsDialog(vm: MainViewModel,appearance: AppAppearanceViewModel,close: () -> Unit,onAddServer: () -> Unit) {
    Dialog(onDismissRequest=close,properties=DialogProperties(usePlatformDefaultWidth=false)) {
        Surface(Modifier.windowInsetsPadding(WindowInsets.safeDrawing).fillMaxWidth(.96f).widthIn(max=960.dp).fillMaxHeight(.92f),shape=MaterialTheme.shapes.extraLarge,tonalElevation=6.dp) {
            Column(Modifier.fillMaxSize().padding(16.dp)) {
                Row(verticalAlignment=Alignment.CenterVertically) {
                    Text("Ajustes",style=MaterialTheme.typography.titleLarge,modifier=Modifier.weight(1f))
                    IconButton(onClick=close) {Icon(Icons.Default.Close,"Cerrar ajustes")}
                }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(top=8.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                    ServerSettingsCard(vm,onAddServer)
                    AppearanceSettings(appearance)
                    SettingsInformationCard()
                    SettingsVersionFooter()
                }
            }
        }
    }
}
