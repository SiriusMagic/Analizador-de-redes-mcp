package com.zirakusa.redes

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The app's launcher icons: the Wi‑Fi waves in four styles, «Lupa» by default (since 0.3.1). Each is an `activity-alias`
 * in the manifest; exactly one is enabled. Alias names never change (Android keeps the enabled state by name), so the old
 * colour aliases now carry the new pictures, and the fifth one («Verde», [RETIRED]) stays in the manifest, always off.
 */
enum class AppIcon(val alias: String, val label: Int, val preview: Int, val background: Long) {
    LENS("Home", R.string.icon_lens, R.mipmap.ic_launcher_lens_foreground, 0xFF02A3FC),
    NEON("HomeClaro", R.string.icon_neon, R.mipmap.ic_launcher_neon_foreground, 0xFF00104E),
    BUBBLE("HomeDorado", R.string.icon_bubble, R.mipmap.ic_launcher_bubble_foreground, 0xFF1AB8F0),
    PIXEL("HomeAzul", R.string.icon_pixel, R.mipmap.ic_launcher_pixel_foreground, 0xFF00013A);

    private fun component(context: Context) = ComponentName(context.packageName, "com.zirakusa.redes.$alias")
    fun isEnabled(context: Context): Boolean = when (context.packageManager.getComponentEnabledSetting(component(context))) {
        PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
        PackageManager.COMPONENT_ENABLED_STATE_DEFAULT -> this == LENS           // as the manifest says
        else -> false
    }

    companion object {
        var current by mutableStateOf(LENS); private set

        /** The alias of the fifth icon of 0.3.0 («Verde»): never offered again, always turned off. */
        const val RETIRED = "HomeVerde"
        private fun retired(context: Context) = ComponentName(context.packageName, "com.zirakusa.redes.$RETIRED")

        /** Reads the enabled one. If none is, or the retired «Verde» still is, «Lupa» comes back: never no icon, never two. */
        fun load(context: Context) {
            val on = entries.firstOrNull { it.isEnabled(context) }
            val old = context.packageManager.getComponentEnabledSetting(retired(context)) == PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            if (on == null || old) apply(context, on ?: LENS) else current = on
        }

        /** Enables [icon] first and then disables the others, without closing the app. Launchers refresh on their own. */
        fun apply(context: Context, icon: AppIcon) {
            val pm = context.packageManager
            pm.setComponentEnabledSetting(icon.component(context), PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
            entries.filter { it != icon }.forEach {
                pm.setComponentEnabledSetting(it.component(context), PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
            }
            pm.setComponentEnabledSetting(retired(context), PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
            current = icon
        }
    }
}

/** «Icono de la app»: the four icons; a tap changes the home-screen icon. */
@Composable fun IconCard(card: Color, text: Color, muted: Color, accent: Color) {
    val context = LocalContext.current
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(card).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.icon_title), color = text, fontSize = 18.sp)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            AppIcon.entries.forEach { icon ->
                val on = icon == AppIcon.current
                val name = stringResource(icon.label)
                Column(Modifier.clip(RoundedCornerShape(14.dp)).clickable(onClickLabel = name) { AppIcon.apply(context, icon) }.padding(2.dp),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(Modifier.size(52.dp).clip(RoundedCornerShape(14.dp)).background(Color(icon.background))
                        .border(if (on) 3.dp else 0.dp, if (on) accent else Color.Transparent, RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
                        Image(painterResource(icon.preview), null, Modifier.size(52.dp))
                    }
                    Text(name, color = if (on) accent else muted, fontSize = 11.sp, textAlign = TextAlign.Center, maxLines = 1)
                }
            }
        }
        Text(stringResource(R.string.icon_hint), color = muted, fontSize = 13.sp)
    }
}
