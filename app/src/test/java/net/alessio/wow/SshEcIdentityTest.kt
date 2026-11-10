package net.alessio.wow

import org.junit.Assert.*
import org.junit.Test
import org.junit.Assume.assumeTrue
import org.bouncycastle.asn1.ASN1EncodableVector
import org.bouncycastle.asn1.ASN1Integer
import org.bouncycastle.asn1.DERSequence
import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.net.Socket
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.math.BigInteger
import java.util.Base64

class SshEcIdentityTest {
    private fun pair()=KeyPairGenerator.getInstance("EC").apply{initialize(ECGenParameterSpec("secp256r1"))}.generateKeyPair()
    private fun field(input: DataInputStream): ByteArray {
        val n=input.readInt();require(n in 0..4096);return ByteArray(n).also{input.readFully(it)}
    }
    @Test fun signaturesVerifyWithIndependentJavaVerifier() {
        val pair=pair();val id=SshEcIdentity(pair.private,pair.public as ECPublicKey)
        repeat(12) { round ->
            val message="SSH authentication test $round".toByteArray()
            val packet=DataInputStream(ByteArrayInputStream(id.getSignature(message)))
            assertEquals("ecdsa-sha2-nistp256",String(field(packet)))
            val ints=DataInputStream(ByteArrayInputStream(field(packet)))
            val r=BigInteger(field(ints));val s=BigInteger(field(ints))
            assertTrue(r.signum()>0 && s.signum()>0)
            val vector=ASN1EncodableVector().apply{add(ASN1Integer(r));add(ASN1Integer(s))}
            val der=DERSequence(vector).encoded
            val verifier=Signature.getInstance("SHA256withECDSA")
            verifier.initVerify(pair.public);verifier.update(message);assertTrue(verifier.verify(der))
            verifier.initVerify(pair.public);verifier.update(message+byteArrayOf(1));assertFalse(verifier.verify(der))
        }
        val blob=DataInputStream(ByteArrayInputStream(id.publicKeyBlob))
        assertEquals("ecdsa-sha2-nistp256",String(field(blob)));assertEquals("nistp256",String(field(blob)))
        val point=field(blob);assertEquals(65,point.size);assertEquals(4,point[0].toInt())
        assertTrue(id.publicLine().startsWith("ecdsa-sha2-nistp256 "))
    }
    @Test fun keyAuthenticationAndWrongKeyRejectionAgainstSshFixture() {
        assumeTrue(System.getenv("WOW_SSH_TEST_EVENTS")!=null)
        val pair=pair();val id=SshEcIdentity(pair.private,pair.public as ECPublicKey)
        Socket("127.0.0.1",22334).use { s ->
            s.getOutputStream().write((Base64.getEncoder().encodeToString(id.publicKeyBlob)+"\n").toByteArray())
            assertEquals("OK",s.getInputStream().bufferedReader().readLine())
        }
        val target=Target("key-test","Test","127.0.0.1",22333,"02:11:22:33:44:55","ssh","wow-test",auth="key")
        val probe=runCatching{SshWake.run(target,probe=true)}.exceptionOrNull()
        assertTrue(probe is RouterKeyRequired)
        val pinned=target.copy(hostKey=(probe as RouterKeyRequired).receivedKey)
        SshWake.run(pinned,identityOverride=id)
        val other=pair();val wrong=SshEcIdentity(other.private,other.public as ECPublicKey)
        val failure=runCatching{SshWake.run(pinned,identityOverride=wrong)}.exceptionOrNull()
        assertTrue((failure as? UiFailure)?.text?.id == R.string.key_auth_failed)
        assertEquals("",pinned.password)
    }
}
