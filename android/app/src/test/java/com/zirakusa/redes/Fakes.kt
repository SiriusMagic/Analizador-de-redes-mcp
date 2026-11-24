package com.zirakusa.redes

import com.zirakusa.redes.data.AccessPoint
import com.zirakusa.redes.data.CellTech
import com.zirakusa.redes.data.Link
import com.zirakusa.redes.data.Origin
import com.zirakusa.redes.data.Security
import com.zirakusa.redes.data.Sim
import com.zirakusa.redes.data.Status
import com.zirakusa.redes.data.Transport
import com.zirakusa.redes.data.WifiNow
import kotlinx.coroutines.delay

/** A fake speed server: [mbps] down and [upMbps] up, [pingMs] each ping, pieces of 64 KiB. */
class FakeServer(private val mbps: Double = 80.0, private val upMbps: Double = 20.0, private val pingMs: Long = 12) : Transport {
    var downloaded = 0L
    override suspend fun ping(): Double { delay(pingMs); return pingMs.toDouble() }
    override suspend fun download(bytes: Long, got: (Int) -> Unit): Origin { pour(bytes, mbps) { downloaded += it; got(it) }; return Origin("UIO", "190.152.10.20", "AS27947") }
    override suspend fun upload(bytes: Long, sent: (Int) -> Unit) = pour(bytes, upMbps, sent)
    private suspend fun pour(bytes: Long, rate: Double, each: (Int) -> Unit) {
        val piece = 65_536
        val ms = (piece * 8 / (rate * 1000)).toLong().coerceAtLeast(1)
        var left = bytes
        while (left > 0) { delay(ms); val n = minOf(left, piece.toLong()).toInt(); each(n); left -= n }
    }
}

object Nets {
    val home = WifiNow("Casa", "aa:bb:cc:00:00:01", -52, 2437, 144, 6, Security.WPA2, widthMhz = 20)
    val wifi = Status(Link.WIFI, true, false, home, addresses = listOf("192.168.1.23", "2800:bf0::23"), gateway = "192.168.1.1", dns = listOf("192.168.1.1"))
    val mobile = Status(Link.CELLULAR, true, true, carrier = "Claro", cellType = "4G", addresses = listOf("10.20.30.40"))
    val around = listOf(
        AccessPoint("Casa", "aa:bb:cc:00:00:01", 2437, -52, Security.WPA2),
        AccessPoint("Vecino", "11:00:00:00:00:01", 2437, -48, Security.WPA2),
        AccessPoint("Tienda", "11:00:00:00:00:02", 2432, -60, Security.OPEN),
        AccessPoint("Oficina", "11:00:00:00:00:03", 2462, -85, Security.WPA3),
        AccessPoint("", "11:00:00:00:00:04", 5180, -70, Security.WPA2),
    )

    /** A busy building in Quito: you on 5 GHz channel 157 at 80 MHz, neighbours on 2.4 and 5 GHz. */
    val office = WifiNow("DeUnaNET_5G", "aa:bb:cc:00:00:05", -69, 5785, 433, 5, Security.WPA2, 80, 5775, 866, 866)
    val officeStatus = Status(Link.WIFI, true, false, office, addresses = listOf("192.168.0.14"), gateway = "192.168.0.1")
    val crowd = listOf(
        AccessPoint("DeUnaNET_5G", "aa:bb:cc:00:00:05", 5785, -69, Security.WPA2, 80, 5775),
        AccessPoint("KAPAK_RAYMI", "22:00:00:00:00:01", 5180, -48, Security.WPA2, 80, 5210),
        AccessPoint("Club-D_UnaNet", "22:00:00:00:00:02", 5785, -54, Security.WPA2, 80, 5775),
        AccessPoint("Ropita", "22:00:00:00:00:03", 5220, -85, Security.WPA2, 40, 5230),
        AccessPoint("IVAN_5G", "22:00:00:00:00:04", 5765, -86, Security.WPA3, 20),
        AccessPoint("MANUEL_5GHz", "22:00:00:00:00:05", 5260, -90, Security.WPA2, 80, 5290),
        AccessPoint("CHALAN_5G", "22:00:00:00:00:06", 5180, -92, Security.WPA2, 20),
        AccessPoint("Familia Pérez", "33:00:00:00:00:01", 2437, -58, Security.WPA2, 20),
        AccessPoint("TP-Link_2.4", "33:00:00:00:00:02", 2412, -66, Security.WPA2, 40, 2422),
        AccessPoint("", "33:00:00:00:00:03", 2462, -74, Security.WPA2, 20),
        AccessPoint("Tienda", "33:00:00:00:00:04", 2452, -81, Security.OPEN, 20),
        AccessPoint("CNT_HOGAR", "33:00:00:00:00:05", 2472, -88, Security.WPA2, 20),
    )
    val sims = listOf(Sim(1, "Claro EC", CellTech.G4, -106), Sim(2, "Movistar", CellTech.G4_PLUS, -88))
}
