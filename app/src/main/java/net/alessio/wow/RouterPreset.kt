package net.alessio.wow

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class RouterPreset(val name: String, val template: String, val defaultInterface: String = "") {
    val usesInterface get() = "{interface}" in template
    val supportsBroadcast get() = "{broadcast}" in template

    fun command(mac: String, lanInterface: String, broadcast: Boolean): String {
        WakePacket.create(mac)
        requireText(!usesInterface || interfaceName.matches(lanInterface), R.string.invalid_interface)
        return expand(mac.uppercase().replace('-', ':'), lanInterface, broadcast)
    }

    fun validate() {
        requireText(name.isNotBlank() && name.length <= 80, R.string.preset_name_length)
        expand("02:11:22:33:44:55", "br0", true)
    }

    private fun expand(mac: String, lanInterface: String, broadcast: Boolean): String {
        requireText(template.length in 1..512, R.string.command_length)
        val command = template.replace("{mac}", mac)
            .replace("{interface}", lanInterface)
            .replace("{broadcast}", if (broadcast) "-b" else "")
            .trim().replace(Regex(" +"), " ")
        requireText(commandLine.matches(command), R.string.command_format)
        return command
    }

    fun json() = JSONObject().put("name", name).put("template", template).put("interface", defaultInterface)

    companion object {
        private val interfaceName = Regex("[A-Za-z0-9][A-Za-z0-9_.:-]{0,14}")
        private val commandLine = Regex("[A-Za-z0-9_/][A-Za-z0-9_./:=+,-]*( [A-Za-z0-9_./:=+,-]+)*")

        val asus = RouterPreset("AsusWRT / Merlin", "ether-wake -i {interface} {broadcast} {mac}", "br0")
        val builtIns = listOf(
            asus,
            RouterPreset("OpenWrt", "etherwake -i {interface} {broadcast} {mac}", "br-lan"),
            RouterPreset("MikroTik RouterOS", "/tool wol mac={mac} interface={interface}", "bridge"),
            RouterPreset("Advantech CLI", asus.template, "eth0")
        )

        private val previousBuiltIns = builtIns.zip(listOf(
            "ASUS RT-BE88U / Asuswrt", "OpenWrt · etherwake", "MikroTik · RouterOS", "Advantech · CLI ether-wake"
        )).associate { (preset, oldName) -> preset.copy(name = oldName) to preset }

        fun fromJson(json: JSONObject): RouterPreset {
            val preset = RouterPreset(json.getString("name"), json.getString("template"), json.optString("interface"))
            return previousBuiltIns[preset] ?: preset
        }
    }
}

class RouterPresetStore(context: Context) {
    private val prefs = context.getSharedPreferences("router_presets", Context.MODE_PRIVATE)

    fun read(): List<RouterPreset> {
        val presets = JSONArray(prefs.getString("items", "[]"))
        return List(presets.length()) { RouterPreset.fromJson(presets.getJSONObject(it)) }
    }

    fun save(presets: List<RouterPreset>) {
        val json = JSONArray()
        presets.forEach { json.put(it.json()) }
        requireText(prefs.edit().putString("items", json.toString()).commit(), R.string.preset_save_failed)
    }
}
