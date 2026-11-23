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
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SignalCellularAlt
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.material.icons.outlined.VpnLock
import androidx.compose.material.icons.outlined.SettingsEthernet
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zirakusa.redes.R
import com.zirakusa.redes.data.Link
import com.zirakusa.redes.data.Radio
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
        if (s.link != Link.NONE) Group {
            Item(stringResource(R.string.local_ip), null, value = s.addresses.firstOrNull() ?: "—", onClick = null)
            if (s.gateway.isNotBlank()) { Divider(); Item(stringResource(R.string.router), null, value = s.gateway, onClick = null) }
            if (s.dns.isNotEmpty()) { Divider(); Item(stringResource(R.string.dns), null, value = s.dns.take(2).joinToString("\n"), onClick = null) }
            if (s.privateDns.isNotBlank()) { Divider(); Item(stringResource(R.string.private_dns), null, value = s.privateDns, onClick = null) }
            Divider(); Item(stringResource(R.string.metered), null, value = stringResource(if (s.metered) R.string.yes else R.string.no), onClick = null)
        }
        Item(stringResource(R.string.open_wifi_settings), null, onClick = actions.wifiSettings)
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
