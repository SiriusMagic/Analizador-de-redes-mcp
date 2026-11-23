package com.zirakusa.redes.data

/** One network drawn on the spectrum chart: a trapezoid from [lo] to [hi] MHz on the floor, flat top from [topLo] to [topHi]. */
data class Bell(val ap: AccessPoint, val lo: Double, val hi: Double, val topLo: Double, val topHi: Double, val dbm: Int)

/** The x axis of one band, in MHz, and the channels written under it (channel to its MHz). */
data class Axis(val band: Band, val from: Double, val to: Double, val ticks: List<Pair<Int, Int>>) {
    /** 0–1 across the chart. */
    fun x(mhz: Double) = ((mhz - from) / (to - from)).coerceIn(0.0, 1.0)
}

/**
 * The spectrum chart, pure: each network is as wide as its real channel (20–320 MHz) and centred where it really is
 * (Android's centre frequency for 40 MHz or more), as tall as its signal. Axes: −95 … −30 dBm and channels per band.
 */
object Spectrum {
    const val FLOOR = -95
    const val TOP = -30
    /** The lines across the chart, with their labels. */
    val gridDbm = listOf(-30, -40, -50, -60, -70, -80, -90)

    /** Android's ScanResult.channelWidth code (0 = 20 MHz … 5 = 320 MHz) to MHz; 80+80 is drawn as its first 80. */
    fun widthMhz(code: Int) = when (code) { 1 -> 40; 2 -> 80; 3 -> 160; 4 -> 80; 5 -> 320; else -> 20 }

    /** MHz of a channel number in a band. */
    fun mhz(band: Band, channel: Int) = when (band) {
        Band.B24 -> if (channel == 14) 2484 else 2407 + 5 * channel
        Band.B5 -> 5000 + 5 * channel
        Band.B6 -> 5950 + 5 * channel
    }

    fun axis(band: Band): Axis = when (band) {
        Band.B24 -> Axis(band, 2401.0, 2495.0, (1..14).map { it to mhz(band, it) })
        Band.B5 -> Axis(band, 5160.0, 5845.0, listOf(36, 52, 100, 116, 132, 149, 165).map { it to mhz(band, it) })
        Band.B6 -> Axis(band, 5935.0, 7135.0, listOf(1, 37, 69, 101, 133, 165, 197, 233).map { it to mhz(band, it) })
    }

    /** Where the channel is centred: the centre frequency for bonded channels (40 MHz or more), else the primary one. */
    fun center(ap: AccessPoint) = if (ap.widthMhz >= 40 && ap.centerMhz > 0) ap.centerMhz else ap.mhz

    fun bell(ap: AccessPoint): Bell {
        val c = center(ap).toDouble()
        val half = ap.widthMhz / 2.0
        val slope = (ap.widthMhz * 0.1).coerceIn(2.0, 8.0)
        return Bell(ap, c - half, c + half, c - half + slope, c + half - slope, ap.dbm.coerceIn(FLOOR, TOP))
    }

    /** 0 at the floor (−95 dBm), 1 at the top (−30 dBm). */
    fun y(dbm: Int) = (dbm.coerceIn(FLOOR, TOP) - FLOOR).toDouble() / (TOP - FLOOR)

    /** The bells of a band, strongest last (drawn on top). */
    fun bells(aps: List<AccessPoint>, band: Band) = aps.filter { it.band == band }.sortedBy { it.dbm }.map(::bell)

    fun counts(aps: List<AccessPoint>): Map<Band, Int> = Band.entries.associateWith { b -> aps.count { it.band == b } }

    /** «36» for 20 MHz, «36 · 80 MHz» when wider. */
    fun channels(ap: AccessPoint) = if (ap.widthMhz > 20) "${ap.channel} · ${ap.widthMhz} MHz" else "${ap.channel}"
}
