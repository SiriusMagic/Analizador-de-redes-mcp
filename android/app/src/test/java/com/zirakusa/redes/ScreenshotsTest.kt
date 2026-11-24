package com.zirakusa.redes

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.matcher.ViewMatchers.isRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.zirakusa.redes.data.BleDevice
import com.zirakusa.redes.data.History
import com.zirakusa.redes.data.LatencyLog
import com.zirakusa.redes.data.LatencyMeter
import com.zirakusa.redes.data.Pinger
import com.zirakusa.redes.data.Reach
import com.zirakusa.redes.data.Roaming
import com.zirakusa.redes.data.Link
import com.zirakusa.redes.data.MapValues
import com.zirakusa.redes.data.Phase
import com.zirakusa.redes.data.Result
import com.zirakusa.redes.data.Runner
import com.zirakusa.redes.data.Settings
import com.zirakusa.redes.ui.Page
import com.zirakusa.redes.ui.RedesApp
import com.zirakusa.redes.ui.RedesState
import com.zirakusa.redes.ui.theme.RedesTheme
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Every screen, painted: Red, Velocidad (running and done), Wi‑Fi cercanas (with and without permission) and Ajustes. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "es-w411dp-h891dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ScreenshotsTest {
    private class SteadyPinger : Pinger {
        var n = 0
        override suspend fun gateway(address: String): Double? = (n++).let { if (it % 17 == 9) null else 2.0 + (it % 5) }
        override suspend fun internet(): Double? = if (n % 23 == 11) null else 28.0 + (n * 7 % 13)
    }
    private class HomeReach : Reach {
        override suspend fun alive(ip: String) = ip.substringAfterLast('.').toInt() in setOf(5, 12, 34, 101)
        override suspend fun name(ip: String) = if (ip.endsWith(".34")) "tele-sala.lan" else ""
    }

    /** Señal (live chart, latency, roaming), the path on Velocidad, the history cards and Descubrir (Wi‑Fi and Bluetooth). */
    @Config(qualifiers = "es-w411dp-h1400dp-xhdpi")
    @Test fun signalDiscoverAndHistory() {
        var t = 1_763_700_000_000L
        val h = History(values)
        h.add(Result(0, 1_763_600_000_000, 18.2, 4.1, 41.0, 9.0, Link.CELLULAR, "Claro 4G", "UIO"))
        h.add(Result(0, 1_763_650_000_000, 92.4, 31.0, 14.0, 3.0, Link.WIFI, "DeUnaNET_5G", "UIO"))
        val runner = Runner(h, FakeServer(), Settings(values), seconds = 1)
        runBlocking { runner.run(Nets.officeStatus) }
        val roaming = Roaming(values)
        roaming.observe(Nets.office.copy(bssid = "aa:bb:cc:00:00:07", dbm = -81, mhz = 5180), t - 90_000)
        roaming.observe(Nets.office.copy(rxMbps = 780, txMbps = 650), t - 88_000)
        var wifi = Nets.office.copy(rxMbps = 780, txMbps = 650)
        val meter = LatencyMeter(SteadyPinger(), LatencyLog()) { t }
        val state = RedesState(values, runner, { Nets.officeStatus.copy(wifi = wifi, dns = listOf("192.168.0.1")) }, { Nets.crowd },
            launch = { block -> runBlocking { block() } }, roaming = roaming, meter = meter, reach = HomeReach(), model = "Samsung Galaxy A54", clock = { t })
        state.canName = true
        repeat(140) { i ->
            wifi = wifi.copy(dbm = -60 - (i / 10 % 4) * 3 - (i % 3)); state.refresh(); runBlocking { state.measureLatency() }; t += 2_000
        }
        compose.setContent { RedesTheme(state.themeMode, state.accent) { RedesApp(state) } }
        compose.onNodeWithContentDescription("Señal").performClick()
        compose.onNodeWithText("5 GHz (80 MHz)").assertExists(); compose.onNodeWithText("↓ 780  ↑ 650 Mbps").assertExists()
        compose.onNodeWithText("Roaming de punto de acceso").assertExists()
        shot("signal")
        compose.onNodeWithContentDescription("Velocidad").performClick()
        compose.onNodeWithText("Samsung Galaxy A54").assertExists(); compose.onNodeWithText("AS27947\n190.152.10.20").assertExists()
        shot("speed-path")
        state.page = Page.HISTORY
        compose.onNodeWithText("Historial de prueba de velocidad").assertExists()
        shot("history")
        state.query = "celular"
        compose.onNodeWithText("Celular · Claro 4G").assertExists(); compose.onNodeWithText("DeUnaNET_5G").assertDoesNotExist()
        state.page = Page.DISCOVER
        compose.onNodeWithText("Dispositivos (6)").assertExists(); compose.onNodeWithText("Portal").assertExists()
        compose.onNodeWithText("Yo (Samsung Galaxy A54)").assertExists(); compose.onNodeWithText("tele-sala.lan").assertExists()
        shot("discover-wifi")
        state.bleState = BleState.READY
        state.bleSeen(BleDevice("C4:7F:00:11:22:33", "Galaxy Buds", -58, t)); state.bleSeen(BleDevice("5A:10:9E:44:55:66", "", -77, t))
        compose.onNodeWithText("Bluetooth").performClick()
        compose.onNodeWithText("Galaxy Buds").assertExists(); compose.onNodeWithText("Genérico").assertExists()
        shot("discover-bluetooth")
    }

    /** No Wi‑Fi yet: the empty states of Señal (no readings, no roaming) and the phone permission explained. */
    @Test fun emptyStates() {
        val state = RedesState(values, Runner(History(values), FakeServer(), Settings(values)), { Nets.mobile }, { emptyList() })
        state.refresh()
        compose.setContent { RedesTheme(state.themeMode, state.accent) { RedesApp(state) } }
        compose.onNodeWithText("Ver mis SIM").assertExists()
        state.phoneAsked = true
        compose.onNodeWithText("Abrir ajustes de la app").assertExists()
        compose.onNodeWithContentDescription("Señal").performClick()
        compose.onNodeWithText("Aún no se han registrado cambios en puntos de acceso").assertExists()
        shot("signal-empty")
    }

    @get:Rule val compose = createComposeRule()
    private val values = MapValues()
    private fun shot(name: String) { compose.waitForIdle(); onView(isRoot()).captureRoboImage("build/screenshots/redes-$name.png") }

    @Test fun allScreens() {
        val h = History(values)
        h.add(Result(0, 1_763_700_000_000, 18.2, 4.1, 41.0, 9.0, Link.CELLULAR, "Claro 4G", "UIO"))
        val state = RedesState(values, Runner(h, FakeServer(), Settings(values)), { Nets.wifi }, { Nets.around })
        state.canName = true; state.refresh()
        compose.setContent { RedesTheme(state.themeMode, state.accent) { RedesApp(state) } }
        compose.onNodeWithText("Casa").assertExists(); compose.onNodeWithText("192.168.1.23").assertExists()
        shot("network")
        compose.onNodeWithContentDescription("Velocidad").performClick()
        state.progress(Phase.DOWNLOAD, 187.0, 0.6f)
        compose.onNodeWithText("Mbps · Descarga").assertExists()
        shot("speed-running")
        state.progress(Phase.DONE, 0.0, 1f)
        compose.onNodeWithContentDescription("Wi‑Fi cercanas").performClick()
        compose.onNodeWithText("Permitir").assertExists()
        state.canScan = true
        compose.onNodeWithText("Cambia tu router al canal 1: hay menos redes que estorben.").assertExists()
        shot("nearby")
        state.page = Page.SETTINGS
        compose.onNodeWithText("Ahorro de datos").assertExists()
        shot("settings")
    }

    /** The spectrum (5 GHz with your 80 MHz network, 2.4 GHz, empty 6 GHz) and the speed factors, on a tall phone to see them whole. */
    @Config(qualifiers = "es-w411dp-h1400dp-xhdpi")
    @Test fun spectrumAndFactors() {
        val state = RedesState(values, Runner(History(values), FakeServer(), Settings(values)), { Nets.officeStatus }, { Nets.crowd }, cells = { Nets.sims })
        state.canName = true; state.canScan = true; state.canPhone = true; state.refresh()
        compose.setContent { RedesTheme(state.themeMode, state.accent) { RedesApp(state) } }
        compose.onNodeWithText("Factores de velocidad del Wi‑Fi").assertExists(); compose.onNodeWithText("Justo").assertExists()
        compose.onNodeWithText("SIM 2").assertExists()
        shot("factors")
        compose.onNodeWithContentDescription("Wi‑Fi cercanas").performClick()
        compose.onNodeWithText("5 GHz (7)").assertExists(); compose.onNodeWithText("Conectada").assertExists()
        shot("spectrum-5")
        compose.onNodeWithText("2,4 GHz (5)").performClick()
        compose.onNodeWithText("canal 1 · 40 MHz · -66 dBm").assertExists()
        shot("spectrum-24")
        compose.onNodeWithText("6 GHz (0)").performClick()
        compose.onNodeWithText("No se ven redes en 6 GHz.").assertExists()
        shot("spectrum-6")
    }
}
