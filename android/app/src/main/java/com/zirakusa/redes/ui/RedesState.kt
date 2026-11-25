package com.zirakusa.redes.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.zirakusa.redes.data.AccessPoint
import com.zirakusa.redes.data.BleDevice
import com.zirakusa.redes.data.BleList
import com.zirakusa.redes.data.Device
import com.zirakusa.redes.data.DeviceSort
import com.zirakusa.redes.data.Devices
import com.zirakusa.redes.data.HistoryView
import com.zirakusa.redes.data.LatencyMeter
import com.zirakusa.redes.data.Reach
import com.zirakusa.redes.data.Roaming
import com.zirakusa.redes.data.Sample
import com.zirakusa.redes.data.SignalLog
import com.zirakusa.redes.data.Busy
import com.zirakusa.redes.data.Channels
import com.zirakusa.redes.data.History
import com.zirakusa.redes.data.KeyValues
import com.zirakusa.redes.data.Link
import com.zirakusa.redes.data.Phase
import com.zirakusa.redes.data.Result
import com.zirakusa.redes.data.Runner
import com.zirakusa.redes.data.Settings
import com.zirakusa.redes.data.Sim
import com.zirakusa.redes.data.Status
import com.zirakusa.redes.data.WifiFactors
import com.zirakusa.redes.ui.theme.Accent
import com.zirakusa.redes.ui.theme.ThemeMode
import androidx.compose.runtime.mutableDoubleStateOf

/** The four tabs at the bottom. */
enum class Tab { NETWORK, SPEED, SIGNAL, NEARBY }

/** What covers the tabs: nothing, Ajustes, Modo MCP, Descubrir or the whole speed history. */
enum class Page { MAIN, SETTINGS, DISCOVER, HISTORY }

/**
 * The only owner of Redes' state: the connection now, the networks nearby, the speed test running (phase, live speed,
 * how far) and the history, and the settings. [read] and [look] ask Android; [launch] runs the test. Screens only paint.
 */
