package iecxiv.cronmaster

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.json.JSONObject

class MainActivity: ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val vm: MainViewModel=viewModel()
            val themes: ThemeStyleViewModel=viewModel()
            val display: DisplayModeViewModel=viewModel()
            SettingsAppTheme(style=themes.style,mode=display.mode) {
                Surface(Modifier.fillMaxSize(),color=MaterialTheme.colorScheme.background) {
                    key(vm.sessionEpoch) { App(vm) }
                }
            }
        }
    }
}
private data class Editor(val title: String,val initial: JSONObject,val save: (JSONObject) -> Unit)
private data class Confirmation(val title: String,val detail: String,val run: () -> Unit)
private data class LogHistory(val id: String,val name: String,val rows: List<JSONObject>)
private data class Destination(val title: String,val icon: ImageVector)
private val destinations=listOf(
    Destination("Tareas",Icons.Default.Schedule),Destination("Scripts",Icons.Default.Code),
    Destination("Backups",Icons.Default.Backup),Destination("Sistema",Icons.Default.Dns),Destination("Ajustes",Icons.Default.Settings)
)
@Composable private fun ActionIcon(icon: ImageVector,label: String,enabled: Boolean=true,tint: Color=LocalContentColor.current,onClick: () -> Unit) {
    val rowScope = LocalCardActionRowScope.current
    if (rowScope != null) {
        with(rowScope) {
            CompactCardAction(icon, label, enabled, tint, onClick)
        }
    } else {
        IconButton(onClick=onClick,enabled=enabled) { Icon(icon,contentDescription=label,tint=if(enabled) tint else tint.copy(alpha=.35f)) }
    }
}
@Composable private fun Actions(content: @Composable RowScope.() -> Unit) {
    CardActions(content)
}
@Composable private fun WideModal(title: String,close: () -> Unit,content: @Composable ColumnScope.() -> Unit) {
    Dialog(onDismissRequest=close,properties=DialogProperties(usePlatformDefaultWidth=false)) {
        Surface(Modifier.windowInsetsPadding(WindowInsets.safeDrawing).fillMaxWidth(.96f).widthIn(max=960.dp).fillMaxHeight(.92f),shape=MaterialTheme.shapes.extraLarge,tonalElevation=6.dp) {
            Column(Modifier.fillMaxSize().padding(16.dp)) {
                Row(verticalAlignment=Alignment.CenterVertically) {
                    Text(title,style=MaterialTheme.typography.titleLarge,modifier=Modifier.weight(1f))
                    ActionIcon(Icons.Default.Close,"Cerrar",onClick=close)
                }
                HorizontalDivider()
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(top=12.dp),verticalArrangement=Arrangement.spacedBy(12.dp),content=content)
            }
        }
    }
}
@Composable private fun Field(label: String,value: String,secret: Boolean=false,onChange: (String) -> Unit) {
    if(secret) OutlinedTextField(value,onChange,label={Text(label)},visualTransformation=PasswordVisualTransformation(),singleLine=true,modifier=Modifier.fillMaxWidth())
    else OutlinedTextField(value,onChange,label={Text(label)},modifier=Modifier.fillMaxWidth(),minLines=if(label=="Contenido") 12 else 1,
        textStyle=LocalTextStyle.current.copy(fontFamily=if(label in listOf("Contenido","Comando","Expresión cron")) FontFamily.Monospace else FontFamily.Default))
}
@Composable private fun JobHealth(job: JSONObject) {
    val state=health(job)
    val color=when(state.kind) { Health.Kind.GOOD -> Color(0xFF58C996); Health.Kind.BAD -> Color(0xFFFF7187); Health.Kind.UNKNOWN -> Color(0xFF8995A8) }
    Row(Modifier.padding(vertical=8.dp),verticalAlignment=Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).background(color,CircleShape))
        Spacer(Modifier.width(8.dp))
        Text(state.label,color=color,style=MaterialTheme.typography.labelMedium)
    }
}
@Composable private fun StatTree(data: JSONObject,prefix: String="") {
    data.keys().asSequence().toList().forEach { key ->
        val value=data.opt(key)
        if(value is JSONObject) { Text(prefix+key,style=MaterialTheme.typography.titleSmall); StatTree(value,"$prefix  ") }
        else Row(Modifier.fillMaxWidth().padding(vertical=4.dp)) {
            Text(prefix+key,modifier=Modifier.weight(1f),color=MaterialTheme.colorScheme.onSurfaceVariant)
            SelectionContainer { Text(value.toString(),style=MaterialTheme.typography.bodyMedium) }
        }
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun App(vm: MainViewModel) {
    val appearance: AppAppearanceViewModel=viewModel()
    val themes: ThemeStyleViewModel=viewModel()
    val display: DisplayModeViewModel=viewModel()
    ApplyAppearanceToTask(appearance)
    val owner=LocalLifecycleOwner.current
    val clipboard=LocalClipboardManager.current
    var tab by remember { mutableStateOf(0) }
    var lastTab by remember { mutableStateOf(0) }
    var appearanceDialog by remember { mutableStateOf(false) }
    var addServerDialog by remember { mutableStateOf(false) }
    var serverSwitcher by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var scriptQuery by remember { mutableStateOf("") }
    val context=LocalContext.current
    val listPreferences=remember(context) { context.applicationContext.getSharedPreferences("list_preferences",0) }
    var nonZeroFirst by remember(listPreferences) { mutableStateOf(listPreferences.getBoolean("tasks_non_zero_first",false)) }
    var selectedUser by remember { mutableStateOf("") }
    var filters by remember { mutableStateOf(false) }
    var editor by remember { mutableStateOf<Editor?>(null) }
    var confirmation by remember { mutableStateOf<Confirmation?>(null) }
    var history by remember { mutableStateOf<LogHistory?>(null) }
    var output by remember { mutableStateOf<String?>(null) }
    var runId by remember { mutableStateOf<String?>(null) }
    val enabled=!vm.busy && !vm.restoring
    LaunchedEffect(vm.connected) {
        if(!vm.connected) { tab=0; lastTab=0 } else appearanceDialog=false
    }
    BackHandler(enabled=vm.connected && tab==4) { tab=lastTab }
    LaunchedEffect(owner,vm.connected,vm.restoring) {
        if(vm.connected && !vm.restoring) owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) { vm.foregroundLoop() }
    }
    fun openSettings() {
        if(vm.connected) { if(tab in 0..3) lastTab=tab; tab=4 } else appearanceDialog=true
    }
    fun confirm(title: String,detail: String,run: () -> Unit) { confirmation=Confirmation(title,detail,run) }
    fun openJob(job: JSONObject?=null,script: JSONObject?=null) {
        val data=obj("id" to job?.optString("id"),"schedule" to (job?.optString("schedule") ?: "0 2 * * *"),
            "command" to (job?.let { it.optString("editableCommand",it.optString("command")) } ?: ""),
            "selectedScriptId" to (script?.optString("id") ?: ""),"comment" to (job?.optString("comment") ?: ""),
            "user" to (job?.optString("user") ?: vm.users.firstOrNull().orEmpty()),"logsEnabled" to (job?.optBoolean("logsEnabled") ?: true))
        editor=Editor(if(job==null) "Nueva tarea" else "Editar tarea",data) { changed ->
            vm.action(if(job==null) "job.create" else "job.edit",changed) { editor=null }
        }
    }
    fun openScript(script: JSONObject?=null) {
        if(script==null) editor=Editor("Nuevo script",JSONObject(vm.draft.toString())) { changed ->
            vm.action("script.create",changed) { editor=null; vm.draft=obj("name" to "","description" to "","content" to "#!/bin/bash\n") }
        }
        else vm.work {
            val content=vm.request("script.content",obj("filename" to script.getString("filename"))).optString("data")
            editor=Editor("Editar script",obj("id" to script.getString("id"),"name" to script.optString("name"),"description" to script.optString("description"),"content" to content)) { changed ->
                vm.action("script.update",changed) { editor=null }
            }
        }
    }
    Scaffold(
        containerColor=MaterialTheme.colorScheme.background,
        topBar={ CronmasterAppBar(
            appearance=appearance,
            section=if(appearanceDialog) "Ajustes" else if(vm.connected) destinations[tab].title else "Acceso",
            onOpenSettings={openSettings()},
            onRefresh=if(vm.connected) { { vm.work { vm.refresh(); if(tab==2) vm.backups=vm.request("backups").rows(); vm.message="Datos actualizados" } } } else null,
            refreshEnabled=enabled,
            connectionStatus=vm.connectionStatus,
            serverCount=vm.serverProfiles.size,
            serverName=vm.activeServerName,
            onSwitchServer={serverSwitcher=true},
            switchEnabled=enabled,
            nonZeroFirst=nonZeroFirst,
            onNonZeroFirstChange=if(vm.connected && tab==0 && !appearanceDialog) { { selected ->
                nonZeroFirst=selected
                listPreferences.edit().putBoolean("tasks_non_zero_first",selected).apply()
            } } else null
        ) },
        bottomBar={ if(vm.connected) NavigationBar {
            destinations.take(4).forEachIndexed { index,d -> NavigationBarItem(selected=tab==index,onClick={tab=index; lastTab=index; if(index==2) vm.loadBackups()},icon={Icon(d.icon,d.title)},label=null) }
        } },
        floatingActionButton={ if(vm.connected && tab in 0..1) FloatingActionButton(onClick={if(enabled) { if(tab==0) openJob() else openScript() }}) { Icon(Icons.Default.Add,if(tab==0) "Nueva tarea" else "Nuevo script") } }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal=16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            if(vm.busy || vm.restoring) LinearProgressIndicator(Modifier.fillMaxWidth())
            if(vm.message.isNotBlank()) Surface(shape=MaterialTheme.shapes.medium,color=MaterialTheme.colorScheme.secondaryContainer) {
                Row(Modifier.fillMaxWidth().padding(start=12.dp),verticalAlignment=Alignment.CenterVertically) {
                    Text(vm.message,modifier=Modifier.weight(1f),style=MaterialTheme.typography.bodySmall)
                    ActionIcon(Icons.Default.Close,"Descartar aviso") { vm.message="" }
                }
            }
            if(vm.restoring) Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center) { Text("Restaurando acceso seguro…") }
            else if(!vm.connected) Column(Modifier.verticalScroll(rememberScrollState()).padding(vertical=12.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
                Icon(Icons.Default.Terminal,null,modifier=Modifier.size(56.dp),tint=MaterialTheme.colorScheme.primary)
                Text("Tu servidor, a un toque",style=MaterialTheme.typography.headlineMedium)
                Text("Identifícate una vez. El acceso se guarda cifrado en este dispositivo.",color=MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(vm.sessionName,{if(it.length<=64) vm.sessionName=it},label={Text(stringResource(R.string.session_name_optional))},singleLine=true,enabled=enabled,modifier=Modifier.fillMaxWidth())
                Field("URL del servidor",vm.url,onChange={vm.url=it})
                Field("Contraseña",vm.password,true) { vm.password=it }
                Field("API key (alternativa)",vm.token,true) { vm.token=it }
                Row(verticalAlignment=Alignment.CenterVertically) { Checkbox(vm.allowHttp,{vm.allowHttp=it}); Text("Permitir HTTP sin cifrado",style=MaterialTheme.typography.bodySmall) }
                if(vm.allowHttp) Text("HTTP expone el acceso en la red. Utiliza HTTPS siempre que sea posible.",color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)
                Button(onClick={vm.connect()},enabled=enabled && vm.url.isNotBlank(),modifier=Modifier.fillMaxWidth()) { Icon(Icons.Default.Login,null); Spacer(Modifier.width(8.dp)); Text("Conectar y guardar") }
                TextButton(onClick={confirm("Borrar acceso local",if(vm.activeServerId==null) "Se eliminarán los accesos guardados de este dispositivo." else "Se eliminará solo el acceso del servidor seleccionado. Los demás se conservarán.") { vm.logout() }},enabled=enabled) { Text("Borrar acceso guardado") }
            }
            else {
                if(vm.wrapperModified) Text("Wrapper de logs modificado en el servidor",color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)
                when(tab) {
                    0 -> {
                        OutlinedTextField(query,{query=it},label={Text("Buscar tareas")},leadingIcon={Icon(Icons.Default.Search,null)},trailingIcon={ActionIcon(Icons.Default.FilterList,"Filtrar por usuario") { filters=!filters }},singleLine=true,modifier=Modifier.fillMaxWidth(),shape=MaterialTheme.shapes.large)
                        if(filters) Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                            FilterChip(selected=selectedUser.isEmpty(),onClick={selectedUser=""},label={Text("Todos")})
                            vm.users.forEach { u -> FilterChip(selected=selectedUser==u,onClick={selectedUser=u},label={Text(u)}) }
                        }
                        val filtered=vm.jobs.filter { (selectedUser.isEmpty() || it.optString("user")==selectedUser) && it.toString().contains(query,true) }
                        val visible=if(nonZeroFirst) filtered.sortedBy { if(it.optJSONObject("logError")?.intOrNull("latestExitCode")==0) 1 else 0 } else filtered
                        Text("${visible.size} tareas",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        LazyColumn(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(12.dp),contentPadding=PaddingValues(bottom=90.dp)) {
                            if(visible.isEmpty()) item { Text("No hay tareas que mostrar",modifier=Modifier.padding(16.dp)) }
                            items(visible,key={it.getString("id")}) { job ->
                                val id=job.getString("id"); val args=obj("id" to id); val paused=job.optBoolean("paused",job.optBoolean("isPaused"))
                                CronmasterCard(Modifier.fillMaxWidth(),shape=MaterialTheme.shapes.large) { Column(Modifier.padding(16.dp)) {
                                    Row(verticalAlignment=Alignment.CenterVertically) {
                                        Column(Modifier.weight(1f)) {
                                            Text(job.optString("comment").ifBlank { id },style=MaterialTheme.typography.titleMedium)
                                            Text(job.optString("user"),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        if(paused) Icon(Icons.Default.Pause,"Tarea pausada",tint=MaterialTheme.colorScheme.secondary)
                                    }
                                    JobHealth(job)
                                    Text(if(vm.human) job.optString("humanSchedule",job.optString("schedule")) else job.optString("schedule"),style=MaterialTheme.typography.bodyMedium)
                                    if(!vm.minimal) {
                                        Spacer(Modifier.height(8.dp))
                                        Surface(shape=MaterialTheme.shapes.small,color=MaterialTheme.colorScheme.background) {
                                            SelectionContainer { Text(job.optString("editableCommand",job.optString("command")),fontFamily=FontFamily.Monospace,style=MaterialTheme.typography.bodySmall,modifier=Modifier.fillMaxWidth().padding(10.dp)) }
                                        }
                                        job.optJSONArray("nextRuns")?.let { a -> if(a.length()>0) Text("Próxima (UTC): ${a.optString(0)}",style=MaterialTheme.typography.labelSmall,modifier=Modifier.padding(top=8.dp),color=MaterialTheme.colorScheme.onSurfaceVariant) }
                                    }
                                    Actions {
                                        ActionIcon(Icons.Default.PlayArrow,"Ejecutar tarea",enabled && !paused,MaterialTheme.colorScheme.primary) { confirm("Ejecutar tarea",job.optString("command")) {
                                            vm.action("job.run",args) { result -> val run=result.optString("runId"); if(run.isNotBlank()) runId=run else output=result.optString("output",result.optString("message")) }
                                        } }
                                        ActionIcon(Icons.Default.Edit,"Editar tarea",enabled) { openJob(job) }
                                        ActionIcon(if(paused) Icons.Default.PlayArrow else Icons.Default.Pause,if(paused) "Reanudar tarea" else "Pausar tarea",enabled) { confirm("Cambiar estado",job.optString("comment",id)) { vm.action(if(paused) "job.resume" else "job.pause",args) } }
                                        ActionIcon(Icons.Default.History,"Historial de logs",enabled) { vm.work { history=LogHistory(id,job.optString("comment",id),vm.request("logs",args).rows()) } }
                                        ActionIcon(Icons.Default.ContentCopy,"Clonar tarea",enabled) {
                                            editor=Editor("Clonar tarea",obj("comment" to (job.optString("comment")+" (copia)"))) { data -> data.put("id",id); vm.action("job.clone",data) { editor=null } }
                                        }
                                        ActionIcon(Icons.Default.ReceiptLong,"Alternar logging",enabled) { confirm("Cambiar logging",id) { vm.action("job.logging",args) } }
                                        ActionIcon(Icons.Default.Backup,"Guardar backup de tarea",enabled) { confirm("Guardar backup",id) { vm.action("job.backup",args) } }
                                        ActionIcon(Icons.Default.Delete,"Eliminar tarea",enabled,MaterialTheme.colorScheme.error) { confirm("Eliminar tarea",job.toString(2)) { vm.action("job.delete",args) } }
                                    }
                                } }
                            }
                        }
                    }
                    1 -> {
                        OutlinedTextField(scriptQuery,{scriptQuery=it},label={Text("Buscar scripts")},leadingIcon={Icon(Icons.Default.Search,null)},singleLine=true,modifier=Modifier.fillMaxWidth(),shape=MaterialTheme.shapes.large)
                        val visibleScripts=vm.scripts.filter { script ->
                            listOf("name","description","filename").any { script.optString(it).contains(scriptQuery,true) }
                        }
                        Text("${visibleScripts.size} scripts",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        LazyColumn(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(12.dp),contentPadding=PaddingValues(bottom=90.dp)) {
                        if(visibleScripts.isEmpty()) item { Text(if(vm.scripts.isEmpty()) "Crea tu primer script con el botón +" else "No hay scripts que mostrar",modifier=Modifier.padding(16.dp)) }
                        items(visibleScripts,key={it.getString("id")}) { script ->
                            val id=script.getString("id")
                            CronmasterCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) {
                                Text(script.optString("name"),style=MaterialTheme.typography.titleMedium)
                                Text(script.optString("description"),style=MaterialTheme.typography.bodyMedium)
                                Text(script.optString("filename"),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                                Actions {
                                    ActionIcon(Icons.Default.Edit,"Editar script",enabled) { openScript(script) }
                                    ActionIcon(Icons.Default.Schedule,"Programar script",enabled) { openJob(script=script) }
                                    ActionIcon(Icons.Default.ContentCopy,"Copiar contenido",enabled) { vm.work { clipboard.setText(AnnotatedString(vm.request("script.content",obj("filename" to script.getString("filename"))).optString("data"))); vm.message="Script copiado" } }
                                    ActionIcon(Icons.Default.FileCopy,"Clonar script",enabled) { editor=Editor("Clonar script",obj("name" to (script.optString("name")+" (copia)"))) { d -> d.put("id",id); vm.action("script.clone",d) { editor=null } } }
                                    ActionIcon(Icons.Default.Delete,"Eliminar script",enabled,MaterialTheme.colorScheme.error) { confirm("Eliminar script",script.optString("name")+". Sus tareas no se eliminarán.") { vm.action("script.delete",obj("id" to id)) } }
                                }
                            } }
                        }
                    }
                    }
                    2 -> {
                        Actions {
                            ActionIcon(Icons.Default.Backup,"Backup de todas las tareas",enabled) { confirm("Backup completo","Guardar todas las tareas en el servidor") { vm.action("backup.all") } }
                            ActionIcon(Icons.Default.Restore,"Restaurar todos los backups",enabled) { confirm("Restaurar todos","Puede crear tareas duplicadas.") { vm.action("restore.all") } }
                        }
                        LazyColumn(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(12.dp),contentPadding=PaddingValues(bottom=16.dp)) {
                            if(vm.backups.isEmpty()) item { Text("No hay backups cargados. Pulsa el icono de actualizar.") }
                            items(vm.backups) { b -> CronmasterCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) {
                                Text(b.optJSONObject("job")?.optString("comment").orEmpty().ifBlank { b.optString("filename") },style=MaterialTheme.typography.titleMedium)
                                Text(b.optString("filename"),style=MaterialTheme.typography.bodySmall)
                                Text(b.optString("backedUpAt"),style=MaterialTheme.typography.labelSmall)
                                Actions {
                                    ActionIcon(Icons.Default.Restore,"Restaurar backup",enabled) { confirm("Restaurar backup",b.optString("filename")+". Puede crear duplicados.") { vm.action("backup.restore",obj("filename" to b.getString("filename"))) } }
                                    ActionIcon(Icons.Default.Delete,"Eliminar backup",enabled,MaterialTheme.colorScheme.error) { confirm("Eliminar backup",b.optString("filename")) { vm.action("backup.delete",obj("filename" to b.getString("filename"))) } }
                                }
                            } } }
                        }
                    }
                    3 -> LazyColumn(Modifier.weight(1f).padding(top=8.dp),verticalArrangement=Arrangement.spacedBy(12.dp),contentPadding=PaddingValues(bottom=16.dp)) {
                        items(vm.stats.keys().asSequence().toList()) { key -> CronmasterCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) {
                            Text(key.replaceFirstChar { it.uppercase() },style=MaterialTheme.typography.titleMedium)
                            val value=vm.stats.opt(key)
                            if(value is JSONObject) StatTree(value) else Text(value.toString(),style=MaterialTheme.typography.headlineSmall)
                        } } }
                    }
                    4 -> Column(Modifier.weight(1f).padding(top=8.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                        AppSettingsContent(vm,appearance,themes,display,onAddServer={addServerDialog=true})
                    }
                }
            }
        }
    }
    editor?.let { current -> key(current) {
        var data by remember { mutableStateOf(JSONObject(current.initial.toString())) }
        WideModal(current.title,{editor=null}) {
            if(data.has("schedule")) {
                Text("Programación",style=MaterialTheme.typography.titleSmall)
                Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    listOf("Cada 5 min" to "*/5 * * * *","Cada hora" to "0 * * * *","Diario" to "0 2 * * *","Semanal" to "0 2 * * 0","Al arrancar" to "@reboot").forEach { (name,cron) -> AssistChip(onClick={data=JSONObject(data.toString()).put("schedule",cron)},label={Text(name)}) }
                }
                Text("Comando manual o script. Un script seleccionado tiene prioridad.",style=MaterialTheme.typography.bodySmall)
                Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected=data.optString("selectedScriptId").isBlank(),onClick={data=JSONObject(data.toString()).put("selectedScriptId","")},label={Text("Manual")})
                    vm.scripts.forEach { s -> FilterChip(selected=data.optString("selectedScriptId")==s.getString("id"),onClick={data=JSONObject(data.toString()).put("selectedScriptId",s.getString("id"))},label={Text(s.optString("name"))}) }
                }
            }
            val labels=mapOf("schedule" to "Expresión cron","command" to "Comando","comment" to "Nombre / comentario","name" to "Nombre","description" to "Descripción","content" to "Contenido","user" to "Usuario")
            current.initial.keys().asSequence().toList().filter { it !in listOf("id","selectedScriptId") }.forEach { field ->
                when(field) {
                    "logsEnabled" -> Row(verticalAlignment=Alignment.CenterVertically) { Text("Guardar logs de ejecución",modifier=Modifier.weight(1f)); Switch(data.optBoolean(field),{data=JSONObject(data.toString()).put(field,it)}) }
                    "user" -> if(!current.initial.isNull("id")) Text("Usuario: ${data.optString("user")}") else {
                        Text("Usuario")
                        Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) { vm.users.forEach { u -> FilterChip(selected=data.optString("user")==u,onClick={data=JSONObject(data.toString()).put("user",u)},label={Text(u)}) } }
                    }
                    else -> Field(labels[field] ?: field,data.optString(field,"")) { value -> data=JSONObject(data.toString()).put(field,value); if(current.title=="Nuevo script") vm.draft=JSONObject(data.toString()) }
                }
            }
            if(data.has("content")) {
                TextButton(onClick={vm.loadSnippets()},enabled=enabled) { Icon(Icons.Default.DataObject,null); Spacer(Modifier.width(8.dp)); Text("Snippets") }
                vm.snippets.forEach { snippet -> AssistChip(onClick={data=JSONObject(data.toString()).put("content",data.optString("content")+"\n"+snippet.optString("template")); if(current.title=="Nuevo script") vm.draft=JSONObject(data.toString())},label={Text(snippet.optString("title",snippet.optString("name",snippet.optString("id"))))}) }
            }
            Button(onClick={current.save(JSONObject(data.toString()))},enabled=enabled,modifier=Modifier.fillMaxWidth()) { Icon(Icons.Default.Save,null); Spacer(Modifier.width(8.dp)); Text("Guardar cambios") }
        }
    } }
    confirmation?.let { c -> AlertDialog(onDismissRequest={confirmation=null},title={Text(c.title)},text={Text(c.detail)},confirmButton={TextButton(onClick={confirmation=null;c.run()},enabled=enabled) { Text("Confirmar") }},dismissButton={TextButton(onClick={confirmation=null}) { Text("Cancelar") }}) }
    history?.let { h -> WideModal("Logs · ${h.name}",{history=null}) {
        Actions { ActionIcon(Icons.Default.DeleteSweep,"Eliminar todos los logs",enabled,MaterialTheme.colorScheme.error) { confirm("Eliminar todos los logs",h.id) { vm.action("logs.deleteAll",obj("id" to h.id)) { history=null } } } }
        if(h.rows.isEmpty()) Text("No hay logs guardados")
        h.rows.forEach { log -> CronmasterCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) {
            Text(log.optString("filename"),style=MaterialTheme.typography.titleSmall)
            Text("${log.optLong("size")} bytes · exit ${if(log.isNull("exitCode")) "—" else log.optString("exitCode","—")}",style=MaterialTheme.typography.bodySmall)
            val args=obj("id" to h.id,"filename" to log.getString("filename"))
            Actions {
                ActionIcon(Icons.Default.Terminal,"Abrir log en terminal",enabled) { vm.work { output=vm.request("log.content",args).optString("data") } }
                ActionIcon(Icons.Default.Delete,"Eliminar log",enabled,MaterialTheme.colorScheme.error) { confirm("Eliminar log",log.optString("filename")) { vm.action("log.delete",args) { history=null } } }
            }
        } } }
    } }
    output?.let { text -> TerminalDialog("Log de ejecución",text,"Salida guardada",close={output=null}) }
    runId?.let { id -> SessionLiveTerminal(vm,id) { runId=null } }
    if(appearanceDialog) UnifiedSettingsDialog(vm,appearance,themes,display,close={appearanceDialog=false},onAddServer={addServerDialog=true})
    if(serverSwitcher && vm.serverProfiles.size>1) ServerSwitcherDialog(vm) { serverSwitcher=false }
    if(addServerDialog) AddServerDialog(vm) { addServerDialog=false }
}

