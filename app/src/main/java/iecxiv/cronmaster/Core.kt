package iecxiv.cronmaster

import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.CancellationException

fun obj(vararg values: Pair<String, Any?>): JSONObject = JSONObject().apply {
    values.forEach { (k,v) -> put(k,v ?: JSONObject.NULL) }
}
fun JSONArray.objects(): List<JSONObject> = (0 until length()).mapNotNull { optJSONObject(it) }
fun JSONObject.rows(): List<JSONObject> = optJSONArray("data")?.objects() ?: emptyList()
fun JSONObject.intOrNull(key: String): Int? = if (!has(key) || isNull(key)) null else opt(key).toString().toIntOrNull()
suspend fun <T> safely(block: suspend () -> T): Result<T> = try {
    Result.success(block())
} catch(e: CancellationException) { throw e } catch(e: Exception) { Result.failure(e) }

data class Health(val kind: Kind, val label: String) {
    enum class Kind { GOOD, BAD, UNKNOWN }
}
fun health(job: JSONObject): Health {
    if (!job.optBoolean("logsEnabled")) return Health(Health.Kind.UNKNOWN,"Sin logging")
    val h=job.optJSONObject("logError") ?: return Health(Health.Kind.UNKNOWN,"Sin datos")
    val latest=h.intOrNull("latestExitCode")
    val historical=h.optBoolean("hasHistoricalFailures")
    return when {
        h.optBoolean("hasError") || (latest != null && latest != 0) -> Health(Health.Kind.BAD,"Fallida · exit ${latest ?: h.intOrNull("exitCode") ?: "?"}")
        latest == 0 -> Health(Health.Kind.GOOD,if(historical) "Correcta · fallos anteriores" else "Correcta · exit 0")
        else -> Health(Health.Kind.UNKNOWN,if(historical) "Sin resultado reciente · fallos anteriores" else "Sin resultado")
    }
}
