package iecxiv.cronmaster

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class SecureStore(context: Context, private val profileId: String? = null, private val expectedRevision: String? = null) {
    companion object { private val lock = Any() }
    private val app = context.applicationContext
    internal val resources get() = app.resources
    private val prefs = app.getSharedPreferences("secure_session", Context.MODE_PRIVATE)
    private val alias = "iecxiv.cronmaster.session.v1"
    private fun <T> localizedErrors(block: () -> T): T = try {
        block()
    } catch (e: ClientTextException) {
        throw IllegalArgumentException(resources.getString(e.stringId), e)
    }
    private fun <T> locked(block: () -> T): T = synchronized(lock) { localizedErrors(block) }
    init { if (profileId != null) localizedErrors { ServerProfileDocument.requireId(profileId) } }
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setKeySize(256).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    private fun read(): JSONObject? {
        val saved = prefs.getString("sealed", null) ?: return null
        val bytes = Base64.decode(saved, Base64.NO_WRAP)
        require(bytes.size > 28) { resources.getString(R.string.error_encrypted_store_damaged) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
            updateAAD(alias.toByteArray(Charsets.UTF_8))
        }
        return JSONObject(String(cipher.doFinal(bytes.copyOfRange(12, bytes.size)), Charsets.UTF_8))
    }
    private fun write(value: JSONObject) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.ENCRYPT_MODE, key()); updateAAD(alias.toByteArray(Charsets.UTF_8))
        }
        val bytes = cipher.iv + cipher.doFinal(value.toString().toByteArray(Charsets.UTF_8))
        check(prefs.edit().putString("sealed", Base64.encodeToString(bytes, Base64.NO_WRAP)).commit()) { resources.getString(R.string.error_save_session) }
    }
    private fun document(): JSONObject {
        val saved = read()
        val result = ServerProfileDocument.upgrade(saved)
        if (saved != null && !saved.has("profilesVersion")) write(result)
        return result
    }
    fun load(): JSONObject? = locked { ServerProfileDocument.load(document(), profileId) }
    fun save(value: JSONObject) = locked {
        val root = document()
        ServerProfileDocument.save(root, profileId, value)
        write(root)
    }
    fun saveCookies(cookies: JSONArray): Unit = locked {
        val root = document()
        if (expectedRevision != null && !profileRevisionMatches(ServerProfileDocument.load(root, profileId), expectedRevision)) return@locked
        if (ServerProfileDocument.saveCookies(root, profileId, cookies)) write(root)
    }
    internal fun saveProfile(value: JSONObject, name: String, makeActive: Boolean = true): String = locked {
        val root = document()
        val id = ServerProfileDocument.save(root, profileId, value)
        ServerProfileDocument.rename(root, id, name)
        if (makeActive) ServerProfileDocument.activate(root, id)
        write(root)
        id
    }
    internal fun forProfile(id: String) = SecureStore(app, id)
    internal fun forSession(id: String, config: JSONObject) = SecureStore(app, id, config.optString("credential_revision", ""))
    internal fun snapshot(): ServerProfiles = locked { ServerProfileDocument.snapshot(document()) }
    internal fun activate(id: String) = locked {
        val root = document()
        ServerProfileDocument.activate(root, id)
        write(root)
    }
    internal fun rename(id: String, name: String) = locked {
        val root = document()
        ServerProfileDocument.rename(root, id, name)
        write(root)
    }
    private fun clearAll() {
        check(prefs.edit().clear().commit()) { resources.getString(R.string.error_remove_access) }
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (store.containsAlias(alias)) store.deleteEntry(alias)
    }
    // An unbound store clears all access; a bound store clears only its own profile.
    fun clear(): Unit = locked {
        if (profileId == null) clearAll()
        else {
            val root = document()
            if (ServerProfileDocument.remove(root, profileId)) {
                if (ServerProfileDocument.snapshot(root).items.isEmpty()) clearAll() else write(root)
            }
        }
    }
}
