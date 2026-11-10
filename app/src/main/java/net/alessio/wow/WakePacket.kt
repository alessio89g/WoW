package net.alessio.wow

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress

object WakePacket {
    fun create(mac: String): ByteArray {
        requireText(Regex("(?i)^[0-9a-f]{2}([:-][0-9a-f]{2}){5}$").matches(mac), R.string.invalid_mac)
        val bytes = mac.split(':', '-').map { it.toInt(16).toByte() }
        requireText(bytes[0].toInt() and 1 == 0 && bytes.any { it != 0.toByte() }, R.string.invalid_unicast)
        return ByteArray(102) { if (it < 6) 0xff.toByte() else bytes[(it - 6) % 6] }
    }
    fun validateHost(host: String) {
        requireText(host.isNotBlank() && host.length <= 253 && !host.contains(Regex("[\\s/:?#]")), R.string.host_format)
        requireText(host.split('.').all { it.isNotEmpty() && it.length <= 63 && Regex("[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?").matches(it) }, R.string.invalid_host)
        if (host.all { it.isDigit() || it == '.' }) requireText(host.split('.').size == 4 && host.split('.').all { it.toIntOrNull() in 0..255 }, R.string.invalid_ipv4)
    }
    fun send(host: String, port: Int, mac: String) {
        validateHost(host); require(port in 1..65535)
        val payload = create(mac)
        val address = InetAddress.getAllByName(host).firstOrNull { it is Inet4Address } ?: throw UiFailure(R.string.no_ipv4)
        DatagramSocket().use { socket ->
            socket.broadcast = true
            socket.send(DatagramPacket(payload, payload.size, address, port))
        }
    }
}
