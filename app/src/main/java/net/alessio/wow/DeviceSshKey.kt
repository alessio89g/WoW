package net.alessio.wow

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec

object DeviceSshKey {
    private const val ALIAS="wow_ssh_device_p256_v1"
    private fun store()=KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    fun exists()=store().containsAlias(ALIAS)
    @Synchronized fun generate(): String {
        if(!exists()) KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC,"AndroidKeyStore").apply {
            initialize(KeyGenParameterSpec.Builder(ALIAS,KeyProperties.PURPOSE_SIGN)
                .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
                .setDigests(KeyProperties.DIGEST_SHA256)
                .setUserAuthenticationRequired(false).build())
        }.generateKeyPair()
        return publicLine()
    }
    fun identity(): SshEcIdentity {
        val entry=store().getEntry(ALIAS,null) as? KeyStore.PrivateKeyEntry
            ?: error("Genera prima la chiave SSH di questo dispositivo.")
        return SshEcIdentity(entry.privateKey,entry.certificate.publicKey as ECPublicKey)
    }
    fun publicLine()=identity().publicLine()
}
