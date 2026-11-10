package net.alessio.wow

import com.jcraft.jsch.Identity
import org.bouncycastle.asn1.ASN1Integer
import org.bouncycastle.asn1.ASN1Sequence
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.math.BigInteger
import java.security.PrivateKey
import java.security.Signature
import java.security.interfaces.ECPublicKey
import java.util.Base64

/** SSH wire-format adapter: the signer can use a non-exportable Android Keystore key. */
class SshEcIdentity(private val privateKey: PrivateKey, publicKey: ECPublicKey) : Identity {
    private val algorithm="ecdsa-sha2-nistp256"
    private val blob=fields(algorithm.toByteArray(),"nistp256".toByteArray(),byteArrayOf(4)+coordinate(publicKey.w.affineX)+coordinate(publicKey.w.affineY))
    init { require(publicKey.params.curve.field.fieldSize==256) { "Chiave EC non compatibile." } }
    override fun getName()="WoW Android Keystore"
    override fun getAlgName()=algorithm
    override fun isEncrypted()=false
    override fun setPassphrase(passphrase: ByteArray?)=true
    override fun clear()=Unit // JSch must never delete the persistent device key.
    override fun getPublicKeyBlob()=blob.copyOf()
    override fun getSignature(data: ByteArray): ByteArray {
        val signer=Signature.getInstance("SHA256withECDSA")
        signer.initSign(privateKey);signer.update(data)
        val sequence=ASN1Sequence.getInstance(signer.sign())
        require(sequence.size()==2)
        val r=ASN1Integer.getInstance(sequence.getObjectAt(0)).positiveValue.toByteArray()
        val s=ASN1Integer.getInstance(sequence.getObjectAt(1)).positiveValue.toByteArray()
        return fields(algorithm.toByteArray(),fields(r,s))
    }
    override fun getSignature(data: ByteArray, alg: String): ByteArray {
        require(alg==algorithm) { "Algoritmo di firma non compatibile." }
        return getSignature(data)
    }
    fun publicLine()="$algorithm ${Base64.getEncoder().encodeToString(blob)} WoW-Android"
    companion object {
        private fun coordinate(n: BigInteger): ByteArray {
            val bytes=n.toByteArray();return ByteArray(32).also { dst ->
                val count=minOf(bytes.size,32);bytes.copyInto(dst,32-count,bytes.size-count,bytes.size)
            }
        }
        private fun fields(vararg values: ByteArray): ByteArray {
            val output=ByteArrayOutputStream()
            DataOutputStream(output).use { stream -> values.forEach { stream.writeInt(it.size);stream.write(it) } }
            return output.toByteArray()
        }
    }
}