class RedesState(kv: KeyValues, private val runner: Runner, private val read: () -> Status = { Status.NONE },
    private val look: () -> List<AccessPoint> = { emptyList() }, private val launch: (suspend () -> Unit) -> Unit = {},
    private val cells: () -> List<Sim> = { emptyList() }, private val signalLog: SignalLog = SignalLog(),
    private val roaming: Roaming = Roaming(kv), private val meter: LatencyMeter? = null, private val reach: Reach? = null,
    /** «Pixel 8»: this phone, for «Yo» and the path diagram. */
    val model: String = "", private val clock: () -> Long = System::currentTimeMillis) {
    private val settings = Settings(kv)
    private val history = History(kv)

    /** «Señal»: the readings of the last 5 minutes, the latency ones, and the access point changes. */
    var signal by mutableStateOf(emptyList<Sample>()); private set
    var roams by mutableStateOf(roaming.list()); private set
    var gatewayPings by mutableStateOf(meter?.log?.gateway?.list().orEmpty()); private set
    var internetPings by mutableStateOf(meter?.log?.internet?.list().orEmpty()); private set
    /** What Cloudflare said of you in the last test (only in memory). */
    var origin by mutableStateOf(runner.origin); private set
    /** The phone permission was asked (and maybe refused) in this run. */
    var phoneAsked by mutableStateOf(false)

    /** «Descubrir»: devices on your network and Bluetooth LE ones nearby, only in memory. */
    var devices by mutableStateOf(emptyList<Device>()); private set
    var looking by mutableStateOf(false); private set
    var looked by mutableIntStateOf(0); private set
    var deviceSort by mutableStateOf(DeviceSort.IP); private set
    var ble by mutableStateOf(emptyList<BleDevice>()); private set
    /** Whether Bluetooth can be listened to now (the activity tells). */
    var bleState by mutableStateOf(com.zirakusa.redes.BleState.UNSUPPORTED)
    /** Counts the times Redes came back to the front (Bluetooth listening starts again). */
    var resumes by mutableIntStateOf(0)
    /** «Historial de prueba de velocidad»: the search. */
    var query by mutableStateOf("")
    val found get() = HistoryView.search(results, query)

    var tab by mutableStateOf(Tab.NETWORK)
    var page by mutableStateOf(Page.MAIN)
    var status by mutableStateOf(Status.NONE); private set
    var nearby by mutableStateOf(emptyList<AccessPoint>()); private set
    /** The active SIMs, for «Factores de velocidad celular». */
    var sims by mutableStateOf(emptyList<Sim>()); private set
    var results by mutableStateOf(history.list()); private set
    /** The phase of the test running, or null. */
    var phase by mutableStateOf<Phase?>(null); private set
    var live by mutableDoubleStateOf(0.0); private set
    var fraction by mutableFloatStateOf(0f); private set
    var last by mutableStateOf<Result?>(null); private set
    var failed by mutableStateOf(false); private set
    /** Android lets Redes see the Wi‑Fi's name (location) and the networks nearby. */
    var canName by mutableStateOf(false)
    var canScan by mutableStateOf(false)
    /** Android lets Redes read the SIMs (phone permission). */
    var canPhone by mutableStateOf(false)

    var saver by mutableStateOf(settings.saver); private set
    var themeMode by mutableStateOf(settings.theme?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.BLACK); private set
    var accent by mutableStateOf(settings.accent?.let { runCatching { Accent.valueOf(it) }.getOrNull() } ?: Accent.VERDE); private set

    val advice get() = status.wifi?.let { Channels.advice(it.asAccessPoint(), nearby) }
    val factors get() = status.wifi?.let { WifiFactors.of(it, nearby) }
    val canTest get() = status.link != Link.NONE && phase == null

    fun refresh() {
        status = read(); nearby = look(); sims = cells()
        val now = clock()
        signalLog.add(now, status.wifi)
        signal = signalLog.recent(now, SIGNAL_WINDOW)
        if (roaming.observe(status.wifi, now) != null) roams = roaming.list()
    }

    /** One latency round (router and Internet) while «Señal» is open. */
    suspend fun measureLatency() {
        val m = meter ?: return
        if (m.once(status, runner.running, settings.saver)) { gatewayPings = m.log.gateway.list(); internetPings = m.log.internet.list() }
    }

    /** The time on the state's clock (the screens draw the charts up to it). */
    fun now() = clock()

    fun clearRoaming() { roaming.clear(); roams = emptyList() }

    /** «Descubrir › Wi‑Fi»: knocks the /24 of your network (once at a time). */
    fun discover() {
        val r = reach ?: return
        if (looking || !Devices.canLook(status)) return
        looking = true; looked = 0
        val now = status
        launch {
            try { devices = Devices.sort(Devices.look(now, r) { looked = it }, deviceSort) } finally { looking = false }
        }
    }
    fun sortDevices() { deviceSort = if (deviceSort == DeviceSort.IP) DeviceSort.NAME else DeviceSort.IP; devices = Devices.sort(devices, deviceSort) }
    fun bleSeen(d: BleDevice) { ble = BleList.fresh(BleList.add(ble, d), clock()) }
    fun bleClear() { ble = emptyList() }
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
            try { last = runner.run(now); results = history.list(); origin = runner.origin }
            catch (_: Busy) { }
            catch (e: Exception) { if (e is kotlinx.coroutines.CancellationException) throw e; failed = true }
            finally { phase = null }
        }
    }

    fun clearHistory() { history.clear(); results = emptyList(); last = null }
    fun changeSaver(on: Boolean) { saver = on; settings.saver = on }
    fun changeTheme(mode: ThemeMode) { themeMode = mode; settings.theme = mode.name }
    fun changeAccent(next: Accent) { accent = next; settings.accent = next.name }

    companion object { const val SIGNAL_WINDOW = 5 * 60_000L }
}
