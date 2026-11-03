package iecxiv.cronmaster

import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.CancellationException

fun obj(vararg values: Pair<String, Any?>): JSONObject = JSONObject().apply {
    values.forEach { (k,v) -> put(k,v ?: JSONObject.NULL) }
}
fun JSONArray.objects(): List<JSONObject> = (0 until length()).mapNotNull { optJSONObject(it) }
fun JSONObject.rows(): List<JSONObject> = optJSONArray("data")?.objects() ?: emptyList()
fun JSONObject.intOrNull(key: String): Int? = if (!has(key) || isNull(key)) null else opt(key)?.toString()?.toIntOrNull()
suspend fun <T> safely(block: suspend () -> T): Result<T> = try {
    Result.success(block())
} catch(e: CancellationException) { throw e } catch(e: Exception) { Result.failure(e) }

data class Health(val kind: Kind, val status: Status, val exitCode: Int? = null) {
    enum class Kind { GOOD, BAD, UNKNOWN }
    enum class Status {
        LOGGING_DISABLED, NO_DATA, FAILED, SUCCEEDED, SUCCEEDED_WITH_HISTORY,
        NO_RECENT_RESULT_WITH_HISTORY, NO_RESULT
    }
}
fun health(job: JSONObject): Health {
    if (!job.optBoolean("logsEnabled")) return Health(Health.Kind.UNKNOWN,Health.Status.LOGGING_DISABLED)
    val h=job.optJSONObject("logError") ?: return Health(Health.Kind.UNKNOWN,Health.Status.NO_DATA)
    val latest=h.intOrNull("latestExitCode")
    val historical=h.optBoolean("hasHistoricalFailures")
    return when {
        h.optBoolean("hasError") || (latest != null && latest != 0) -> Health(Health.Kind.BAD,Health.Status.FAILED,latest ?: h.intOrNull("exitCode"))
        latest == 0 -> Health(Health.Kind.GOOD,if(historical) Health.Status.SUCCEEDED_WITH_HISTORY else Health.Status.SUCCEEDED,0)
        else -> Health(Health.Kind.UNKNOWN,if(historical) Health.Status.NO_RECENT_RESULT_WITH_HISTORY else Health.Status.NO_RESULT)
    }
}
