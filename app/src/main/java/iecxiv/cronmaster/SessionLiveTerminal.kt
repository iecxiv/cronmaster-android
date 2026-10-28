package iecxiv.cronmaster

import androidx.compose.runtime.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

@Composable
internal fun SessionLiveTerminal(vm: MainViewModel,id: String,close: () -> Unit) {
    val owner=LocalLifecycleOwner.current
    val epoch=remember(vm,id) { vm.sessionEpoch }
    var content by remember(id) { mutableStateOf("") }
    var status by remember(id) { mutableStateOf("Conectando…") }
    var error by remember(id) { mutableStateOf("") }
    var finished by remember(id) { mutableStateOf(false) }
    LaunchedEffect(id,owner,epoch) {
        var offset=0L
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while(!finished) {
                try {
                    val result=vm.live(id,offset,epoch)
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
