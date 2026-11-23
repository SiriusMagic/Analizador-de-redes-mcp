package com.zirakusa.redes

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.matcher.ViewMatchers.isRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.zirakusa.redes.data.History
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
