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

class HttpFailure(val code: Int, message: String, val serverMessage: String? = null): IOException(message)
class ServerFailure(message: String, val serverMessage: String): IOException(message)
class Api(
    private val config: JSONObject,
    private val store: SecureStore,
    private val storageError: (String) -> Unit
) {
    private fun localized(id: Int,vararg args: Any): String=store.resources.getString(id,*args)
    private fun contentLocale(): String=localized(R.string.content_language)
    private fun errorText(data: JSONObject?,code: Int): String {
        val id=when(data?.optString("errorCode")) {
            "authentication_required" -> R.string.remote_auth_required
            "origin_rejected" -> R.string.remote_origin_rejected
            "json_required" -> R.string.remote_json_required
            "payload_too_large" -> R.string.remote_payload_too_large
            "invalid_request" -> R.string.remote_invalid_request
            "operation_rejected" -> R.string.api_operation_rejected
            "operation_failed" -> R.string.remote_operation_failed
            else -> when(code) {
                401 -> R.string.saved_access_rejected
                403 -> R.string.remote_access_denied
                404 -> R.string.remote_not_found
                413 -> R.string.remote_payload_too_large
                415 -> R.string.remote_json_required
                503 -> R.string.remote_unavailable
                else -> R.string.remote_operation_failed
            }
        }
        return localized(id)
    }
    private val mutationOps=setOf("job.create","job.edit","job.delete","job.pause","job.resume","job.clone","job.logging","job.run","job.backup","backup.all","backup.restore","backup.delete","restore.all","script.create","script.update","script.delete","script.clone","log.delete","logs.deleteAll")
    private val url=config.getString("url").trim().trimEnd('/').toHttpUrl().also {
        require(it.username.isEmpty() && it.password.isEmpty() && it.query==null && it.fragment==null) { localized(R.string.api_url_without_credentials) }
        require(it.isHttps || config.optBoolean("http")) { localized(R.string.api_https_required) }
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
                catch(e: Exception) { storageError(localized(R.string.api_cookie_persistence_failed,e.message.orEmpty())) }
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
        .header("Accept","application/json").header("Accept-Language",contentLocale()).apply { if(token.isNotBlank()) header("Authorization","Bearer $token") }
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
                        val data=try { JSONObject(text) } catch(_: Exception) { null }
                        val detail=data?.optString("message",data.optString("error")) ?: text.take(1024)
                        if(!r.isSuccessful) throw HttpFailure(r.code,localized(R.string.api_http_error,r.code,errorText(data,r.code)),detail)
                        if(data==null) throw ServerFailure(localized(R.string.api_non_json_response),detail)
                        if(!data.optBoolean("success",true)) throw ServerFailure(errorText(data,r.code),detail)
                        if(path.startsWith("api/logs/stream?")) {
                            val status=data.optString("status")
                            val statusId=when(status) {
                                "running" -> R.string.remote_status_running
                                "completed" -> R.string.remote_status_completed
                                "failed" -> R.string.remote_status_failed
                                else -> R.string.remote_status_unknown
                            }
                            data.put("serverStatus",status).put("status",localized(statusId))
                        }
                        data
                    }
                    if(cont.isActive) cont.resume(result)
                } catch(e: Exception) { if(cont.isActive) cont.resumeWithException(e) }
            }
        })
    }
    suspend fun login() {
        check(password.isNotBlank()) { localized(R.string.api_password_missing) }
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
    suspend fun native(op: String,args: JSONObject=JSONObject()): JSONObject {
        val locale=contentLocale()
        val result=call("api/native","POST",obj("op" to op,"args" to args,"locale" to locale))
        if(op=="jobs" && locale=="en") {
            result.rows().forEach { job ->
                if(job.optString("humanScheduleLocale")!=locale) job.put("humanSchedule",job.optString("schedule"))
            }
        }
        if(op in mutationOps) {
            if(result.has("message")) result.put("serverMessage",result.optString("message"))
            val messageId=if(result.optString("messageCode")=="execution_requested" || op=="job.run") R.string.remote_execution_requested else R.string.operation_completed
            result.put("message",localized(messageId))
        }
        return result
    }
    // These callback values are internal signals consumed by MainViewModel, not UI labels.
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
