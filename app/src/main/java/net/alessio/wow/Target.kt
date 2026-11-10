package net.alessio.wow

import org.json.JSONObject

data class Target(
    val id: String,
    val name: String,
    val host: String,
    val port: Int,
    val mac: String,
    val mode: String = "udp",
    val username: String = "",
    val password: String = "",
    val lanInterface: String = "br0",
    val broadcast: Boolean = false,
    val hostKey: String = "",
    val auth: String = "password",
    val routerPreset: RouterPreset = RouterPreset.asus
) {
    val isSsh get() = mode == "ssh"
    val usesDeviceKey get() = auth == "key"
    fun json() = JSONObject().put("id",id).put("name",name).put("host",host)
        .put("port",port).put("mac",mac).put("mode",mode).put("username",username)
        .put("password",password).put("lanInterface",lanInterface).put("broadcast",broadcast).put("hostKey",hostKey).put("auth",auth).put("routerPreset",routerPreset.json())
    companion object {
        fun fromJson(j: JSONObject) = Target(j.getString("id"),j.getString("name"),j.getString("host"),j.getInt("port"),j.getString("mac"),
            j.optString("mode","udp"),j.optString("username",""),j.optString("password",""),
            j.optString("lanInterface","br0"),j.optBoolean("broadcast",false),j.optString("hostKey",""),j.optString("auth","password"),
            j.optJSONObject("routerPreset")?.let(RouterPreset::fromJson) ?: RouterPreset.asus)
    }
}
