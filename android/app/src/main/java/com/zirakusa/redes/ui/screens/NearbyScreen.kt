package com.zirakusa.redes.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.WifiFind
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zirakusa.redes.R
import com.zirakusa.redes.data.AccessPoint
import com.zirakusa.redes.data.Band
import com.zirakusa.redes.data.Channels
import com.zirakusa.redes.data.Radio
import com.zirakusa.redes.ui.AppActions
import com.zirakusa.redes.ui.RedesState
import com.zirakusa.redes.ui.theme.RedesColors

/** «Wi‑Fi cercanas»: the advice for your router's channel, how full each 2.4 GHz channel is, and every network around. */
@Composable fun NearbyScreen(state: RedesState, actions: AppActions) =
    Column(Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.tab_nearby), Modifier.weight(1f).padding(start = 4.dp), fontSize = 26.sp)
            if (state.canScan) RoundButton(Icons.Outlined.Refresh, stringResource(R.string.scan_again), onClick = actions.scan)
        }
        if (!state.canScan) {
            Group { Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Outlined.WifiFind, null, Modifier.size(48.dp), tint = RedesColors.AccentText)
                Text(stringResource(R.string.allow_scan_text), color = RedesColors.Muted, textAlign = TextAlign.Center)
                MainButton(stringResource(R.string.allow), onClick = actions.allowWifi)
            } }
            return@Column
        }
        state.advice?.let { a ->
            Group { Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.your_channel, a.current, a.band.label), fontSize = 18.sp)
                Text(stringResource(R.string.load, a.load), color = RedesColors.Muted)
                Meter(a.load / 100f)
                Text(stringResource(if (a.worthChanging) R.string.advice_change else R.string.advice_ok, a.best), fontSize = 16.sp)
            } }
        }
        val counts = Channels.count(state.nearby, Band.B24)
        if (counts.isNotEmpty()) {
            Caption(stringResource(R.string.channels_24))
            Group { Row(Modifier.padding(16.dp).height(110.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.Bottom) {
                val most = (counts.values.maxOrNull() ?: 1).coerceAtLeast(1)
                (1..13).forEach { ch ->
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        val n = counts[ch] ?: 0
                        Box(Modifier.width(14.dp).height((70f * n / most).dp.coerceAtLeast(2.dp)).clip(RoundedCornerShape(4.dp))
                            .background(if (ch == state.status.wifi?.channel) RedesColors.Accent else RedesColors.Muted.copy(alpha = 0.5f)))
                        Text("$ch", fontSize = 11.sp, color = RedesColors.Muted)
                    }
                }
            } }
        }
        Caption(stringResource(R.string.networks, state.nearby.size))
        if (state.nearby.isEmpty()) Text(stringResource(R.string.no_networks), Modifier.padding(8.dp), color = RedesColors.Muted)
        else Group { state.nearby.take(40).forEachIndexed { i, ap -> if (i > 0) Divider(); ApRow(ap, ap.bssid.equals(state.status.wifi?.bssid, true)) } }
    }

@Composable private fun ApRow(ap: AccessPoint, yours: Boolean) = Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
    Bars(Radio.bars(ap.dbm), Modifier.padding(end = 14.dp))
    Column(Modifier.weight(1f)) {
        Text(ap.ssid.ifEmpty { stringResource(R.string.hidden_network) } + if (yours) " · " + stringResource(R.string.yours) else "", fontSize = 16.sp, maxLines = 1)
        Text("${ap.band?.label ?: "?"} · ${stringResource(R.string.channel)} ${ap.channel} · ${ap.dbm} dBm", color = RedesColors.Muted, fontSize = 13.sp)
    }
    Icon(if (ap.security.weak) Icons.Outlined.LockOpen else Icons.Outlined.Lock, ap.security.label, Modifier.size(18.dp),
        tint = if (ap.security.weak) RedesColors.Danger else RedesColors.Muted)
}

/** Four little bars, the lit ones in the accent. */
@Composable fun Bars(lit: Int, modifier: Modifier = Modifier) {
    val on = RedesColors.AccentText; val off = RedesColors.Chip
    androidx.compose.foundation.Canvas(modifier.size(28.dp, 22.dp)) {
        val w = size.width / 4
        for (i in 0 until 4) {
            val h = size.height * (i + 1) / 4
            drawRoundRect(if (i < lit.coerceAtLeast(1)) on else off, androidx.compose.ui.geometry.Offset(i * w, size.height - h),
                androidx.compose.ui.geometry.Size(w * 0.7f, h), androidx.compose.ui.geometry.CornerRadius(2.dp.toPx()))
        }
    }
}
