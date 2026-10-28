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
import java.util.UUID

class MainViewModel(app: Application): AndroidViewModel(app) {
    private val vault=SecureStore(app)
    private val preferences=app.getSharedPreferences("appearance",0)
    private val refreshMutex=Mutex()
    private var api: Api?=null
    private var stream: EventSource?=null
    private var editingServerId: String?=null
    internal var serverProfiles by mutableStateOf(emptyList<ServerProfile>())
        private set
    internal var activeServerId by mutableStateOf<String?>(null)
        private set
    internal var sessionEpoch by mutableStateOf(0L)
        private set
    internal val activeServerName: String get()=serverProfiles.firstOrNull { it.id==activeServerId }?.name.orEmpty()
    var url by mutableStateOf("")
    var token by mutableStateOf("")
    var password by mutableStateOf("")
    var allowHttp by mutableStateOf(false)
    var connected by mutableStateOf(false)
    var restoring by mutableStateOf(true)
    var busy by mutableStateOf(false)
    var message by mutableStateOf("")
    var eventState by mutableStateOf("Conectando")
    internal var connectionStatus by mutableStateOf(ConnectionStatus.UNKNOWN)
        private set
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
    private data class Saved(val profiles: ServerProfiles, val config: JSONObject?)
    private fun saved(): Saved {
        val snapshot=vault.snapshot()
        return Saved(snapshot,snapshot.activeId?.let { checkNotNull(vault.forProfile(it).load()) { "El acceso guardado no está disponible" } })
    }
    init {
        viewModelScope.launch {
            try {
                val stored=withContext(Dispatchers.IO) { saved() }
                serverProfiles=stored.profiles.items
                val id=stored.profiles.activeId
                val config=stored.config
                if(id!=null && config!=null) {
                    attach(config,id,createApi(config,id),stored.profiles)
                    refreshAfterChange("")
                }
            } catch(e: CancellationException) { throw e }
            catch(e: Exception) { message="No se pudo abrir el acceso cifrado: ${e.message}. Puedes volver a identificarte o borrar el acceso local." }
            finally { restoring=false }
        }
    }
    private fun createApi(config: JSONObject,id: String,persist: Boolean=true): Api {
        lateinit var client: Api
        client=Api(config,vault.forProfile(id)) { text -> viewModelScope.launch { if(api===client) message=text } }
        if(persist && config.has("cookies")) client.enablePersistence()
        return client
    }
    private fun closeApi(shutdown: Boolean=true): Api? {
        val old=api
        api=null
        old?.disablePersistence(); stream?.cancel(); stream=null; old?.cancelCalls()
        if(shutdown) old?.shutdown()
        connectionStatus=ConnectionStatus.UNKNOWN
        eventState="Sin estado verificable"
        return old
    }
    private fun clearData() {
        jobs=emptyList(); scripts=emptyList(); backups=emptyList(); snippets=emptyList(); users=emptyList(); stats=JSONObject()
        wrapperModified=false
        draft=obj("name" to "","description" to "","content" to "#!/bin/bash\n")
        message=""
    }
    private fun attach(config: JSONObject,id: String,client: Api,snapshot: ServerProfiles) {
        closeApi()
        clearData()
        serverProfiles=snapshot.items; activeServerId=id; editingServerId=null
        api=client; url=config.optString("url"); allowHttp=config.optBoolean("http")
        password=""; token=""; connected=true
        connectionStatus=ConnectionStatus.CONNECTING
        sessionEpoch++
    }
    private fun disconnected(snapshot: ServerProfiles=ServerProfiles(null,emptyList())) {
        closeApi(); clearData()
        serverProfiles=snapshot.items; activeServerId=snapshot.activeId; editingServerId=null
        connected=false; url=""; token=""; password=""
        sessionEpoch++
    }
    fun saveAppearance() { preferences.edit().putBoolean("dark",dark).putBoolean("minimal",minimal).putBoolean("human",human).apply() }
    private fun showError(e: Exception) {
        message=if(e is HttpFailure && e.code==401) "El servidor rechazó el acceso guardado. Revisa la contraseña o API key; no se han borrado tus credenciales." else (e.message ?: "Error de conexión")
    }
    fun work(block: suspend () -> Unit) {
        if(busy || restoring) return
        busy=true
        viewModelScope.launch {
            try { block() }
            catch(e: CancellationException) { throw e }
            catch(e: Exception) { showError(e) }
            finally { busy=false }
        }
    }
    private suspend fun registerServer(id: String?,name: String,serverUrl: String,apiKey: String,secret: String,http: Boolean,done: () -> Unit) {
        check(apiKey.isNotBlank() || secret.isNotBlank()) { "Introduce contraseña o API key" }
        require(name.trim().length<=64) { "El nombre no puede superar 64 caracteres" }
        val targetId=id ?: UUID.randomUUID().toString()
        val config=obj("url" to serverUrl.trim().trimEnd('/'),"token" to apiKey,"password" to secret,"http" to http)
        val candidate=createApi(config,targetId,false)
        var adopted=false
        try {
            val capabilities=withTimeoutOrNull(30000) { candidate.native("capabilities") }
                ?: error("Se agotó el tiempo para validar el servidor")
            check(capabilities.optInt("version")==1) { "Extensión api/native incompatible" }
            config.put("cookies",candidate.cookieSnapshot())
            val snapshot=withContext(Dispatchers.IO) {
                vault.forProfile(targetId).saveProfile(config,name)
                vault.snapshot()
            }
            candidate.enablePersistence()
            attach(config,targetId,candidate,snapshot)
            adopted=true
            done()
            refreshAfterChange("Servidor guardado de forma cifrada")
        } finally { if(!adopted) candidate.shutdown() }
    }
    fun connect()=work {
        val profile=serverProfiles.firstOrNull { it.id==editingServerId }
        val name=profile?.let { if(it.name==it.url) "" else it.name }.orEmpty()
        registerServer(editingServerId,name,url,token,password,allowHttp) {}
    }
    internal fun addServer(name: String,serverUrl: String,apiKey: String,secret: String,http: Boolean,done: () -> Unit,failed: (String) -> Unit)=work {
        try { registerServer(null,name,serverUrl,apiKey,secret,http,done) }
        catch(e: CancellationException) { throw e }
        catch(e: Exception) { showError(e); failed(message) }
    }
    internal fun switchServer(id: String,done: () -> Unit={})=work {
        if(id==activeServerId && api!=null) { done(); return@work }
        val config=withContext(Dispatchers.IO) { checkNotNull(vault.forProfile(id).load()) { "El servidor ya no está guardado" } }
        val candidate=createApi(config,id)
        var adopted=false
        try {
            val snapshot=withContext(Dispatchers.IO) { vault.activate(id); vault.snapshot() }
            attach(config,id,candidate,snapshot)
            adopted=true
            done()
            refreshAfterChange("Servidor seleccionado: $activeServerName")
        } finally { if(!adopted) candidate.shutdown() }
    }
    private fun ensureCurrent(client: Api) {
        if(api!==client) throw CancellationException("El servidor activo ha cambiado")
    }
    suspend fun request(op: String,args: JSONObject=JSONObject()): JSONObject {
        val current=checkNotNull(api) { "No hay un servidor activo" }
        val result=current.native(op,args)
        ensureCurrent(current)
        return result
    }
    suspend fun refresh() {
        val current=api ?: return
        refreshFor(current)
    }
    private suspend fun refreshFor(current: Api): Unit=refreshMutex.withLock {
        ensureCurrent(current)
        val newJobs=current.native("jobs").rows()
        ensureCurrent(current)
        val newScripts=current.call("api/scripts").rows()
        ensureCurrent(current)
        val u=current.native("users").optJSONArray("data")
        val newUsers=u?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: emptyList()
        ensureCurrent(current)
        val newStats=safely { current.call("api/system-stats") }
        ensureCurrent(current)
        val newWrapper=safely { current.call("api/system/wrapper-check") }
        ensureCurrent(current)
        jobs=newJobs; scripts=newScripts; users=newUsers
        newStats.onSuccess { stats=it }
        newWrapper.onSuccess { wrapperModified=it.optBoolean("modified") }
        Unit
    }
    private suspend fun refreshAfterChange(success: String) {
        val current=api ?: return
        try { refreshFor(current); if(success.isNotBlank()) message=success }
        catch(e: CancellationException) { throw e }
        catch(e: Exception) { if(api===current) showError(e) }
    }
    private suspend fun refreshQuietly(current: Api) {
        if(api!==current || refreshMutex.isLocked || busy) return
        try { refreshFor(current) }
        catch(e: CancellationException) { throw e }
        catch(e: Exception) { if(api===current) showError(e) }
    }
    suspend fun foregroundLoop(): Unit=coroutineScope {
        val current=api ?: return@coroutineScope
        var source: EventSource?=null
        var tick=0
        var generation=0
        fun subscribe() {
            if(api!==current) return
            val subscription=++generation
            connectionStatus=ConnectionStatus.CONNECTING
            source?.cancel()
            source=current.events(
                { launch { if(api===current && generation==subscription && stream===source) refreshQuietly(current) } },
                { text -> launch {
                    if(api===current && generation==subscription && stream===source) {
                        eventState=text
                        connectionStatus=if(text=="Conexión en vivo") ConnectionStatus.CONNECTED else ConnectionStatus.UNKNOWN
                    }
                } }
            )
            stream=source
        }
        try {
            subscribe()
            while(isActive && api===current) {
                refreshQuietly(current)
                delay(10000)
                if(api!==current) break
                if(++tick%6==0) subscribe()
            }
        } finally {
            ++generation; source?.cancel()
            if(stream===source) { stream=null; connectionStatus=ConnectionStatus.UNKNOWN }
        }
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
    suspend fun live(id: String,offset: Long,expectedEpoch: Long=sessionEpoch): JSONObject {
        if(expectedEpoch!=sessionEpoch) throw CancellationException("La terminal pertenece a otra sesión")
        val current=checkNotNull(api) { "No hay un servidor activo" }
        val result=current.call("api/logs/stream?runId=${URLEncoder.encode(id,"UTF-8")}&offset=$offset&maxLines=1000")
        ensureCurrent(current)
        if(expectedEpoch!=sessionEpoch) throw CancellationException("La sesión ha cambiado")
        return result
    }
    fun logout()=work {
        val id=activeServerId
        if(id==null) {
            check(serverProfiles.isEmpty()) { "Selecciona el servidor que quieres cerrar" }
            withContext(Dispatchers.IO) { vault.clear() }
            disconnected()
            message="Acceso eliminado del dispositivo"
            return@work
        }
        val old=api
        old?.disablePersistence()
        val stored=try {
            withContext(Dispatchers.IO) { vault.forProfile(id).clear(); saved() }
        } catch(e: Exception) { old?.enablePersistence(); throw e }
        closeApi(false)
        val next=stored.profiles.activeId
        val config=stored.config
        if(next!=null && config!=null) attach(config,next,createApi(config,next),stored.profiles) else disconnected(stored.profiles)
        message=if(next==null) "Sesión eliminada del dispositivo" else "Sesión cerrada; servidor activo: $activeServerName"
        try { if(old!=null) withTimeoutOrNull(5000) { safely { old.logoutRemote() } } }
        finally { old?.shutdown() }
        if(api!=null) refreshAfterChange(message)
    }
    fun editConnection()=work {
        val id=checkNotNull(activeServerId) { "No hay un servidor seleccionado" }
        val config=withContext(Dispatchers.IO) { checkNotNull(vault.forProfile(id).load()) }
        closeApi(); clearData(); connected=false; editingServerId=id
        url=config.optString("url"); allowHttp=config.optBoolean("http")
        token=""; password=""; sessionEpoch++
        message="Introduce el nuevo acceso. El anterior se conserva cifrado hasta que guardes uno válido o cierres esta sesión."
    }
    override fun onCleared() { closeApi(); super.onCleared() }
}
