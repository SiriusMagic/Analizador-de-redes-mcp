package com.zirakusa.redes.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Router
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zirakusa.redes.BleState
import com.zirakusa.redes.R
import com.zirakusa.redes.data.BleDevice
import com.zirakusa.redes.data.Device
import com.zirakusa.redes.data.DeviceSort
import com.zirakusa.redes.data.Devices
import com.zirakusa.redes.data.Lan
import com.zirakusa.redes.data.Role
import com.zirakusa.redes.ui.AppActions
import com.zirakusa.redes.ui.Page
import com.zirakusa.redes.ui.RedesState
import com.zirakusa.redes.ui.theme.RedesColors

/**
 * «Descubrir»: the devices on your own network (Wi‑Fi tab: the router is «Portal», this phone «Yo») and the Bluetooth
 * LE ones nearby (listened to only while this tab is open). Nothing leaves the phone and nothing is kept.
 */
@Composable fun DiscoverScreen(state: RedesState, actions: AppActions) = Sheet(stringResource(R.string.discover), onBack = { state.page = Page.MAIN }) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 40.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Segmented(listOf(stringResource(R.string.wifi), stringResource(R.string.bluetooth)), tab, Modifier.fillMaxWidth()) { tab = it }
        if (tab == 0) LanTab(state) else BleTab(state, actions)
    }
}

@Composable private fun Header(count: Int, sort: (() -> Unit)?, again: (() -> Unit)?) = Row(verticalAlignment = Alignment.CenterVertically) {
    Text(stringResource(R.string.devices_n, count), Modifier.weight(1f).padding(start = 4.dp), fontSize = 20.sp)
    if (sort != null) RoundButton(Icons.AutoMirrored.Outlined.Sort, stringResource(R.string.sort), onClick = sort)
    if (again != null) RoundButton(Icons.Outlined.Refresh, stringResource(R.string.scan_again), Modifier.padding(start = 8.dp), onClick = again)
}

@Composable private fun LanTab(state: RedesState) {
    val can = Devices.canLook(state.status)
    LaunchedEffect(can) { if (can && state.devices.isEmpty()) state.discover() }
    Header(state.devices.size, if (state.devices.isNotEmpty()) state::sortDevices else null, if (can && !state.looking) state::discover else null)
    if (!can) return Group { Text(stringResource(R.string.discover_no_lan), Modifier.padding(20.dp), color = RedesColors.Muted) }
    Text(if (state.looking) stringResource(R.string.discover_looking, state.looked, Lan.hosts(state.status.addresses.firstOrNull { Lan.parts(it) != null }.orEmpty()).size)
        else stringResource(if (state.deviceSort == DeviceSort.IP) R.string.sorted_ip else R.string.sorted_name), Modifier.padding(start = 4.dp), color = RedesColors.Muted, fontSize = 14.sp)
    if (state.devices.isNotEmpty()) Group { state.devices.forEachIndexed { i, d -> if (i > 0) Divider(); DeviceRow(d, state.model) } }
    Text(stringResource(R.string.discover_lan_note), Modifier.padding(horizontal = 4.dp), color = RedesColors.Muted, fontSize = 13.sp)
}

@Composable private fun DeviceRow(d: Device, model: String) = Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
    Icon(when (d.role) { Role.GATEWAY -> Icons.Outlined.Router; Role.ME -> Icons.Outlined.Smartphone; Role.OTHER -> Icons.Outlined.Devices }, null,
        Modifier.size(28.dp), tint = if (d.role == Role.OTHER) RedesColors.Muted else RedesColors.AccentText)
    Column(Modifier.weight(1f).padding(start = 14.dp)) {
        Text(d.ip, fontSize = 17.sp)
        Text(when (d.role) { Role.GATEWAY -> stringResource(R.string.role_gateway); Role.ME -> stringResource(R.string.role_me, model.ifBlank { stringResource(R.string.this_phone) })
            Role.OTHER -> d.name.ifBlank { stringResource(R.string.role_other) } }, color = RedesColors.Muted, fontSize = 14.sp)
    }
}

@Composable private fun BleTab(state: RedesState, actions: AppActions) {
    val ble = state.bleState
    DisposableEffect(ble, state.resumes) {
        if (ble == BleState.READY) actions.bleStart()
        onDispose { actions.bleStop() }
    }
    Header(state.ble.size, null, null)
    when (ble) {
        BleState.UNSUPPORTED -> Group { Text(stringResource(R.string.ble_unsupported), Modifier.padding(20.dp), color = RedesColors.Muted) }
        BleState.OFF -> Group { Text(stringResource(R.string.ble_off), Modifier.padding(20.dp), color = RedesColors.Muted) }
        BleState.NO_PERMISSION -> Group {
            Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(stringResource(R.string.ble_permission), color = RedesColors.Muted)
                MainButton(stringResource(R.string.allow), onClick = actions.allowBle)
            }
        }
        BleState.READY -> if (state.ble.isEmpty()) Group { Text(stringResource(R.string.ble_looking), Modifier.padding(20.dp), color = RedesColors.Muted) }
            else Group { state.ble.take(80).forEachIndexed { i, d -> if (i > 0) Divider(); BleRow(d) } }
    }
}

@Composable private fun BleRow(d: BleDevice) = Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
    Icon(Icons.Outlined.Bluetooth, null, Modifier.size(26.dp), tint = RedesColors.AccentText)
    Column(Modifier.weight(1f).padding(start = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(d.name.ifBlank { stringResource(R.string.role_other) }, Modifier.weight(1f, fill = false), fontSize = 16.sp, maxLines = 1)
            Text("LE", Modifier.padding(start = 8.dp).clip(CircleShape).background(RedesColors.Chip).padding(horizontal = 8.dp, vertical = 2.dp), fontSize = 11.sp)
        }
        Text(d.address, color = RedesColors.Muted, fontSize = 13.sp)
    }
    Text("${d.dbm} dBm", Modifier.padding(start = 8.dp), fontSize = 15.sp)
}
