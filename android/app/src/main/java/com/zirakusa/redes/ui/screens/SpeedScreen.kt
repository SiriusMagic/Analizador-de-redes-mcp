package com.zirakusa.redes.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.CellTower
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Router
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zirakusa.redes.R
import com.zirakusa.redes.data.HistoryView
import com.zirakusa.redes.data.Link
import com.zirakusa.redes.data.Phase
import com.zirakusa.redes.data.Use
import com.zirakusa.redes.ui.Page
import com.zirakusa.redes.ui.RedesState
import com.zirakusa.redes.ui.theme.RedesColors
import java.time.ZoneId
import java.util.Locale
import kotlin.math.ln

/** «Velocidad»: the gauge (the speed climbs as it is measured), «Iniciar», the result with what it is good for, and the history. */
@OptIn(ExperimentalLayoutApi::class)
@Composable fun SpeedScreen(state: RedesState, zone: ZoneId = ZoneId.systemDefault()) =
    Column(Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(stringResource(R.string.tab_speed), Modifier.fillMaxWidth().padding(top = 20.dp, start = 4.dp), fontSize = 26.sp)
        val running = state.phase != null
        val shown = if (running) state.live else state.last?.down ?: 0.0
        Gauge(shown, when (state.phase) {
            Phase.PING -> stringResource(R.string.phase_ping); Phase.DOWNLOAD -> stringResource(R.string.download)
            Phase.UPLOAD -> stringResource(R.string.upload); else -> stringResource(if (state.last != null) R.string.download else R.string.ready)
        })
        if (state.failed) Text(stringResource(R.string.test_failed), color = RedesColors.Danger, fontSize = 15.sp)
        if (!running) MainButton(stringResource(if (state.last == null) R.string.start else R.string.again)) { state.startTest() }
        state.last?.let { r ->
            Group { Row(Modifier.padding(16.dp)) {
                Figure(stringResource(R.string.download), "${num(r.down)}", "Mbps"); Figure(stringResource(R.string.upload), "${num(r.up)}", "Mbps")
                Figure(stringResource(R.string.ping), "${r.ping.toInt()}", "ms"); Figure(stringResource(R.string.jitter), "${r.jitter.toInt()}", "ms")
            } }
            Text(stringResource(R.string.good_for), Modifier.fillMaxWidth().padding(start = 4.dp), color = RedesColors.Muted)
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Use.entries.forEach { u ->
                    val ok = u in r.uses
                    Text((if (ok) "✓ " else "✗ ") + stringResource(useName(u)), Modifier.clip(CircleShape)
                        .background(if (ok) RedesColors.Accent.copy(alpha = 0.2f) else RedesColors.Chip).padding(horizontal = 12.dp, vertical = 7.dp),
                        color = if (ok) RedesColors.Text else RedesColors.Muted, fontSize = 14.sp)
                }
            }
            if (r.server.isNotBlank()) Text(stringResource(R.string.server, r.server), color = RedesColors.Muted, fontSize = 13.sp)
        }
        if (state.status.link != Link.NONE) PathCard(state)
        if (state.results.isNotEmpty()) {
            Box(Modifier.fillMaxWidth()) { Caption(stringResource(R.string.history)) }
            val top = HistoryView.top(state.results)
            state.results.take(3).forEach { TestCard(it, top, state.model, zone) }
            Group { Item(stringResource(R.string.history_all, state.results.size), null, onClick = { state.query = ""; state.page = Page.HISTORY }) }
        }
    }

fun num(v: Double) = if (v < 100) String.format(Locale.ROOT, "%.1f", v).replace('.', ',') else v.toInt().toString()

/**
 * The path of the test: your provider (its network number and your public address only if Cloudflare said them in the
 * last test, else just «Internet») → the access point (or the mobile network) → this phone, with its local address.
 */
