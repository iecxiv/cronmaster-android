package iecxiv.cronmaster

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ServerProfilesTest {
    private val a = "00000000-0000-0000-0000-000000000001"
    private val b = "00000000-0000-0000-0000-000000000002"
    private fun config(url: String, password: String) = JSONObject().put("url", url).put("password", password).put("cookies", JSONArray().put("initial"))
    @Test fun migratesLegacyWithoutLosingAccess() {
        val root = ServerProfileDocument.upgrade(config("https://a.example", "sample-a"))
        val snapshot = ServerProfileDocument.snapshot(root)
        assertEquals(1, snapshot.items.size)
        assertNotNull(snapshot.activeId)
        assertEquals("sample-a", ServerProfileDocument.load(root, snapshot.activeId)!!.getString("password"))
        assertEquals("initial", ServerProfileDocument.load(root, snapshot.activeId)!!.getJSONArray("cookies").getString(0))
    }
    @Test fun switchingKeepsCredentialsAndCookiesSeparate() {
        val root = ServerProfileDocument.upgrade(null)
        ServerProfileDocument.save(root, a, config("https://a.example", "sample-a"))
        ServerProfileDocument.save(root, b, config("https://b.example", "sample-b"))
        ServerProfileDocument.activate(root, b)
        ServerProfileDocument.saveCookies(root, a, JSONArray().put("updated-a"))
        assertEquals(b, ServerProfileDocument.snapshot(root).activeId)
        assertEquals("sample-b", ServerProfileDocument.load(root, null)!!.getString("password"))
        assertEquals("initial", ServerProfileDocument.load(root, b)!!.getJSONArray("cookies").getString(0))
        assertEquals("updated-a", ServerProfileDocument.load(root, a)!!.getJSONArray("cookies").getString(0))
    }
    @Test fun removingOneProfilePreservesTheOtherAndRejectsLateCookies() {
        val root = ServerProfileDocument.upgrade(null)
        ServerProfileDocument.save(root, a, config("https://a.example", "sample-a"))
        ServerProfileDocument.save(root, b, config("https://b.example", "sample-b"))
        assertTrue(ServerProfileDocument.remove(root, a))
        assertEquals(b, ServerProfileDocument.snapshot(root).activeId)
        assertFalse(ServerProfileDocument.saveCookies(root, a, JSONArray().put("late-a")))
        assertEquals(1, ServerProfileDocument.snapshot(root).items.size)
        assertNull(ServerProfileDocument.load(root, a))
    }
    @Test fun loadedConfigIsACopy() {
        val root = ServerProfileDocument.upgrade(null)
        ServerProfileDocument.save(root, a, config("https://a.example", "sample-a"))
        ServerProfileDocument.load(root, a)!!.put("password", "changed-copy")
        assertEquals("sample-a", ServerProfileDocument.load(root, a)!!.getString("password"))
    }
    @Test(expected = IllegalArgumentException::class)
    fun refusesUnknownFormats() { ServerProfileDocument.upgrade(JSONObject().put("profilesVersion", 2)) }
}
