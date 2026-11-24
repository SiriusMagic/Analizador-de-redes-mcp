package com.zirakusa.redes.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MonitorHeart
import androidx.compose.material.icons.outlined.NetworkCheck
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.WifiFind
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zirakusa.redes.R
import com.zirakusa.redes.ui.screens.DiscoverScreen
import com.zirakusa.redes.ui.screens.HistoryScreen
import com.zirakusa.redes.ui.screens.McpScreen
import com.zirakusa.redes.ui.screens.NearbyScreen
import com.zirakusa.redes.ui.screens.NetworkScreen
import com.zirakusa.redes.ui.screens.SettingsScreen
import com.zirakusa.redes.ui.screens.SignalScreen
import com.zirakusa.redes.ui.screens.SpeedScreen
import com.zirakusa.redes.ui.theme.RedesColors

/** What the screens ask of the phone (the activity gives the real ones; tests leave the defaults). */
class AppActions(
    /** Asks Android to let Redes see the Wi‑Fi's name and the networks nearby. */
    val allowWifi: () -> Unit = {},
    val scan: () -> Unit = {},
    val wifiSettings: () -> Unit = {},
    val copyLink: (String) -> Unit = {},
    val goToHub: () -> Unit = {},
    /** Asks only for the phone permission (the SIMs), and opens the app's page in Android's settings if it was refused. */
    val allowPhone: () -> Unit = {},
    val appSettings: () -> Unit = {},
    /** «Descubrir › Bluetooth»: asks for the permission, starts and stops listening. */
    val allowBle: () -> Unit = {},
    val bleStart: () -> Unit = {},
    val bleStop: () -> Unit = {},
)

/** The whole app: the chosen tab with the bar at the bottom, or a page over it. */
@Composable fun RedesApp(state: RedesState, actions: AppActions = AppActions()) {
    when (state.page) {
        Page.SETTINGS -> return SettingsScreen(state)
        Page.MCP -> return McpScreen(actions.copyLink, actions.goToHub) { state.page = Page.SETTINGS }
        Page.DISCOVER -> return DiscoverScreen(state, actions)
        Page.HISTORY -> return HistoryScreen(state)
        Page.MAIN -> Unit
    }
    Box(Modifier.fillMaxSize().background(RedesColors.Background)) {
        when (state.tab) {
            Tab.NETWORK -> NetworkScreen(state, actions)
            Tab.SPEED -> SpeedScreen(state)
            Tab.SIGNAL -> SignalScreen(state, actions)
            Tab.NEARBY -> NearbyScreen(state, actions)
        }
        Row(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 12.dp).clip(RoundedCornerShape(32.dp))
            .background(RedesColors.Card.copy(alpha = 0.96f)).padding(6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TabItem(Icons.Outlined.NetworkCheck, R.string.tab_network, state.tab == Tab.NETWORK) { state.tab = Tab.NETWORK }
            TabItem(Icons.Outlined.Speed, R.string.tab_speed, state.tab == Tab.SPEED) { state.tab = Tab.SPEED }
            TabItem(Icons.Outlined.MonitorHeart, R.string.tab_signal, state.tab == Tab.SIGNAL) { state.tab = Tab.SIGNAL }
            TabItem(Icons.Outlined.WifiFind, R.string.tab_nearby, state.tab == Tab.NEARBY) { state.tab = Tab.NEARBY }
        }
    }
}

@Composable private fun TabItem(icon: ImageVector, label: Int, on: Boolean, onClick: () -> Unit) =
    Row(Modifier.clip(CircleShape).background(if (on) RedesColors.Accent else Color.Transparent).clickable(onClick = onClick)
        .padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, if (on) null else stringResource(label), tint = if (on) RedesColors.OnAccent else RedesColors.Text)
        if (on) Text(stringResource(label), Modifier.padding(start = 8.dp), color = RedesColors.OnAccent, fontSize = 15.sp)
    }
