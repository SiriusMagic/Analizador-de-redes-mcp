package com.zirakusa.redes

import com.zirakusa.redes.data.Busy
import com.zirakusa.redes.data.History
import com.zirakusa.redes.data.Link
import com.zirakusa.redes.data.MapValues
import com.zirakusa.redes.data.Ping
import com.zirakusa.redes.data.Result
import com.zirakusa.redes.data.RoamEvent
import com.zirakusa.redes.data.Sample
import com.zirakusa.redes.data.Status
import com.zirakusa.redes.mcp.Access
import com.zirakusa.redes.mcp.HubLink
import com.zirakusa.redes.mcp.McpHandler
import com.zirakusa.redes.mcp.NetData
import com.zirakusa.redes.mcp.Token
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.ZoneOffset

/** Redes' MCP: tools list, good calls, bad arguments, not found, mobile data and busy, read-only tokens, wrong tokens, hub link. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class McpTest {
    private val history = History(MapValues())
    private val data = object : NetData {
        var now: Status = Nets.wifi
        var busy = false
        var tests = 0
        override fun status() = now
        override fun history() = history.list()
        override fun nearby() = Nets.around
        override suspend fun speedTest(): Result { if (busy) throw Busy(); tests++; return history.add(Result(0, 1_700_000_000_000, 92.4, 18.1, 11.0, 2.0, now.link, now.networkName(), "UIO")) }
        override fun zone() = ZoneOffset.UTC
        override fun now() = 1_700_000_300_000
        var pings = listOf(Ping(1_700_000_290_000, 3.0), Ping(1_700_000_292_000, null))
        var latencyRounds = 0
        override fun signal() = listOf(Sample(1_700_000_000_000, -80), Sample(1_700_000_230_000, -60), Sample(1_700_000_298_000, -52))
        override fun roaming() = listOf(RoamEvent(1_700_000_100_000, "Casa", "aa:bb:cc:00:00:02", "aa:bb:cc:00:00:01", -78, -52, 36, 6))
        override fun gatewayPings() = pings
        override fun internetPings() = pings.map { it.copy(ms = it.ms?.times(10)) }
        override suspend fun latencyTest(count: Int): Pair<List<Ping>, List<Ping>> {
            if (busy) throw Busy(); latencyRounds += count
            return List(count) { Ping(it.toLong(), 2.0 + it) } to List(count) { Ping(it.toLong(), if (it == 0) null else 30.0) }
        }
    }
    private val writer = Token("red_" + "w".repeat(40), Access.WRITE)
    private val reader = Token("red_" + "r".repeat(40), Access.READ)
    private val handler = McpHandler({ listOf(writer, reader) }, data, "0.1.0")

    private fun rpc(token: Token, method: String, params: JSONObject = JSONObject()) = runBlocking {
        JSONObject(handler.handle("POST", "/mcp", mapOf("Authorization" to "Bearer ${token.secret}"), emptyMap(),
            JSONObject().put("jsonrpc", "2.0").put("id", 1).put("method", method).put("params", params).toString()).body).getJSONObject("result")
    }
    private fun tool(name: String, args: JSONObject, token: Token = writer) = rpc(token, "tools/call", JSONObject().put("name", name).put("arguments", args))
    private fun ok(name: String, args: JSONObject = JSONObject()) =
        tool(name, args).also { assertFalse(it.toString(), it.optBoolean("isError")) }.getJSONObject("structuredContent")
    private fun code(name: String, args: JSONObject = JSONObject(), token: Token = writer) = tool(name, args, token).also { assertTrue(it.optBoolean("isError")) }
        .getJSONObject("structuredContent").getJSONObject("error").getString("code")
    private fun names(token: Token) = rpc(token, "tools/list").getJSONArray("tools").let { t -> (0 until t.length()).map { t.getJSONObject(it).getString("name") } }

    @Test fun listsTheTools() {
        assertEquals(listOf("network_status", "network_wifi_nearby", "network_speed_history", "network_speed_result", "network_speed_test",
            "network_signal", "network_latency", "network_latency_test"), names(writer))
        assertEquals(listOf("network_status", "network_wifi_nearby", "network_speed_history", "network_speed_result", "network_signal", "network_latency"), names(reader))
        assertEquals("redes", rpc(writer, "initialize").getJSONObject("serverInfo").getString("name"))
    }

    @Test fun statusNearbyAndTests() {
        val s = ok("network_status")
        assertEquals("wifi", s.getString("link")); assertEquals("Casa", s.getJSONObject("wifi").getString("name"))
        assertEquals(6, s.getJSONObject("wifi").getInt("channel")); assertEquals("excellent", s.getJSONObject("wifi").getString("signal"))
        assertEquals("192.168.1.1", s.getString("gateway"))
        assertEquals(20, s.getJSONObject("wifi").getInt("width_mhz"))
        val f = s.getJSONObject("wifi").getJSONObject("factors")
        assertEquals("fair", f.getString("grade")); assertEquals("[\"spectrum\"]", f.getJSONArray("warnings").toString())
        val n = ok("network_wifi_nearby", JSONObject().put("limit", 3))
        assertEquals(5, n.getInt("total")); assertEquals(3, n.getJSONArray("networks").length())
        assertEquals(1, n.getJSONObject("advice").getInt("best_channel")); assertTrue(n.getJSONObject("advice").getBoolean("worth_changing"))
        assertEquals(20, n.getJSONArray("networks").getJSONObject(0).getInt("width_mhz"))
        assertFalse(n.getJSONArray("networks").getJSONObject(0).has("bssid"))                 // neighbours' hardware addresses never go out
        val t = ok("network_speed_test").getJSONObject("test")
        assertEquals(92.4, t.getDouble("download_mbps"), 0.0); assertEquals("Casa", t.getString("network"))
        assertTrue(t.getJSONArray("good_for").toString().contains("video_4k"))
        assertEquals(1, ok("network_speed_history").getInt("total"))
        assertEquals("2023-11-14T22:13:20", ok("network_speed_result", JSONObject().put("id", t.getLong("id"))).getJSONObject("test").getString("at"))
    }

    @Test fun badArgumentsAreRefused() {
        assertEquals("BAD_ARGUMENT", code("network_wifi_nearby", JSONObject().put("limit", 0)))
        assertEquals("BAD_ARGUMENT", code("network_speed_history", JSONObject().put("limit", "5")))
        assertEquals("BAD_ARGUMENT", code("network_speed_result", JSONObject().put("id", "1")))
        assertEquals("BAD_ARGUMENT", code("network_speed_test", JSONObject().put("allow_mobile", "yes")))
        assertEquals("BAD_ARGUMENT", code("network_status", JSONObject().put("host", "evil.example")))
        assertEquals(0, data.tests)
    }

    @Test fun notFoundMobileBusyAndReadOnly() {
        assertEquals("NOT_FOUND", code("network_speed_result", JSONObject().put("id", 99)))
        assertEquals("UNKNOWN_TOOL", code("network_ping_host"))
        assertEquals("READ_ONLY", code("network_speed_test", JSONObject(), reader))
        data.now = Nets.mobile
        assertEquals("ON_MOBILE_DATA", code("network_speed_test"))
        assertEquals("Claro 4G", ok("network_speed_test", JSONObject().put("allow_mobile", true)).getJSONObject("test").getString("network"))
        data.busy = true
        assertEquals("BUSY", code("network_speed_test", JSONObject().put("allow_mobile", true)))
        data.now = Status(Link.NONE)
        assertEquals("NO_CONNECTION", code("network_speed_test"))
        assertEquals(1, data.tests)
    }

    /** «Señal» and «Latencia»: good calls, bad arguments, not found, read-only and busy; no hardware addresses go out. */
    @Test fun signalAndLatency() {
        val s = ok("network_signal", JSONObject().put("minutes", 1))
        assertEquals(-52, s.getInt("signal_dbm")); assertEquals("2,4 GHz (20 MHz)", s.getString("band_label")); assertEquals(144, s.getJSONObject("link_mbps").getInt("down"))
        assertEquals(1, s.getJSONArray("readings").length()); assertEquals(2, s.getJSONArray("readings").getJSONObject(0).getInt("seconds_ago"))
        assertEquals(3, ok("network_signal").getJSONArray("readings").length())
        val roam = s.getJSONArray("roaming").getJSONObject(0)
        assertEquals(36, roam.getInt("from_channel")); assertEquals(-52, roam.getInt("after_dbm"))
        assertFalse(s.toString().contains("aa:bb:cc"))                                           // no BSSID at all, not even yours
        val l = ok("network_latency")
        assertEquals(3.0, l.getJSONObject("router").getDouble("average_ms"), 0.0); assertEquals(50, l.getJSONObject("router").getInt("loss_percent"))
        assertEquals("speed.cloudflare.com", l.getJSONObject("internet").getString("host")); assertEquals("192.168.1.1", l.getJSONObject("router").getString("address"))
        val t = ok("network_latency_test", JSONObject().put("count", 4))
        assertEquals(4, t.getJSONObject("router").getInt("readings")); assertEquals(25, t.getJSONObject("internet").getInt("loss_percent"))
        assertEquals(4, data.latencyRounds)

        assertEquals("BAD_ARGUMENT", code("network_signal", JSONObject().put("minutes", 0)))
        assertEquals("BAD_ARGUMENT", code("network_signal", JSONObject().put("minutes", "5")))
        assertEquals("BAD_ARGUMENT", code("network_latency", JSONObject().put("host", "8.8.8.8")))     // no host to choose, ever
        assertEquals("BAD_ARGUMENT", code("network_latency_test", JSONObject().put("count", 50)))
        assertEquals("BAD_ARGUMENT", code("network_latency_test", JSONObject().put("host", "google.com")))
        assertEquals("READ_ONLY", code("network_latency_test", JSONObject(), reader))
        assertEquals(4, data.latencyRounds)

        data.pings = emptyList()
        assertEquals("NOT_FOUND", code("network_latency"))
        data.now = Nets.mobile
        assertEquals("NOT_FOUND", code("network_signal"))
        data.busy = true
        assertEquals("BUSY", code("network_latency_test"))
        data.now = Status(Link.NONE)
        assertEquals("NO_CONNECTION", code("network_latency_test"))
    }

    @Test fun wrongAndPausedTokensAndHubLink() = runBlocking {
        val body = """{"jsonrpc":"2.0","id":1,"method":"tools/list"}"""
        assertEquals(401, handler.handle("POST", "/mcp", mapOf("Authorization" to "Bearer wrong_xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx"), emptyMap(), body).status)
        assertEquals(401, handler.handle("POST", "/mcp", mapOf("Authorization" to "Bearer red_wrong"), emptyMap(), body).status)
        assertEquals(401, handler.handle("POST", "/mcp", emptyMap(), emptyMap(), body).status)
        assertEquals(200, handler.handle("POST", "/mcp", emptyMap(), mapOf("token" to reader.secret), body).status)
        assertTrue(Token.secret().startsWith("red_"))
        assertEquals("NOT_SAME_DEVELOPER", HubLink.answer(false).getString("error"))
        assertEquals("REDIRECT_OFF", HubLink.answer(true).getString("error"))
        assertEquals("NOT_SAME_DEVELOPER", HubLink.connect(false).getString("error"))
        assertEquals("NOT_SAME_DEVELOPER", HubLink.tool(false, "{}").getString("error"))
        // Only the same developer (Arka) may connect, disconnect, read the status or run a tool.
        listOf(HubLink.disconnect(false), HubLink.status(false)).forEach { assertEquals("NOT_SAME_DEVELOPER", it.getString("error")) }
    }

    /** Lucía runs a tool on the phone through the same handler, with or without the public link; Arka turns that on and off. */
    @Test fun luciaRunsToolsOnThePhone() {
        val relay = com.zirakusa.redes.mcp.McpMode.relayUrl
        com.zirakusa.redes.mcp.McpMode.relayUrl = ""                                             // no relay in tests
        com.zirakusa.redes.mcp.McpMode.load(org.robolectric.RuntimeEnvironment.getApplication())
        com.zirakusa.redes.mcp.McpMode.turnRemote(false)
        val list = JSONObject(com.zirakusa.redes.mcp.McpMode.local("""{"jsonrpc":"2.0","id":1,"method":"tools/list"}""")!!)
        assertTrue(list.getJSONObject("result").getJSONArray("tools").length() > 0)
        com.zirakusa.redes.mcp.McpMode.allowLocal(false)
        assertEquals("LOCAL_DISABLED", HubLink.tool(true, "{}").getString("error"))
        com.zirakusa.redes.mcp.McpMode.allowLocal(true)
        val viaProvider = JSONObject(HubLink.tool(true, """{"jsonrpc":"2.0","id":2,"method":"tools/list"}""").getString("response"))
        assertEquals(2, viaProvider.getInt("id"))
        assertEquals("BAD_REQUEST", HubLink.tool(true, "").getString("error"))
        assertEquals("NOT_SAME_DEVELOPER", HubLink.tool(false, "{}").getString("error"))
        assertTrue(HubLink.status(true).containsKey("remote"))
        HubLink.connect(true); assertTrue(com.zirakusa.redes.mcp.McpMode.remote)                 // Redireccionar a Hub
        assertFalse(HubLink.disconnect(true).getBoolean("remote")); assertFalse(com.zirakusa.redes.mcp.McpMode.remote)
        com.zirakusa.redes.mcp.McpMode.allowLocal(false)
        com.zirakusa.redes.mcp.McpMode.relayUrl = relay                                          // as the other tests expect
    }

    /** The hub's token from before Arka took over is kept, so the hub's link keeps working. */
    @Test fun theOldHubTokenIsKept() {
        val old = """[{"id":"a","secret":"${"l".repeat(44)}","kind":"llm"},{"id":"b","secret":"${"h".repeat(44)}","kind":"redirect"}]"""
        assertEquals("h".repeat(44), com.zirakusa.redes.mcp.Legacy.redirectSecret(old))
        assertEquals(null, com.zirakusa.redes.mcp.Legacy.redirectSecret("[]"))
        assertEquals(null, com.zirakusa.redes.mcp.Legacy.redirectSecret(""))
    }
}
