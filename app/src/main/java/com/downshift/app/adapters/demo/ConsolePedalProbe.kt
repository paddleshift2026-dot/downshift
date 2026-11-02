package com.downshift.app.adapters.demo

import android.util.Log
import com.downshift.app.core.audio.LatencyStats
import com.downshift.app.core.probe.ProbeLedger
import java.net.HttpURLConnection
import java.net.URL

/**
 * Emulator-only pedal source: polls the Pleos Virtual Console, which is served inside the emulator on 127.0.0.1:8088.
 * The SDK does not expose the accelerator, so demos read the console's controls (slider or a G29 pedal) instead.
 */
class ConsolePedalProbe(private val ledger: ProbeLedger, private val onPedal: (Double) -> Unit) {
    private val tag = "DownshiftConsole"
    @Volatile private var running = false
    private var thread: Thread? = null
    val roundTrip = LatencyStats()

    fun start() {
        ledger.expect("console.accelerator", "가속 페달 (Virtual Console)", "127.0.0.1:8088/vehicle/controls")
        running = true
        thread = Thread({
            var last = Double.NaN
            while (running) {
                val t0 = System.nanoTime()
                val v = runCatching { read() }.getOrNull()
                if (v != null) {
                    roundTrip.add((System.nanoTime() - t0) / 1e6)
                    if (v != last) {
                        ledger.record("console.accelerator", "%.0f%%".format(v), System.currentTimeMillis())
                        Log.i(tag, "accel=${v.toInt()} at=${System.currentTimeMillis()}")
                        onPedal(v / 100.0)
                        last = v
                    }
                }
                Thread.sleep(POLL_MS)
            }
        }, "DownshiftConsole").apply {
            isDaemon = true
            start()
        }
    }

    private fun read(): Double? {
        val c = URL(CONTROLS_URL).openConnection() as HttpURLConnection
        c.connectTimeout = 300
        c.readTimeout = 300
        return try {
            val body = c.inputStream.bufferedReader().use { it.readText() }
            ACCEL.find(body)?.groupValues?.get(1)?.toDouble()
        } finally {
            c.disconnect()
        }
    }

    fun stop() {
        running = false
        thread?.join(300)
        thread = null
    }

    private companion object {
        const val CONTROLS_URL = "http://127.0.0.1:8088/vehicle/controls"
        const val POLL_MS = 50L
        val ACCEL = Regex("\"accelerator_pct\"\\s*:\\s*([0-9.]+)")
    }
}
