package com.zirakusa.redes

import android.Manifest
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.SideEffect
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.zirakusa.redes.data.SharedValues
import com.zirakusa.redes.mcp.McpMode
import com.zirakusa.redes.ui.AppActions
import com.zirakusa.redes.ui.Page
import com.zirakusa.redes.ui.RedesApp
import com.zirakusa.redes.ui.RedesState
import com.zirakusa.redes.ui.Tab
import com.zirakusa.redes.ui.theme.RedesColors
import com.zirakusa.redes.ui.theme.RedesTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Creates the state, reads the connection every 2 s while open, asks for the Wi‑Fi permissions and does what the screens ask. */
class MainActivity : ComponentActivity() {
    private lateinit var state: RedesState
    private lateinit var phone: Phone
    private val allow = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions(); phone.scan(); state.refresh() }
    private val askPhone = registerForActivityResult(ActivityResultContracts.RequestPermission()) { state.phoneAsked = !it; permissions(); state.refresh() }
    private val askBle = registerForActivityResult(ActivityResultContracts.RequestPermission()) { permissions() }
    private lateinit var ble: Ble
    private val scans = object : BroadcastReceiver() { override fun onReceive(c: Context, i: Intent) = state.refresh() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppIcon.load(this)
        enableEdgeToEdge()
        phone = Phone(this)
        val values = SharedValues(this)
        val runner = Tests.runner(values)
        ble = Ble(this)
        state = RedesState(values, runner, phone::status, phone::lastScan, launch = { block -> lifecycleScope.launch { block() } }, cells = phone::sims,
            signalLog = Live.signal, roaming = Live.roaming(values), meter = Live.latency, reach = Local, model = phone.model())
        runner.listener = { p, v, f -> runOnUiThread { state.progress(p, v, f) } }
        McpMode.onChanged = { state.reload() }
        McpMode.load(this)
        val actions = AppActions(allowWifi = ::askWifi, scan = { if (!phone.scan()) Toast.makeText(this, R.string.scan_later, Toast.LENGTH_SHORT).show() },
            wifiSettings = { startActivity(Intent(Settings.ACTION_WIFI_SETTINGS)) }, openArka = ::openArka,
            allowPhone = { askPhone.launch(Manifest.permission.READ_PHONE_STATE) },
            appSettings = { startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))) },
            allowBle = { askBle.launch(ble.permission) },
            bleStart = { state.bleClear(); ble.start { d -> runOnUiThread { state.bleSeen(d) } } }, bleStop = { ble.stop() })
        ContextCompat.registerReceiver(this, scans, IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION), ContextCompat.RECEIVER_NOT_EXPORTED)
        // Every 2 s while open: the connection (and the signal and roaming readings); latency only while «Señal» is shown.
        lifecycleScope.launch { repeatOnLifecycle(Lifecycle.State.RESUMED) { while (true) {
            state.refresh(); state.bleState = ble.state()
            if (state.tab == Tab.SIGNAL && state.page == Page.MAIN) launch { state.measureLatency() }
            delay(2_000)
        } } }
        setContent {
            RedesTheme(state.themeMode, state.accent) {
                val light = RedesColors.isLight
                SideEffect { WindowCompat.getInsetsController(window, window.decorView).run { isAppearanceLightStatusBars = light; isAppearanceLightNavigationBars = light } }
                BackHandler(state.page != Page.MAIN || state.tab != Tab.NETWORK) {
                    when (state.page) { Page.SETTINGS, Page.DISCOVER, Page.HISTORY -> state.page = Page.MAIN
                        Page.MAIN -> state.tab = Tab.NETWORK }
                }
                RedesApp(state, actions)
            }
        }
    }

    override fun onResume() { super.onResume(); state.resumes++; permissions(); state.reload(); phone.scan() }
    override fun onPause() { ble.stop(); super.onPause() }
    override fun onDestroy() { unregisterReceiver(scans); super.onDestroy() }

    private fun permissions() {
        state.canName = phone.canName(); state.canScan = phone.canScan(); state.canPhone = phone.canPhone(); state.bleState = ble.state()
    }

    private fun askWifi() = allow.launch(buildList {
        add(Manifest.permission.ACCESS_COARSE_LOCATION); add(Manifest.permission.ACCESS_FINE_LOCATION); add(Manifest.permission.READ_PHONE_STATE)
        if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.NEARBY_WIFI_DEVICES)
    }.toTypedArray())


    /** «Modo MCP» in Ajustes: it is turned on, allowed on the phone and given tokens in Arka, so this opens Arka. */
    private fun openArka() {
        val open = packageManager.getLaunchIntentForPackage("com.zirakusa.gatewayhub")
        if (open != null) startActivity(open) else Toast.makeText(this, R.string.mcp_no_arka, Toast.LENGTH_LONG).show()
    }
}
