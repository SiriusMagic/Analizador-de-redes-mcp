package com.zirakusa.redes

import com.zirakusa.redes.data.AccessPoint
import com.zirakusa.redes.data.Band
import com.zirakusa.redes.data.CellTech
import com.zirakusa.redes.data.Security
import com.zirakusa.redes.data.Sim
import com.zirakusa.redes.data.Spectrum
import com.zirakusa.redes.data.Verdict
import com.zirakusa.redes.data.WifiFactors
import com.zirakusa.redes.data.WifiNow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The spectrum chart's maths (width, centre, axes) and the speed factors (Wi‑Fi and SIMs). */
class SpectrumTest {
    private fun ap(mhz: Int, width: Int = 20, center: Int = 0, dbm: Int = -60) = AccessPoint("x", "b$mhz$width", mhz, dbm, Security.WPA2, width, center)

    @Test fun widthsFromAndroid() {
        assertEquals(listOf(20, 40, 80, 160, 80, 320, 20), listOf(0, 1, 2, 3, 4, 5, -1).map(Spectrum::widthMhz))
    }

    @Test fun bellsAreAsWideAsTheChannel() {
        val b20 = Spectrum.bell(ap(2437))                                    // channel 6, 20 MHz: 2427–2447
        assertEquals(2427.0, b20.lo, 0.0); assertEquals(2447.0, b20.hi, 0.0); assertTrue(b20.topLo > b20.lo && b20.topHi < b20.hi)
        val b80 = Spectrum.bell(ap(5785, 80, 5775))                           // 157 inside 149–161: centred on 155
        assertEquals(5735.0, b80.lo, 0.0); assertEquals(5815.0, b80.hi, 0.0)
        assertEquals(5785, Spectrum.center(ap(5785, 20, 5775)))               // 20 MHz ignores any centre
        assertEquals(5785, Spectrum.center(ap(5785, 80, 0)))                  // unknown centre: the primary one
        assertEquals(-95, Spectrum.bell(ap(2412, dbm = -101)).dbm); assertEquals(-30, Spectrum.bell(ap(2412, dbm = -20)).dbm)
        assertEquals(0.0, Spectrum.y(-95), 0.0); assertEquals(1.0, Spectrum.y(-30), 0.0); assertEquals(0.5, Spectrum.y(-62), 0.01)
    }

    @Test fun axesAndLabels() {
        assertEquals((1..14).toList(), Spectrum.axis(Band.B24).ticks.map { it.first })
        assertEquals(2484, Spectrum.axis(Band.B24).ticks.last().second)
        assertEquals(listOf(36, 52, 100, 116, 132, 149, 165), Spectrum.axis(Band.B5).ticks.map { it.first })
        assertEquals(5955, Spectrum.mhz(Band.B6, 1)); assertEquals(7115, Spectrum.mhz(Band.B6, 233))
        Band.entries.forEach { b -> Spectrum.axis(b).ticks.forEach { (_, mhz) -> assertTrue(Spectrum.axis(b).x(mhz.toDouble()) in 0.01..0.99) } }
        assertEquals("36", Spectrum.channels(ap(5180))); assertEquals("157 · 80 MHz", Spectrum.channels(ap(5785, 80, 5775)))
        assertEquals(mapOf(Band.B24 to 4, Band.B5 to 1, Band.B6 to 0), Spectrum.counts(Nets.around))
        assertEquals(listOf(-85, -60, -52, -48), Spectrum.bells(Nets.around, Band.B24).map { it.dbm })
    }

    @Test fun wifiFactors() {
        val office = WifiFactors.of(Nets.office, Nets.crowd)                 // 5 GHz 80 MHz, Wi‑Fi 5, −69 dBm
        assertTrue(office.spectrumOk); assertFalse(office.radioOk); assertEquals(Verdict.FAIR, office.verdict)
        assertEquals(866, office.maxRxMbps); assertEquals(listOf("radio"), office.problems)
        assertTrue(office.weakSignal); assertFalse(office.oldStandard)
        val good = WifiFactors(Band.B5, 80, 6, -50, 0, 0, 36, 10)
        assertEquals(Verdict.GOOD, good.verdict); assertEquals(0, good.warnings)
        assertFalse(WifiFactors(Band.B5, 20, 6, -50, 0, 0, 36, 10).spectrumOk)  // 20 MHz
        assertTrue(WifiFactors(Band.B5, 0, 0, -50, 0, 0, 36, 10).let { it.spectrumOk && it.radioOk }) // unknown width and standard
        val bad = WifiFactors(Band.B24, 20, 4, -80, 0, 0, 6, 80)
        assertEquals(3, bad.warnings); assertEquals(Verdict.BAD, bad.verdict); assertEquals(listOf("spectrum", "radio", "channel"), bad.problems)
        assertEquals(Verdict.FAIR, WifiFactors.of(WifiNow("a", "b", -50, 2437, 100, 6), emptyList()).verdict) // 2.4 GHz, width unknown
    }

    @Test fun simFactors() {
        val (claro, movistar) = Nets.sims
        assertTrue(claro.weak); assertEquals(1, claro.warnings); assertFalse(movistar.weak); assertEquals(0, movistar.warnings)
        assertFalse(Sim(1, "Claro", CellTech.G5, -98).weak); assertTrue(Sim(1, "Claro", CellTech.G5, -101).weak)
        assertEquals(1, Sim(1, "CNT", CellTech.G3, -80).warnings); assertEquals(0, Sim(1, "CNT", CellTech.G4, null).warnings)
    }
}
