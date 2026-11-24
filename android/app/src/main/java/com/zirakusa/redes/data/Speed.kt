package com.zirakusa.redes.data

import org.json.JSONArray
import org.json.JSONObject

/** How the phone is connected. [key] is what MCP uses. */
enum class Link(val key: String) { WIFI("wifi"), CELLULAR("cellular"), ETHERNET("ethernet"), VPN("vpn"), NONE("none") }

/** What a connection is good for, by its speed and delay. [key] is what MCP uses. */
enum class Use(val key: String) {
    BROWSE("browse"), MUSIC("music"), VIDEO_HD("video_hd"), VIDEO_4K("video_4k"), CALLS("video_calls"), GAMING("gaming"), UPLOADS("big_uploads")
}

/** One speed test. Speeds in Mbps, delay in ms. [network] is the Wi‑Fi's name or the carrier. */
data class Result(val id: Long, val at: Long, val down: Double, val up: Double, val ping: Double, val jitter: Double, val link: Link,
    val network: String = "", val server: String = "") {
    val uses get() = Grade.uses(this)
    fun json(): JSONObject = JSONObject().put("id", id).put("at", at).put("down", down).put("up", up).put("ping", ping).put("jitter", jitter)
        .put("link", link.key).put("network", network).put("server", server)
    companion object {
        fun from(o: JSONObject) = Result(o.getLong("id"), o.getLong("at"), o.getDouble("down"), o.getDouble("up"), o.getDouble("ping"),
            o.optDouble("jitter", 0.0), Link.entries.firstOrNull { it.key == o.optString("link") } ?: Link.NONE, o.optString("network"), o.optString("server"))
    }
}

/** Pure speed maths. */
object SpeedMath {
    /** Megabits per second from bytes over milliseconds. */
    fun mbps(bytes: Long, millis: Long) = if (millis <= 0) 0.0 else bytes * 8.0 / millis / 1000.0

    fun median(values: List<Double>): Double {
        if (values.isEmpty()) return 0.0
        val s = values.sorted(); val m = s.size / 2
        return if (s.size % 2 == 1) s[m] else (s[m - 1] + s[m]) / 2
    }

    /** How much the delay jumps between one measure and the next (average of the differences). */
    fun jitter(values: List<Double>) = if (values.size < 2) 0.0 else values.zipWithNext { a, b -> kotlin.math.abs(b - a) }.average()

    /**
     * The speed of a transfer, from when each piece arrived ([events]: ms since start → bytes). The first [warmup] ms
     * are left out (the connection is still speeding up), as speed tests do.
     */
    fun throughput(events: List<Pair<Long, Int>>, warmup: Long = 1000): Double {
        if (events.isEmpty()) return 0.0
        val end = events.maxOf { it.first }
        val from = if (end > warmup * 2) warmup else 0L
        val bytes = events.filter { it.first > from }.sumOf { it.second.toLong() }
        return mbps(bytes, end - from)
    }

    /** One decimal below 100, whole above: «8,4», «245». */
    fun round(mbps: Double) = if (mbps < 100) Math.round(mbps * 10) / 10.0 else Math.round(mbps).toDouble()
}

/** What a result is good for. Pure. */
object Grade {
    fun uses(r: Result): List<Use> = buildList {
        if (r.down >= 1) add(Use.BROWSE)
        if (r.down >= 0.5) add(Use.MUSIC)
        if (r.down >= 5) add(Use.VIDEO_HD)
        if (r.down >= 25) add(Use.VIDEO_4K)
        if (r.down >= 3 && r.up >= 1.5 && r.ping < 150) add(Use.CALLS)
        if (r.down >= 3 && r.ping <= 50 && r.jitter <= 15) add(Use.GAMING)
        if (r.up >= 10) add(Use.UPLOADS)
    }
}

/** The tests made, newest first, at most [MAX]. JSON in the app's preferences. */
class History(private val kv: KeyValues) {
    fun list(): List<Result> = runCatching {
        val a = JSONArray(kv.get(KEY) ?: "[]")
        (0 until a.length()).map { Result.from(a.getJSONObject(it)) }
    }.getOrDefault(emptyList())

    fun add(r: Result): Result {
        val items = list()
        val saved = r.copy(id = (items.maxOfOrNull { it.id } ?: 0) + 1)
        kv.put(KEY, JSONArray((listOf(saved) + items).take(MAX).map { it.json() }).toString())
        return saved
    }

    fun clear() = kv.put(KEY, null)

    companion object { const val MAX = 200; private const val KEY = "history" }
}

/** «Historial de prueba de velocidad»: the search and the bars, pure. */
object HistoryView {
    /** Words of the search found in the network's name, the kind («wifi», «celular», «móvil»…) or the server. Accents do not matter. */
    fun search(list: List<Result>, query: String): List<Result> {
        val words = plain(query).split(' ').filter { it.isNotBlank() }
        if (words.isEmpty()) return list
        return list.filter { r ->
            val text = plain(listOf(r.network, r.server, r.link.key, when (r.link) {
                Link.WIFI -> "wi-fi wifi"; Link.CELLULAR -> "celular movil datos mobile"; Link.ETHERNET -> "cable ethernet"; Link.VPN -> "vpn"; Link.NONE -> ""
            }).joinToString(" "))
            words.all { it in text }
        }
    }

    /** The top of the bars: the fastest download or upload of the list (at least 1 Mbps). */
    fun top(list: List<Result>) = maxOf(1.0, list.maxOfOrNull { maxOf(it.down, it.up) } ?: 0.0)

    /** How long a bar is, 0–1 (a tiny one still shows). */
    fun bar(mbps: Double, top: Double) = if (mbps <= 0) 0f else (mbps / top).toFloat().coerceIn(0.02f, 1f)

    private fun plain(s: String) = java.text.Normalizer.normalize(s.lowercase(), java.text.Normalizer.Form.NFD).filter { it.code !in 0x300..0x36f }
}
