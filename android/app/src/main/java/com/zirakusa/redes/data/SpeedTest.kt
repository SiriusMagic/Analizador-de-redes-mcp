package com.zirakusa.redes.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** The speed test server, as seen from the test (the app uses Cloudflare's, tests a fake). */
interface Transport {
    /** One tiny request; how long the answer took, in ms. */
    suspend fun ping(): Double
    /** Downloads [bytes]; [got] is told each piece as it arrives. Returns what the server says of itself and of you, if it does. */
    suspend fun download(bytes: Long, got: (Int) -> Unit): Origin
    /** Uploads [bytes]; [sent] is told each piece as it leaves. */
    suspend fun upload(bytes: Long, sent: (Int) -> Unit)
}

enum class Phase { PING, DOWNLOAD, UPLOAD, DONE }

/**
 * What the speed server says along with the download: its place ([colo], «UIO») and, for the path diagram, your public
 * address and your provider's network number («AS27947»). Kept only in memory, never in the history or MCP.
 */
data class Origin(val colo: String = "", val ip: String = "", val asn: String = "") {
    companion object {
        /** Only what looks right goes through: letters for the place, an IP literal, digits for the network. */
        fun of(colo: String?, ip: String?, asn: String?) = Origin(
            colo.orEmpty().filter { it.isLetter() }.take(4),
            ip.orEmpty().trim().takeIf { v -> v.length in 3..45 && v.all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' || it == '.' || it == ':' } &&
                (Lan.parts(v) != null || ':' in v) }.orEmpty(),
            asn.orEmpty().trim().removePrefix("AS").takeIf { v -> v.length in 1..10 && v.all(Char::isDigit) }?.let { "AS$it" }.orEmpty())
    }
}

/** A test is already running (only one at a time, from the screen or from MCP). */
class Busy : Exception()

/**
 * Runs one test at a time and keeps it in the history with the network it was made on. [listener] follows the
 * progress (the screen shows it, also when the AI started the test).
 */
class Runner(private val history: History, private val transport: Transport, private val settings: Settings,
    private val seconds: Long = 8, private val clock: () -> Long = System::currentTimeMillis) {
    @Volatile var running = false; private set
    @Volatile var listener: (Phase, Double, Float) -> Unit = { _, _, _ -> }
    /** What Cloudflare said of the last test (public address, provider's network), only in memory. */
    @Volatile var origin: Origin? = null; private set

    suspend fun run(status: Status): Result {
        synchronized(this) { if (running) throw Busy(); running = true }
        try {
            val m = SpeedTest(transport, seconds, budget = settings.budget(status.metered), clock = clock).run { p, v, f -> listener(p, v, f) }
            origin = m.origin
            return history.add(Result(0, clock(), m.down, m.up, m.ping, m.jitter, status.link, status.networkName(), m.origin.colo))
        } finally { running = false }
    }
}

/** A finished measure (before it gets a network name and a place in the history). */
data class Measured(val down: Double, val up: Double, val ping: Double, val jitter: Double, val origin: Origin)

/**
 * A speed test like the well-known ones: 10 pings (median and jitter), then [streams] downloads at once for [seconds]
 * with growing pieces, then the same for uploads. It stops early when [budget] bytes were used (mobile data). [progress]
 * gets the phase, the speed so far (Mbps) and how far the phase is (0–1).
 */
class SpeedTest(private val transport: Transport, private val seconds: Long = 8, private val streams: Int = 3,
    private val budget: Long = 120L shl 20, private val clock: () -> Long = System::currentTimeMillis) {

    suspend fun run(progress: (Phase, Double, Float) -> Unit = { _, _, _ -> }): Measured {
        progress(Phase.PING, 0.0, 0f)
        val pings = (1..10).map { transport.ping() }
        val ping = SpeedMath.median(pings.drop(1)) // the first one also opens the connection
        progress(Phase.PING, ping, 1f)
        var origin = Origin()
        val down = transfer(Phase.DOWNLOAD, progress) { size, got -> transport.download(size, got).let { if (it.colo.isNotBlank() || it.ip.isNotBlank()) origin = it } }
        val up = transfer(Phase.UPLOAD, progress) { size, sent -> transport.upload(size, sent) }
        progress(Phase.DONE, down, 1f)
        return Measured(SpeedMath.round(down), SpeedMath.round(up), Math.round(ping * 10) / 10.0, Math.round(SpeedMath.jitter(pings.drop(1)) * 10) / 10.0, origin)
    }

    private suspend fun transfer(phase: Phase, progress: (Phase, Double, Float) -> Unit, one: suspend (Long, (Int) -> Unit) -> Unit): Double {
        val start = clock()
        val events = ArrayList<Pair<Long, Int>>()
        var used = 0L
        val limit = seconds * 1000
        // Downloads may use three quarters of the budget, uploads the rest.
        val allowed = if (phase == Phase.UPLOAD) budget / 4 else budget / 4 * 3
        fun note(n: Int) = synchronized(events) { events += (clock() - start) to n; used += n }
        fun room() = clock() - start < limit && synchronized(events) { used } < allowed
        withTimeoutOrNull(limit) {
            coroutineScope {
                val workers = List(streams) {
                    launch {
                        var i = 0
                        while (isActive && room()) {
                            try { one(PIECES[minOf(i, PIECES.lastIndex)]) { note(it) } }
                            catch (e: CancellationException) { throw e } catch (_: Exception) { delay(200) }
                            i++
                        }
                    }
                }
                val ticker = launch {
                    while (isActive) {
                        delay(250)
                        val now = synchronized(events) { events.toList() }
                        progress(phase, SpeedMath.throughput(now), ((clock() - start).toFloat() / limit).coerceIn(0f, 1f))
                    }
                }
                workers.forEach { it.join() }
                ticker.cancel()
            }
        }
        return SpeedMath.throughput(synchronized(events) { events.toList() })
    }

    companion object {
        /** Sizes asked one after another by each stream: small first (slow links), then big (fast ones). */
        val PIECES = listOf(100_000L, 1_000_000L, 10_000_000L, 25_000_000L)
    }
}
