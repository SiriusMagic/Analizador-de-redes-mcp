package com.zirakusa.redes

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.ScanResult
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import com.zirakusa.redes.data.AccessPoint
import com.zirakusa.redes.data.Link
import com.zirakusa.redes.data.Security
import com.zirakusa.redes.data.Status
import com.zirakusa.redes.data.WifiNow
import java.net.Inet4Address

/**
 * What Android says about the connection: the kind, the Wi‑Fi (name only with the location permission), addresses,
 * router, DNS, and the Wi‑Fi networks nearby. Reads only; nothing is sent anywhere.
 */
class Phone(context: Context) {
    private val app = context.applicationContext
    private val cm = app.getSystemService(ConnectivityManager::class.java)
    private val wm = app.getSystemService(WifiManager::class.java)
    private val tm = app.getSystemService(TelephonyManager::class.java)

    fun canName() = granted(Manifest.permission.ACCESS_FINE_LOCATION)
    fun canScan() = if (Build.VERSION.SDK_INT >= 33) granted(Manifest.permission.NEARBY_WIFI_DEVICES) || canName() else canName()
    private fun granted(p: String) = ContextCompat.checkSelfPermission(app, p) == PackageManager.PERMISSION_GRANTED

    fun status(): Status {
        val net = cm?.activeNetwork ?: return Status.NONE
        val caps = cm.getNetworkCapabilities(net) ?: return Status.NONE
        val props = cm.getLinkProperties(net)
        val link = when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> Link.VPN
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> Link.WIFI
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> Link.CELLULAR
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> Link.ETHERNET
            else -> Link.NONE
        }
        val addresses = props?.linkAddresses.orEmpty().map { it.address }.sortedBy { if (it is Inet4Address) 0 else 1 }.mapNotNull { it.hostAddress?.substringBefore('%') }
        val gateway = props?.routes.orEmpty().firstOrNull { it.isDefaultRoute && it.gateway != null }?.gateway?.hostAddress.orEmpty()
        return Status(link, caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED),
            !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED),
            if (link == Link.WIFI) wifi(caps) else null,
            if (link == Link.CELLULAR) tm?.networkOperatorName.orEmpty() else "", if (link == Link.CELLULAR) cellType() else "",
            Status.shown(addresses), gateway, props?.dnsServers.orEmpty().mapNotNull { it.hostAddress?.substringBefore('%') },
            if (Build.VERSION.SDK_INT >= 28) props?.privateDnsServerName.orEmpty() else "",
            caps.linkDownstreamBandwidthKbps, caps.linkUpstreamBandwidthKbps)
    }

    @Suppress("DEPRECATION") // WifiInfo from the capabilities hides the name unless asked by a callback; this older call still gives it with the permission.
    private fun wifi(caps: NetworkCapabilities): WifiNow? {
        val info: WifiInfo = (if (Build.VERSION.SDK_INT >= 31) caps.transportInfo as? WifiInfo else null)?.takeIf { it.ssid != UNKNOWN }
            ?: wm?.connectionInfo ?: return null
        val ssid = info.ssid?.removeSurrounding("\"")?.takeIf { it.isNotBlank() && it != UNKNOWN && it != "<unknown ssid>" }
        val bssid = info.bssid?.takeIf { it != "02:00:00:00:00:00" }
        val security = bssid?.let { b -> lastScan().firstOrNull { it.bssid.equals(b, true) }?.security }
        return WifiNow(ssid, bssid, info.rssi, info.frequency, info.linkSpeed, if (Build.VERSION.SDK_INT >= 30) info.wifiStandard else 0, security)
    }

    @SuppressLint("MissingPermission") // only asked after the user allowed it
    private fun cellType(): String = runCatching {
        if (!granted(Manifest.permission.READ_PHONE_STATE)) return ""
        when (tm?.dataNetworkType) {
            TelephonyManager.NETWORK_TYPE_NR -> "5G"
            TelephonyManager.NETWORK_TYPE_LTE -> "4G"
            TelephonyManager.NETWORK_TYPE_HSPAP, TelephonyManager.NETWORK_TYPE_HSPA, TelephonyManager.NETWORK_TYPE_UMTS -> "3G"
            TelephonyManager.NETWORK_TYPE_EDGE, TelephonyManager.NETWORK_TYPE_GPRS -> "2G"
            else -> ""
        }
    }.getOrDefault("")

    /** Asks Android to look for networks (it allows a few times every 2 minutes); the answer comes in [lastScan]. */
    @Suppress("DEPRECATION") // still the only way to ask for a fresh list
    fun scan(): Boolean = canScan() && runCatching { wm?.startScan() == true }.getOrDefault(false)

    @SuppressLint("MissingPermission")
    fun lastScan(): List<AccessPoint> = if (!canScan()) emptyList() else runCatching {
        wm?.scanResults.orEmpty().map { r: ScanResult ->
            @Suppress("DEPRECATION") val name = if (Build.VERSION.SDK_INT >= 33) r.wifiSsid?.toString()?.removeSurrounding("\"").orEmpty() else r.SSID.orEmpty()
            AccessPoint(name.takeIf { it != UNKNOWN }.orEmpty(), r.BSSID.orEmpty(), r.frequency, r.level, Security.of(r.capabilities.orEmpty()))
        }.sortedByDescending { it.dbm }
    }.getOrDefault(emptyList())

    private companion object { const val UNKNOWN = "<unknown ssid>" } // WifiManager.UNKNOWN_SSID, only named from Android 11
}
