package net.alessio.wow

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import org.json.JSONArray
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Profiles, including credentials, are encrypted with a non-exportable Android Keystore key. */
class ProfileStore(context: Context) {
    private val prefs = context.getSharedPreferences("targets", Context.MODE_PRIVATE)
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey("wow_profiles_v1",null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder("wow_profiles_v1",KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    fun read(): List<Target> {
        val secure=prefs.getString("encrypted_profiles",null)
        val json = if(secure==null) prefs.getString("items","[]")!! else {
            val parts=secure.split(':'); require(parts.size==2)
            val cipher=Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE,key(),GCMParameterSpec(128,Base64.getDecoder().decode(parts[0])))
            String(cipher.doFinal(Base64.getDecoder().decode(parts[1])),Charsets.UTF_8)
        }
        val array=JSONArray(json)
        return (0 until array.length()).map { Target.fromJson(array.getJSONObject(it)) }
    }
    fun save(targets: List<Target>) {
        val array=JSONArray(); targets.forEach { array.put(it.json()) }
        val cipher=Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.ENCRYPT_MODE,key())
        val encrypted=cipher.doFinal(array.toString().toByteArray(Charsets.UTF_8))
        val value=Base64.getEncoder().encodeToString(cipher.iv)+":"+Base64.getEncoder().encodeToString(encrypted)
        requireText(prefs.edit().putString("encrypted_profiles",value).remove("items").commit(), R.string.save_failed)
    }
}
