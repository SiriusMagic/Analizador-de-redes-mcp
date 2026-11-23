package com.zirakusa.redes.data

import androidx.core.content.edit

/** The Wi‑Fi the phone is on. [ssid] is null without the location permission (Android hides the name). */
data class WifiNow(val ssid: String?, val bssid: String?, val dbm: Int, val mhz: Int, val linkMbps: Int, val standard: Int = 0,
    val security: Security? = null) {
    val band get() = Radio.band(mhz)
    val channel get() = Radio.channel(mhz)
    val level get() = SignalLevel.of(dbm)
    /** «Wi‑Fi 6» from Android's standard number (4 = 802.11n … 7 = 802.11be). */
    val generation get() = when (standard) { 4 -> 4; 5 -> 5; 6 -> 6; 8 -> 7; else -> 0 }
    fun asAccessPoint() = AccessPoint(ssid.orEmpty(), bssid.orEmpty(), mhz, dbm, security ?: Security.WPA2)
}

/** How the phone is connected right now, as Android tells it. */
data class Status(val link: Link, val online: Boolean = false, val metered: Boolean = false, val wifi: WifiNow? = null,
    val carrier: String = "", val cellType: String = "", val addresses: List<String> = emptyList(), val gateway: String = "",
    val dns: List<String> = emptyList(), val privateDns: String = "", val downKbps: Int = 0, val upKbps: Int = 0) {
    /** «Casa» or «Claro 4G»: what the history says the test was on. */
    fun networkName() = when (link) {
        Link.WIFI -> wifi?.ssid.orEmpty()
        Link.CELLULAR -> listOf(carrier, cellType).filter { it.isNotBlank() }.joinToString(" ")
        else -> ""
    }

    companion object {
        val NONE = Status(Link.NONE)
        /** A local address is private (the router gave it) or public; IPv6 link-local ones (fe80) are left out of the list. */
        fun shown(addresses: List<String>) = addresses.filterNot { it.lowercase().startsWith("fe80") }
    }
}

/** Where the settings live: the phone's preferences in the app, a map in the tests. */
interface KeyValues {
    fun get(key: String): String?
    fun put(key: String, value: String?)
}

class MapValues(private val map: MutableMap<String, String> = mutableMapOf()) : KeyValues {
    override fun get(key: String) = map[key]
    override fun put(key: String, value: String?) { if (value == null) map.remove(key) else map[key] = value }
}

/** Every setting of Redes, typed. */
class Settings(private val kv: KeyValues) {
    private fun flag(key: String, default: Boolean) = kv.get(key)?.let { it == "1" } ?: default
    private fun setFlag(key: String, on: Boolean) = kv.put(key, if (on) "1" else "0")

    /** «Ahorro de datos»: on mobile data the test uses at most 30 MB instead of 120. */
    var saver: Boolean get() = flag("saver", true); set(v) = setFlag("saver", v)
    var theme: String? get() = kv.get("theme"); set(v) = kv.put("theme", v)
    var accent: String? get() = kv.get("accent"); set(v) = kv.put("accent", v)

    fun budget(metered: Boolean) = if (metered && saver) SAVER_BYTES else FULL_BYTES

    companion object { const val FULL_BYTES = 120L shl 20; const val SAVER_BYTES = 30L shl 20 }
}

/** The phone's preferences file «redes» (kept out of backups). The MCP tools and the family summary read the same. */
class SharedValues(context: android.content.Context) : KeyValues {
    private val prefs = context.applicationContext.getSharedPreferences("redes", android.content.Context.MODE_PRIVATE)
    override fun get(key: String): String? = prefs.getString(key, null)
    override fun put(key: String, value: String?) {
        prefs.edit(commit = true) { if (value == null) remove(key) else putString(key, value) }
    }
}
