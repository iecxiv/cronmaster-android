package iecxiv.cronmaster

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

private val Bg=Color(0xFF080D14)
private val Bar=Color(0xFF121C2A)
private val Ink=Color(0xFFD8E3F1)
private val Muted=Color(0xFF8190A6)
private val Green=Color(0xFF77D6A2)
private val Yellow=Color(0xFFF1CE77)
private val Red=Color(0xFFFF8496)
private val Blue=Color(0xFF89BFFF)
private val Osc=Regex("\u001B\\][^\u0007\u001B]*(?:\u0007|\u001B\\\\)")
private val Csi=Regex("\u001B\\[[0-?]*[ -/]*[@-~]")
private val ShortEscape=Regex("\u001B[@-_]")
private val Errors=Regex("\\b(error|failed|failure|fatal|exception)\\b",RegexOption.IGNORE_CASE)
private val Warnings=Regex("\\b(warn|warning|skipped|aviso)\\b",RegexOption.IGNORE_CASE)
private val Success=Regex("\\b(ok|success|successful|completed|done)\\b",RegexOption.IGNORE_CASE)
private fun clean(text: String)=text.replace(Osc,"").replace(Csi,"").replace(ShortEscape,"").replace("\r\n","\n").replace("\r","\n").replace("\u0000","")
private fun lineColor(line: String): Color=when {
    Errors.containsMatchIn(line) -> Red
    Warnings.containsMatchIn(line) -> Yellow
    Success.containsMatchIn(line) -> Green
    line.trimStart().startsWith("--- [") -> Blue
    else -> Ink
}
@Composable fun TerminalDialog(title: String,text: String,status: String="",error: String="",live: Boolean=false,close: () -> Unit) {
    val clipboard=LocalClipboardManager.current
    var size by remember { mutableStateOf(12) }
    var follow by remember { mutableStateOf(live) }
    var wrap by remember { mutableStateOf(false) }
    val list=rememberLazyListState()
    val horizontal=rememberScrollState()
    val cleaned=remember(text) { clean(text) }
    val lines=remember(cleaned) { if(cleaned.isEmpty()) emptyList() else cleaned.split('\n') }
    val longest=remember(lines) { lines.maxOfOrNull { it.length } ?: 0 }
    LaunchedEffect(cleaned.length,lines.size,follow,wrap,size) {
        if(follow && lines.isNotEmpty()) list.scrollToItem(lines.lastIndex,Int.MAX_VALUE)
    }
    Dialog(onDismissRequest=close,properties=DialogProperties(usePlatformDefaultWidth=false)) {
        Surface(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing),color=Bg,contentColor=Ink) {
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().background(Bar).padding(start=16.dp),verticalAlignment=Alignment.CenterVertically) {
                    Text("●",color=if(live) Green else Blue)
                    Column(Modifier.weight(1f).padding(12.dp)) {
                        Text(title,style=MaterialTheme.typography.titleMedium)
                        Text(status,color=Muted,fontFamily=FontFamily.Monospace,fontSize=10.sp,maxLines=3)
                    }
                    IconButton(onClick=close) { Icon(Icons.Default.Close,"Cerrar terminal",tint=Ink) }
                }
                Row(Modifier.fillMaxWidth().background(Bar).horizontalScroll(rememberScrollState()),verticalAlignment=Alignment.CenterVertically) {
                    IconButton(onClick={clipboard.setText(AnnotatedString(text))}) { Icon(Icons.Default.ContentCopy,"Copiar salida",tint=Blue) }
                    IconButton(onClick={size=(size-1).coerceAtLeast(9)}) { Icon(Icons.Default.Remove,"Reducir texto",tint=Blue) }
                    Text("$size",color=Muted,fontSize=11.sp)
                    IconButton(onClick={size=(size+1).coerceAtMost(22)}) { Icon(Icons.Default.Add,"Aumentar texto",tint=Blue) }
                    IconToggleButton(checked=follow,onCheckedChange={follow=it}) { Icon(Icons.Default.VerticalAlignBottom,"Seguir nuevas líneas",tint=if(follow) Green else Muted) }
                    IconToggleButton(checked=wrap,onCheckedChange={wrap=it}) { Icon(Icons.Default.WrapText,"Ajustar líneas",tint=if(wrap) Green else Muted) }
                }
                if(error.isNotBlank()) Text(error,color=Red,modifier=Modifier.fillMaxWidth().background(Red.copy(alpha=.1f)).padding(10.dp),fontSize=12.sp)
                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                    val contentWidth=if(wrap) maxWidth else maxOf(maxWidth,(longest*size*.85f+100).dp)
                    if(lines.isEmpty()) Text(if(live) "Esperando salida…" else "Log vacío",color=Muted,modifier=Modifier.padding(16.dp),fontFamily=FontFamily.Monospace)
                    else Box(Modifier.fillMaxSize().horizontalScroll(horizontal)) {
                        SelectionContainer {
                            LazyColumn(state=list,modifier=Modifier.width(contentWidth).fillMaxHeight(),contentPadding=PaddingValues(vertical=12.dp)) {
                                itemsIndexed(lines) { index,line ->
                                    Row(Modifier.fillMaxWidth().padding(horizontal=12.dp,vertical=1.dp)) {
                                        Text("${index+1}",color=Muted.copy(alpha=.55f),fontSize=size.sp,fontFamily=FontFamily.Monospace,modifier=Modifier.width(54.dp))
                                        Text(line.ifEmpty { " " },color=lineColor(line),fontSize=size.sp,lineHeight=(size+5).sp,fontFamily=FontFamily.Monospace,softWrap=wrap,modifier=Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().background(Bar).padding(12.dp),horizontalArrangement=Arrangement.SpaceBetween) {
                    Text("${lines.size} líneas",fontSize=11.sp,color=Muted)
                    Text(if(follow) "Siguiendo salida" else "Lectura libre",fontSize=11.sp,color=if(follow) Green else Muted)
                }
            }
        }
    }
}
@Composable fun LiveTerminal(vm: MainViewModel,id: String,close: () -> Unit) {
    val owner=LocalLifecycleOwner.current
    var content by remember(id) { mutableStateOf("") }
    var status by remember(id) { mutableStateOf("Conectando…") }
    var error by remember(id) { mutableStateOf("") }
    var finished by remember(id) { mutableStateOf(false) }
    LaunchedEffect(id,owner) {
        var offset=0L
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while(!finished) {
                try {
                    val result=vm.live(id,offset)
                    content=if(offset==0L) result.optString("content") else content+result.optString("newContent")
                    if(content.length>250000) content="[Vista recortada; el log completo sigue en el servidor]\n"+content.takeLast(240000)
                    offset=result.optLong("fileSize",offset)
                    finished=result.optBoolean("isComplete")
                    val exit=if(result.isNull("exitCode")) "—" else result.optString("exitCode","—")
                    status="${result.optString("status")} · exit $exit · $id"
                    error=""
                } catch(e: CancellationException) { throw e }
                catch(e: Exception) { error=e.message ?: "No se pudo leer el log" }
                if(!finished) delay(1500)
            }
        }
    }
    TerminalDialog("Ejecución en vivo",content,status,error,!finished,close)
}
