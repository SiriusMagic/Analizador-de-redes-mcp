package com.zirakusa.redes.data

import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.ceil
import kotlin.math.floor

/** The last [capacity] items, oldest first; the oldest falls out when full. Thread safe (screen and MCP read it). */
class Ring<T>(val capacity: Int) {
    private val items = ArrayDeque<T>(capacity)
    @Synchronized fun add(item: T) { if (items.size == capacity) items.removeFirst(); items.addLast(item) }
    @Synchronized fun list(): List<T> = items.toList()
    @Synchronized fun clear() = items.clear()
    @Synchronized fun last(): T? = items.lastOrNull()
    val size @Synchronized get() = items.size
}

/** One reading of the Wi‑Fi's signal: when (ms) and how strong (dBm). */
data class Sample(val at: Long, val dbm: Int)

/** «Señal»: readings of the connected Wi‑Fi every ~2 s while Redes is open, only in memory (6 minutes). */
class SignalLog(capacity: Int = 180) {
    val ring = Ring<Sample>(capacity)
    fun add(at: Long, w: WifiNow?) { if (w != null && w.dbm in -127..0) ring.add(Sample(at, w.dbm)) }
    /** The readings of the last [ms] before [now]. */
    fun recent(now: Long, ms: Long) = ring.list().filter { now - it.at in 0..ms }
}

/** A point of a chart: x and y from 0 to 1 (y up); y null is a gap (a lost ping). */
data class ChartPoint(val x: Float, val y: Float?)

/** A chart's value axis: from [lo] to [hi], with a line every [step]. */
data class ChartRange(val lo: Double, val hi: Double, val step: Double) {
    val lines get() = generateSequence(lo) { it + step }.takeWhile { it <= hi + 1e-9 }.toList()
    fun y(v: Double) = if (hi <= lo) 0f else ((v - lo) / (hi - lo)).toFloat().coerceIn(0f, 1f)
}

/** Pure chart maths for «Señal» and «Latencia». */
object Chart {
    /** dBm: whole tens around the readings, at least 30 dB tall (−100…−30 when there is nothing yet). */
    fun dbm(values: List<Int>): ChartRange {
        if (values.isEmpty()) return ChartRange(-100.0, -30.0, 10.0)
        var lo = floor((values.min() - 5) / 10.0) * 10
        var hi = ceil((values.max() + 5) / 10.0) * 10
        while (hi - lo < 30) { if (hi < 0) hi += 10; if (hi - lo < 30) lo -= 10 }
        return ChartRange(lo.coerceAtLeast(-120.0), hi.coerceAtMost(0.0), 10.0)
    }

    /** Milliseconds: from 0 to a round top above the highest (at least 50 ms), with four lines. */
    fun ms(values: List<Double>): ChartRange {
        val top = (values.maxOrNull() ?: 0.0) * 1.2
        val hi = listOf(50.0, 100.0, 200.0, 300.0, 500.0, 1000.0, 2000.0).firstOrNull { it >= top } ?: (ceil(top / 1000) * 1000)
        return ChartRange(0.0, hi, hi / 4)
    }

    /** Where each reading goes: x by its time in the [window] ending at [now], y on [range]. Older ones are left out. */
    fun points(values: List<Pair<Long, Double?>>, now: Long, window: Long, range: ChartRange): List<ChartPoint> =
        values.filter { now - it.first in 0..window }.map { (at, v) ->
            ChartPoint(1f - (now - at).toFloat() / window, v?.let(range::y))
        }
}

/** The access point of your own Wi‑Fi changed: from one to another of the same network, with the signal before and after. */
data class RoamEvent(val at: Long, val ssid: String, val from: String, val to: String, val dbmBefore: Int, val dbmAfter: Int,
    val fromChannel: Int, val toChannel: Int) {
    fun json(): JSONObject = JSONObject().put("at", at).put("ssid", ssid).put("from", from).put("to", to).put("before", dbmBefore)
        .put("after", dbmAfter).put("fc", fromChannel).put("tc", toChannel)
    companion object {
        fun from(o: JSONObject) = RoamEvent(o.getLong("at"), o.optString("ssid"), o.optString("from"), o.optString("to"),
            o.optInt("before"), o.optInt("after"), o.optInt("fc"), o.optInt("tc"))
    }
}

/**
 * «Roaming de punto de acceso»: watches the connected Wi‑Fi (only your own network, never the neighbours) and keeps
 * each change of access point in the phone's preferences (out of backups), newest first, at most [MAX]. A change of
 * network is not roaming; without the location permission Android hides the access point, so nothing is noted.
 */
class Roaming(private val kv: KeyValues) {
    private var last: WifiNow? = null

    fun list(): List<RoamEvent> = runCatching {
        val a = JSONArray(kv.get(KEY) ?: "[]"); (0 until a.length()).map { RoamEvent.from(a.getJSONObject(it)) }
    }.getOrDefault(emptyList())

    /** Called with every reading; returns the change it saw, if any. */
    @Synchronized fun observe(w: WifiNow?, at: Long): RoamEvent? {
        val before = last
        last = w?.takeIf { it.bssid != null }
        if (before == null || w?.bssid == null || before.bssid.equals(w.bssid, true)) return null
        if (before.ssid != w.ssid || w.ssid == null) return null
        val e = RoamEvent(at, w.ssid, before.bssid.orEmpty(), w.bssid, before.dbm, w.dbm, before.channel, w.channel)
        kv.put(KEY, JSONArray((listOf(e) + list()).take(MAX).map { it.json() }).toString())
        return e
    }

    fun clear() = kv.put(KEY, null)

    companion object { const val MAX = 100; private const val KEY = "roaming" }
}
