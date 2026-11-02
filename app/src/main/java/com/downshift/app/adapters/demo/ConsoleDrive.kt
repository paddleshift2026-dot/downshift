package com.downshift.app.adapters.demo

import android.util.Log
import com.downshift.app.core.audio.LatencyStats
import com.downshift.app.core.model.GearState
import com.downshift.app.core.powertrain.DemoVehicleModel
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.math.abs

/** One reading of the Virtual Console pedals, 0..1, and when it arrived (wall clock). */
data class ConsolePedal(val throttle: Double, val brake: Double, val atMs: Long)

/**
 * Emulator demo driving: reads the Pleos Virtual Console pedals (slider or G29) every 20 ms and, because the console
 * has no physics, runs DemoVehicleModel and writes the speed back so the cluster and the AAOS speed follow the pedal.
 * The connection is reused (the stream is closed, the connection is not disconnected). On a real car nothing answers
 * on 127.0.0.1:8088, so it only retries once a second and reports nothing.
 */
class ConsoleDrive(private val gear: () -> GearState, private val onPedal: (ConsolePedal) -> Unit) {
    private val tag = "DownshiftConsole"
    @Volatile private var running = false
    private var thread: Thread? = null
    private val car = DemoVehicleModel()
    val roundTrip = LatencyStats()

    @Volatile var demoPhysics = true
    @Volatile var connected = false
        private set
    @Volatile var demoSpeedMps = 0.0
        private set

    fun start() {
        if (running) return
        running = true
        thread = Thread({ loop() }, "DownshiftConsole").apply {
            isDaemon = true
            start()
        }
    }

    fun stop() {
        running = false
        thread?.join(300)
        thread = null
    }

    private fun loop() {
        val controls = URL("$BASE/vehicle/controls")
        val session = URL("$BASE/browser/session?client=downshift-app")
        var lastNs = System.nanoTime()
        var lastSessionMs = 0L
        var lastPostMs = 0L
        var postedKph = Double.NaN
        var lastAccel = Double.NaN
        while (running) {
            val t0 = System.nanoTime()
            val body = runCatching { get(controls) }.getOrNull()
            val now = System.currentTimeMillis()
            if (body == null) {
                if (connected) Log.w(tag, "console stopped answering")
                connected = false
                sleepMs(RETRY_MS)
                lastNs = System.nanoTime()
                continue
            }
            if (!connected) Log.i(tag, "console connected")
            connected = true
            roundTrip.add((System.nanoTime() - t0) / 1e6)
            val accel = number(ACCEL, body) ?: 0.0
            val brake = number(BRAKE, body) ?: 0.0
            if (accel != lastAccel) {
                Log.i(tag, "accel=${accel.toInt()} at=$now")
                lastAccel = accel
            }
            onPedal(ConsolePedal(accel / 100.0, brake / 100.0, now))

            if (now - lastSessionMs >= SESSION_MS) {
                runCatching { post(session) }
                lastSessionMs = now
            }
            val ns = System.nanoTime()
            val dt = ((ns - lastNs) / 1e9).coerceIn(0.0, 0.2)
            lastNs = ns
            if (demoPhysics) {
                val kph = car.step(accel / 100.0, brake / 100.0, gear(), dt) * 3.6
                demoSpeedMps = car.speedMps
                val changed = postedKph.isNaN() || abs(kph - postedKph) >= SPEED_STEP_KPH
                if ((changed && now - lastPostMs >= MIN_POST_MS) || now - lastPostMs >= MAX_POST_MS) {
                    val q = String.format(Locale.ROOT, "%.1f", kph)
                    if (runCatching { post(URL("$BASE/vehicle/controls?speed=$q")) }.isSuccess) postedKph = kph
                    lastPostMs = now
                }
            }
            sleepMs(POLL_MS - (System.nanoTime() - t0) / 1_000_000)
        }
    }

    private fun get(url: URL): String {
        val c = url.openConnection() as HttpURLConnection
        c.connectTimeout = TIMEOUT_MS
        c.readTimeout = TIMEOUT_MS
        return c.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
    }

    private fun post(url: URL) {
        val c = url.openConnection() as HttpURLConnection
        c.requestMethod = "POST"
        c.connectTimeout = TIMEOUT_MS
        c.readTimeout = TIMEOUT_MS
        c.doOutput = true
        c.setFixedLengthStreamingMode(0)
        c.outputStream.close()
        val ok = c.responseCode in 200..299
        (if (ok) c.inputStream else c.errorStream)?.use { it.readBytes() }
    }

    private fun number(re: Regex, body: String) = re.find(body)?.groupValues?.get(1)?.toDoubleOrNull()

    private fun sleepMs(ms: Long) {
        if (ms > 0) Thread.sleep(ms)
    }

    private companion object {
        const val BASE = "http://127.0.0.1:8088"
        const val POLL_MS = 20L
        const val RETRY_MS = 1000L
        const val SESSION_MS = 2000L
        const val TIMEOUT_MS = 300
        const val SPEED_STEP_KPH = 0.5
        const val MIN_POST_MS = 50L
        const val MAX_POST_MS = 250L
        val ACCEL = Regex("\"accelerator_pct\"\\s*:\\s*([0-9.]+)")
        val BRAKE = Regex("\"brake_pct\"\\s*:\\s*([0-9.]+)")
    }
}
