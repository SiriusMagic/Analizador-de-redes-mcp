package com.zirakusa.redes.data

import kotlin.math.abs

/** Wi‑Fi bands. [key] is what MCP uses. */
enum class Band(val key: String, val label: String) { B24("2.4", "2,4 GHz"), B5("5", "5 GHz"), B6("6", "6 GHz") }

/** How good a Wi‑Fi signal is, from its strength in dBm. */
enum class SignalLevel(val key: String, val from: Int) {
    EXCELLENT("excellent", -55), GOOD("good", -67), FAIR("fair", -75), WEAK("weak", -85), POOR("poor", Int.MIN_VALUE);
    companion object { fun of(dbm: Int) = entries.first { dbm >= it.from } }
}

/** How a Wi‑Fi network is protected, from Android's capabilities text («[WPA2-PSK-CCMP][ESS]»). */
enum class Security(val key: String, val label: String) {
    WPA3("wpa3", "WPA3"), WPA2("wpa2", "WPA2"), WPA("wpa", "WPA"), WEP("wep", "WEP"), OPEN("open", "—");
    /** Old or none: others nearby can read what goes through it. */
    val weak get() = this == WEP || this == OPEN || this == WPA
    companion object {
        fun of(capabilities: String): Security {
            val c = capabilities.uppercase()
            return when {
                "SAE" in c || "WPA3" in c -> WPA3
                "WPA2" in c || "RSN" in c -> WPA2
                "WPA" in c -> WPA
                "WEP" in c -> WEP
                else -> OPEN
            }
        }
    }
}

/** Pure radio maths: channel and band from the frequency, signal quality and bars. */
object Radio {
    fun band(mhz: Int): Band? = when (mhz) { in 2400..2500 -> Band.B24; in 4900..5899 -> Band.B5; in 5925..7125 -> Band.B6; else -> null }

    fun channel(mhz: Int): Int = when (mhz) {
        2484 -> 14
        in 2412..2472 -> (mhz - 2407) / 5
        in 5000..5899 -> (mhz - 5000) / 5
        in 5955..7115 -> (mhz - 5950) / 5
        else -> 0
    }

    /** 0–100 %, as Android shows: −100 dBm or worse is 0, −50 or better is 100. */
    fun quality(dbm: Int) = (2 * (dbm + 100)).coerceIn(0, 100)

    /** 0–4 bars. */
    fun bars(dbm: Int) = when (SignalLevel.of(dbm)) {
        SignalLevel.EXCELLENT -> 4; SignalLevel.GOOD -> 3; SignalLevel.FAIR -> 2; SignalLevel.WEAK -> 1; SignalLevel.POOR -> 0
    }
}

/** A Wi‑Fi network seen nearby. Hidden networks have an empty [ssid]. */
data class AccessPoint(val ssid: String, val bssid: String, val mhz: Int, val dbm: Int, val security: Security) {
    val band get() = Radio.band(mhz)
    val channel get() = Radio.channel(mhz)
}

/** What to change: a calmer channel on the router, and how crowded the current one is (0 empty … 100 very). */
data class ChannelAdvice(val band: Band, val current: Int, val load: Int, val best: Int, val bestLoad: Int) {
    val worthChanging get() = best != current && load - bestLoad >= 25
}

/**
 * How crowded each channel is with the networks nearby. On 2.4 GHz channels overlap (each network spreads ±2
 * channels, fading), so only 1, 6 and 11 are proposed; on 5 GHz each channel stands alone. Stronger networks weigh
 * more. Pure.
 */
object Channels {
    val clean24 = listOf(1, 6, 11)
    val common5 = listOf(36, 40, 44, 48, 149, 153, 157, 161)

    /** A network's weight: a strong one (−40 dBm) 1, a faint one (−90) almost nothing. */
    private fun weight(dbm: Int) = ((dbm + 95).coerceIn(0, 55)) / 55.0

    fun load(aps: List<AccessPoint>, band: Band, channel: Int, ownBssid: String? = null): Int {
        val score = aps.filter { it.band == band && it.bssid != ownBssid }.sumOf { ap ->
            val d = abs(ap.channel - channel)
            when {
                band != Band.B24 -> if (d == 0) weight(ap.dbm) else 0.0
                d >= 5 -> 0.0
                else -> weight(ap.dbm) * (1.0 - d / 5.0)
            }
        }
        return (score * 34).toInt().coerceIn(0, 100) // three strong neighbours on the same channel ≈ 100
    }

    /** Advice for the network we are on ([own]), or null when it is not on 2.4 or 5 GHz. */
    fun advice(own: AccessPoint, aps: List<AccessPoint>): ChannelAdvice? {
        val band = own.band ?: return null
        val options = when (band) { Band.B24 -> clean24; Band.B5 -> common5; Band.B6 -> return null }
        val current = load(aps, band, own.channel, own.bssid)
        val (best, bestLoad) = options.map { it to load(aps, band, it, own.bssid) }.minWith(compareBy({ it.second }, { abs(it.first - own.channel) }))
        return ChannelAdvice(band, own.channel, current, best, bestLoad)
    }

    /** Networks per channel, for the chart. */
    fun count(aps: List<AccessPoint>, band: Band): Map<Int, Int> = aps.filter { it.band == band }.groupingBy { it.channel }.eachCount()
}
