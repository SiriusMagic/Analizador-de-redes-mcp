package com.zirakusa.redes.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.WifiFind
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zirakusa.redes.R
import com.zirakusa.redes.data.AccessPoint
import com.zirakusa.redes.data.Band
import com.zirakusa.redes.data.Radio
import com.zirakusa.redes.data.Spectrum
import com.zirakusa.redes.ui.AppActions
import com.zirakusa.redes.ui.RedesState
import com.zirakusa.redes.ui.theme.RedesColors

/** One colour per network on the chart and its dot in the list; yours is always the accent. */
private val NetColors = listOf(Color(0xFFF48FB1), Color(0xFF9FA8FF), Color(0xFFFFD54F), Color(0xFF80DEEA), Color(0xFFFFAB91),
    Color(0xFFCE93D8), Color(0xFFA5D6A7), Color(0xFF90CAF9), Color(0xFFFFCC80), Color(0xFFB0BEC5))

/**
 * «Wi‑Fi cercanas»: the advice for your router's channel, the spectrum of the chosen band (each network as wide as its
 * real channel and as tall as its signal), your network and every network on that band.
 */
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
        val ownBssid = state.status.wifi?.bssid
        val counts = Spectrum.counts(state.nearby)
        var picked by remember { mutableStateOf<Band?>(null) }
        val band = picked ?: state.status.wifi?.band ?: counts.maxByOrNull { it.value }?.key ?: Band.B24
        Segmented(Band.entries.map { "${it.label} (${counts[it] ?: 0})" }, Band.entries.indexOf(band)) { picked = Band.entries[it] }
        val inBand = state.nearby.filter { it.band == band }
        val colors = inBand.mapIndexed { i, ap -> ap.bssid to if (ap.bssid.equals(ownBssid, true)) RedesColors.Accent else NetColors[i % NetColors.size] }.toMap()
        Group { Column(Modifier.padding(start = 12.dp, end = 16.dp, top = 16.dp, bottom = 12.dp)) {
            Row(Modifier.padding(start = 8.dp, bottom = 8.dp)) {
                Text(stringResource(R.string.spectrum), Modifier.weight(1f), fontSize = 18.sp)
                Text(band.label, color = RedesColors.Muted)
            }
            SpectrumChart(inBand, band, ownBssid) { colors[it.bssid] ?: RedesColors.Muted }
            if (inBand.isEmpty()) Text(stringResource(R.string.spectrum_empty, band.label), Modifier.padding(8.dp), color = RedesColors.Muted)
        } }
        inBand.firstOrNull { it.bssid.equals(ownBssid, true) }?.let { own ->
            Group { Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                Dot(RedesColors.Accent)
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(own.ssid.ifEmpty { stringResource(R.string.hidden_network) }, fontSize = 18.sp, maxLines = 1)
                    Text(stringResource(R.string.connected), color = RedesColors.AccentText, fontSize = 13.sp)
                    Text(stringResource(R.string.channel_is, Spectrum.channels(own)), color = RedesColors.Muted, fontSize = 14.sp)
                }
                Text("${own.dbm} dBm", fontSize = 22.sp, color = RedesColors.AccentText)
            } }
        }
        Caption(stringResource(R.string.networks, inBand.size))
        if (state.nearby.isEmpty()) Text(stringResource(R.string.no_networks), Modifier.padding(8.dp), color = RedesColors.Muted)
        else if (inBand.isNotEmpty()) Group {
            inBand.take(40).forEachIndexed { i, ap -> if (i > 0) Divider(); ApRow(ap, ap.bssid.equals(ownBssid, true), colors[ap.bssid] ?: RedesColors.Muted) }
        }
    }

