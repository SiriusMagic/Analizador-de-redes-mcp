package com.zirakusa.redes.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material.icons.outlined.SignalCellularAlt
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.material.icons.outlined.VpnLock
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SettingsEthernet
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zirakusa.redes.R
import com.zirakusa.redes.data.Link
import com.zirakusa.redes.data.Radio
import com.zirakusa.redes.data.Verdict
import com.zirakusa.redes.data.WifiFactors
import com.zirakusa.redes.ui.AppActions
import com.zirakusa.redes.ui.Page
import com.zirakusa.redes.ui.RedesState
import com.zirakusa.redes.ui.theme.RedesColors

/** «Red»: how the phone is connected now (it refreshes every few seconds), the Wi‑Fi's signal, and the addresses. */
@Composable fun NetworkScreen(state: RedesState, actions: AppActions) =
    Column(Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.app_title), Modifier.weight(1f).padding(start = 4.dp), fontSize = 26.sp)
            RoundButton(Icons.Outlined.Settings, stringResource(R.string.settings)) { state.page = Page.SETTINGS }
        }
        val s = state.status
        val w = s.wifi
        Group {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(when (s.link) { Link.WIFI -> Icons.Outlined.Wifi; Link.CELLULAR -> Icons.Outlined.SignalCellularAlt
                        Link.ETHERNET -> Icons.Outlined.SettingsEthernet; Link.VPN -> Icons.Outlined.VpnLock; Link.NONE -> Icons.Outlined.WifiOff },
                        null, Modifier.size(36.dp), tint = RedesColors.AccentText)
                    Column(Modifier.padding(start = 14.dp)) {
                        Text(when (s.link) {
                            Link.WIFI -> w?.ssid ?: stringResource(R.string.wifi_hidden_name)
                            Link.CELLULAR -> s.networkName().ifBlank { stringResource(R.string.mobile_data) }
                            Link.ETHERNET -> stringResource(R.string.ethernet); Link.VPN -> stringResource(R.string.vpn)
                            Link.NONE -> stringResource(R.string.no_connection)
                        }, fontSize = 22.sp)
                        Text(stringResource(when { s.link == Link.NONE -> R.string.no_connection_hint; s.online -> R.string.online; else -> R.string.no_internet }),
                            color = RedesColors.Muted, fontSize = 14.sp)
                    }
                }
                if (s.link == Link.WIFI && w?.ssid == null && !state.canName) MainButton(stringResource(R.string.allow_name), onClick = actions.allowWifi)
                if (w != null) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text("${w.dbm}", fontSize = 52.sp)
                        Text(" dBm · ${stringResource(levelName(w.level))}", Modifier.padding(bottom = 10.dp), fontSize = 18.sp, color = RedesColors.Muted)
                    }
                    Meter(Radio.quality(w.dbm) / 100f)
                }
            }
        }
        if (w != null) Group {
            Item(stringResource(R.string.band_channel), null, value = "${w.band?.label ?: "—"} · ${w.channel}", onClick = null)
            Divider(); Item(stringResource(R.string.link_speed), null, value = "${w.linkMbps} Mbps", onClick = null)
            if (w.generation > 0) { Divider(); Item(stringResource(R.string.standard), null, value = "Wi‑Fi ${w.generation}", onClick = null) }
            w.security?.let { sec -> Divider(); Item(stringResource(R.string.security), if (sec.weak) stringResource(R.string.weak_security) else null, value = sec.label, onClick = null) }
        }
        state.factors?.let { WifiFactorsCard(it) }
        CellFactorsCard(state, actions)
        if (s.link != Link.NONE) Group {
            Item(stringResource(R.string.local_ip), null, value = s.addresses.firstOrNull() ?: "—", onClick = null)
            if (s.gateway.isNotBlank()) { Divider(); Item(stringResource(R.string.router), null, value = s.gateway, onClick = null) }
            if (s.dns.isNotEmpty()) { Divider(); Item(stringResource(R.string.dns), null, value = s.dns.take(2).joinToString("\n"), onClick = null) }
            if (s.privateDns.isNotBlank()) { Divider(); Item(stringResource(R.string.private_dns), null, value = s.privateDns, onClick = null) }
            Divider(); Item(stringResource(R.string.metered), null, value = stringResource(if (s.metered) R.string.yes else R.string.no), onClick = null)
        }
        Item(stringResource(R.string.open_wifi_settings), null, onClick = actions.wifiSettings)
    }

private val Warn = Color(0xFFFFB547)

