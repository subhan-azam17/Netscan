package com.example.netscan

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView

data class ScanResult(val host: String, val port: Int, val banner: String?)

sealed class Row {
    data class Host(val name: String, val openCount: Int) : Row()
    data class Port(val host: String, val port: Int, val banner: String?) : Row()
}

class ResultAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val groups = sortedMapOf<String, MutableList<ScanResult>>()
    private var rows = listOf<Row>()

    companion object {
        private const val TYPE_HOST = 0
        private const val TYPE_PORT = 1

        private val KNOWN_SERVICES = mapOf(
            21 to "FTP", 22 to "SSH", 23 to "Telnet", 25 to "SMTP",
            53 to "DNS", 80 to "HTTP", 110 to "POP3", 111 to "RPC",
            135 to "MSRPC", 139 to "NetBIOS", 143 to "IMAP", 443 to "HTTPS",
            445 to "SMB", 993 to "IMAPS", 995 to "POP3S", 1433 to "MSSQL",
            1521 to "Oracle", 1723 to "PPTP", 3306 to "MySQL", 3389 to "RDP",
            5432 to "PostgreSQL", 5900 to "VNC", 6379 to "Redis",
            8080 to "HTTP-Alt", 8443 to "HTTPS-Alt", 8888 to "HTTP-Alt",
            9100 to "Printer", 27017 to "MongoDB"
        )

        private val WEB = setOf(80, 443, 8000, 8080, 8443, 8888)
        private val REMOTE = setOf(21, 22, 23, 3389, 5900)
        private val MAIL = setOf(25, 110, 143, 465, 587, 993, 995)
        private val DB = setOf(1433, 1521, 3306, 5432, 6379, 27017, 9200)
    }

    fun serviceName(port: Int) = KNOWN_SERVICES[port] ?: ""

    class HostVH(v: View) : RecyclerView.ViewHolder(v) {
        val header: TextView = v.findViewById(R.id.tvHostHeader)
        val meta: TextView = v.findViewById(R.id.tvHostMeta)
    }

    class PortVH(v: View) : RecyclerView.ViewHolder(v) {
        val badge: TextView = v.findViewById(R.id.tvBadge)
        val service: TextView = v.findViewById(R.id.tvService)
        val banner: TextView = v.findViewById(R.id.tvBanner)
    }

    override fun getItemViewType(position: Int) =
        if (rows[position] is Row.Host) TYPE_HOST else TYPE_PORT

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_HOST) {
            HostVH(inflater.inflate(R.layout.item_host, parent, false))
        } else {
            PortVH(inflater.inflate(R.layout.item_port, parent, false))
        }
    }

    override fun getItemCount() = rows.size

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val ctx = holder.itemView.context
        when (val row = rows[position]) {
            is Row.Host -> {
                holder as HostVH
                holder.header.text = row.name
                holder.meta.text = "${row.openCount} open port(s)"
            }
            is Row.Port -> {
                holder as PortVH
                holder.badge.text = "tcp/${row.port}"
                val svc = serviceName(row.port)
                holder.service.text = svc.ifEmpty { "unknown" }
                holder.banner.text = row.banner ?: "open"
                val color = when (row.port) {
                    in WEB -> R.color.accent
                    in REMOTE -> R.color.blue
                    in MAIL -> R.color.amber
                    in DB -> R.color.purple
                    else -> R.color.muted
                }
                holder.badge.setTextColor(ContextCompat.getColor(ctx, color))
            }
        }
    }

    fun add(result: ScanResult) {
        groups.getOrPut(result.host) { mutableListOf() }.add(result)
        rebuild()
    }

    fun clear() {
        groups.clear()
        rebuild()
    }

    fun openPortCount() = groups.values.sumOf { it.size }
    fun liveHostCount() = groups.size

    private fun rebuild() {
        val newRows = mutableListOf<Row>()
        for ((host, results) in groups) {
            newRows.add(Row.Host(host, results.size))
            results.sortedBy { it.port }.forEach {
                newRows.add(Row.Port(it.host, it.port, it.banner))
            }
        }
        rows = newRows
        notifyDataSetChanged()
    }
}
