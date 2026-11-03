package iecxiv.cronmaster

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

internal data class ServerProfile(val id: String, val name: String, val url: String)
internal data class ServerProfiles(val activeId: String?, val items: List<ServerProfile>)

private fun requireProfile(condition: Boolean, stringId: Int) {
    if (!condition) throw ClientTextException(stringId)
}

internal object ServerProfileDocument {
    fun requireId(id: String) {
        requireProfile(Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}").matches(id), R.string.error_profile_id)
    }
    fun upgrade(saved: JSONObject?): JSONObject {
        if (saved == null) return JSONObject().put("profilesVersion", 1).put("activeId", JSONObject.NULL).put("profiles", JSONArray())
        if (saved.has("profilesVersion")) {
            requireProfile(saved.getInt("profilesVersion") == 1, R.string.error_profile_format)
            snapshot(saved)
            return saved
        }
        requireProfile(saved.optString("url").isNotBlank(), R.string.error_saved_server_url)
        val result = upgrade(null)
        save(result, null, saved)
        return result
    }
    fun snapshot(root: JSONObject): ServerProfiles {
        val array = root.getJSONArray("profiles")
        val items = (0 until array.length()).map { index ->
            val entry = array.getJSONObject(index)
            val id = entry.getString("id").also { requireId(it) }
            val url = entry.getJSONObject("config").getString("url")
            requireProfile(url.isNotBlank(), R.string.error_server_without_url)
            ServerProfile(id, entry.optString("name").ifBlank { url }, url)
        }
        requireProfile(items.map { it.id }.distinct().size == items.size, R.string.error_duplicate_server_ids)
        val active = if (root.isNull("activeId")) null else root.getString("activeId")
        requireProfile(if (items.isEmpty()) active == null else items.any { it.id == active }, R.string.error_active_server_invalid)
        return ServerProfiles(active, items)
    }
    private fun entry(root: JSONObject, id: String?): JSONObject? {
        if (id == null) return null
        val array = root.getJSONArray("profiles")
        return (0 until array.length()).map { array.getJSONObject(it) }.firstOrNull { it.getString("id") == id }
    }
    fun load(root: JSONObject, id: String?): JSONObject? {
        val target = id ?: snapshot(root).activeId
        return entry(root, target)?.getJSONObject("config")?.let { JSONObject(it.toString()) }
    }
    fun save(root: JSONObject, id: String?, config: JSONObject): String {
        requireProfile(config.optString("url").isNotBlank(), R.string.error_server_without_url)
        val current = snapshot(root)
        val target = (id ?: current.activeId ?: UUID.randomUUID().toString()).also { requireId(it) }
        val existing = entry(root, target)
        if (existing == null) {
            root.getJSONArray("profiles").put(JSONObject().put("id", target).put("name", config.getString("url")).put("config", JSONObject(config.toString())))
        } else {
            if (existing.optString("name") == existing.getJSONObject("config").optString("url")) existing.put("name", config.getString("url"))
            existing.put("config", JSONObject(config.toString()))
        }
        if (current.activeId == null) root.put("activeId", target)
        return target
    }
    fun saveCookies(root: JSONObject, id: String?, cookies: JSONArray): Boolean {
        val target = entry(root, id ?: snapshot(root).activeId) ?: return false
        target.getJSONObject("config").put("cookies", JSONArray(cookies.toString()))
        return true
    }
    fun activate(root: JSONObject, id: String) {
        requireProfile(entry(root, id) != null, R.string.error_server_not_saved)
        root.put("activeId", id)
    }
    fun rename(root: JSONObject, id: String, name: String) {
        requireProfile(name.trim().length <= 64, R.string.error_name_too_long)
        val target = entry(root, id) ?: throw ClientTextException(R.string.error_server_not_saved)
        target.put("name", name.trim().ifBlank { target.getJSONObject("config").getString("url") })
    }
    fun remove(root: JSONObject, id: String): Boolean {
        val before = snapshot(root)
        if (before.items.none { it.id == id }) return false
        val kept = JSONArray()
        val array = root.getJSONArray("profiles")
        for (index in 0 until array.length()) {
            val item = array.getJSONObject(index)
            if (item.getString("id") != id) kept.put(item)
        }
        root.put("profiles", kept)
        if (before.activeId == id) root.put("activeId", if (kept.length() == 0) JSONObject.NULL else kept.getJSONObject(0).getString("id"))
        return true
    }
}