@Composable private fun PathCard(state: RedesState) = Group {
    val s = state.status
    val o = state.origin
    Row(Modifier.fillMaxWidth().padding(vertical = 16.dp, horizontal = 8.dp), verticalAlignment = Alignment.Top) {
        Node(Icons.Outlined.Public, stringResource(R.string.path_internet), listOfNotNull(o?.asn?.takeIf { it.isNotBlank() }, o?.ip?.takeIf { it.isNotBlank() }).joinToString("\n"))
        Arrow()
        Node(if (s.link == Link.CELLULAR) Icons.Outlined.CellTower else Icons.Outlined.Router,
            if (s.link == Link.CELLULAR) stringResource(R.string.path_cell) else stringResource(R.string.path_ap),
            if (s.link == Link.CELLULAR) s.networkName() else s.wifi?.let { w -> listOfNotNull(w.ssid, w.band?.label).joinToString("\n") } ?: s.gateway)
        Arrow()
        Node(Icons.Outlined.Smartphone, state.model.ifBlank { stringResource(R.string.this_phone) }, s.addresses.firstOrNull().orEmpty())
    }
}

@Composable private fun androidx.compose.foundation.layout.RowScope.Node(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, detail: String) =
    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.size(48.dp).clip(CircleShape).background(RedesColors.Chip), contentAlignment = Alignment.Center) {
            Icon(icon, null, Modifier.size(26.dp), tint = RedesColors.AccentText)
        }
        Text(title, fontSize = 14.sp, textAlign = TextAlign.Center, maxLines = 2)
        if (detail.isNotBlank()) Text(detail, color = RedesColors.Muted, fontSize = 12.sp, textAlign = TextAlign.Center)
    }

@Composable private fun Arrow() = Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, Modifier.padding(top = 12.dp).size(22.dp), tint = RedesColors.Muted)

@Composable private fun androidx.compose.foundation.layout.RowScope.Figure(label: String, value: String, unit: String) =
    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = RedesColors.Muted, fontSize = 12.sp)
        Text(value, fontSize = 22.sp, fontWeight = FontWeight.Medium)
        Text(unit, color = RedesColors.Muted, fontSize = 12.sp)
    }

/** A half ring from 0 to 1000 Mbps on a log scale (slow links still move it), with the number big in the middle. */
@Composable private fun Gauge(mbps: Double, label: String) {
    val f by animateFloatAsState((ln(1 + mbps) / ln(1001.0)).toFloat().coerceIn(0f, 1f), tween(400), label = "gauge")
    val track = RedesColors.Chip; val fill = RedesColors.Accent
    Box(Modifier.size(260.dp, 180.dp), contentAlignment = Alignment.BottomCenter) {
        Canvas(Modifier.size(240.dp)) {
            val w = 18.dp.toPx()
            val box = Size(size.width - w, size.height - w)
            drawArc(track, 150f, 240f, false, Offset(w / 2, w / 2), box, style = Stroke(w, cap = StrokeCap.Round))
            drawArc(fill, 150f, 240f * f, false, Offset(w / 2, w / 2), box, style = Stroke(w, cap = StrokeCap.Round))
        }
        Column(Modifier.padding(bottom = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(num(mbps), fontSize = 52.sp, fontWeight = FontWeight.Light)
            Text("Mbps · $label", color = RedesColors.Muted, fontSize = 14.sp)
        }
    }
}

fun useName(u: Use) = when (u) {
    Use.BROWSE -> R.string.use_browse; Use.MUSIC -> R.string.use_music; Use.VIDEO_HD -> R.string.use_hd; Use.VIDEO_4K -> R.string.use_4k
    Use.CALLS -> R.string.use_calls; Use.GAMING -> R.string.use_gaming; Use.UPLOADS -> R.string.use_uploads
}

fun linkName(l: com.zirakusa.redes.data.Link) = when (l) {
    com.zirakusa.redes.data.Link.WIFI -> R.string.wifi; com.zirakusa.redes.data.Link.CELLULAR -> R.string.mobile_data
    com.zirakusa.redes.data.Link.ETHERNET -> R.string.ethernet; com.zirakusa.redes.data.Link.VPN -> R.string.vpn
    com.zirakusa.redes.data.Link.NONE -> R.string.no_connection
}
