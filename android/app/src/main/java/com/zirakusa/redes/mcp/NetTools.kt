package com.zirakusa.redes.mcp

import com.zirakusa.redes.data.AccessPoint
import com.zirakusa.redes.data.Channels
import com.zirakusa.redes.data.Link
import com.zirakusa.redes.data.Result
import com.zirakusa.redes.data.Status
import com.zirakusa.redes.data.WifiFactors
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId

/** What the tools read and run; the app gives Android's connection and Cloudflare, tests a fake. */
interface NetData {
    fun status(): Status
    fun history(): List<Result>
    /** The Wi‑Fi networks of the last look around (empty without permission). */
    fun nearby(): List<AccessPoint>
    /** Runs a speed test and keeps it; [com.zirakusa.redes.data.Busy] when one is running. */
    suspend fun speedTest(): Result
    fun zone(): ZoneId = ZoneId.systemDefault()
}

/** An expected failure the AI can read (structuredContent.error.code), never a crash. */
class ToolFailure(val code: String, message: String) : Exception(message)

/**
 * Redes' MCP tools: the connection now, the Wi‑Fi networks nearby with channel advice, and the speed tests made; with a
 * read-and-write token the AI can also run a test (it uses data, so on mobile data only when asked to).
 */
object NetTools {
    class Tool(val name: String, val description: String, val write: Boolean, val schema: JSONObject)

    private fun schema(required: List<String>, vararg props: Pair<String, JSONObject>) = JSONObject()
        .put("type", "object").put("properties", JSONObject().apply { props.forEach { (k, v) -> put(k, v) } })
        .put("required", JSONArray(required)).put("additionalProperties", false)
    private fun prop(type: String, description: String) = JSONObject().put("type", type).put("description", description)

    val tools = listOf(
        Tool("network_status", "How the phone is connected now: Wi‑Fi (name, signal, band, channel, speed of the link) or mobile data, " +
            "local addresses, router and DNS; for Wi‑Fi also the channel width and speed factors (grade good/fair/bad and what limits it: " +
            "spectrum, radio, channel).", false, schema(emptyList())),
        Tool("network_wifi_nearby", "Wi‑Fi networks seen nearby (name, signal, band, channel, channel width, security) and, for the phone's Wi‑Fi, " +
            "a calmer channel to set on the router.", false, schema(emptyList(), "limit" to prop("integer", "1–50, default 20."))),
        Tool("network_speed_history", "The speed tests made, newest first: download and upload in Mbps, ping and jitter in ms, " +
            "and what the connection is good for.", false, schema(emptyList(), "limit" to prop("integer", "1–50, default 10."))),
        Tool("network_speed_result", "One speed test by its id (from network_speed_history).", false,
            schema(listOf("id"), "id" to prop("integer", "From network_speed_history."))),
        Tool("network_speed_test", "Runs a speed test now (about 20 seconds; uses up to 120 MB, 30 MB on mobile data with data saver) and keeps it.",
            true, schema(emptyList(), "allow_mobile" to prop("boolean", "Also run on mobile data (it uses the user's data). Default false."))),
    )

    fun available(access: Access) = tools.filter { !it.write || access == Access.WRITE }

    suspend fun call(name: String, args: JSONObject, access: Access, data: NetData): JSONObject {
        val tool = tools.firstOrNull { it.name == name } ?: throw ToolFailure("UNKNOWN_TOOL", "No tool named $name.")
        if (tool.write && access != Access.WRITE) throw ToolFailure("READ_ONLY", "This token can only read. Ask for a read-and-write token.")
        val known = tool.schema.getJSONObject("properties").keys().asSequence().toSet()
        args.keys().forEach { if (it !in known) throw ToolFailure("BAD_ARGUMENT", "Unknown argument «$it».") }
        return when (name) {
            "network_status" -> status(data.status(), data.nearby())
            "network_wifi_nearby" -> nearby(data, limit(args, 20, 50))
            "network_speed_history" -> limit(args, 10, 50).let { n ->
                val all = data.history()
                JSONObject().put("total", all.size).put("tests", JSONArray(all.take(n).map { result(it, data.zone()) }))
            }
            "network_speed_result" -> {
                val id = (args.opt("id") as? Int)?.toLong() ?: (args.opt("id") as? Long) ?: throw ToolFailure("BAD_ARGUMENT", "«id» is a whole number.")
                val r = data.history().firstOrNull { it.id == id } ?: throw ToolFailure("NOT_FOUND", "No speed test with id $id.")
                JSONObject().put("test", result(r, data.zone()))
            }
            "network_speed_test" -> test(args, data)
            else -> throw ToolFailure("UNKNOWN_TOOL", "No tool named $name.")
        }
    }

