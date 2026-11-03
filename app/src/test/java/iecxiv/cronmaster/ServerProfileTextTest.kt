package iecxiv.cronmaster

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ServerProfileTextTest {
    private val a = "00000000-0000-0000-0000-000000000001"
    private val b = "00000000-0000-0000-0000-000000000002"
    private fun populated(): JSONObject = ServerProfileDocument.upgrade(null).also {
        ServerProfileDocument.save(it, a, obj("url" to "https://a.example"))
    }
    private fun expectText(id: Int, block: () -> Unit) {
        try {
            block()
            fail("Expected a localized validation error")
        } catch (e: ClientTextException) {
            assertEquals(id, e.stringId)
        }
    }
    @Test fun invalidIdHasResource() {
        expectText(R.string.error_profile_id) { ServerProfileDocument.requireId("invalid") }
    }
    @Test fun unknownFormatHasResource() {
        expectText(R.string.error_profile_format) { ServerProfileDocument.upgrade(obj("profilesVersion" to 2)) }
    }
    @Test fun legacyAccessWithoutUrlHasResource() {
        expectText(R.string.error_saved_server_url) { ServerProfileDocument.upgrade(obj("url" to "")) }
    }
    @Test fun emptyServerUrlHasResource() {
        expectText(R.string.error_server_without_url) {
            ServerProfileDocument.save(ServerProfileDocument.upgrade(null), a, obj("url" to ""))
        }
    }
    @Test fun duplicateIdsHaveResource() {
        val root = populated()
        root.getJSONArray("profiles").put(JSONObject(root.getJSONArray("profiles").getJSONObject(0).toString()))
        expectText(R.string.error_duplicate_server_ids) { ServerProfileDocument.snapshot(root) }
    }
    @Test fun invalidActiveServerHasResource() {
        val root = populated().put("activeId", b)
        expectText(R.string.error_active_server_invalid) { ServerProfileDocument.snapshot(root) }
    }
    @Test fun missingServerHasResource() {
        expectText(R.string.error_server_not_saved) { ServerProfileDocument.activate(populated(), b) }
    }
    @Test fun longNameHasResource() {
        expectText(R.string.error_name_too_long) { ServerProfileDocument.rename(populated(), a, "x".repeat(65)) }
    }
    @Test fun missingRenameTargetHasResource() {
        expectText(R.string.error_server_not_saved) { ServerProfileDocument.rename(populated(), b, "Name") }
    }
}
