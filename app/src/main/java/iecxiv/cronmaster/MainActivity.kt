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
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
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
private data class Editor(val title: Int,val initial: JSONObject,val save: (JSONObject) -> Unit)
private data class Confirmation(val title: String,val detail: String,val run: () -> Unit)
private data class LogHistory(val id: String,val name: String,val rows: List<JSONObject>)
private data class Destination(val title: Int,val icon: ImageVector)
private val destinations=listOf(
    Destination(R.string.section_jobs,Icons.Default.Schedule),Destination(R.string.section_scripts,Icons.Default.Code),
    Destination(R.string.section_backups,Icons.Default.Backup),Destination(R.string.section_system,Icons.Default.Dns),Destination(R.string.settings,Icons.Default.Settings)
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
                    ActionIcon(Icons.Default.Close,stringResource(R.string.close),onClick=close)
                }
                HorizontalDivider()
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(top=12.dp),verticalArrangement=Arrangement.spacedBy(12.dp),content=content)
            }
        }
    }
}
@Composable private fun Field(label: String,value: String,secret: Boolean=false,multiline: Boolean=false,code: Boolean=false,onChange: (String) -> Unit) {
    if(secret) OutlinedTextField(value,onChange,label={Text(label)},visualTransformation=PasswordVisualTransformation(),singleLine=true,modifier=Modifier.fillMaxWidth())
    else OutlinedTextField(value,onChange,label={Text(label)},modifier=Modifier.fillMaxWidth(),minLines=if(multiline) 12 else 1,
        textStyle=LocalTextStyle.current.copy(fontFamily=if(code) FontFamily.Monospace else FontFamily.Default))
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
@Composable private fun SystemStatHeading(title: String,large: Boolean=false) {
    val key=title.trim().lowercase(java.util.Locale.ROOT)
    val icon=when {
        "cpu" in key || "processor" in key || "load" in key -> Icons.Default.Speed
        "mem" in key || "ram" in key || "swap" in key -> Icons.Default.Memory
        "disk" in key || "storage" in key || "filesystem" in key -> Icons.Default.Storage
        "network" in key || "interface" in key -> Icons.Default.NetworkCheck
        "uptime" in key || "time" in key -> Icons.Default.Schedule
        "system" in key || "host" in key || key == "os" -> Icons.Default.Dns
        else -> Icons.Default.Info
    }
    Row(Modifier.fillMaxWidth().padding(vertical=2.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        Icon(icon,null,Modifier.size(if(large) 20.dp else 18.dp),tint=MaterialTheme.colorScheme.primary)
        Text(title.trim().replaceFirstChar { it.uppercase() },modifier=Modifier.weight(1f),style=if(large) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleSmall,color=MaterialTheme.colorScheme.onSurface,softWrap=true)
    }
}
@Composable private fun SystemStatValue(value: String,modifier: Modifier=Modifier) {
    SelectionContainer(modifier) {
        Text(value,modifier=Modifier.fillMaxWidth(),style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurface,textAlign=androidx.compose.ui.text.style.TextAlign.End,softWrap=true)
    }
}
@Composable private fun StatTree(data: JSONObject,prefix: String="") {
    data.keys().asSequence().toList().forEach { key ->
        val value=data.opt(key)
        if(value is JSONObject) {
            SystemStatHeading(prefix+key)
            StatTree(value,"$prefix  ")
        } else BoxWithConstraints(Modifier.fillMaxWidth().padding(vertical=4.dp)) {
            val gap=12.dp
            val labelWidth=((maxWidth-gap).coerceAtLeast(0.dp))/3f
            val valueWidth=((maxWidth-gap).coerceAtLeast(0.dp))*2f/3f
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(gap),verticalAlignment=Alignment.Top) {
                Text(prefix+key,modifier=Modifier.width(labelWidth),style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant,softWrap=true)
                Box(Modifier.width(valueWidth)) { SystemStatValue(value?.toString() ?: "null") }
            }
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
    fun tr(id: Int,vararg args: Any): String=context.getString(id,*args)
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
        editor=Editor(if(job==null) R.string.main_new_job else R.string.main_edit_job,data) { changed ->
            vm.action(if(job==null) "job.create" else "job.edit",changed) { editor=null }
        }
    }
    fun openScript(script: JSONObject?=null) {
        if(script==null) editor=Editor(R.string.main_new_script,JSONObject(vm.draft.toString())) { changed ->
            vm.action("script.create",changed) { editor=null; vm.draft=obj("name" to "","description" to "","content" to "#!/bin/bash\n") }
        }
        else vm.work {
            val content=vm.request("script.content",obj("filename" to script.getString("filename"))).optString("data")
            editor=Editor(R.string.main_edit_script,obj("id" to script.getString("id"),"name" to script.optString("name"),"description" to script.optString("description"),"content" to content)) { changed ->
                vm.action("script.update",changed) { editor=null }
            }
        }
    }
    Scaffold(
        containerColor=MaterialTheme.colorScheme.background,
        topBar={ CronmasterAppBar(
            appearance=appearance,
            section=if(appearanceDialog) tr(R.string.settings) else if(vm.connected) stringResource(destinations[tab].title) else tr(R.string.section_access),
            onOpenSettings={openSettings()},
            onRefresh=if(vm.connected) { { vm.work { vm.refresh(); if(tab==2) vm.backups=vm.request("backups").rows(); vm.message=tr(R.string.data_updated) } } } else null,
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
            destinations.take(4).forEachIndexed { index,d -> NavigationBarItem(selected=tab==index,onClick={tab=index; lastTab=index; if(index==2) vm.loadBackups()},icon={Icon(d.icon,stringResource(d.title))},label=null) }
        } },
        floatingActionButton={ if(vm.connected && tab in 0..1) FloatingActionButton(onClick={if(enabled) { if(tab==0) openJob() else openScript() }}) { Icon(Icons.Default.Add,if(tab==0) tr(R.string.main_new_job) else tr(R.string.main_new_script)) } }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal=16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            if(vm.busy || vm.restoring) LinearProgressIndicator(Modifier.fillMaxWidth())
            if(vm.message.isNotBlank()) Surface(shape=MaterialTheme.shapes.medium,color=MaterialTheme.colorScheme.secondaryContainer) {
                Row(Modifier.fillMaxWidth().padding(start=12.dp),verticalAlignment=Alignment.CenterVertically) {
                    Text(vm.message,modifier=Modifier.weight(1f),style=MaterialTheme.typography.bodySmall)
                    ActionIcon(Icons.Default.Close,tr(R.string.discard_notice)) { vm.message="" }
                }
            }
            if(vm.restoring) Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center) { Text(tr(R.string.main_restore_access)) }
            else if(!vm.connected) Column(Modifier.verticalScroll(rememberScrollState()).padding(vertical=12.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
                Icon(Icons.Default.Terminal,null,modifier=Modifier.size(56.dp),tint=MaterialTheme.colorScheme.primary)
                Text(tr(R.string.main_access_title),style=MaterialTheme.typography.headlineMedium)
                Text(tr(R.string.main_access_hint),color=MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(vm.sessionName,{if(it.length<=64) vm.sessionName=it},label={Text(stringResource(R.string.session_name_optional))},singleLine=true,enabled=enabled,modifier=Modifier.fillMaxWidth())
                Field(tr(R.string.server_url),vm.url,onChange={vm.url=it})
                Field(tr(R.string.password),vm.password,true) { vm.password=it }
                Field(tr(R.string.api_key_alternative),vm.token,true) { vm.token=it }
                Row(verticalAlignment=Alignment.CenterVertically) { Checkbox(vm.allowHttp,{vm.allowHttp=it}); Text(tr(R.string.allow_http),style=MaterialTheme.typography.bodySmall) }
                if(vm.allowHttp) Text(tr(R.string.http_warning),color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)
                Button(onClick={vm.connect()},enabled=enabled && vm.url.isNotBlank(),modifier=Modifier.fillMaxWidth()) { Icon(Icons.AutoMirrored.Filled.Login,null); Spacer(Modifier.width(8.dp)); Text(tr(R.string.main_connect_save)) }
                TextButton(onClick={confirm(tr(R.string.clear_local_access),if(vm.activeServerId==null) tr(R.string.clear_all_access_confirmation) else tr(R.string.clear_selected_access_confirmation)) { vm.logout() }},enabled=enabled) { Text(tr(R.string.main_clear_saved_access)) }
            }
            else {
                if(vm.wrapperModified) Text(tr(R.string.main_wrapper_modified),color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)
                when(tab) {
                    0 -> {
                        OutlinedTextField(query,{query=it},label={Text(tr(R.string.main_search_jobs))},leadingIcon={Icon(Icons.Default.Search,null)},trailingIcon={ActionIcon(Icons.Default.FilterList,tr(R.string.main_filter_user)) { filters=!filters }},singleLine=true,modifier=Modifier.fillMaxWidth(),shape=MaterialTheme.shapes.large)
                        if(filters) Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                            FilterChip(selected=selectedUser.isEmpty(),onClick={selectedUser=""},label={Text(tr(R.string.main_all_users))})
                            vm.users.forEach { u -> FilterChip(selected=selectedUser==u,onClick={selectedUser=u},label={Text(u)}) }
                        }
                        val filtered=vm.jobs.filter { (selectedUser.isEmpty() || it.optString("user")==selectedUser) && it.toString().contains(query,true) }
                        val visible=if(nonZeroFirst) filtered.sortedBy { if(it.optJSONObject("logError")?.intOrNull("latestExitCode")==0) 1 else 0 } else filtered
                        Text(pluralStringResource(R.plurals.main_job_count,visible.size,visible.size),style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        LazyColumn(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(12.dp),contentPadding=PaddingValues(bottom=90.dp)) {
                            if(visible.isEmpty()) item { Text(tr(R.string.main_empty_jobs),modifier=Modifier.padding(16.dp)) }
                            items(visible,key={it.getString("id")}) { job ->
                                val id=job.getString("id"); val args=obj("id" to id); val paused=job.optBoolean("paused",job.optBoolean("isPaused"))
                                CronmasterCard(Modifier.fillMaxWidth(),shape=MaterialTheme.shapes.large) { Column(Modifier.padding(16.dp)) {
                                    Row(verticalAlignment=Alignment.CenterVertically) {
                                        Column(Modifier.weight(1f)) {
                                            Text(job.optString("comment").ifBlank { id },style=MaterialTheme.typography.titleMedium)
                                            Text(job.optString("user"),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        if(paused) Icon(Icons.Default.Pause,tr(R.string.main_paused_job),tint=MaterialTheme.colorScheme.secondary)
                                    }
                                    JobHealth(job)
                                    Text(if(vm.human) job.optString("humanSchedule",job.optString("schedule")) else job.optString("schedule"),style=MaterialTheme.typography.bodyMedium)
                                    if(!vm.minimal) {
                                        Spacer(Modifier.height(8.dp))
                                        Surface(shape=MaterialTheme.shapes.small,color=MaterialTheme.colorScheme.background) {
                                            SelectionContainer { Text(job.optString("editableCommand",job.optString("command")),fontFamily=FontFamily.Monospace,style=MaterialTheme.typography.bodySmall,modifier=Modifier.fillMaxWidth().padding(10.dp)) }
                                        }
                                        job.optJSONArray("nextRuns")?.let { a -> if(a.length()>0) Text(tr(R.string.main_next_run,a.optString(0)),style=MaterialTheme.typography.labelSmall,modifier=Modifier.padding(top=8.dp),color=MaterialTheme.colorScheme.onSurfaceVariant) }
                                    }
                                    Actions {
                                        ActionIcon(Icons.Default.PlayArrow,tr(R.string.main_run_job),enabled && !paused,MaterialTheme.colorScheme.primary) { confirm(tr(R.string.main_run_job),job.optString("command")) {
                                            vm.action("job.run",args) { result -> val run=result.optString("runId"); if(run.isNotBlank()) runId=run else output=result.optString("output",result.optString("message")) }
                                        } }
                                        ActionIcon(Icons.Default.Edit,tr(R.string.main_edit_job),enabled) { openJob(job) }
                                        ActionIcon(if(paused) Icons.Default.PlayArrow else Icons.Default.Pause,if(paused) tr(R.string.main_resume_job) else tr(R.string.main_pause_job),enabled) { confirm(tr(R.string.main_change_status),job.optString("comment",id)) { vm.action(if(paused) "job.resume" else "job.pause",args) } }
                                        ActionIcon(Icons.Default.History,tr(R.string.main_log_history),enabled) { vm.work { history=LogHistory(id,job.optString("comment",id),vm.request("logs",args).rows()) } }
                                        ActionIcon(Icons.Default.ContentCopy,tr(R.string.main_clone_job),enabled) {
                                            editor=Editor(R.string.main_clone_job,obj("comment" to tr(R.string.main_clone_name,job.optString("comment")))) { data -> data.put("id",id); vm.action("job.clone",data) { editor=null } }
                                        }
                                        ActionIcon(Icons.AutoMirrored.Filled.ReceiptLong,tr(R.string.main_toggle_logging),enabled) { confirm(tr(R.string.main_change_logging),id) { vm.action("job.logging",args) } }
                                        ActionIcon(Icons.Default.Backup,tr(R.string.main_backup_job),enabled) { confirm(tr(R.string.main_save_backup),id) { vm.action("job.backup",args) } }
                                        ActionIcon(Icons.Default.Delete,tr(R.string.main_delete_job),enabled,MaterialTheme.colorScheme.error) { confirm(tr(R.string.main_delete_job),job.toString(2)) { vm.action("job.delete",args) } }
                                    }
                                } }
                            }
                        }
                    }
                    1 -> {
                        OutlinedTextField(scriptQuery,{scriptQuery=it},label={Text(tr(R.string.main_search_scripts))},leadingIcon={Icon(Icons.Default.Search,null)},singleLine=true,modifier=Modifier.fillMaxWidth(),shape=MaterialTheme.shapes.large)
                        val visibleScripts=vm.scripts.filter { script ->
                            listOf("name","description","filename").any { script.optString(it).contains(scriptQuery,true) }
                        }
                        Text(pluralStringResource(R.plurals.main_script_count,visibleScripts.size,visibleScripts.size),style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        LazyColumn(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(12.dp),contentPadding=PaddingValues(bottom=90.dp)) {
                        if(visibleScripts.isEmpty()) item { Text(if(vm.scripts.isEmpty()) tr(R.string.main_first_script) else tr(R.string.main_empty_scripts),modifier=Modifier.padding(16.dp)) }
                        items(visibleScripts,key={it.getString("id")}) { script ->
                            val id=script.getString("id")
                            CronmasterCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) {
                                Text(script.optString("name"),style=MaterialTheme.typography.titleMedium)
                                Text(script.optString("description"),style=MaterialTheme.typography.bodyMedium)
                                Text(script.optString("filename"),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                                Actions {
                                    ActionIcon(Icons.Default.Edit,tr(R.string.main_edit_script),enabled) { openScript(script) }
                                    ActionIcon(Icons.Default.Schedule,tr(R.string.main_schedule_script),enabled) { openJob(script=script) }
                                    ActionIcon(Icons.Default.ContentCopy,tr(R.string.main_copy_content),enabled) { vm.work { clipboard.setText(AnnotatedString(vm.request("script.content",obj("filename" to script.getString("filename"))).optString("data"))); vm.message=tr(R.string.main_script_copied) } }
                                    ActionIcon(Icons.Default.FileCopy,tr(R.string.main_clone_script),enabled) { editor=Editor(R.string.main_clone_script,obj("name" to tr(R.string.main_clone_name,script.optString("name")))) { d -> d.put("id",id); vm.action("script.clone",d) { editor=null } } }
                                    ActionIcon(Icons.Default.Delete,tr(R.string.main_delete_script),enabled,MaterialTheme.colorScheme.error) { confirm(tr(R.string.main_delete_script),tr(R.string.main_delete_script_detail,script.optString("name"))) { vm.action("script.delete",obj("id" to id)) } }
                                }
                            } }
                        }
                    }
                    }
                    2 -> {
                        Actions {
                            ActionIcon(Icons.Default.Backup,tr(R.string.main_backup_all),enabled) { confirm(tr(R.string.main_full_backup),tr(R.string.main_backup_all_detail)) { vm.action("backup.all") } }
                            ActionIcon(Icons.Default.Restore,tr(R.string.main_restore_backups),enabled) { confirm(tr(R.string.main_restore_all),tr(R.string.main_duplicate_warning)) { vm.action("restore.all") } }
                        }
                        LazyColumn(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(12.dp),contentPadding=PaddingValues(bottom=16.dp)) {
                            if(vm.backups.isEmpty()) item { Text(tr(R.string.main_empty_backups)) }
                            items(vm.backups) { b -> CronmasterCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) {
                                Text(b.optJSONObject("job")?.optString("comment").orEmpty().ifBlank { b.optString("filename") },style=MaterialTheme.typography.titleMedium)
                                Text(b.optString("filename"),style=MaterialTheme.typography.bodySmall)
                                Text(b.optString("backedUpAt"),style=MaterialTheme.typography.labelSmall)
                                Actions {
                                    ActionIcon(Icons.Default.Restore,tr(R.string.main_restore_backup),enabled) { confirm(tr(R.string.main_restore_backup),tr(R.string.main_restore_backup_detail,b.optString("filename"))) { vm.action("backup.restore",obj("filename" to b.getString("filename"))) } }
                                    ActionIcon(Icons.Default.Delete,tr(R.string.main_delete_backup),enabled,MaterialTheme.colorScheme.error) { confirm(tr(R.string.main_delete_backup),b.optString("filename")) { vm.action("backup.delete",obj("filename" to b.getString("filename"))) } }
                                }
                            } } }
                        }
                    }
                    3 -> LazyColumn(Modifier.weight(1f).padding(top=8.dp),verticalArrangement=Arrangement.spacedBy(12.dp),contentPadding=PaddingValues(bottom=16.dp)) {
                        items(vm.stats.keys().asSequence().toList()) { key -> CronmasterCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) {
                            SystemStatHeading(key,large=true)
                            Spacer(Modifier.height(6.dp))
                            HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
                            Spacer(Modifier.height(6.dp))
                            val value=vm.stats.opt(key)
                            if(value is JSONObject) StatTree(value) else BoxWithConstraints(Modifier.fillMaxWidth()) {
                                val valueWidth=maxWidth*2f/3f
                                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End) {
                                    Box(Modifier.width(valueWidth)) { SystemStatValue(value?.toString() ?: "null") }
                                }
                            }
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
        WideModal(stringResource(current.title),{editor=null}) {
            if(data.has("schedule")) {
                Text(tr(R.string.main_scheduling),style=MaterialTheme.typography.titleSmall)
                Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    listOf(R.string.main_every_five_minutes to "*/5 * * * *",R.string.main_hourly to "0 * * * *",R.string.main_daily to "0 2 * * *",R.string.main_weekly to "0 2 * * 0",R.string.main_at_reboot to "@reboot").forEach { (name,cron) -> AssistChip(onClick={data=JSONObject(data.toString()).put("schedule",cron)},label={Text(stringResource(name))}) }
                }
                Text(tr(R.string.main_manual_hint),style=MaterialTheme.typography.bodySmall)
                Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected=data.optString("selectedScriptId").isBlank(),onClick={data=JSONObject(data.toString()).put("selectedScriptId","")},label={Text(tr(R.string.main_manual))})
                    vm.scripts.forEach { s -> FilterChip(selected=data.optString("selectedScriptId")==s.getString("id"),onClick={data=JSONObject(data.toString()).put("selectedScriptId",s.getString("id"))},label={Text(s.optString("name"))}) }
                }
            }
            val labels=mapOf("schedule" to tr(R.string.main_field_schedule),"command" to tr(R.string.main_field_command),"comment" to tr(R.string.main_field_comment),"name" to tr(R.string.main_field_name),"description" to tr(R.string.main_field_description),"content" to tr(R.string.main_field_content),"user" to tr(R.string.main_field_user))
            current.initial.keys().asSequence().toList().filter { it !in listOf("id","selectedScriptId") }.forEach { field ->
                when(field) {
                    "logsEnabled" -> Row(verticalAlignment=Alignment.CenterVertically) { Text(tr(R.string.main_save_logs),modifier=Modifier.weight(1f)); Switch(data.optBoolean(field),{data=JSONObject(data.toString()).put(field,it)}) }
                    "user" -> if(!current.initial.isNull("id")) Text(tr(R.string.main_user_value,data.optString("user"))) else {
                        Text(tr(R.string.main_field_user))
                        Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) { vm.users.forEach { u -> FilterChip(selected=data.optString("user")==u,onClick={data=JSONObject(data.toString()).put("user",u)},label={Text(u)}) } }
                    }
                    else -> Field(labels[field] ?: field,data.optString(field,""),multiline=field=="content",code=field in listOf("content","command","schedule")) { value -> data=JSONObject(data.toString()).put(field,value); if(current.title==R.string.main_new_script) vm.draft=JSONObject(data.toString()) }
                }
            }
            if(data.has("content")) {
                TextButton(onClick={vm.loadSnippets()},enabled=enabled) { Icon(Icons.Default.DataObject,null); Spacer(Modifier.width(8.dp)); Text(tr(R.string.main_snippets)) }
                vm.snippets.forEach { snippet -> AssistChip(onClick={data=JSONObject(data.toString()).put("content",data.optString("content")+"\n"+snippet.optString("template")); if(current.title==R.string.main_new_script) vm.draft=JSONObject(data.toString())},label={Text(snippet.optString("title",snippet.optString("name",snippet.optString("id"))))}) }
            }
            Button(onClick={current.save(JSONObject(data.toString()))},enabled=enabled,modifier=Modifier.fillMaxWidth()) { Icon(Icons.Default.Save,null); Spacer(Modifier.width(8.dp)); Text(tr(R.string.main_save_changes)) }
        }
    } }
    confirmation?.let { c -> AlertDialog(onDismissRequest={confirmation=null},title={Text(c.title)},text={Text(c.detail)},confirmButton={TextButton(onClick={confirmation=null;c.run()},enabled=enabled) { Text(tr(R.string.main_confirm)) }},dismissButton={TextButton(onClick={confirmation=null}) { Text(tr(R.string.cancel)) }}) }
    history?.let { h -> WideModal(tr(R.string.main_logs_title,h.name),{history=null}) {
        Actions { ActionIcon(Icons.Default.DeleteSweep,tr(R.string.main_delete_all_logs),enabled,MaterialTheme.colorScheme.error) { confirm(tr(R.string.main_delete_all_logs),h.id) { vm.action("logs.deleteAll",obj("id" to h.id)) { history=null } } } }
        if(h.rows.isEmpty()) Text(tr(R.string.main_empty_logs))
        h.rows.forEach { log -> CronmasterCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) {
            Text(log.optString("filename"),style=MaterialTheme.typography.titleSmall)
            Text(tr(R.string.main_log_metadata,log.optLong("size"),if(log.isNull("exitCode")) "—" else log.optString("exitCode","—")),style=MaterialTheme.typography.bodySmall)
            val args=obj("id" to h.id,"filename" to log.getString("filename"))
            Actions {
                ActionIcon(Icons.Default.Terminal,tr(R.string.main_open_log),enabled) { vm.work { output=vm.request("log.content",args).optString("data") } }
                ActionIcon(Icons.Default.Delete,tr(R.string.main_delete_log),enabled,MaterialTheme.colorScheme.error) { confirm(tr(R.string.main_delete_log),log.optString("filename")) { vm.action("log.delete",args) { history=null } } }
            }
        } } }
    } }
    output?.let { text -> TerminalDialog(tr(R.string.main_execution_log),text,tr(R.string.main_saved_output),close={output=null}) }
    runId?.let { id -> SessionLiveTerminal(vm,id) { runId=null } }
    if(appearanceDialog) UnifiedSettingsDialog(vm,appearance,themes,display,close={appearanceDialog=false},onAddServer={addServerDialog=true})
    if(serverSwitcher && vm.serverProfiles.size>1) ServerSwitcherDialog(vm) { serverSwitcher=false }
    if(addServerDialog) AddServerDialog(vm) { addServerDialog=false }
}

