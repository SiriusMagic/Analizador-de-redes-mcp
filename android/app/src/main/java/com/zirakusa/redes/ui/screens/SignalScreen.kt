package com.zirakusa.redes.ui.screens

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zirakusa.redes.R
import com.zirakusa.redes.data.Chart
import com.zirakusa.redes.data.ChartPoint
import com.zirakusa.redes.data.ChartRange
import com.zirakusa.redes.data.LatencyStats
import com.zirakusa.redes.data.Ping
import com.zirakusa.redes.data.RoamEvent
import com.zirakusa.redes.ui.AppActions
import com.zirakusa.redes.ui.RedesState
import com.zirakusa.redes.ui.theme.RedesColors
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** Internet's line on the latency chart (the router's is the accent). */
val InternetColor = Color(0xFF9FA8FF)

/**
 * «Señal»: the connected Wi‑Fi (band and width, dBm, link speed ↓/↑) with its signal over the last 5 minutes, the
 * latency to the router and to Internet with loss, and the access point changes of your own network.
 */
@Composable fun SignalScreen(state: RedesState, actions: AppActions, zone: ZoneId = ZoneId.systemDefault(), now: Long = state.now()) =
    Column(Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.tab_signal), Modifier.fillMaxWidth().padding(top = 20.dp, start = 4.dp), fontSize = 26.sp)
        val w = state.status.wifi
        if (w == null) Group { Text(stringResource(R.string.signal_no_wifi), Modifier.padding(20.dp), color = RedesColors.Muted) }
        else {
            Group {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(w.ssid ?: stringResource(R.string.wifi_hidden_name), fontSize = 20.sp)
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text("${w.dbm}", fontSize = 48.sp)
                        Text(" dBm · ${stringResource(levelName(w.level))}", Modifier.padding(bottom = 9.dp), fontSize = 17.sp, color = RedesColors.Muted)
                    }
                }
                Divider(); Item(stringResource(R.string.band_width), null, value = w.bandWidth, onClick = null)
                Divider(); Item(stringResource(R.string.channel_title), null, value = "${w.channel}", onClick = null)
                Divider(); Item(stringResource(R.string.phy_speed), null, value = "↓ ${w.downMbps}  ↑ ${w.upMbps} Mbps", onClick = null)
            }
            Caption(stringResource(R.string.signal_chart))
            Group {
                val range = Chart.dbm(state.signal.map { it.dbm })
                LineChart(listOf(Chart.points(state.signal.map { it.at to it.dbm.toDouble() }, now, WINDOW, range) to RedesColors.Accent), range, "dBm",
                    Modifier.padding(16.dp))
                if (state.signal.isEmpty()) Text(stringResource(R.string.signal_wait), Modifier.padding(start = 20.dp, bottom = 16.dp), color = RedesColors.Muted, fontSize = 14.sp)
            }
        }
        LatencyCard(state, now)
        RoamingCard(state, actions, zone)
    }

private const val WINDOW = 5 * 60_000L

@Composable private fun LatencyCard(state: RedesState, now: Long) {
    Caption(stringResource(R.string.latency))
    Group {
        val g = LatencyStats.of(state.gatewayPings); val i = LatencyStats.of(state.internetPings)
        LatencyRow(stringResource(R.string.latency_router), state.status.gateway.ifBlank { "—" }, g, RedesColors.Accent)
        Divider()
        LatencyRow(stringResource(R.string.latency_internet), "speed.cloudflare.com", i, InternetColor)
        val range = Chart.ms((state.gatewayPings + state.internetPings).mapNotNull { it.ms })
        fun pts(p: List<Ping>) = Chart.points(p.map { it.at to it.ms }, now, LATENCY_WINDOW, range)
        LineChart(listOf(pts(state.gatewayPings) to RedesColors.Accent, pts(state.internetPings) to InternetColor), range, "ms", Modifier.padding(16.dp))
        Text(stringResource(if (state.gatewayPings.isEmpty() && state.internetPings.isEmpty()) R.string.latency_wait else R.string.latency_hint),
            Modifier.padding(start = 20.dp, end = 20.dp, bottom = 16.dp), color = RedesColors.Muted, fontSize = 13.sp)
    }
}

