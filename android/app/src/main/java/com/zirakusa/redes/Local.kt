package com.zirakusa.redes

import com.zirakusa.redes.data.Lan
import com.zirakusa.redes.data.Pinger
import com.zirakusa.redes.data.Reach
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.ConnectException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket

/**
 * The phone's own local network, never the internet: a short TCP knock to an address of the /24 (an answer or a «no»
 * from the device both mean it is there and how long it took). Addresses are built from their four numbers, so no name
 * is ever looked up; only private ones are knocked ([Lan.isLocal]).
 */
object Local : Pinger, Reach {
    private fun address(ip: String): InetAddress? =
        Lan.parts(ip)?.takeIf { Lan.isLocal(ip) }?.let { p -> InetAddress.getByAddress(ByteArray(4) { p[it].toByte() }) }

    /** ms until the device answered (open or refused), or null (no answer within [timeout]). */
    private fun knock(a: InetAddress, port: Int, timeout: Int): Double? {
        val start = System.nanoTime()
        return try {
            Socket().use { it.connect(InetSocketAddress(a, port), timeout) }
            (System.nanoTime() - start) / 1_000_000.0
        } catch (e: ConnectException) {
            if (e.message.orEmpty().contains("refused", true)) (System.nanoTime() - start) / 1_000_000.0 else null
        } catch (_: Exception) { null }
    }

    /** The router: its DNS port (routers answer there), then its web page. */
    override suspend fun gateway(address: String): Double? = withContext(Dispatchers.IO) {
        val a = address(address) ?: return@withContext null
        knock(a, 53, 1_000) ?: knock(a, 80, 500)
    }

    /** «Internet» is only Cloudflare's speed server. */
    override suspend fun internet(): Double? = Cloudflare.latency()

    override suspend fun alive(ip: String): Boolean = withContext(Dispatchers.IO) {
        val a = address(ip) ?: return@withContext false
        runCatching { a.isReachable(300) }.getOrDefault(false) || PORTS.any { knock(a, it, 300) != null }
    }

    /** The name the local DNS gives (only called when every DNS server is on the local network). */
    override suspend fun name(ip: String): String = withContext(Dispatchers.IO) {
        val a = address(ip) ?: return@withContext ""
        runCatching { a.canonicalHostName.trimEnd('.') }.getOrDefault("").takeIf { it != ip && Lan.parts(it) == null }.orEmpty()
    }

    private val PORTS = listOf(80, 443)
}
