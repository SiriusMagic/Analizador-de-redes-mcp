package com.zirakusa.redes.data

/**
 * How latency is measured; the app gives a short TCP knock to the router and Cloudflare's tiny request, tests a fake.
 * Each answer is the time in ms, or null when nothing came back (lost).
 */
interface Pinger {
    /** The router of the phone's own network ([address] is always a local one, see [Lan.isLocal]). */
    suspend fun gateway(address: String): Double?
    /** «Internet»: speed.cloudflare.com, and nothing else. */
    suspend fun internet(): Double?
}

/** One latency reading: when, and the time (null = lost). */
data class Ping(val at: Long, val ms: Double?)

/** Average, jitter (how much it jumps from one to the next), best and worst, and how many were lost (%). */
data class LatencyStats(val count: Int, val average: Double?, val jitter: Double?, val min: Double?, val max: Double?, val lossPercent: Int) {
    companion object {
        fun of(pings: List<Ping>): LatencyStats {
            val ok = pings.mapNotNull { it.ms }
            val loss = if (pings.isEmpty()) 0 else Math.round((pings.size - ok.size) * 100.0 / pings.size).toInt()
            if (ok.isEmpty()) return LatencyStats(pings.size, null, null, null, null, loss)
            return LatencyStats(pings.size, round(ok.average()), round(SpeedMath.jitter(ok)), round(ok.min()), round(ok.max()), loss)
        }
        private fun round(v: Double) = Math.round(v * 10) / 10.0
    }
}

/** «Latencia»: the router's and Internet's readings, only in memory (the last [capacity], 3 minutes at one each 2 s). */
class LatencyLog(capacity: Int = 90) {
    val gateway = Ring<Ping>(capacity)
    val internet = Ring<Ping>(capacity)
    fun clear() { gateway.clear(); internet.clear() }
}

/**
 * Takes latency readings, one round at a time (a second call while one runs does nothing). The router is only knocked
 * when it is a local address; Internet is skipped while a speed test runs (it would spoil both) and, on mobile data with
 * «Ahorro de datos», asked only every fifth round.
 */
class LatencyMeter(private val pinger: Pinger, val log: LatencyLog, private val clock: () -> Long = System::currentTimeMillis) {
    @Volatile var busy = false; private set
    private var round = 0

    suspend fun once(status: Status, speedTestRunning: Boolean, saver: Boolean): Boolean {
        synchronized(this) { if (busy) return false; busy = true }
        try {
            if (status.link == Link.NONE) return false
            val n = round++
            if (Lan.isLocal(status.gateway)) log.gateway.add(Ping(clock(), pinger.gateway(status.gateway)))
            if (internetDue(n, status.metered, saver) && !speedTestRunning) log.internet.add(Ping(clock(), pinger.internet()))
            return true
        } finally { busy = false }
    }

    companion object {
        fun internetDue(round: Int, metered: Boolean, saver: Boolean) = !(metered && saver) || round % 5 == 0
    }
}

/** Pure maths of the phone's own local network: which addresses are local, and its /24 neighbours. */
object Lan {
    /** An IPv4 address as four numbers, or null. Only digits and dots: never a name to look up. */
    fun parts(ip: String): List<Int>? {
        val p = ip.trim().split('.')
        if (p.size != 4 || p.any { it.isEmpty() || it.length > 3 || !it.all(Char::isDigit) }) return null
        return p.map { it.toInt() }.takeIf { l -> l.all { it in 0..255 } }
    }

    /** Private (10/8, 172.16/12, 192.168/16) or link-local (169.254/16) IPv4, or IPv6 link-local / unique local. */
    fun isLocal(ip: String): Boolean {
        parts(ip)?.let { (a, b) -> return a == 10 || (a == 172 && b in 16..31) || (a == 192 && b == 168) || (a == 169 && b == 254) }
        val v6 = ip.trim().lowercase()
        if (v6.isEmpty() || !v6.all { it.isDigit() || it in 'a'..'f' || it == ':' || it == '%' } || ':' !in v6) return false
        return v6.startsWith("fe80:") || v6.startsWith("fc") || v6.startsWith("fd")
    }

    /** The 254 addresses of the /24 of [ip] (x.y.z.1 … x.y.z.254), only for a private IPv4; else empty. */
    fun hosts(ip: String): List<String> {
        val p = parts(ip)?.takeIf { isLocal(ip) } ?: return emptyList()
        return (1..254).map { "${p[0]}.${p[1]}.${p[2]}.$it" }
    }

    /** A number to sort addresses in order (192.168.1.9 before 192.168.1.10). */
    fun order(ip: String): Long = parts(ip)?.fold(0L) { acc, n -> acc * 256 + n } ?: Long.MAX_VALUE
}