/** «Factores de velocidad del Wi‑Fi»: overall verdict, then spectrum, radio and channel, each with ✓ or ⚠. */
@Composable private fun WifiFactorsCard(f: WifiFactors) {
    Row(Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.wifi_factors), Modifier.weight(1f), fontSize = 18.sp)
        Text(stringResource(when (f.verdict) { Verdict.GOOD -> R.string.verdict_good; Verdict.FAIR -> R.string.verdict_fair; Verdict.BAD -> R.string.verdict_bad }),
            fontSize = 18.sp, color = when (f.verdict) { Verdict.GOOD -> RedesColors.AccentText; Verdict.FAIR -> Warn; Verdict.BAD -> RedesColors.Danger })
    }
    Group {
        Factor(stringResource(R.string.spectrum), stringResource(R.string.f_spectrum_text, f.band?.label ?: "—", if (f.widthMhz > 0) "${f.widthMhz} MHz" else "—"),
            f.spectrumOk, stringResource(R.string.f_spectrum_warn))
        Divider()
        Factor(stringResource(R.string.f_radio), stringResource(R.string.f_radio_text, if (f.generation > 0) "Wi‑Fi ${f.generation}" else "—", f.dbm) +
            if (f.maxRxMbps > 0) "\n" + stringResource(R.string.f_radio_max, f.maxRxMbps, f.maxTxMbps) else "", f.radioOk,
            listOfNotNull(if (f.weakSignal) stringResource(R.string.f_radio_weak) else null, if (f.oldStandard) stringResource(R.string.f_radio_old) else null).joinToString(" "))
        Divider()
        Factor(stringResource(R.string.f_channel), stringResource(R.string.f_channel_text, f.channel, f.load), f.channelOk, stringResource(R.string.f_channel_warn))
    }
}

/** «Factores de velocidad celular»: one row per active SIM, or why there is none. */
@Composable private fun CellFactorsCard(state: RedesState, actions: AppActions) {
    Text(stringResource(R.string.cell_factors), Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp), fontSize = 18.sp)
    Group {
        when {
            !state.canPhone -> Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                MainButton(stringResource(R.string.allow_phone), onClick = actions.allowWifi)
            }
            state.sims.isEmpty() -> Text(stringResource(R.string.no_sim), Modifier.padding(20.dp), color = RedesColors.Muted)
            else -> state.sims.forEachIndexed { i, sim ->
                if (i > 0) Divider()
                Factor(stringResource(R.string.sim_n, sim.slot), stringResource(R.string.sim_text, sim.carrier.ifBlank { "—" }, sim.tech.label,
                    sim.dbm?.let { "$it dBm" } ?: "—"), sim.warnings == 0,
                    listOfNotNull(if (sim.weak) stringResource(R.string.sim_weak) else null, if (sim.tech.old) stringResource(R.string.sim_old) else null).joinToString(" "))
            }
        }
    }
}

/** One factor: title, what Android says, and ✓ or ⚠ (with what to do). */
@Composable private fun Factor(title: String, text: String, ok: Boolean, hint: String) =
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, fontSize = 16.sp)
            Text(text, color = RedesColors.Muted, fontSize = 14.sp)
            if (!ok) Text(hint, color = Warn, fontSize = 13.sp)
        }
        Icon(if (ok) Icons.Outlined.CheckCircle else Icons.Outlined.WarningAmber, stringResource(if (ok) R.string.check_ok else R.string.check_warn),
            Modifier.padding(start = 12.dp).size(24.dp), tint = if (ok) RedesColors.AccentText else Warn)
    }

/** A thin bar 0–1, in the accent. */
@Composable fun Meter(f: Float) {
    val track = RedesColors.Chip; val fill = RedesColors.Accent
    Canvas(Modifier.fillMaxWidth().size(height = 10.dp, width = 1.dp)) {
        drawRoundRect(track, cornerRadius = CornerRadius(size.height / 2))
        drawRoundRect(fill, Offset.Zero, Size(size.width * f.coerceIn(0f, 1f), size.height), CornerRadius(size.height / 2))
    }
}

fun levelName(l: com.zirakusa.redes.data.SignalLevel) = when (l) {
    com.zirakusa.redes.data.SignalLevel.EXCELLENT -> R.string.sig_excellent; com.zirakusa.redes.data.SignalLevel.GOOD -> R.string.sig_good
    com.zirakusa.redes.data.SignalLevel.FAIR -> R.string.sig_fair; com.zirakusa.redes.data.SignalLevel.WEAK -> R.string.sig_weak
    com.zirakusa.redes.data.SignalLevel.POOR -> R.string.sig_poor
}
