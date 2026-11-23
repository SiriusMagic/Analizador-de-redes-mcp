package com.zirakusa.redes.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.zirakusa.redes.data.AccessPoint
import com.zirakusa.redes.data.Busy
import com.zirakusa.redes.data.Channels
import com.zirakusa.redes.data.History
import com.zirakusa.redes.data.KeyValues
import com.zirakusa.redes.data.Link
import com.zirakusa.redes.data.Phase
import com.zirakusa.redes.data.Result
import com.zirakusa.redes.data.Runner
import com.zirakusa.redes.data.Settings
import com.zirakusa.redes.data.Status
import com.zirakusa.redes.ui.theme.Accent
import com.zirakusa.redes.ui.theme.ThemeMode

/** The three tabs at the bottom. */
enum class Tab { NETWORK, SPEED, NEARBY }

/** What covers the tabs: nothing, Ajustes or Modo MCP. */
enum class Page { MAIN, SETTINGS, MCP }

/**
 * The only owner of Redes' state: the connection now, the networks nearby, the speed test running (phase, live speed,
 * how far) and the history, and the settings. [read] and [look] ask Android; [launch] runs the test. Screens only paint.
 */
class RedesState(kv: KeyValues, private val runner: Runner, private val read: () -> Status = { Status.NONE },
    private val look: () -> List<AccessPoint> = { emptyList() }, private val launch: (suspend () -> Unit) -> Unit = {}) {
    private val settings = Settings(kv)
    private val history = History(kv)

    var tab by mutableStateOf(Tab.NETWORK)
    var page by mutableStateOf(Page.MAIN)
    var status by mutableStateOf(Status.NONE); private set
    var nearby by mutableStateOf(emptyList<AccessPoint>()); private set
    var results by mutableStateOf(history.list()); private set
    /** The phase of the test running, or null. */
    var phase by mutableStateOf<Phase?>(null); private set
    var live by mutableStateOf(0.0); private set
    var fraction by mutableFloatStateOf(0f); private set
    var last by mutableStateOf<Result?>(null); private set
    var failed by mutableStateOf(false); private set
    /** Android lets Redes see the Wi‑Fi's name (location) and the networks nearby. */
    var canName by mutableStateOf(false)
    var canScan by mutableStateOf(false)

    var saver by mutableStateOf(settings.saver); private set
    var themeMode by mutableStateOf(settings.theme?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.BLACK); private set
    var accent by mutableStateOf(settings.accent?.let { runCatching { Accent.valueOf(it) }.getOrNull() } ?: Accent.VERDE); private set

    val advice get() = status.wifi?.let { Channels.advice(it.asAccessPoint(), nearby) }
    val canTest get() = status.link != Link.NONE && phase == null

    fun refresh() { status = read(); nearby = look() }
    fun reload() { results = history.list() }

    /** Progress of a test (also one the AI started). */
    fun progress(p: Phase, value: Double, f: Float) {
        phase = if (p == Phase.DONE) null else p
        if (p != Phase.PING) live = value
        fraction = f
    }

    fun startTest() {
        if (!canTest || runner.running) return
        failed = false; last = null; live = 0.0; fraction = 0f; phase = Phase.PING
        val now = status
        launch {
            try { last = runner.run(now); results = history.list() }
            catch (_: Busy) { }
            catch (e: Exception) { if (e is kotlinx.coroutines.CancellationException) throw e; failed = true }
            finally { phase = null }
        }
    }

    fun clearHistory() { history.clear(); results = emptyList(); last = null }
    fun changeSaver(on: Boolean) { saver = on; settings.saver = on }
    fun changeTheme(mode: ThemeMode) { themeMode = mode; settings.theme = mode.name }
    fun changeAccent(next: Accent) { accent = next; settings.accent = next.name }
}
