package iecxiv.cronmaster

import org.json.JSONObject
import java.util.UUID

internal class ClientTextException(val stringId: Int) : IllegalArgumentException()
internal data class ServerEditData(val id: String, val name: String, val url: String, val http: Boolean)
internal data class PreparedServerEdit(val config: JSONObject, val connectionChanged: Boolean)

private fun requireEdit(condition: Boolean, stringId: Int) {
    if (!condition) throw ClientTextException(stringId)
}

internal fun prepareServerEdit(saved: JSONObject, name: String, serverUrl: String, newToken: String, newPassword: String, http: Boolean): PreparedServerEdit {
    requireEdit(name.trim().length <= 64, R.string.error_name_too_long)
    val targetUrl = serverUrl.trim().trimEnd('/')
    requireEdit(targetUrl.isNotBlank(), R.string.error_server_url_required)
    val token = newToken.trim()
    val hasToken = token.isNotEmpty()
    val hasPassword = newPassword.isNotBlank()
    requireEdit(!(hasToken && hasPassword), R.string.error_one_credential)
    val urlChanged = targetUrl != saved.optString("url").trim().trimEnd('/')
    val newAccess = hasToken || hasPassword
    requireEdit(!urlChanged || newAccess, R.string.error_new_server_access)
    val result = JSONObject(saved.toString()).put("url", targetUrl).put("http", http)
    if (hasToken) {
        result.put("token", token).put("password", "")
    } else if (hasPassword) {
        result.put("password", newPassword).put("token", "")
    }
    if (urlChanged || newAccess) result.remove("cookies")
    val changed = urlChanged || newAccess || http != saved.optBoolean("http")
    if (changed) result.put("credential_revision", UUID.randomUUID().toString())
    return PreparedServerEdit(result, changed)
}

internal fun profileRevisionMatches(config: JSONObject?, expectedRevision: String?): Boolean =
    config != null && (expectedRevision == null || config.optString("credential_revision", "") == expectedRevision)
