package net.alessio.wow
import org.junit.Assert.*
import org.junit.Test
import java.net.DatagramSocket
import java.net.DatagramPacket

class WakePacketTest {
    @Test fun payloadMatchesMagicPacketFormat() {
        val expected="ffffffffffff" + "021122334455".repeat(16)
        assertEquals(expected,WakePacket.create("02:11:22:33:44:55").joinToString("") { "%02x".format(it.toInt() and 255) })
    }
    @Test fun udpDeliveryHasExactlyExpectedPayload() {
        DatagramSocket(0).use { receiver ->
            receiver.soTimeout=2000
            WakePacket.send("127.0.0.1",receiver.localPort,"02:11:22:33:44:55")
            val packet=DatagramPacket(ByteArray(2048),2048)
            receiver.receive(packet)
            assertEquals(102,packet.length)
            assertArrayEquals(WakePacket.create("02:11:22:33:44:55"),packet.data.copyOf(packet.length))
        }
    }
    @Test fun rejectsMalformedInputs() {
        listOf("https://router.example.net", "router.example.net:9", "999.1.1.1", "", "-a.it").forEach { host ->
            assertTrue(runCatching { WakePacket.validateHost(host) }.isFailure)
        }
        listOf("ff:ff:ff:ff:ff:ff", "00:00:00:00:00:00", "02:11:22:33:44", "xyz").forEach { mac ->
            assertTrue(runCatching { WakePacket.create(mac) }.isFailure)
        }
    }
    @Test fun acceptsLocalRemoteAndDdns() {
        listOf("192.168.42.255","127.0.0.1","router.example.net").forEach { WakePacket.validateHost(it) }
    }
}
