package com.zirakusa.redes

import com.zirakusa.redes.data.BleDevice
import com.zirakusa.redes.data.BleList
import com.zirakusa.redes.data.CellTech
import com.zirakusa.redes.data.Chart
import com.zirakusa.redes.data.Device
import com.zirakusa.redes.data.DeviceSort
import com.zirakusa.redes.data.Devices
import com.zirakusa.redes.data.HistoryView
import com.zirakusa.redes.data.Lan
import com.zirakusa.redes.data.LatencyLog
import com.zirakusa.redes.data.LatencyMeter
import com.zirakusa.redes.data.LatencyStats
import com.zirakusa.redes.data.Link
import com.zirakusa.redes.data.MapValues
import com.zirakusa.redes.data.Origin
import com.zirakusa.redes.data.Ping
import com.zirakusa.redes.data.Pinger
import com.zirakusa.redes.data.Reach
import com.zirakusa.redes.data.Result
import com.zirakusa.redes.data.Ring
import com.zirakusa.redes.data.Role
import com.zirakusa.redes.data.Roaming
import com.zirakusa.redes.data.SignalLog
import com.zirakusa.redes.data.Status
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Señal, Latencia, Roaming, Descubrir, the history cards and the cellular labels: all pure, no internet. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SignalTest {
    @Test fun ringAndSignalLog() {
        val r = Ring<Int>(3)
        (1..5).forEach(r::add)
        assertEquals(listOf(3, 4, 5), r.list()); assertEquals(5, r.last()); assertEquals(3, r.size)
        val log = SignalLog(capacity = 10)
        log.add(1_000, Nets.home); log.add(2_000, null); log.add(400_000, Nets.office)
        assertEquals(2, log.ring.size)                                                    // no Wi‑Fi, no reading
        assertEquals(listOf(-69), log.recent(400_000, 300_000).map { it.dbm })            // only the last 5 minutes
        assertEquals("5 GHz (80 MHz)", Nets.office.bandWidth); assertEquals("2,4 GHz (20 MHz)", Nets.home.bandWidth)
        assertEquals("5 GHz", Nets.office.copy(widthMhz = 0).bandWidth)
        assertEquals(144, Nets.home.downMbps); assertEquals(390, Nets.home.copy(rxMbps = 390, txMbps = 260).downMbps)
    }

    @Test fun chartScales() {
        assertEquals(-100.0, Chart.dbm(emptyList()).lo, 0.0); assertEquals(-30.0, Chart.dbm(emptyList()).hi, 0.0)
        val r = Chart.dbm(listOf(-52, -48, -50))
        assertTrue(r.hi - r.lo >= 30); assertTrue(r.lo <= -57 && r.hi >= -43); assertEquals(0.0, r.lo % 10, 0.0)
        assertEquals(listOf(-80.0, -70.0, -60.0, -50.0, -40.0), Chart.dbm(listOf(-72, -48)).lines)
        assertEquals(0.5f, r.y((r.lo + r.hi) / 2), 0.001f)
        assertEquals(1f, r.y(10.0), 0f)                                                   // clamped
        assertEquals(50.0, Chart.ms(listOf(3.0, 12.0)).hi, 0.0); assertEquals(12.5, Chart.ms(listOf(3.0)).step, 0.0)
        assertEquals(200.0, Chart.ms(listOf(120.0)).hi, 0.0); assertEquals(50.0, Chart.ms(emptyList()).hi, 0.0)
        val pts = Chart.points(listOf(0L to 10.0, 50_000L to null, 100_000L to 25.0, -5L to 1.0), 100_000, 100_000, Chart.ms(listOf(25.0)))
        assertEquals(3, pts.size)                                                         // older than the window: out
        assertEquals(0f, pts[0].x, 0f); assertEquals(1f, pts[2].x, 0f); assertNull(pts[1].y); assertEquals(0.5f, pts[2].y!!, 0.001f)
    }

    @Test fun roamingOnlyOwnNetworkChanges() {
        val kv = MapValues()
        val roam = Roaming(kv)
        val a = Nets.office.copy(bssid = "aa:aa:aa:00:00:01", dbm = -78)
        assertNull(roam.observe(a, 1)); assertNull(roam.observe(a, 2))                     // same access point
        val e = roam.observe(a.copy(bssid = "aa:aa:aa:00:00:02", dbm = -55, mhz = 5180), 3)!!
        assertEquals(-78, e.dbmBefore); assertEquals(-55, e.dbmAfter); assertEquals(157, e.fromChannel); assertEquals(36, e.toChannel)
        assertNull(roam.observe(Nets.home, 4))                                            // another network is not roaming
        assertNull(roam.observe(Nets.home.copy(bssid = null), 5))                         // hidden access point: nothing
        assertNull(roam.observe(Nets.home.copy(bssid = "aa:bb:cc:00:00:09"), 6))         // after a gap, no "before"
        assertNull(roam.observe(Nets.home.copy(ssid = null, bssid = "x"), 7))              // no name (no permission): not noted
        assertEquals(listOf(e), Roaming(kv).list())                                        // kept in the phone's preferences
        roam.clear(); assertTrue(roam.list().isEmpty())
    }

    private class FakePinger(var router: Double? = 2.0, var net: Double? = 30.0) : Pinger {
        val asked = mutableListOf<String>()
        override suspend fun gateway(address: String): Double? { asked += address; return router }
        override suspend fun internet(): Double? { asked += "internet"; return net }
    }

    @Test fun latencyStatsAndMeter() = runBlocking {
        val s = LatencyStats.of(listOf(Ping(0, 10.0), Ping(1, null), Ping(2, 20.0), Ping(3, 12.0)))
        assertEquals(14.0, s.average!!, 0.0); assertEquals(25, s.lossPercent); assertEquals(10.0, s.min!!, 0.0); assertEquals(20.0, s.max!!, 0.0)
        assertEquals(9.0, s.jitter!!, 0.0)                                                // |20-10| and |12-20| → 9
        assertEquals(LatencyStats(2, null, null, null, null, 100), LatencyStats.of(listOf(Ping(0, null), Ping(1, null))))
        assertEquals(0, LatencyStats.of(emptyList()).lossPercent)

        val fake = FakePinger()
        val meter = LatencyMeter(fake, LatencyLog(), clock = { 5L })
        assertTrue(meter.once(Nets.wifi, speedTestRunning = false, saver = true))
        assertEquals(listOf("192.168.1.1", "internet"), fake.asked)
        fake.net = null; meter.once(Nets.wifi, speedTestRunning = true, saver = true)      // no Internet during a speed test
        assertEquals(2, meter.log.gateway.size); assertEquals(1, meter.log.internet.size)
        fake.asked.clear()
        meter.once(Nets.wifi.copy(gateway = "8.8.8.8"), false, false)                     // never knocks a public address
        assertEquals(listOf("internet"), fake.asked); assertNull(meter.log.internet.last()!!.ms)
        assertFalse(meter.once(Status(Link.NONE), false, false))
        assertTrue(LatencyMeter.internetDue(3, metered = false, saver = true)); assertFalse(LatencyMeter.internetDue(3, metered = true, saver = true))
        assertTrue(LatencyMeter.internetDue(5, metered = true, saver = true)); assertTrue(LatencyMeter.internetDue(3, metered = true, saver = false))
    }

    @Test fun localNetworkMaths() {
        listOf("192.168.1.1", "10.0.0.138", "172.16.0.1", "172.31.255.1", "169.254.3.4", "fe80::1", "fd00::2").forEach { assertTrue(it, Lan.isLocal(it)) }
        listOf("8.8.8.8", "172.32.0.1", "100.64.0.1", "1.1.1.1", "speed.cloudflare.com", "192.168.1", "192.168.1.256", "2800:bf0::1", "", "google.com")
            .forEach { assertFalse(it, Lan.isLocal(it)) }
        val hosts = Lan.hosts("192.168.1.23")
        assertEquals(254, hosts.size); assertEquals("192.168.1.1", hosts.first()); assertEquals("192.168.1.254", hosts.last())
        assertTrue(Lan.hosts("190.152.10.20").isEmpty()); assertTrue(Lan.hosts("evil.example").isEmpty())
        assertTrue(Lan.order("192.168.1.9") < Lan.order("192.168.1.10"))
    }

    private class FakeReach(val up: Set<String>, val names: Map<String, String> = emptyMap()) : Reach {
        var named = 0
        override suspend fun alive(ip: String) = ip in up
        override suspend fun name(ip: String): String { named++; return names[ip].orEmpty() }
    }

    @Test fun discoverLabelsAndSorts() = runBlocking {
        val reach = FakeReach(setOf("192.168.1.10", "192.168.1.9", "192.168.1.200"), mapOf("192.168.1.200" to "tele-sala.lan"))
        var asked = 0
        val list = Devices.look(Nets.wifi, reach, parallel = 8) { asked = it }
        assertEquals(254, asked)
        assertEquals(listOf("192.168.1.1", "192.168.1.9", "192.168.1.10", "192.168.1.23", "192.168.1.200"), list.map { it.ip })
        assertEquals(Role.GATEWAY, list[0].role); assertEquals(Role.ME, list[3].role); assertEquals("tele-sala.lan", list[4].name)
        assertEquals(listOf("192.168.1.1", "192.168.1.23", "192.168.1.200", "192.168.1.9", "192.168.1.10"), Devices.sort(list, DeviceSort.NAME).map { it.ip })
        // A public DNS (or private DNS) would send the lookups out: then no names are asked.
        val outside = FakeReach(setOf("192.168.1.9"), mapOf("192.168.1.9" to "x"))
        assertEquals("", Devices.look(Nets.wifi.copy(dns = listOf("8.8.8.8")), outside).first { it.ip == "192.168.1.9" }.name); assertEquals(0, outside.named)
        assertFalse(Devices.canName(Nets.wifi.copy(privateDns = "dns.example")))
        assertTrue(Devices.look(Nets.mobile, reach).isEmpty())                             // never on mobile data
        assertEquals(Role.OTHER, Devices.role("192.168.1.5", "192.168.1.1", "192.168.1.23"))
        assertEquals("Samsung SM-A546E", Devices.model("samsung", "SM-A546E")); assertEquals("Pixel 8", Devices.model("Google", "Pixel 8"))
        assertEquals("Xiaomi 13T", Devices.model("Xiaomi", "Xiaomi 13T")); assertEquals("Android", Devices.model("", ""))
        assertEquals(Device("1", Role.OTHER), Device("1", Role.OTHER, ""))
    }

    @Test fun bluetoothList() {
        var l = BleList.add(emptyList(), BleDevice("A", "Reloj", -70, 0))
        l = BleList.add(l, BleDevice("B", "", -50, 1))
        l = BleList.add(l, BleDevice("A", "", -40, 2))                                     // same device: one row, name kept
        assertEquals(listOf("A", "B"), l.map { it.address }); assertEquals("Reloj", l[0].name); assertEquals(-40, l[0].dbm)
        assertEquals(listOf("A"), BleList.fresh(l, now = 30_002).map { it.address })
    }

    @Test fun cellLabelsOriginAndHistory() {
        assertEquals(CellTech.G5, CellTech.of(20)); assertEquals(CellTech.G4, CellTech.of(13)); assertEquals(CellTech.G4_PLUS, CellTech.of(13, 2))
        assertEquals(CellTech.G4_PLUS, CellTech.of(19)); assertEquals(CellTech.G3, CellTech.of(15)); assertEquals(CellTech.G2, CellTech.of(2))
        assertEquals(CellTech.UNKNOWN, CellTech.of(0)); assertEquals("4G+", CellTech.of(13, 3).label)

        assertEquals(Origin("UIO", "190.152.10.20", "AS27947"), Origin.of("UIO", "190.152.10.20", "27947"))
        assertEquals(Origin("", "", ""), Origin.of(null, "<script>", "AS12x"))
        assertEquals("2800:bf0::1", Origin.of("", "2800:bf0::1", "").ip)

        val list = listOf(Result(1, 0, 18.2, 4.1, 41.0, 9.0, Link.CELLULAR, "Claro 4G"), Result(2, 0, 240.0, 60.0, 9.0, 1.0, Link.WIFI, "Casa Pérez"))
        assertEquals(listOf(1L), HistoryView.search(list, "celular").map { it.id }); assertEquals(listOf(2L), HistoryView.search(list, "perez wifi").map { it.id })
        assertEquals(2, HistoryView.search(list, "  ").size); assertTrue(HistoryView.search(list, "nada").isEmpty())
        assertEquals(240.0, HistoryView.top(list), 0.0); assertEquals(1.0, HistoryView.top(emptyList()), 0.0)
        assertEquals(0.25f, HistoryView.bar(60.0, 240.0), 0.001f); assertEquals(0.02f, HistoryView.bar(0.1, 240.0), 0f); assertEquals(0f, HistoryView.bar(0.0, 240.0), 0f)
    }
}