/** The chart: dBm up the side (−95 … −30), channels along the bottom, one trapezoid per network. */
@Composable private fun SpectrumChart(aps: List<AccessPoint>, band: Band, ownBssid: String?, color: (AccessPoint) -> Color) {
    val measurer = rememberTextMeasurer()
    val label = TextStyle(color = RedesColors.Muted, fontSize = 11.sp)
    val lines = RedesColors.Chip
    val axis = Spectrum.axis(band)
    val bells = Spectrum.bells(aps, band).sortedBy { it.ap.bssid.equals(ownBssid, true) } // yours on top
    Canvas(Modifier.fillMaxWidth().height(220.dp)) {
        val left = 34.dp.toPx(); val bottom = 22.dp.toPx(); val top = 6.dp.toPx()
        val w = size.width - left; val h = size.height - bottom - top
        fun x(mhz: Double) = left + (axis.x(mhz) * w).toFloat()
        fun y(dbm: Int) = top + ((1 - Spectrum.y(dbm)) * h).toFloat()
        Spectrum.gridDbm.forEach { d ->
            drawLine(lines, Offset(left, y(d)), Offset(size.width, y(d)), 1.dp.toPx())
            val t = measurer.measure("$d", label)
            drawText(t, topLeft = Offset(left - t.size.width - 6.dp.toPx(), y(d) - t.size.height / 2f))
        }
        drawLine(lines, Offset(left, y(Spectrum.FLOOR)), Offset(size.width, y(Spectrum.FLOOR)), 1.dp.toPx())
        axis.ticks.forEach { (ch, mhz) ->
            val px = x(mhz.toDouble())
            drawLine(lines.copy(alpha = 0.6f), Offset(px, top), Offset(px, y(Spectrum.FLOOR)), 1.dp.toPx())
            val t = measurer.measure("$ch", label)
            drawText(t, topLeft = Offset((px - t.size.width / 2f).coerceIn(left - 6.dp.toPx(), size.width - t.size.width), size.height - t.size.height))
        }
        clipRect(left, 0f, size.width, y(Spectrum.FLOOR)) {
            bells.forEach { b ->
                val yours = b.ap.bssid.equals(ownBssid, true)
                val c = color(b.ap)
                val path = Path().apply {
                    moveTo(x(b.lo), y(Spectrum.FLOOR)); lineTo(x(b.topLo), y(b.dbm)); lineTo(x(b.topHi), y(b.dbm)); lineTo(x(b.hi), y(Spectrum.FLOOR)); close()
                }
                drawPath(path, c.copy(alpha = if (yours) 0.30f else 0.16f))
                drawPath(path, c, style = Stroke((if (yours) 3.dp else 1.5.dp).toPx()))
            }
        }
    }
}

@Composable private fun Dot(color: Color) = androidx.compose.foundation.layout.Box(Modifier.size(10.dp).clip(CircleShape).background(color))

@Composable private fun ApRow(ap: AccessPoint, yours: Boolean, color: Color) = Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
    Dot(color)
    Bars(Radio.bars(ap.dbm), Modifier.padding(start = 12.dp, end = 14.dp))
    Column(Modifier.weight(1f)) {
        Text(ap.ssid.ifEmpty { stringResource(R.string.hidden_network) } + if (yours) " · " + stringResource(R.string.yours) else "", fontSize = 16.sp, maxLines = 1)
        Text("${stringResource(R.string.channel)} ${Spectrum.channels(ap)} · ${ap.dbm} dBm", color = RedesColors.Muted, fontSize = 13.sp)
    }
    Icon(if (ap.security.weak) Icons.Outlined.LockOpen else Icons.Outlined.Lock, ap.security.label, Modifier.size(18.dp),
        tint = if (ap.security.weak) RedesColors.Danger else RedesColors.Muted)
}

/** Four little bars, the lit ones in the accent. */
@Composable fun Bars(lit: Int, modifier: Modifier = Modifier) {
    val on = RedesColors.AccentText; val off = RedesColors.Chip
    Canvas(modifier.size(28.dp, 22.dp)) {
        val w = size.width / 4
        for (i in 0 until 4) {
            val h = size.height * (i + 1) / 4
            drawRoundRect(if (i < lit.coerceAtLeast(1)) on else off, Offset(i * w, size.height - h),
                androidx.compose.ui.geometry.Size(w * 0.7f, h), androidx.compose.ui.geometry.CornerRadius(2.dp.toPx()))
        }
    }
}
