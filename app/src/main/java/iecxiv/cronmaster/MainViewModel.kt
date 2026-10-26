package iecxiv.cronmaster

import android.app.Application
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.sse.EventSource
import org.json.JSONObject
import java.net.URLEncoder

class MainViewModel(app: Application): AndroidViewModel(app) {
    private val vault=SecureStore(app)
    private val preferences=app.getSharedPreferences("appearance",0)
    private val refreshMutex=Mutex()
    private var api: Api?=null
    private var stream: EventSource?=null
    var url by mutableStateOf("")
    var token by mutableStateOf("")
    var password by mutableStateOf("")
    var allowHttp by mutableStateOf(false)
    var connected by mutableStateOf(false)
    var restoring by mutableStateOf(true)
    var busy by mutableStateOf(false)
    var message by mutableStateOf("")
    var eventState by mutableStateOf("Conectando")
    var dark by mutableStateOf(preferences.getBoolean("dark",true))
    var minimal by mutableStateOf(preferences.getBoolean("minimal",false))
    var human by mutableStateOf(preferences.getBoolean("human",true))
    var jobs by mutableStateOf(emptyList<JSONObject>())
    var scripts by mutableStateOf(emptyList<JSONObject>())
    var backups by mutableStateOf(emptyList<JSONObject>())
    var snippets by mutableStateOf(emptyList<JSONObject>())
    var users by mutableStateOf(emptyList<String>())
    var stats by mutableStateOf(JSONObject())
    var wrapperModified by mutableStateOf(false)
    var draft by mutableStateOf(obj("name" to "","description" to "","content" to "#!/bin/bash\n"))
    init {
        viewModelScope.launch {
            try {
                val saved=withContext(Dispatchers.IO) { vault.load() }
                if(saved!=null) {
                    url=saved.optString("url"); allowHttp=saved.optBoolean("http")
                    api=createApi(saved)
                    // Conserva el acceso lógico incluso si la red no está disponible.
                    connected=true
                    try { refresh() }
                    catch(e: CancellationException) { throw e }
                    catch(e: Exception) { showError(e) }
                }
            } catch(e: CancellationException) { throw e }
            catch(e: Exception) { message="No se pudo abrir el acceso cifrado: ${e.message}. Puedes volver a identificarte o borrar el acceso local." }
            finally { restoring=false }
        }
    }
    private fun createApi(config: JSONObject)=Api(config,vault) { text -> viewModelScope.launch { message=text } }.also { if(config.has("cookies")) it.enablePersistence() }
    fun saveAppearance() { preferences.edit().putBoolean("dark",dark).putBoolean("minimal",minimal).putBoolean("human",human).apply() }
    private fun showError(e: Exception) {
        message=if(e is HttpFailure && e.code==401) "El servidor rechazó el acceso guardado. Revisa la contraseña o API key; no se han borrado tus credenciales." else (e.message ?: "Error de conexión")
    }
    fun work(block: suspend () -> Unit) {
        if(busy || restoring) return
        viewModelScope.launch {
            busy=true
            try { block() }
            catch(e: CancellationException) { throw e }
            catch(e: Exception) { showError(e) }
            finally { busy=false }
        }
    }
    fun connect()=work {
        check(token.isNotBlank() || password.isNotBlank()) { "Introduce contraseña o API key" }
        val config=obj("url" to url.trim().trimEnd('/'),"token" to token,"password" to password,"http" to allowHttp)
        val candidate=createApi(config)
        try {
            check(candidate.native("capabilities").optInt("version")==1) { "Extensión api/native incompatible" }
            config.put("cookies",candidate.cookieSnapshot())
            withContext(Dispatchers.IO) { vault.save(config) }
            candidate.enablePersistence()
        } catch(e: Exception) { candidate.shutdown(); throw e }
        api?.shutdown(); api=candidate; connected=true
        password=""; token=""
        refresh()
        message="Acceso guardado de forma cifrada"
    }
    suspend fun request(op: String,args: JSONObject=JSONObject()): JSONObject = checkNotNull(api).native(op,args)
    suspend fun refresh(): Unit=refreshMutex.withLock {
        val current=api ?: return@withLock
        val newJobs=current.native("jobs").rows()
        val newScripts=current.call("api/scripts").rows()
        val u=current.native("users").optJSONArray("data")
        val newUsers=u?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: emptyList()
        val newStats=safely { current.call("api/system-stats") }
        val newWrapper=safely { current.call("api/system/wrapper-check") }
        if(api!==current) return@withLock
        jobs=newJobs; scripts=newScripts; users=newUsers
        newStats.onSuccess { stats=it }
        newWrapper.onSuccess { wrapperModified=it.optBoolean("modified") }
        Unit
    }
    private suspend fun refreshQuietly() {
        if(refreshMutex.isLocked || busy) return
        try { refresh() }
        catch(e: CancellationException) { throw e }
        catch(e: Exception) { showError(e) }
    }
    suspend fun foregroundLoop(): Unit=coroutineScope {
        val current=api ?: return@coroutineScope
        var source: EventSource?=null
        var tick=0
        fun subscribe() {
            source?.cancel()
            source=current.events(
                { launch { refreshQuietly() } },
                { text -> launch { eventState=text } }
            )
            stream=source
        }
        try {
            subscribe()
            while(isActive) {
                refreshQuietly()
                delay(10000)
                if(++tick%6==0) subscribe()
            }
        } finally { source?.cancel(); if(stream===source) stream=null }
    }
    fun action(op: String,args: JSONObject=JSONObject(),done: (JSONObject) -> Unit={})=work {
        val result=request(op,args)
        message=result.optString("message","Operación completada")
        done(result)
        safely { refresh() }.onFailure { message="Operación completada; no se pudieron refrescar los datos: ${it.message}" }
        if(op.startsWith("backup.") || op=="restore.all" || op=="job.backup") {
            safely { request("backups").rows() }.onSuccess { backups=it }
        }
    }
    fun loadBackups()=work { backups=request("backups").rows() }
    fun loadSnippets()=work { snippets=request("snippets").rows() }
    suspend fun live(id: String,offset: Long): JSONObject = checkNotNull(api).call("api/logs/stream?runId=${URLEncoder.encode(id,"UTF-8")}&offset=$offset&maxLines=1000")
    fun logout()=work {
        val old=api
        // Primero impide que una respuesta tardía vuelva a guardar cookies.
        old?.disablePersistence(); old?.cancelCalls(); stream?.cancel(); stream=null
        withContext(Dispatchers.IO) { vault.clear() }
        api=null; connected=false; token=""; password=""; url=""
        jobs=emptyList(); scripts=emptyList(); backups=emptyList(); snippets=emptyList(); stats=JSONObject(); users=emptyList()
        draft=obj("name" to "","description" to "","content" to "#!/bin/bash\n")
        message="Acceso eliminado del dispositivo"
        // Invalida la cookie en el servidor si hay red. No impide el logout local.
        try { if(old!=null) withTimeoutOrNull(5000) { safely { old.logoutRemote() } } }
        finally { old?.shutdown() }
    }
    fun editConnection()=work {
        val saved=withContext(Dispatchers.IO) { vault.load() }
        stream?.cancel(); api?.shutdown(); api=null; connected=false
        url=saved?.optString("url") ?: url
        allowHttp=saved?.optBoolean("http") ?: allowHttp
        token=""; password=""
        message="Introduce el nuevo acceso. El anterior se conserva cifrado hasta que guardes uno válido o cierres sesión."
    }
    override fun onCleared() { stream?.cancel(); api?.shutdown(); super.onCleared() }
}
