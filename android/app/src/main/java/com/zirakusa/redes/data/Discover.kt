package com.zirakusa.redes.data

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/** What a device on your network is: the router («Portal»), this phone («Yo») or anything else («Genérico»). [key] is for MCP. */
enum class Role(val key: String) { GATEWAY("gateway"), ME("me"), OTHER("other") }

/** A device that answered on your own network. [name] is its local name, when the router's DNS knows it. */
data class Device(val ip: String, val role: Role, val name: String = "")

/** How the devices are listed: by address, or by role and then name. */
enum class DeviceSort { IP, NAME }

/** Asks the local network, one address at a time (the app: a short knock that never leaves the network; tests: a fake). */
interface Reach {
    suspend fun alive(ip: String): Boolean
    /** Its name from the local DNS (only asked when the DNS is on the local network), or "". */
    suspend fun name(ip: String): String
}

/** «Descubrir › Wi‑Fi»: pure labelling and sorting, and the look over the /24 of the phone's address. */
object Devices {
    /** «Samsung SM-A546E», «Pixel 8» (when the model already starts with the maker, it is not repeated). */
    fun model(maker: String, model: String): String {
        val m = maker.trim().replaceFirstChar { it.uppercase() }; val n = model.trim()
        return when { n.isEmpty() -> m.ifEmpty { "Android" }; m.isEmpty() || n.startsWith(m, true) || n.startsWith("Pixel") -> n; else -> "$m $n" }
    }

    fun role(ip: String, gateway: String, own: String) = when (ip) { gateway -> Role.GATEWAY; own -> Role.ME; else -> Role.OTHER }

    fun sort(list: List<Device>, by: DeviceSort): List<Device> = when (by) {
        DeviceSort.IP -> list.sortedBy { Lan.order(it.ip) }
        DeviceSort.NAME -> list.sortedWith(compareBy<Device> { it.role.ordinal }.thenBy { it.name.isBlank() }.thenBy { it.name.lowercase() }.thenBy { Lan.order(it.ip) })
    }

    /** Only on Wi‑Fi or cable with a private IPv4, and names only when every DNS server is local (no lookup goes out). */
    fun canLook(s: Status) = (s.link == Link.WIFI || s.link == Link.ETHERNET) && Lan.hosts(s.addresses.firstOrNull { Lan.parts(it) != null }.orEmpty()).isNotEmpty()
    fun canName(s: Status) = s.dns.isNotEmpty() && s.dns.all(Lan::isLocal) && s.privateDns.isBlank()

    /**
     * Knocks every address of the /24 ([parallel] at once) and returns those that answered, labelled, sorted by address.
     * The router and the phone are always listed. [found] is told how many addresses were asked so far.
     */
    suspend fun look(s: Status, reach: Reach, parallel: Int = 32, found: (Int) -> Unit = {}): List<Device> {
        if (!canLook(s)) return emptyList()
        val own = s.addresses.first { Lan.parts(it) != null }
        val names = canName(s)
        val gate = Semaphore(parallel)
        var asked = 0
        val all = coroutineScope {
            Lan.hosts(own).map { ip ->
                async {
                    gate.withPermit {
                        val role = role(ip, s.gateway, own)
                        val up = role != Role.OTHER || reach.alive(ip)
                        synchronized(this@Devices) { asked++; found(asked) }
                        if (!up) null else Device(ip, role, if (names && role == Role.OTHER) reach.name(ip).take(60) else "")
                    }
                }
            }.awaitAll().filterNotNull()
        }
        return sort(all, DeviceSort.IP)
    }
}

/** A Bluetooth LE device nearby, as the last advert said. Kept only in memory, only while «Descubrir» is open. */
data class BleDevice(val address: String, val name: String, val dbm: Int, val seen: Long)

/** Pure list of the Bluetooth devices: one row per address, newest reading wins, strongest first. */
object BleList {
    fun add(list: List<BleDevice>, d: BleDevice): List<BleDevice> =
        (list.filterNot { it.address == d.address } + d.copy(name = d.name.ifBlank { list.firstOrNull { it.address == d.address }?.name.orEmpty() }))
            .sortedWith(compareByDescending<BleDevice> { it.dbm }.thenBy { it.address })

    /** Those not heard for [ms] go away. */
    fun fresh(list: List<BleDevice>, now: Long, ms: Long = 30_000) = list.filter { now - it.seen <= ms }
}
