package net.alessio.wow

import com.jcraft.jsch.*
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.Base64
import java.util.concurrent.TimeUnit

class RouterKeyRequired(val receivedKey: String, val changed: Boolean) : Exception("SSH key needs verification")

object SshWake {
    fun fingerprint(key: String): String = "SHA256:"+Base64.getEncoder().withoutPadding()
        .encodeToString(MessageDigest.getInstance("SHA-256").digest(Base64.getDecoder().decode(key)))

    /** A probe never authenticates or executes commands: it aborts after reading the server key. */
    fun run(target: Target, probe: Boolean = false, identityOverride: Identity? = null) {
        WakePacket.validateHost(target.host)
        require(target.port in 1..65535)
        val cmd=if (probe) "" else target.routerPreset.command(target.mac,target.lanInterface,target.broadcast)
        requireText(target.username.isNotBlank() && !target.username.contains('\n'), R.string.missing_username)
        requireText(probe || target.usesDeviceKey || target.password.isNotEmpty(), R.string.missing_password)
        val jsch=JSch()
        var received: String?=null
        jsch.setHostKeyRepository(object : HostKeyRepository {
            override fun check(host: String?, key: ByteArray): Int {
                val incoming=Base64.getEncoder().encodeToString(key)
                received=incoming
                if(probe || target.hostKey.isEmpty()) return HostKeyRepository.NOT_INCLUDED
                return if(MessageDigest.isEqual(key,Base64.getDecoder().decode(target.hostKey))) HostKeyRepository.OK else HostKeyRepository.CHANGED
            }
            override fun add(key: HostKey?, ui: UserInfo?) = Unit
            override fun remove(host: String?, type: String?) = Unit
            override fun remove(host: String?, type: String?, key: ByteArray?) = Unit
            override fun getKnownHostsRepositoryID() = "WoW router pin"
            override fun getHostKey(): Array<HostKey> = emptyArray()
            override fun getHostKey(host: String?, type: String?): Array<HostKey> = emptyArray()
        })
        val session=jsch.getSession(target.username,target.host,target.port)
        session.setConfig("StrictHostKeyChecking","yes")
        // Use the bundled lightweight Ed25519 implementation on every Android version.
        session.setConfig("ssh-ed25519","com.jcraft.jsch.bc.SignatureEd25519")
        session.setConfig("server_host_key","ssh-ed25519,ecdsa-sha2-nistp256,ecdsa-sha2-nistp384,ecdsa-sha2-nistp521,rsa-sha2-512,rsa-sha2-256")
        session.setConfig("PreferredAuthentications",if(target.usesDeviceKey) "publickey" else "password")
        if(!probe && target.hostKey.isNotEmpty()) {
            if(target.usesDeviceKey) jsch.addIdentity(identityOverride ?: DeviceSshKey.identity(),null)
            else session.setPassword(target.password.toByteArray(Charsets.UTF_8))
        }
        session.timeout=12000
        try {
            session.connect(10000)
            requireText(!probe, R.string.key_verify_failed)
            val channel=session.openChannel("exec") as ChannelExec
            try {
                channel.setCommand(cmd)
                channel.setInputStream(null)
                val stdout=channel.inputStream; val stderr=channel.errStream
                val output=ByteArrayOutputStream(); val buffer=ByteArray(1024)
                channel.connect(10000)
                val deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(12)
                fun drain() {
                    for(stream in listOf(stdout,stderr)) while(stream.available()>0) {
                        val n=stream.read(buffer,0,minOf(buffer.size,stream.available()))
                        if(n<=0) break
                        requireText(output.size()+n<=8192, R.string.ssh_output_limit)
                        output.write(buffer,0,n)
                    }
                }
                while(!channel.isClosed) {
                    drain()
                    requireText(System.nanoTime()<deadline, R.string.ssh_command_timeout)
                    Thread.sleep(25)
                }
                drain()
                when(channel.exitStatus) {
                    0 -> Unit
                    127 -> throw UiFailure(R.string.wol_unavailable)
                    126 -> throw UiFailure(R.string.wol_forbidden)
                    else -> throw UiFailure(R.string.runtime_exit, channel.exitStatus)
                }
            } finally { channel.disconnect() }
        } catch(e: JSchException) {
            val key=received
            if(key!=null && (probe || target.hostKey.isEmpty() || key!=target.hostKey))
                throw RouterKeyRequired(key,target.hostKey.isNotEmpty() && key!=target.hostKey)
            val msg=e.message.orEmpty()
            when {
                msg.contains("Auth fail",true) || msg.contains("auth cancel",true) -> throw UiFailure(if(target.usesDeviceKey) R.string.key_auth_failed else R.string.password_auth_failed)
                msg.contains("Algorithm negotiation",true) -> throw UiFailure(R.string.ssh_algorithms)
                else -> throw UiFailure(R.string.ssh_connect_failed)
            }
        } finally { session.disconnect() }
    }
}
