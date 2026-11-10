package net.alessio.wow

import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.json.JSONObject
import java.io.File

class SshWakeTest {
    @Test fun buildsOnlyValidatedCommands() {
        assertEquals("ether-wake -i br0 02:11:22:33:44:55",RouterPreset.asus.command("02-11-22-33-44-55","br0",false))
        assertEquals("ether-wake -i br0 -b 02:11:22:33:44:55",RouterPreset.asus.command("02:11:22:33:44:55","br0",true))
        listOf("br0; reboot","$(reboot)","br0\nreboot","-b", "br0 foo").forEach {
            assertTrue(runCatching { RouterPreset.asus.command("02:11:22:33:44:55",it,false) }.isFailure)
        }
        assertTrue(runCatching { RouterPreset.asus.command("02:11:22:33:44:55;reboot","br0",false) }.isFailure)
    }
    @Test fun existingUdpProfilesKeepTheirMeaning() {
        val original=JSONObject().put("id","old").put("name","LAN").put("host","192.168.1.255").put("port",9).put("mac","02:11:22:33:44:55")
        val t=Target.fromJson(original)
        assertFalse(t.isSsh);assertEquals(9,t.port);assertEquals("",t.password)
        assertEquals(t,Target.fromJson(t.json()))
    }
    @Test fun sshCredentialsRoundTripWithSpecialCharacters() {
        val t=Target("ssh","Router","router.test",2222,"02:11:22:33:44:55",mode="ssh",username="wow",password="special $ ` \\ \" !")
        assertEquals(t,Target.fromJson(t.json()))
    }
    @Test fun existingSshProfileKeepsItsCommandAndLanSettings() {
        val old = Target("old", "ASUS", "router.test", 2222, "02:11:22:33:44:55",
            mode = "ssh", lanInterface = "vlan1", broadcast = true)
        val json = old.json()
        json.remove("routerPreset")
        val migrated = Target.fromJson(json)
        assertEquals(old, migrated)
        assertEquals("ether-wake -i vlan1 -b 02:11:22:33:44:55",
            migrated.routerPreset.command(migrated.mac, migrated.lanInterface, migrated.broadcast))
    }

    @Test fun firmwareCommandsUseTheirOwnSyntax() {
        val expected = listOf(
            "ether-wake -i br0 -b 02:11:22:33:44:55",
            "etherwake -i br-lan -b 02:11:22:33:44:55",
            "/tool wol mac=02:11:22:33:44:55 interface=bridge",
            "ether-wake -i eth0 -b 02:11:22:33:44:55"
        )
        assertEquals(expected, RouterPreset.builtIns.map {
            it.command("02:11:22:33:44:55", it.defaultInterface, true)
        })
    }

    @Test fun savedComputerCarriesItsCustomPreset() {
        val preset = RouterPreset("Test", "/usr/bin/wakeonlan {mac}")
        preset.validate()
        val target = Target("test", "PC", "router.test", 2222, "02:11:22:33:44:55",
            mode = "ssh", routerPreset = preset)
        val restored = Target.fromJson(target.json())
        assertEquals(target, restored)
        assertEquals("/usr/bin/wakeonlan 02:11:22:33:44:55",
            restored.routerPreset.command(restored.mac, "", false))
        for (bad in listOf("wake {mac}; other", "wake {unknown}", "wake {mac} && other")) {
            assertTrue(runCatching { preset.copy(template = bad).validate() }.isFailure)
        }
    }

    @Test fun sshFixtureVerifiesHostBeforeAuthenticationAndExecutesOnlyWake() {
        assumeTrue("Set WOW_SSH_TEST_EVENTS to run the local SSH integration fixture",System.getenv("WOW_SSH_TEST_EVENTS")!=null)
        val log=File(System.getenv("WOW_SSH_TEST_EVENTS")!!)
        fun authCount()=if(log.exists())log.readLines().count{it.contains("\"auth\"")} else 0
        val t=Target("fixture","Test","127.0.0.1",22333,"02:11:22:33:44:55","ssh","wow-test","test-only-special $ ` \\ \" !")
        val before=authCount()
        val probe=runCatching{SshWake.run(t.copy(password=""),probe=true)}.exceptionOrNull()
        assertTrue(probe.toString(),probe is RouterKeyRequired)
        val key=(probe as RouterKeyRequired).receivedKey
        assertFalse(probe.changed);assertTrue(SshWake.fingerprint(key).startsWith("SHA256:"))
        assertEquals("Probe must not authenticate",before,authCount())
        val pinned=t.copy(hostKey=key)
        SshWake.run(pinned)
        SshWake.run(pinned.copy(broadcast=true))
        SshWake.run(pinned.copy(routerPreset = RouterPreset("Test", "/tool wol mac={mac} interface={interface}")))
        assertEquals(before+3,authCount())
        val badKey=java.util.Base64.getEncoder().encodeToString(byteArrayOf(1,2,3))
        val mismatch=runCatching{SshWake.run(t.copy(hostKey=badKey))}.exceptionOrNull()
        assertTrue(mismatch is RouterKeyRequired && mismatch.changed)
        assertEquals("Mismatch must not authenticate",before+3,authCount())
        val badPassword=runCatching{SshWake.run(pinned.copy(password="wrong"))}.exceptionOrNull()
        assertTrue((badPassword as? UiFailure)?.text?.id == R.string.password_auth_failed)
        val missing=runCatching{SshWake.run(pinned.copy(lanInterface="missing0"))}.exceptionOrNull()
        assertTrue((missing as? UiFailure)?.text?.id == R.string.wol_unavailable)
    }
}