private const val LATENCY_WINDOW = 3 * 60_000L

@Composable private fun LatencyRow(title: String, where: String, s: LatencyStats, color: Color) =
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(12.dp).clip(CircleShape).background(color))
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(title, fontSize = 17.sp)
            Text(where, color = RedesColors.Muted, fontSize = 13.sp)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(s.average?.let { "${ms(it)} ms" } ?: "—", fontSize = 17.sp)
            if (s.count > 0) Text(stringResource(R.string.latency_detail, s.jitter?.let(::ms) ?: "—", s.lossPercent),
                color = if (s.lossPercent > 0) RedesColors.Danger else RedesColors.Muted, fontSize = 13.sp)
        }
    }

private fun ms(v: Double) = if (v < 10) String.format(Locale.ROOT, "%.1f", v).replace('.', ',') else v.toInt().toString()

/** «Roaming de punto de acceso»: each change of access point of your own Wi‑Fi, newest first. */
@Composable private fun RoamingCard(state: RedesState, actions: AppActions, zone: ZoneId) {
    Caption(stringResource(R.string.roaming))
    Group {
        if (state.roams.isEmpty()) {
            Text(stringResource(R.string.roaming_empty), Modifier.padding(20.dp), color = RedesColors.Muted)
            if (!state.canName) Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 20.dp), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.roaming_needs_permission), color = RedesColors.Muted, fontSize = 13.sp)
                MainButton(stringResource(R.string.allow), onClick = actions.allowWifi)
            }
        } else state.roams.take(30).forEachIndexed { i, e -> if (i > 0) Divider(); RoamRow(e, zone) }
    }
}

@Composable private fun RoamRow(e: RoamEvent, zone: ZoneId) =
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("${e.from} → ${e.to}", fontSize = 15.sp)
            Text("${DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT).format(Instant.ofEpochMilli(e.at).atZone(zone))} · ${e.ssid} · " +
                stringResource(R.string.roaming_channels, e.fromChannel, e.toChannel), color = RedesColors.Muted, fontSize = 13.sp)
        }
        Text("${e.dbmBefore} → ${e.dbmAfter} dBm", Modifier.padding(start = 8.dp), fontSize = 14.sp)
    }

/** A line chart of a few series on [range] with its grid and labels; a null point breaks the line (a lost ping). */
@Composable fun LineChart(series: List<Pair<List<ChartPoint>, Color>>, range: ChartRange, unit: String, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    val label = TextStyle(color = RedesColors.Muted, fontSize = 11.sp)
    val grid = RedesColors.Chip
    Canvas(modifier.fillMaxWidth().height(150.dp)) {
        val left = 44.dp.toPx(); val bottom = size.height - 4.dp.toPx(); val top = 8.dp.toPx()
        fun y(f: Float) = bottom - f * (bottom - top)
        range.lines.forEach { v ->
            val py = y(range.y(v))
            drawLine(grid, Offset(left, py), Offset(size.width, py), 1.dp.toPx())
            val t = measurer.measure(if (v % 1.0 == 0.0) "${v.toInt()}" else String.format(Locale.ROOT, "%.1f", v).replace('.', ','), label)
            drawText(t, topLeft = Offset(left - t.size.width - 6.dp.toPx(), py - t.size.height / 2f))
        }
        val u = measurer.measure(unit, label)
        drawText(u, topLeft = Offset(size.width - u.size.width, 0f))
        series.forEach { (points, color) ->
            val path = Path(); var open = false
            points.forEach { p ->
                val py = p.y
                if (py == null) { open = false; return@forEach }
                val px = left + p.x * (size.width - left)
                if (open) path.lineTo(px, y(py)) else path.moveTo(px, y(py))
                open = true
            }
            drawPath(path, color, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            points.lastOrNull { it.y != null }?.let { p -> drawCircle(color, 4.dp.toPx(), Offset(left + p.x * (size.width - left), y(p.y!!))) }
        }
    }
}
