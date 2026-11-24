package com.zirakusa.redes

import com.zirakusa.redes.data.AccessPoint
import com.zirakusa.redes.data.Band
import com.zirakusa.redes.data.Busy
import com.zirakusa.redes.data.Channels
import com.zirakusa.redes.data.Grade
import com.zirakusa.redes.data.History
import com.zirakusa.redes.data.Link
import com.zirakusa.redes.data.MapValues
import com.zirakusa.redes.data.Phase
import com.zirakusa.redes.data.Radio
import com.zirakusa.redes.data.Result
import com.zirakusa.redes.data.Runner
import com.zirakusa.redes.data.Security
import com.zirakusa.redes.data.Settings
import com.zirakusa.redes.data.SignalLevel
import com.zirakusa.redes.data.SpeedMath
import com.zirakusa.redes.data.SpeedTest
import com.zirakusa.redes.data.Use
import com.zirakusa.redes.ui.RedesState
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The pure rules: channels and bands, signal, security, channel advice, speed maths, what a speed is good for, the test and its runner. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RedesMathTest {
    @Test fun radio() {
        assertEquals(1, Radio.channel(2412)); assertEquals(6, Radio.channel(2437)); assertEquals(13, Radio.channel(2472)); assertEquals(14, Radio.channel(2484))
        assertEquals(36, Radio.channel(5180)); assertEquals(149, Radio.channel(5745)); assertEquals(1, Radio.channel(5955)); assertEquals(0, Radio.channel(900))
        assertEquals(Band.B24, Radio.band(2437)); assertEquals(Band.B5, Radio.band(5745)); assertEquals(Band.B6, Radio.band(6115)); assertNull(Radio.band(60000))
        assertEquals(100, Radio.quality(-40)); assertEquals(60, Radio.quality(-70)); assertEquals(0, Radio.quality(-110))
        assertEquals(SignalLevel.EXCELLENT, SignalLevel.of(-50)); assertEquals(SignalLevel.FAIR, SignalLevel.of(-70)); assertEquals(SignalLevel.POOR, SignalLevel.of(-95))
        assertEquals(listOf(4, 3, 2, 1, 0), listOf(-50, -60, -70, -80, -90).map(Radio::bars))
        assertEquals(Security.WPA3, Security.of("[RSN-SAE-CCMP][ESS]")); assertEquals(Security.WPA2, Security.of("[WPA2-PSK-CCMP][ESS]"))
        assertEquals(Security.WEP, Security.of("[WEP][ESS]")); assertEquals(Security.OPEN, Security.of("[ESS]")); assertTrue(Security.OPEN.weak); assertFalse(Security.WPA2.weak)
    }

    @Test fun channelAdvice() {
        val own = Nets.home.asAccessPoint()
        val a = Channels.advice(own, Nets.around)!!
        assertEquals(6, a.current); assertTrue("${a.load}", a.load >= 40)                    // a strong neighbour on 6 and one on 5
        assertEquals(1, a.best); assertTrue(a.bestLoad < 10); assertTrue(a.worthChanging)    // 1 only feels the one on 5, faintly
        assertTrue(Channels.load(Nets.around, Band.B24, 11, Nets.home.bssid) < 10)           // 11 only a faint one
        assertTrue(Channels.load(Nets.around, Band.B24, 6, ownBssid = null) > a.load)       // counting our own router makes it fuller
        assertEquals(mapOf(6 to 2, 5 to 1, 11 to 1), Channels.count(Nets.around, Band.B24))
        val quiet = Channels.advice(own, listOf(own))!!
        assertEquals(0, quiet.load); assertFalse(quiet.worthChanging)
        assertNull(Channels.advice(AccessPoint("x", "b", 6115, -50, Security.WPA3), Nets.around))
        assertEquals(36, Channels.advice(AccessPoint("x", "b", 5180, -50, Security.WPA2), Nets.around)!!.current)
    }

    @Test fun speedMathAndGrades() {
        assertEquals(8.0, SpeedMath.mbps(1_000_000, 1000), 1e-9); assertEquals(0.0, SpeedMath.mbps(5, 0), 0.0)
        assertEquals(3.0, SpeedMath.median(listOf(5.0, 1.0, 3.0)), 0.0); assertEquals(2.5, SpeedMath.median(listOf(1.0, 2.0, 3.0, 4.0)), 0.0)
        assertEquals(2.0, SpeedMath.jitter(listOf(10.0, 12.0, 10.0)), 0.0); assertEquals(0.0, SpeedMath.jitter(listOf(9.0)), 0.0)
        val events = listOf(500L to 10_000_000, 1500L to 1_000_000, 3000L to 2_000_000)   // the first second is warm-up
        assertEquals(SpeedMath.mbps(3_000_000, 2000), SpeedMath.throughput(events), 1e-9)
        assertEquals(8.4, SpeedMath.round(8.4321), 0.0); assertEquals(245.0, SpeedMath.round(244.6), 0.0)
        val fast = Result(1, 0, 300.0, 40.0, 9.0, 2.0, Link.WIFI)
        assertEquals(Use.entries.toList(), Grade.uses(fast))
        val slow = Result(1, 0, 4.0, 0.8, 180.0, 40.0, Link.CELLULAR)
        assertEquals(listOf(Use.BROWSE, Use.MUSIC), Grade.uses(slow))
        val h = History(MapValues())
        repeat(History.MAX + 3) { h.add(fast) }
        assertEquals(History.MAX, h.list().size); assertEquals((History.MAX + 3).toLong(), h.list().first().id)
        assertEquals(30L shl 20, Settings(MapValues()).budget(metered = true)); assertEquals(120L shl 20, Settings(MapValues()).budget(metered = false))
    }

    @Test fun theSpeedTest() = runBlocking {
        val phases = mutableListOf<Phase>()
        val m = SpeedTest(FakeServer(80.0, 20.0, 12), seconds = 2).run { p, _, _ -> if (phases.lastOrNull() != p) phases += p }
        assertEquals(listOf(Phase.PING, Phase.DOWNLOAD, Phase.UPLOAD, Phase.DONE), phases)
        assertEquals(12.0, m.ping, 3.0); assertEquals("UIO", m.origin.colo); assertEquals("AS27947", m.origin.asn)
        assertTrue("down ${m.down}", m.down in 150.0..300.0)                              // 3 streams of 80 Mbps each ≈ 240
        assertTrue("up ${m.up}", m.up in 35.0..75.0)                                      // 3 × 20 ≈ 60
        val small = FakeServer(800.0)
        SpeedTest(small, seconds = 3, budget = 4L shl 20).run()
        assertTrue("used ${small.downloaded}", small.downloaded < 6L shl 20)              // the data budget is kept
    }

    @Test fun runnerAndState() = runBlocking {
        val kv = MapValues()
        val runner = Runner(History(kv), FakeServer(), Settings(kv), seconds = 1)
        val first = async { runner.run(Nets.wifi) }
        delay(50)
        assertTrue(runCatching { runner.run(Nets.wifi) }.exceptionOrNull() is Busy)    // one at a time
        val r = first.await()
        assertEquals("Casa", r.network); assertEquals(Link.WIFI, r.link); assertEquals(1, History(kv).list().size)
        assertEquals("Claro 4G", Nets.mobile.networkName())

        val state = RedesState(kv, runner, { Nets.wifi }, { Nets.around }, launch = { runBlocking { it() } })
        state.refresh()
        assertEquals(1, state.advice!!.best); assertEquals(1, state.results.size)
        state.startTest()
        assertEquals(2, state.results.size); assertNull(state.phase); assertTrue(state.last!!.down > 0)
        state.progress(Phase.DOWNLOAD, 55.0, 0.5f); assertEquals(Phase.DOWNLOAD, state.phase); assertEquals(55.0, state.live, 0.0)
        state.progress(Phase.DONE, 55.0, 1f); assertNull(state.phase)
        val lines = SummaryLines.lines(Nets.wifi, state.last, state.last!!.at + 1000, { "wifi ${it.wifi?.ssid}" }, { "test" })
        assertEquals(listOf("wifi Casa", "test"), lines)
        assertTrue(SummaryLines.lines(com.zirakusa.redes.data.Status.NONE, null, 0, { "x" }, { "y" }).isEmpty())
        state.clearHistory(); assertTrue(state.results.isEmpty())
    }

    /** On 80 MHz the advice never points inside the router's own block (157 → 161 changed nothing). */
    @Test fun wideChannelAdvice() {
        val own = AccessPoint("Casa", "aa", 5785, -69, Security.WPA2, 80, 5775)            // 157, block 149–161
        val near = listOf(AccessPoint("Club", "c1", 5785, -54, Security.WPA2, 80, 5775), AccessPoint("Kapak", "k1", 5180, -48, Security.WPA2, 80, 5210))
        val a = Channels.advice(own, near + own)!!
        assertEquals(157, a.current); assertTrue(a.best == 157 || a.best == 36)
        assertTrue(a.best !in listOf(149, 153, 161))
        // A busy block and an empty one: move to the empty block's first channel.
        val busy = Channels.advice(own, listOf(AccessPoint("Club", "c1", 5785, -45, Security.WPA2, 80, 5775), AccessPoint("Otro", "o1", 5745, -48, Security.WPA2, 80, 5775)))!!
        assertEquals(36, busy.best); assertTrue(busy.worthChanging)
        // Alone in its block: stay.
        assertEquals(157, Channels.advice(own, listOf(own))!!.best)
    }
}