    private fun status(s: Status, aps: List<AccessPoint>): JSONObject {
        val o = JSONObject().put("link", s.link.key).put("online", s.online).put("metered", s.metered)
            .put("addresses", JSONArray(s.addresses)).put("gateway", s.gateway).put("dns", JSONArray(s.dns)).put("private_dns", s.privateDns)
        s.wifi?.let { w ->
            o.put("wifi", JSONObject().put("name", w.ssid ?: JSONObject.NULL).put("signal_dbm", w.dbm).put("signal", w.level.key)
                .put("band", w.band?.key ?: JSONObject.NULL).put("channel", w.channel).put("link_mbps", w.linkMbps)
                .put("generation", if (w.generation > 0) w.generation else JSONObject.NULL).put("security", w.security?.key ?: JSONObject.NULL)
                .put("width_mhz", if (w.widthMhz > 0) w.widthMhz else JSONObject.NULL)
                .put("max_link_mbps", if (w.maxRxMbps > 0) JSONObject().put("rx", w.maxRxMbps).put("tx", w.maxTxMbps) else JSONObject.NULL)
                .put("factors", WifiFactors.of(w, aps).let { f ->
                    JSONObject().put("grade", f.verdict.key).put("warnings", JSONArray(f.problems)).put("channel_load", f.load)
                }))
        }
        if (s.link == Link.CELLULAR) o.put("mobile", JSONObject().put("carrier", s.carrier).put("type", s.cellType))
        return o
    }

    private fun nearby(data: NetData, limit: Int): JSONObject {
        val aps = data.nearby()
        val own = data.status().wifi
        val o = JSONObject().put("total", aps.size).put("networks", JSONArray(aps.take(limit).map { ap ->
            JSONObject().put("name", ap.ssid.ifEmpty { "(hidden)" }).put("signal_dbm", ap.dbm).put("band", ap.band?.key ?: JSONObject.NULL)
                .put("channel", ap.channel).put("width_mhz", ap.widthMhz).put("security", ap.security.key).put("yours", own?.bssid != null && own.bssid.equals(ap.bssid, true))
        }))
        if (aps.isEmpty()) o.put("note", "No list yet: the user has to allow «Wi‑Fi cercanas» in Redes (Android asks for it).")
        own?.let { w -> Channels.advice(w.asAccessPoint(), aps) }?.let { a ->
            o.put("advice", JSONObject().put("band", a.band.key).put("channel", a.current).put("load", a.load).put("best_channel", a.best)
                .put("best_load", a.bestLoad).put("worth_changing", a.worthChanging))
        }
        return o
    }

    private suspend fun test(args: JSONObject, data: NetData): JSONObject {
        val mobileOk = if (args.has("allow_mobile")) args.opt("allow_mobile") as? Boolean ?: throw ToolFailure("BAD_ARGUMENT", "«allow_mobile» is true or false.") else false
        val s = data.status()
        if (s.link == Link.NONE || !s.online) throw ToolFailure("NO_CONNECTION", "The phone has no working internet connection now.")
        if (s.link == Link.CELLULAR && !mobileOk) throw ToolFailure("ON_MOBILE_DATA", "The phone is on mobile data; a test uses the user's data. Ask the user, then call again with allow_mobile true.")
        val r = try { data.speedTest() } catch (_: com.zirakusa.redes.data.Busy) { throw ToolFailure("BUSY", "A speed test is already running on the phone.") }
        return JSONObject().put("test", result(r, data.zone()))
    }

    private fun result(r: Result, zone: ZoneId) = JSONObject().put("id", r.id)
        .put("at", Instant.ofEpochMilli(r.at).atZone(zone).toLocalDateTime().withNano(0).toString())
        .put("download_mbps", r.down).put("upload_mbps", r.up).put("ping_ms", r.ping).put("jitter_ms", r.jitter).put("link", r.link.key)
        .put("network", r.network).put("server", r.server).put("good_for", JSONArray(r.uses.map { it.key }))

    private fun limit(args: JSONObject, default: Int, max: Int): Int {
        if (!args.has("limit")) return default
        val n = (args.opt("limit") as? Int) ?: (args.opt("limit") as? Long)?.toInt() ?: throw ToolFailure("BAD_ARGUMENT", "«limit» is a whole number.")
        if (n !in 1..max) throw ToolFailure("BAD_ARGUMENT", "«limit» is 1–$max.")
        return n
    }
}
