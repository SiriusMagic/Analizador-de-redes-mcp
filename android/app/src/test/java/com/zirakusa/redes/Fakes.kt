package com.zirakusa.redes

import com.zirakusa.redes.data.AccessPoint
import com.zirakusa.redes.data.Link
import com.zirakusa.redes.data.Security
import com.zirakusa.redes.data.Status
import com.zirakusa.redes.data.Transport
import com.zirakusa.redes.data.WifiNow
import kotlinx.coroutines.delay

/** A fake speed server: [mbps] down and [upMbps] up, [pingMs] each ping, pieces of 64 KiB. */
class FakeServer(private val mbps: Double = 80.0, private val upMbps: Double = 20.0, private val pingMs: Long = 12) : Transport {
    var downloaded = 0L
    override suspend fun ping(): Double { delay(pingMs); return pingMs.toDouble() }
    override suspend fun download(bytes: Long, got: (Int) -> Unit): String { pour(bytes, mbps) { downloaded += it; got(it) }; return "UIO" }
    override suspend fun upload(bytes: Long, sent: (Int) -> Unit) = pour(bytes, upMbps, sent)
    private suspend fun pour(bytes: Long, rate: Double, each: (Int) -> Unit) {
        val piece = 65_536
        val ms = (piece * 8 / (rate * 1000)).toLong().coerceAtLeast(1)
        var left = bytes
        while (left > 0) { delay(ms); val n = minOf(left, piece.toLong()).toInt(); each(n); left -= n }
    }
}

object Nets {
    val home = WifiNow("Casa", "aa:bb:cc:00:00:01", -52, 2437, 144, 6, Security.WPA2)
    val wifi = Status(Link.WIFI, true, false, home, addresses = listOf("192.168.1.23", "2800:bf0::23"), gateway = "192.168.1.1", dns = listOf("192.168.1.1"))
    val mobile = Status(Link.CELLULAR, true, true, carrier = "Claro", cellType = "4G", addresses = listOf("10.20.30.40"))
    val around = listOf(
        AccessPoint("Casa", "aa:bb:cc:00:00:01", 2437, -52, Security.WPA2),
        AccessPoint("Vecino", "11:00:00:00:00:01", 2437, -48, Security.WPA2),
        AccessPoint("Tienda", "11:00:00:00:00:02", 2432, -60, Security.OPEN),
        AccessPoint("Oficina", "11:00:00:00:00:03", 2462, -85, Security.WPA3),
        AccessPoint("", "11:00:00:00:00:04", 5180, -70, Security.WPA2),
    )
}
