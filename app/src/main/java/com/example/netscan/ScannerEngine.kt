package com.example.netscan

import android.content.Context
import android.net.wifi.WifiManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Core scanning engine. Works WITHOUT root:
 * - Host discovery: ICMP ping via the system ping binary (like nmap -sn)
 * - Port scanning : TCP connect scan (like nmap -sT)
 * - Service probe : minimal banner grabbing, with a HEAD request for HTTP ports
 */
object ScannerEngine {

    // ---------- Local network info ----------

    fun localNetwork(context: Context): Pair<String, String>? {
        val wm = context.applicationContext
            .getSystemService(Context.WIFI_SERVICE) as? WifiManager ?: return null
        val dhcp = wm.dhcpInfo ?: return null
        if (dhcp.ipAddress == 0) return null
        return formatIp(dhcp.ipAddress) to formatIp(dhcp.netmask)
    }

    private fun formatIp(i: Int): String =
        "${i and 0xff}.${i shr 8 and 0xff}.${i shr 16 and 0xff}.${i shr 24 and 0xff}"

    // ---------- Address helpers ----------

    private fun ipToLong(ip: String): Long {
        val p = ip.trim().split(".").map { it.toLong() }
        require(p.size == 4) { "Invalid IP: $ip" }
        return (p[0] shl 24) or (p[1] shl 16) or (p[2] shl 8) or p[3]
    }

    private fun longToIp(l: Long): String =
        "${l shr 24 and 0xff}.${l shr 16 and 0xff}.${l shr 8 and 0xff}.${l and 0xff}"

    /** All usable host addresses of a subnet. */
    fun subnetHosts(ip: String, netmask: String): List<String> {
        val network = ipToLong(ip) and ipToLong(netmask)
        val broadcast = network or ipToLong(netmask).inv()
        if (broadcast - network <= 1) return listOf(ip)
        return ((network + 1) until broadcast).map { longToIp(it) }
    }

    // ---------- Host discovery (nmap -sn equivalent) ----------

    suspend fun ping(host: String, timeoutMs: Int = 1000): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val timeoutSec = (timeoutMs / 1000).coerceAtLeast(1)
                val p = Runtime.getRuntime()
                    .exec(arrayOf("ping", "-c", "1", "-W", timeoutSec.toString(), host))
                p.waitFor() == 0
            } catch (e: Exception) {
                false
            }
        }

    /** Ping-sweep a list of hosts, calling [onLive] for each reply. */
    suspend fun discoverHosts(
        hosts: List<String>,
        concurrency: Int = 50,
        onLive: (String) -> Unit
    ) = coroutineScope {
        val sem = Semaphore(concurrency)
        hosts.forEach { host ->
            launch(Dispatchers.IO) {
                sem.withPermit {
                    if (ping(host)) onLive(host)
                }
            }
        }
    }

    // ---------- Port parsing ----------

    fun parsePorts(spec: String): List<Int> {
        val result = linkedSetOf<Int>()
        spec.split(",").forEach { part ->
            val t = part.trim()
            if (t.isEmpty()) return@forEach
            if (t.contains("-")) {
                val segs = t.split("-")
                val a = segs[0].trim().toIntOrNull() ?: return@forEach
                val b = segs[1].trim().toIntOrNull() ?: return@forEach
                (minOf(a, b)..maxOf(a, b)).forEach { if (it in 1..65535) result.add(it) }
            } else {
                t.toIntOrNull()?.let { if (it in 1..65535) result.add(it) }
            }
        }
        return result.toList()
    }

    // ---------- TCP connect scan (nmap -sT equivalent) ----------

    suspend fun scanPorts(
        host: String,
        ports: List<Int>,
        timeoutMs: Int = 1000,
        concurrency: Int = 64,
        onOpen: (port: Int, banner: String?) -> Unit
    ) = coroutineScope {
        val sem = Semaphore(concurrency)
        ports.forEach { port ->
            launch(Dispatchers.IO) {
                sem.withPermit {
                    val banner = probePort(host, port, timeoutMs)
                    if (banner != null) onOpen(port, banner)
                }
            }
        }
    }

    /** Try a TCP connect; return a banner/service string if open, null if closed/filtered. */
    private fun probePort(host: String, port: Int, timeoutMs: Int): String? {
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), timeoutMs)
                socket.soTimeout = timeoutMs
                if (port == 80 || port == 8080 || port == 8000 || port == 8888) {
                    // HTTP service probe
                    try {
                        socket.getOutputStream().write(
                            "HEAD / HTTP/1.0\r\nHost: $host\r\n\r\n".toByteArray()
                        )
                    } catch (_: Exception) { }
                }
                readBanner(socket)
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun readBanner(socket: Socket): String {
        return try {
            val buf = ByteArray(256)
            val n = socket.getInputStream().read(buf)
            if (n > 0) {
                String(buf, 0, n)
                    .replace(Regex("[\\x00-\\x1F\\x7F]"), " ")
                    .trim()
                    .take(90)
            } else "open"
        } catch (e: Exception) {
            "open"  // connected but silent within timeout
        }
    }
}
