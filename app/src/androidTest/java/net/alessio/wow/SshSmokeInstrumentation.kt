package net.alessio.wow

import android.app.Instrumentation
import android.os.Bundle

/** Run only on the development emulator with tools/ssh_test_server.py listening on the host. */
class SshSmokeInstrumentation : Instrumentation() {
    private var testCase=""
    override fun onCreate(arguments: Bundle?) { super.onCreate(arguments);testCase=arguments?.getString("case").orEmpty();start() }
    override fun onStart() {
        Thread {
            val result=Bundle()
            try {
                if(testCase=="ui-key" || testCase=="ui-cleanup") {
                    val store=ProfileStore(targetContext)
                    val original=store.read().filterNot{it.id=="ui-test" || it.id=="lock-test"}
                    val testTarget=Target("ui-test","Profilo SSH prova","192.0.2.1",2222,"02:11:22:33:44:55",mode="ssh",username="wow-test",auth="key")
                    store.save(if(testCase=="ui-key") original+testTarget else original)
                    val line=DeviceSshKey.generate()
                    result.putString("stream","PASS: UI fixture configured: $testCase; SSH key fingerprint ${SshWake.fingerprint(line.split(' ')[1])}\n")
                    finish(-1,result);return@Thread
                }
                val target=Target("android-fixture","Test SSH","10.0.2.2",22333,"02:11:22:33:44:55","ssh","wow-test","test-only-special $ ` \\ \" !")
                val store=ProfileStore(targetContext)
                val original=store.read()
                try {
                    store.save(original+target)
                    val prefs=targetContext.getSharedPreferences("targets",0)
                    check(!prefs.contains("items")) { "Plaintext legacy data remained" }
                    val ciphertext=prefs.getString("encrypted_profiles","")!!
                    check(!ciphertext.contains(target.password))
                    check(store.read().last()==target) { "Keystore profile roundtrip failed" }
                } finally { store.save(original) }
                val probe=runCatching{SshWake.run(target.copy(password=""),probe=true)}.exceptionOrNull()
                check(probe is RouterKeyRequired) { "Probe failed: ${probe?.message}" }
                val pinned=target.copy(hostKey=probe.receivedKey)
                SshWake.run(pinned)
                SshWake.run(pinned.copy(broadcast=true))
                val bad=runCatching{SshWake.run(pinned.copy(password="wrong"))}.exceptionOrNull()
                check((bad as? UiFailure)?.text?.id == R.string.password_auth_failed)
                val publicLine=DeviceSshKey.generate()
                check(DeviceSshKey.generate()==publicLine) { "Generation must preserve the existing key" }
                val identity=DeviceSshKey.identity()
                java.net.Socket("10.0.2.2",22334).use { socket ->
                    socket.getOutputStream().write((java.util.Base64.getEncoder().encodeToString(identity.publicKeyBlob)+"\n").toByteArray())
                    check(socket.getInputStream().bufferedReader().readLine()=="OK")
                }
                val keyStore=java.security.KeyStore.getInstance("AndroidKeyStore").apply{load(null)}
                check(keyStore.getKey("wow_ssh_device_p256_v1",null).encoded==null) { "Device private key is exportable" }
                SshWake.run(pinned.copy(password="",auth="key"))
                check(publicLine.startsWith("ecdsa-sha2-nistp256 "))
                result.putString("stream","PASS: Android Keystore roundtrip, encrypted storage, SSH trust probe, unicast/broadcast commands, auth failure, non-exportable device key, public-key SSH authentication.\n")
                finish(-1,result)
            } catch(e:Throwable) {
                result.putString("stream","FAIL: ${e.javaClass.simpleName}: ${e.message}\n")
                finish(0,result)
            }
        }.start()
    }
}
