package iecxiv.cronmaster

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.sse.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class HttpFailure(val code: Int, message: String): IOException(message)
class Api(
    private val config: JSONObject,
    private val store: SecureStore,
    private val storageError: (String) -> Unit
) {
    private val url=config.getString("url").trim().trimEnd('/').toHttpUrl().also {
        require(it.username.isEmpty() && it.password.isEmpty() && it.query==null && it.fragment==null) { "URL sin credenciales, query ni fragmento" }
        require(it.isHttps || config.optBoolean("http")) { "Usa HTTPS o autoriza HTTP explícitamente" }
    }
    private val token=config.optString("token")
    private val password=config.optString("password")
    private val cookies=mutableListOf<Cookie>()
    private val loginMutex=Mutex()
    @Volatile private var persist=false
    init {
        config.optJSONArray("cookies")?.let { a ->
            for(i in 0 until a.length()) Cookie.parse(url,a.optString(i))?.let { cookies.add(it) }
        }
    }
    private val client=OkHttpClient.Builder()
        .connectTimeout(15,TimeUnit.SECONDS).readTimeout(330,TimeUnit.SECONDS)
        .followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false)
        .cookieJar(object: CookieJar {
            override fun loadForRequest(url: HttpUrl): List<Cookie> = synchronized(cookies) {
                cookies.removeAll { it.expiresAt<System.currentTimeMillis() }
                cookies.filter { it.matches(url) }
            }
            override fun saveFromResponse(url: HttpUrl, received: List<Cookie>) {
                synchronized(cookies) {
                    received.forEach { c ->
                        cookies.removeAll { it.name==c.name && it.domain==c.domain && it.path==c.path }
                        cookies.add(c)
                    }
                }
                if(persist) try { store.saveCookies(cookieSnapshot()) }
                catch(e: Exception) { storageError("No se pudo persistir la cookie: ${e.message}") }
            }
        }).build()
    fun cookieSnapshot(): JSONArray = synchronized(cookies) { JSONArray(cookies.filter { it.expiresAt>System.currentTimeMillis() }.map { it.toString() }) }
    fun enablePersistence() { persist=true }
    fun disablePersistence() { persist=false }
    fun cancelCalls() { client.dispatcher.cancelAll() }
    suspend fun logoutRemote() { raw("api/auth/logout","POST",null) }
    fun shutdown() { persist=false; client.dispatcher.cancelAll(); synchronized(cookies) { cookies.clear() } }
    private fun builder(path: String): Request.Builder = Request.Builder()
        .url(url.toString().trimEnd('/')+"/"+path.trimStart('/'))
        .header("Accept","application/json").apply { if(token.isNotBlank()) header("Authorization","Bearer $token") }
    private suspend fun raw(path: String, method: String, payload: JSONObject?): JSONObject = suspendCancellableCoroutine { cont ->
        val body=if(method in listOf("POST","PATCH","PUT")) (payload ?: JSONObject()).toString().toRequestBody("application/json; charset=utf-8".toMediaType()) else null
        val call=client.newCall(builder(path).method(method,body).build())
        cont.invokeOnCancellation { call.cancel() }
        call.enqueue(object: Callback {
            override fun onFailure(call: Call,e: IOException) { if(cont.isActive) cont.resumeWithException(e) }
            override fun onResponse(call: Call,response: Response) {
                try {
                    val result=response.use { r ->
                        val text=r.body?.string().orEmpty()
                        val data=try { JSONObject(text) } catch(_: Exception) { obj("error" to "Respuesta no JSON; revisa la URL o el proxy") }
                        if(!r.isSuccessful) throw HttpFailure(r.code,"HTTP ${r.code}: ${data.optString("message",data.optString("error"))}")
                        check(data.optBoolean("success",true)) { data.optString("message",data.optString("error","Operación rechazada")) }
                        data
                    }
                    if(cont.isActive) cont.resume(result)
                } catch(e: Exception) { if(cont.isActive) cont.resumeWithException(e) }
            }
        })
    }
    suspend fun login() {
        check(password.isNotBlank()) { "No hay contraseña guardada para renovar la sesión" }
        raw("api/auth/login","POST",obj("password" to password))
    }
    suspend fun call(path: String,method: String="GET",payload: JSONObject?=null): JSONObject {
        try { return raw(path,method,payload) }
        catch(e: HttpFailure) {
            if(e.code!=401 || password.isBlank() || token.isNotBlank()) throw e
            loginMutex.withLock { login() }
            return raw(path,method,payload)
        }
    }
    suspend fun native(op: String,args: JSONObject=JSONObject()): JSONObject = call("api/native","POST",obj("op" to op,"args" to args))
    fun events(onEvent: () -> Unit,onState: (String) -> Unit): EventSource {
        val streaming=client.newBuilder().readTimeout(0,TimeUnit.MILLISECONDS).build()
        return EventSources.createFactory(streaming).newEventSource(builder("api/events").header("Accept","text/event-stream").build(),object: EventSourceListener() {
            override fun onOpen(eventSource: EventSource,response: Response) { onState("Conexión en vivo") }
            override fun onEvent(eventSource: EventSource,id: String?,type: String?,data: String) { if(type!="heartbeat") onEvent() }
            override fun onClosed(eventSource: EventSource) { onState("Refresco periódico") }
            override fun onFailure(eventSource: EventSource,t: Throwable?,response: Response?) { onState("Refresco periódico") }
        })
    }
}
