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
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import com.zirakusa.redes.data.AccessPoint
import com.zirakusa.redes.data.CellTech
import com.zirakusa.redes.data.Link
import com.zirakusa.redes.data.Security
import com.zirakusa.redes.data.Sim
import com.zirakusa.redes.data.Spectrum
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
        val seen = bssid?.let { b -> lastScan().firstOrNull { it.bssid.equals(b, true) } }
        val (standard, maxRx, maxTx) = if (Build.VERSION.SDK_INT >= 30)
            Triple(info.wifiStandard, info.maxSupportedRxLinkSpeedMbps.coerceAtLeast(0), info.maxSupportedTxLinkSpeedMbps.coerceAtLeast(0))
            else Triple(0, 0, 0)
        val (rx, tx) = if (Build.VERSION.SDK_INT >= 29) info.rxLinkSpeedMbps.coerceAtLeast(0) to info.txLinkSpeedMbps.coerceAtLeast(0) else 0 to 0
        return WifiNow(ssid, bssid, info.rssi, info.frequency, info.linkSpeed, standard, seen?.security, seen?.widthMhz ?: 0,
            seen?.centerMhz ?: 0, maxRx, maxTx, rx, tx)
    }

    @SuppressLint("MissingPermission") // only asked after the user allowed it
    private fun cellType(): String = runCatching {
        if (!granted(Manifest.permission.READ_PHONE_STATE)) return ""
        CellTech.of(tm?.dataNetworkType ?: 0).takeIf { it != CellTech.UNKNOWN }?.label.orEmpty()
    }.getOrDefault("")

    fun canPhone() = granted(Manifest.permission.READ_PHONE_STATE)

    /** «Samsung Galaxy A54»: the phone's maker and model, for «Yo» and the path diagram. */
    fun model(): String = com.zirakusa.redes.data.Devices.model(Build.MANUFACTURER.orEmpty(), Build.MODEL.orEmpty())

    /**
     * The active SIMs: carrier, 2G…5G (4G+ when Android reports more than one LTE carrier joined) and signal in dBm.
     * Empty without the phone permission or without SIM. Reads only.
     */
    @SuppressLint("MissingPermission") // only after checking the permission
    fun sims(): List<Sim> = if (!canPhone()) emptyList() else runCatching {
        val sm = app.getSystemService(SubscriptionManager::class.java) ?: return emptyList()
        sm.activeSubscriptionInfoList.orEmpty().sortedBy { it.simSlotIndex }.map { info ->
            val t = tm?.createForSubscriptionId(info.subscriptionId)
            val carrier = info.carrierName?.toString()?.takeIf { it.isNotBlank() } ?: info.displayName?.toString().orEmpty()
            Sim(info.simSlotIndex + 1, carrier, tech(t), signal(t))
        }
    }.getOrDefault(emptyList())

    @SuppressLint("MissingPermission")
    private fun tech(t: TelephonyManager?): CellTech = runCatching {
        val type = t?.dataNetworkType ?: 0
        CellTech.of(type, if (Build.VERSION.SDK_INT >= 28 && type == TelephonyManager.NETWORK_TYPE_LTE) t?.serviceState?.cellBandwidths?.size ?: 1 else 1)
    }.getOrDefault(CellTech.UNKNOWN)

    /** RSRP for 4G, SS‑RSRP for 5G, else what Android gives as dBm; null when unknown (or before Android 10). */
    private fun signal(t: TelephonyManager?): Int? = if (Build.VERSION.SDK_INT < 29) null else runCatching {
        t?.signalStrength?.cellSignalStrengths.orEmpty().map { it.dbm }.firstOrNull { it in -150..-20 }
    }.getOrNull()

    /** Asks Android to look for networks (it allows a few times every 2 minutes); the answer comes in [lastScan]. */
    @Suppress("DEPRECATION") // still the only way to ask for a fresh list
    fun scan(): Boolean = canScan() && runCatching { wm?.startScan() == true }.getOrDefault(false)

    @SuppressLint("MissingPermission")
    fun lastScan(): List<AccessPoint> = if (!canScan()) emptyList() else runCatching {
        wm?.scanResults.orEmpty().map { r: ScanResult ->
            @Suppress("DEPRECATION") val name = if (Build.VERSION.SDK_INT >= 33) r.wifiSsid?.toString()?.removeSurrounding("\"").orEmpty() else r.SSID.orEmpty()
            AccessPoint(name.takeIf { it != UNKNOWN }.orEmpty(), r.BSSID.orEmpty(), r.frequency, r.level, Security.of(r.capabilities.orEmpty()),
                Spectrum.widthMhz(r.channelWidth), r.centerFreq0)
        }.sortedByDescending { it.dbm }
    }.getOrDefault(emptyList())

    private companion object { const val UNKNOWN = "<unknown ssid>" } // WifiManager.UNKNOWN_SSID, only named from Android 11
}
