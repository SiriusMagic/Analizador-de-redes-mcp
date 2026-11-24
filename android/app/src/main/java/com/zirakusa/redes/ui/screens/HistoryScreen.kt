package com.zirakusa.redes.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zirakusa.redes.R
import com.zirakusa.redes.data.HistoryView
import com.zirakusa.redes.data.Link
import com.zirakusa.redes.data.Result
import com.zirakusa.redes.ui.Page
import com.zirakusa.redes.ui.RedesState
import com.zirakusa.redes.ui.theme.RedesColors
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

val DownColor = Color(0xFF4F8DFD)
val UpColor = Color(0xFFB07CFF)

/** «Historial de prueba de velocidad»: every test as a card, with a search by network or kind. */
@Composable fun HistoryScreen(state: RedesState, zone: ZoneId = ZoneId.systemDefault()) =
    Sheet(stringResource(R.string.history_short), onBack = { state.page = Page.MAIN }) {
        val list = state.found
        val top = HistoryView.top(state.results)
        LazyColumn(Modifier.fillMaxWidth(), contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { Text(stringResource(R.string.history_title), Modifier.padding(start = 4.dp, bottom = 4.dp), fontSize = 22.sp) }
            item {
                OutlinedTextField(state.query, { state.query = it.take(40) }, Modifier.fillMaxWidth(), singleLine = true,
                    leadingIcon = { Icon(Icons.Outlined.Search, null) }, placeholder = { Text(stringResource(R.string.history_search)) })
            }
            if (list.isEmpty()) item { Text(stringResource(R.string.history_none), Modifier.padding(8.dp), color = RedesColors.Muted) }
            items(list, key = { it.id }) { TestCard(it, top, state.model, zone) }
        }
    }

/** One test: when, on what, a blue bar for download and a purple one for upload (on the same scale), and the path with its delay. */
@Composable fun TestCard(r: Result, top: Double, model: String, zone: ZoneId) = Group {
    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).format(Instant.ofEpochMilli(r.at).atZone(zone)),
                Modifier.weight(1f), color = RedesColors.Muted, fontSize = 13.sp)
            Text(when (r.link) {
                Link.CELLULAR -> stringResource(R.string.cellular) + r.network.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty()
                Link.WIFI -> r.network.ifBlank { stringResource(R.string.wifi) }
                else -> stringResource(linkName(r.link))
            }, fontSize = 15.sp)
        }
        SpeedBar("↓", r.down, top, DownColor)
        SpeedBar("↑", r.up, top, UpColor)
        Text(stringResource(R.string.history_path, model.ifBlank { stringResource(R.string.this_phone) }, r.ping.toInt()), color = RedesColors.Muted, fontSize = 13.sp)
    }
}

@Composable private fun SpeedBar(arrow: String, mbps: Double, top: Double, color: Color) = Row(verticalAlignment = Alignment.CenterVertically) {
    Text(arrow, Modifier.width(18.dp), color = color, fontSize = 15.sp)
    val track = RedesColors.Chip
    Canvas(Modifier.weight(1f).height(10.dp)) {
        drawRoundRect(track, cornerRadius = CornerRadius(size.height / 2))
        drawRoundRect(color, size = Size(size.width * HistoryView.bar(mbps, top), size.height), cornerRadius = CornerRadius(size.height / 2))
    }
    Text("${num(mbps)} Mbps", Modifier.width(96.dp).padding(start = 10.dp), fontSize = 14.sp)
}
