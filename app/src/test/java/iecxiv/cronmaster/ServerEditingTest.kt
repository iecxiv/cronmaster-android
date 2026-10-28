package iecxiv.cronmaster

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ServerEditingTest {
    private fun saved() = JSONObject().put("url", "https://a.example").put("http", false)
        .put("password", "old-password").put("token", "old-token")
        .put("cookies", JSONArray().put("old-cookie")).put("credential_revision", "old-generation")

    @Test fun nameOnlyKeepsConnectionAndCredentials() {
        val before = saved()
        val result = prepareServerEdit(before, "Production", "https://a.example", "", "", false)
        assertFalse(result.connectionChanged)
        assertEquals("old-password", result.config.getString("password"))
        assertEquals("old-token", result.config.getString("token"))
        assertEquals("old-cookie", result.config.getJSONArray("cookies").getString(0))
        assertEquals("old-generation", result.config.getString("credential_revision"))
    }
    @Test fun clearingOptionalNameUsesUrlWithoutChangingAccess() {
        val root = ServerProfileDocument.upgrade(saved())
        val id = ServerProfileDocument.snapshot(root).activeId!!
        ServerProfileDocument.rename(root, id, "Production")
        ServerProfileDocument.rename(root, id, "")
        assertEquals("https://a.example", ServerProfileDocument.snapshot(root).items.single().name)
        assertEquals("old-password", ServerProfileDocument.load(root, id)!!.getString("password"))
    }
    @Test fun changingUrlRequiresNewCredentials() {
        try {
            prepareServerEdit(saved(), "", "https://b.example", "", "", false)
            fail("Expected credential validation")
        } catch (e: ClientTextException) { assertEquals(R.string.error_new_server_access, e.stringId) }
    }
    @Test fun newPasswordDropsOldTokenAndCookiesWithoutMutatingSavedData() {
        val before = saved()
        val result = prepareServerEdit(before, "", "https://a.example", "", "new-password", false)
        assertTrue(result.connectionChanged)
        assertEquals("new-password", result.config.getString("password"))
        assertEquals("", result.config.getString("token"))
        assertFalse(result.config.has("cookies"))
        assertNotEquals("old-generation", result.config.getString("credential_revision"))
        assertEquals("old-password", before.getString("password"))
        assertTrue(before.has("cookies"))
    }
    @Test fun newUrlWithTokenDoesNotReuseOldPasswordOrCookies() {
        val result = prepareServerEdit(saved(), "", "https://b.example", "new-token", "", false)
        assertEquals("https://b.example", result.config.getString("url"))
        assertEquals("new-token", result.config.getString("token"))
        assertEquals("", result.config.getString("password"))
        assertFalse(result.config.has("cookies"))
    }
    @Test fun rejectsTwoNewCredentialTypes() {
        try {
            prepareServerEdit(saved(), "", "https://a.example", "new-token", "new-password", false)
            fail("Expected a single credential type")
        } catch (e: ClientTextException) { assertEquals(R.string.error_one_credential, e.stringId) }
    }
    @Test fun staleCookieRevisionIsRejected() {
        val config = saved().put("credential_revision", "new-generation")
        assertFalse(profileRevisionMatches(config, "old-generation"))
        assertTrue(profileRevisionMatches(config, "new-generation"))
        assertTrue(profileRevisionMatches(JSONObject().put("url", "https://a.example"), ""))
        assertFalse(profileRevisionMatches(null, "old-generation"))
    }
    @Test fun changingHttpPermissionCreatesANewConnectionRevision() {
        val result = prepareServerEdit(saved(), "", "https://a.example", "", "", true)
        assertTrue(result.connectionChanged)
        assertTrue(result.config.getBoolean("http"))
        assertEquals("old-password", result.config.getString("password"))
        assertNotEquals("old-generation", result.config.getString("credential_revision"))
    }
}
