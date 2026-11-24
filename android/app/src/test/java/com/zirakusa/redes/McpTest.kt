package com.zirakusa.redes

import com.zirakusa.redes.data.Busy
import com.zirakusa.redes.data.History
import com.zirakusa.redes.data.Link
import com.zirakusa.redes.data.MapValues
import com.zirakusa.redes.data.Result
import com.zirakusa.redes.data.Status
import com.zirakusa.redes.mcp.Access
import com.zirakusa.redes.mcp.HubLink
import com.zirakusa.redes.mcp.Kind
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

/** Redes' MCP: tools list, good calls, bad arguments, not found, mobile data and busy, read-only tokens, wrong and paused tokens, hub link. */
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
    }
    private val writer = Token("w", "Claude", "red_" + "w".repeat(40), Access.WRITE, Kind.HUB, "Redes")
    private val reader = Token("r", "Lector", "red_" + "r".repeat(40), Access.READ)
    private val paused = Token("p", "Viejo", "red_" + "p".repeat(40), Access.WRITE, paused = true)
    private val handler = McpHandler({ listOf(writer, reader, paused) }, data, "0.1.0")

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
        assertEquals(listOf("network_status", "network_wifi_nearby", "network_speed_history", "network_speed_result", "network_speed_test"), names(writer))
        assertEquals(listOf("network_status", "network_wifi_nearby", "network_speed_history", "network_speed_result"), names(reader))
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

    @Test fun wrongAndPausedTokensAndHubLink() = runBlocking {
        val body = """{"jsonrpc":"2.0","id":1,"method":"tools/list"}"""
        assertEquals(401, handler.handle("POST", "/mcp", mapOf("Authorization" to "Bearer ${paused.secret}"), emptyMap(), body).status)
        assertEquals(401, handler.handle("POST", "/mcp", mapOf("Authorization" to "Bearer red_wrong"), emptyMap(), body).status)
        assertEquals(401, handler.handle("POST", "/mcp", emptyMap(), emptyMap(), body).status)
        assertEquals(200, handler.handle("POST", "/mcp", emptyMap(), mapOf("token" to reader.secret), body).status)
        assertTrue(Token.secret().startsWith("red_"))
        assertEquals("NOT_SAME_DEVELOPER", HubLink.answer(false).getString("error"))
        assertEquals("REDIRECT_OFF", HubLink.answer(true).getString("error"))
        // «Conectar todas» and Lucía's tools: only for the same developer, and tools only with «Redireccionar a Hub» on.
        assertEquals("NOT_SAME_DEVELOPER", HubLink.connect(false).getString("error"))
        assertEquals("NOT_SAME_DEVELOPER", HubLink.tool(false, "{}").getString("error"))
        assertEquals("REDIRECT_OFF", HubLink.tool(true, "{}").getString("error"))
    }

    /** Lucía runs a tool on the phone through the same handler, only while «Redireccionar a Hub» is on. */
    @Test fun luciaRunsToolsOnThePhone() {
        com.zirakusa.redes.mcp.McpMode.relayUrl = ""                                             // no relay in tests
        com.zirakusa.redes.mcp.McpMode.load(org.robolectric.RuntimeEnvironment.getApplication())
        com.zirakusa.redes.mcp.McpMode.redirect(false)
        assertEquals(null, com.zirakusa.redes.mcp.McpMode.local("{}"))
        HubLink.connect(true); assertTrue(com.zirakusa.redes.mcp.McpMode.hubRedirect)                // «Conectar todas»
        val list = JSONObject(com.zirakusa.redes.mcp.McpMode.local("""{"jsonrpc":"2.0","id":1,"method":"tools/list"}""")!!)
        assertTrue(list.getJSONObject("result").getJSONArray("tools").length() > 0)
        val viaProvider = JSONObject(HubLink.tool(true, """{"jsonrpc":"2.0","id":2,"method":"tools/list"}""").getString("response"))
        assertEquals(2, viaProvider.getInt("id"))
        com.zirakusa.redes.mcp.McpMode.redirect(false)
    }
}
