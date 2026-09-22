package com.example.netscan

import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Collections

class MainActivity : AppCompatActivity() {

    private lateinit var adapter: ResultAdapter
    private lateinit var tvStatus: TextView
    private lateinit var tvStats: TextView
    private lateinit var progress: ProgressBar
    private lateinit var btnScan: MaterialButton
    private lateinit var btnStop: MaterialButton
    private var job: Job? = null
    private var startMillis = 0L
    private var totalHosts = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val tietTarget = findViewById<TextInputEditText>(R.id.tietTarget)
        val tietPorts = findViewById<TextInputEditText>(R.id.tietPorts)
        val swPing = findViewById<SwitchMaterial>(R.id.swPing)
        btnScan = findViewById(R.id.btnScan)
        btnStop = findViewById(R.id.btnStop)
        tvStatus = findViewById(R.id.tvStatus)
        tvStats = findViewById(R.id.tvStats)
        progress = findViewById(R.id.progress)
        val rv = findViewById<RecyclerView>(R.id.rvResults)

        adapter = ResultAdapter()
        rv.layoutManager = LinearLayoutManager(this)
        rv.adapter = adapter

        // Prefill with this device's Wi-Fi subnet
        ScannerEngine.localNetwork(this)?.let { (ip, mask) ->
            tietTarget.setText(ip.substringBeforeLast(".") + ".0/" + mask)
        }

        btnScan.setOnClickListener {
            val target = tietTarget.text.toString().trim()
            val ports = ScannerEngine.parsePorts(tietPorts.text.toString())
            if (target.isEmpty() || ports.isEmpty()) {
                Toast.makeText(this, "Enter a valid target and ports", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            adapter.clear()
            job = lifecycleScope.launch { runScan(target, ports, swPing.isChecked) }
        }

        btnStop.setOnClickListener { job?.cancel() }
    }

    private suspend fun runScan(target: String, ports: List<Int>, pingSweep: Boolean) {
        setRunning(true)
        startMillis = System.currentTimeMillis()
        try {
            // Stage 1: resolve targets (+ optional ping sweep host discovery)
            val hosts = resolveTargets(target, pingSweep)
            totalHosts = hosts.size
            if (hosts.isEmpty()) {
                tvStatus.text = "No live hosts found."
                updateStats("DONE", done = 0)
                return
            }
            tvStatus.text = "Port-scanning ${hosts.size} host(s)…"
            updateStats("SCANNING")

            // Stage 2: TCP connect scan, hosts in parallel
            var done = 0
            coroutineScope {
                hosts.forEach { host ->
                    launch(Dispatchers.IO) {
                        ScannerEngine.scanPorts(host, ports) { port, banner ->
                            withContext(Dispatchers.Main) {
                                adapter.add(ScanResult(host, port, banner))
                                updateStats("SCANNING", done)
                            }
                        }
                        withContext(Dispatchers.Main) {
                            done++
                            updateStats("SCANNING", done)
                        }
                    }
                }
            }
            tvStatus.text = "Scan complete."
            updateStats("DONE", done)
        } catch (e: CancellationException) {
            tvStatus.text = "Stopped by user."
            updateStats("STOPPED")
            throw e
        } catch (e: Exception) {
            tvStatus.text = "Error: ${e.message}"
            updateStats("ERROR")
        } finally {
            setRunning(false)
        }
    }

    /** Returns list of hosts to port-scan. Subnet + ping sweep = discovery stage. */
    private suspend fun resolveTargets(target: String, pingSweep: Boolean): List<String> =
        coroutineScope {
            val parts = target.split("/")
            val base = parts[0].trim()
            if (parts.size == 2) {
                val all = ScannerEngine.subnetHosts(base, parts[1].trim())
                if (!pingSweep) return@coroutineScope all
                val live = Collections.synchronizedList(mutableListOf<String>())
                var probed = 0
                tvStatus.text = "Ping sweeping ${all.size} addresses…"
                updateStats("DISCOVERING")
                ScannerEngine.discoverHosts(all) { host ->
                    live.add(host)
                    withContext(Dispatchers.Main) {
                        probed++
                        if (probed % 10 == 0) {
                            tvStatus.text = "Ping sweep: ${live.size} live host(s) so far"
                            updateStats("DISCOVERING")
                        }
                    }
                }
                live.sorted()
            } else {
                listOf(base)
            }
        }

    private fun updateStats(state: String, done: Int = 0) {
        val elapsed = (System.currentTimeMillis() - startMillis) / 1000
        tvStats.text = "%s · hosts %d/%d live · ports %d open · %ds"
            .format(state, adapter.liveHostCount(), totalHosts, adapter.openPortCount(), elapsed)
    }

    private fun setRunning(running: Boolean) {
        btnScan.isEnabled = !running
        btnStop.isEnabled = running
        progress.visibility = if (running) View.VISIBLE else View.GONE
    }
}
