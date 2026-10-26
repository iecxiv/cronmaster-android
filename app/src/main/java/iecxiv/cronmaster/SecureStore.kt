package iecxiv.cronmaster

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class SecureStore(context: Context) {
    private val prefs=context.getSharedPreferences("secure_session",Context.MODE_PRIVATE)
    private val alias="iecxiv.cronmaster.session.v1"
    private fun key(): SecretKey {
        val store=KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias,null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias,KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setKeySize(256).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    @Synchronized fun load(): JSONObject? {
        val saved=prefs.getString("sealed",null) ?: return null
        val bytes=Base64.decode(saved,Base64.NO_WRAP)
        require(bytes.size>28) { "El almacén cifrado está dañado" }
        val cipher=Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE,key(),GCMParameterSpec(128,bytes.copyOfRange(0,12)))
            updateAAD(alias.toByteArray(Charsets.UTF_8))
        }
        return JSONObject(String(cipher.doFinal(bytes.copyOfRange(12,bytes.size)),Charsets.UTF_8))
    }
    @Synchronized fun save(value: JSONObject) {
        val cipher=Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.ENCRYPT_MODE,key()); updateAAD(alias.toByteArray(Charsets.UTF_8))
        }
        val bytes=cipher.iv+cipher.doFinal(value.toString().toByteArray(Charsets.UTF_8))
        check(prefs.edit().putString("sealed",Base64.encodeToString(bytes,Base64.NO_WRAP)).commit()) { "No se pudo guardar la sesión" }
    }
    @Synchronized fun saveCookies(cookies: org.json.JSONArray) {
        val config=load() ?: return
        save(config.put("cookies",cookies))
    }
    @Synchronized fun clear() {
        check(prefs.edit().clear().commit()) { "No se pudo borrar el acceso local" }
        val store=KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if(store.containsAlias(alias)) store.deleteEntry(alias)
    }
}
