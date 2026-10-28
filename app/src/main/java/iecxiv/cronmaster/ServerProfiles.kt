package iecxiv.cronmaster

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

internal data class ServerProfile(val id: String, val name: String, val url: String)
internal data class ServerProfiles(val activeId: String?, val items: List<ServerProfile>)

internal object ServerProfileDocument {
    fun requireId(id: String) {
        require(Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}").matches(id)) { "Identificador de servidor no válido" }
    }
    fun upgrade(saved: JSONObject?): JSONObject {
        if (saved == null) return JSONObject().put("profilesVersion", 1).put("activeId", JSONObject.NULL).put("profiles", JSONArray())
        if (saved.has("profilesVersion")) {
            require(saved.getInt("profilesVersion") == 1) { "Formato de servidores no compatible" }
            snapshot(saved)
            return saved
        }
        require(saved.optString("url").isNotBlank()) { "El acceso guardado no contiene un servidor válido" }
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
            require(url.isNotBlank()) { "Servidor sin URL" }
            ServerProfile(id, entry.optString("name").ifBlank { url }, url)
        }
        require(items.map { it.id }.distinct().size == items.size) { "Identificadores de servidor duplicados" }
        val active = if (root.isNull("activeId")) null else root.getString("activeId")
        require(if (items.isEmpty()) active == null else items.any { it.id == active }) { "Servidor activo no válido" }
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
        require(config.optString("url").isNotBlank()) { "Servidor sin URL" }
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
        require(entry(root, id) != null) { "El servidor ya no está guardado" }
        root.put("activeId", id)
    }
    fun rename(root: JSONObject, id: String, name: String) {
        require(name.trim().length <= 64) { "El nombre no puede superar 64 caracteres" }
        val target = entry(root, id) ?: error("El servidor ya no está guardado")
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
