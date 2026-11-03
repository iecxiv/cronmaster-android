package iecxiv.cronmaster

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

@Composable
internal fun SessionLiveTerminal(vm: MainViewModel,id: String,close: () -> Unit) {
    val owner=LocalLifecycleOwner.current
    val context=LocalContext.current
    val connecting=stringResource(R.string.connecting_ellipsis)
    val epoch=remember(vm,id) { vm.sessionEpoch }
    var content by remember(id) { mutableStateOf("") }
    var status by remember(id,connecting) { mutableStateOf(connecting) }
    var error by remember(id) { mutableStateOf("") }
    var finished by remember(id) { mutableStateOf(false) }
    LaunchedEffect(id,owner,epoch,context) {
        var offset=0L
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while(!finished) {
                try {
                    val result=vm.live(id,offset,epoch)
                    content=if(offset==0L) result.optString("content") else content+result.optString("newContent")
                    if(content.length>250000) content=context.getString(R.string.log_view_truncated)+"\n"+content.takeLast(240000)
                    offset=result.optLong("fileSize",offset)
                    finished=result.optBoolean("isComplete")
                    val exit=if(result.isNull("exitCode")) "—" else result.optString("exitCode","—")
                    status=context.getString(R.string.execution_status_line,result.optString("status"),exit,id)
                    error=""
                } catch(e: CancellationException) { throw e }
                catch(e: Exception) { error=e.message ?: context.getString(R.string.error_read_log) }
                if(!finished) delay(1500)
            }
        }
    }
    TerminalDialog(stringResource(R.string.live_execution),content,status,error,!finished,close)
}
