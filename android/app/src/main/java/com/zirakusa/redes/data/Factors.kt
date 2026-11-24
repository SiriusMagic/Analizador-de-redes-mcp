package com.zirakusa.redes.data

/** Overall verdict of the speed factors. */
enum class Verdict(val key: String) { GOOD("good"), FAIR("fair"), BAD("bad");
    companion object {
        /** No warnings is good, one or two fair, more bad. */
        fun of(warnings: Int) = when { warnings <= 0 -> GOOD; warnings <= 2 -> FAIR; else -> BAD }
    }
}

/** What limits the Wi‑Fi's speed. Each check is true when it is fine. */
data class WifiFactors(val band: Band?, val widthMhz: Int, val generation: Int, val dbm: Int, val maxRxMbps: Int, val maxTxMbps: Int,
    val channel: Int, val load: Int) {
    /** 5 or 6 GHz and wider than 20 MHz (an unknown width is not held against it). */
    val spectrumOk get() = band != null && band != Band.B24 && widthMhz != 20
    /** Signal of −67 dBm or better and Wi‑Fi 5 or newer (an unknown standard is not held against it). */
    val radioOk get() = !weakSignal && !oldStandard
    val weakSignal get() = dbm < GOOD_DBM
    val oldStandard get() = generation in 1..4
    /** The channel is not crowded with neighbours. */
    val channelOk get() = load < CROWDED
    val warnings get() = listOf(spectrumOk, radioOk, channelOk).count { !it }
    val verdict get() = Verdict.of(warnings)
    /** The keys of what is wrong, for MCP. */
    val problems get() = buildList {
        if (!spectrumOk) add("spectrum"); if (!radioOk) add("radio"); if (!channelOk) add("channel")
    }

    companion object {
        const val GOOD_DBM = -67
        const val CROWDED = 50

        fun of(w: WifiNow, aps: List<AccessPoint>): WifiFactors {
            val load = w.band?.let { Channels.load(aps, it, w.channel, w.bssid) } ?: 0
            return WifiFactors(w.band, w.widthMhz, w.generation, w.dbm, w.maxRxMbps, w.maxTxMbps, w.channel, load)
        }
    }
}

/** Mobile network kinds, with the signal (dBm) under which each is weak: RSRP for 4G, SS‑RSRP for 5G, RSCP / RSSI before. */
enum class CellTech(val label: String, val weakBelow: Int, val old: Boolean) {
    G2("2G", -95, true), G3("3G", -95, true), G4("4G", -95, false), G4_PLUS("4G+", -95, false), G5("5G", -100, false), UNKNOWN("—", -95, false);

    companion object {
        /**
         * From Android's data network type (TelephonyManager.NETWORK_TYPE_*, by number so this stays pure) and how many
         * carriers are joined: LTE with more than one is 4G+.
         */
        fun of(type: Int, carriers: Int = 1): CellTech = when (type) {
            20 -> G5                                   // NR
            13, 19 -> if (carriers > 1 || type == 19) G4_PLUS else G4 // LTE, LTE_CA
            3, 5, 6, 8, 9, 10, 12, 14, 15, 17 -> G3    // UMTS, EVDO 0/A/B, HSDPA, HSUPA, HSPA, EHRPD, HSPAP, TD_SCDMA
            1, 2, 4, 7, 11, 16 -> G2                   // GPRS, EDGE, CDMA, 1xRTT, IDEN, GSM
            else -> UNKNOWN
        }
    }
}

/** One active SIM: slot (1, 2…), carrier, kind and signal (null when Android does not say). */
data class Sim(val slot: Int, val carrier: String, val tech: CellTech, val dbm: Int?) {
    val weak get() = dbm != null && dbm < tech.weakBelow
    val warnings get() = (if (weak) 1 else 0) + (if (tech.old) 1 else 0)
}
