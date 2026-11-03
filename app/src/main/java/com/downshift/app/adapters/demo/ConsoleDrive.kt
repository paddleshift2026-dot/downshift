package com.downshift.app.adapters.demo

import android.util.Log
import com.downshift.app.core.model.GearState
import com.downshift.app.core.powertrain.DemoVehicleModel
import java.net.InetSocketAddress
import java.net.Socket
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.floor
import kotlin.math.roundToInt

/** One reading of the Virtual Console pedals, 0..1, and when it arrived (wall clock). */
data class ConsolePedal(val throttle: Double, val brake: Double, val atMs: Long)

/** The demo buttons' moves, sent to the console as a driver would make them there. */
sealed class ConsoleCommand {
    data class Gear(val gear: GearState) : ConsoleCommand()
    /** The pedal in percent; a newer value replaces one not yet sent (the web pedal sends up to 50 a second). */
    data class Accelerator(val pct: Int) : ConsoleCommand()
    /** A seat taken or left, e.g. area "passenger". */
    data class Seat(val area: String, val occupied: Boolean) : ConsoleCommand()
    /** Stop where the car is and put it in park. */
    object Arrive : ConsoleCommand()
}

/**
 * Emulator demo driving: reads the Pleos Virtual Console pedals (slider or G29) and, because the console has no
 * physics, runs DemoVehicleModel and writes the speed back so the cluster and the AAOS speed follow the pedal. It also
 * moves the console's odometer on every 0.1 km the demo car covers, so the cluster and the Pleos odometer listener
 * see the distance as on a real car, and sends the demo buttons' commands (post). While fast() (the engine is running)
 * it polls every 30 ms, or 40 ms once the pedal has held still for 2 s; otherwise every 200 ms (enough for the speed
 * and the garage's parked check; 100 ms put the idle garage over its 5% CPU budget, M2). On a real car nothing answers
 * on 127.0.0.1:8088, so it only retries once a second, reports nothing and drops commands.
 */
class ConsoleDrive(private val gear: () -> GearState, private val fast: () -> Boolean, private val onPedal: (ConsolePedal) -> Unit) {
    private val tag = "DownshiftConsole"
    @Volatile private var running = false
    private var thread: Thread? = null
    private val car = DemoVehicleModel()
    private val buf = ByteArray(64 * 1024) // polling thread only
    private val commands = ConcurrentLinkedQueue<ConsoleCommand>()
    private val pendingAccel = AtomicReference<Int?>(null) // the newest pedal not yet written
    // Odometer writing, polling thread only: the console's reading when it connected, and what the demo car added.
    private var odometerBase: Double? = null
    private var demoKm = 0.0
    private var odometerPosted = 0.0

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

    /**
     * Sent on the polling thread's next turn (any thread may call). Pedal values are folded into one, the newest, so a
     * pedal moving fast costs at most one write per poll.
     */
    fun post(cmd: ConsoleCommand) {
        if (cmd is ConsoleCommand.Accelerator) pendingAccel.set(cmd.pct) else commands += cmd
    }

    private fun loop() {
        var lastNs = System.nanoTime()
        var lastSessionMs = 0L
        var lastPostMs = 0L
        var postedKph = -1
        var warned = false
        var lastAccel = Double.NaN
        var lastBrake = Double.NaN
        var lastChangeMs = 0L
        while (running) {
            val t0 = System.nanoTime()
            if (connected) sendCommands()
            val body = request("GET", "/vehicle/controls")
            val now = System.currentTimeMillis()
            if (body == null) {
                if (connected) Log.w(tag, "console stopped answering")
                connected = false
                commands.clear()
                pendingAccel.set(null)
                sleepMs(RETRY_MS)
                lastNs = System.nanoTime()
                continue
            }
            if (!connected) {
                Log.i(tag, "console connected")
                odometerBase = request("GET", "/vehicle/state")?.let { number(ODOMETER, it) }
                demoKm = 0.0
                odometerPosted = odometerBase?.let { floorTenth(it) } ?: 0.0
            }
            connected = true
            val accel = number(ACCEL, body) ?: 0.0
            val brake = number(BRAKE, body) ?: 0.0
            if (accel != lastAccel || brake != lastBrake) {
                if (accel != lastAccel) Log.i(tag, "accel=${accel.toInt()} at=$now")
                lastAccel = accel
                lastBrake = brake
                lastChangeMs = now
            }
            onPedal(ConsolePedal(accel / 100.0, brake / 100.0, now))

            if (now - lastSessionMs >= SESSION_MS) {
                request("POST", "/browser/session?client=downshift-app")
                lastSessionMs = now
            }
            val ns = System.nanoTime()
            val dt = ((ns - lastNs) / 1e9).coerceIn(0.0, 0.2)
            lastNs = ns
            if (demoPhysics) {
                // The console takes whole km/h only (30.5 -> 400 invalid_speed), and answers a write with its full state.
                val kph = (car.step(accel / 100.0, brake / 100.0, gear(), dt) * 3.6).roundToInt()
                demoSpeedMps = car.speedMps
                if ((kph != postedKph && now - lastPostMs >= MIN_POST_MS) || now - lastPostMs >= MAX_POST_MS) {
                    if (request("POST", "/vehicle/controls?speed=$kph") != null) {
                        postedKph = kph
                    } else if (!warned) {
                        Log.w(tag, "console refused speed=$kph")
                        warned = true
                    }
                    lastPostMs = now
                }
                moveOdometer(car.speedMps * dt / 1000.0)
            }
            val period = when {
                !fast() -> SLOW_POLL_MS
                now - lastChangeMs < STEADY_MS -> FAST_POLL_MS
                else -> STEADY_POLL_MS
            }
            sleepMs(period - (System.nanoTime() - t0) / 1_000_000)
        }
    }

    /**
     * One HTTP/1.0 exchange on a fresh socket; the body on a 2xx answer, else null. The console closes every
     * connection (Connection: close), and a bare socket cost far less CPU than HttpURLConnection (M1 measurement).
     */
    private fun request(method: String, path: String): String? = runCatching {
        Socket().use { s ->
            s.tcpNoDelay = true
            s.soTimeout = TIMEOUT_MS
            s.connect(ADDRESS, TIMEOUT_MS)
            val out = s.getOutputStream()
            out.write("$method $path HTTP/1.0\r\nHost: 127.0.0.1\r\nContent-Length: 0\r\n\r\n".toByteArray(Charsets.US_ASCII))
            out.flush()
            val input = s.getInputStream()
            var n = 0
            while (n < buf.size) {
                val r = input.read(buf, n, buf.size - n)
                if (r < 0) break
                n += r
            }
            val text = String(buf, 0, n, Charsets.ISO_8859_1)
            val code = text.substringAfter(' ', "").take(3).toIntOrNull()
            if (code != null && code in 200..299) text.substringAfter("\r\n\r\n", "") else null
        }
    }.getOrNull()

    private fun sendCommands() {
        while (true) {
            val c = commands.poll() ?: break
            val paths = when (c) {
                is ConsoleCommand.Gear -> listOfNotNull(gearName(c.gear)?.let { "/vehicle/gear?gear=$it" })
                is ConsoleCommand.Accelerator -> listOf(accelPath(c.pct))
                is ConsoleCommand.Seat -> listOf("/vehicle/seat?kind=occupied&area=${c.area}&value=${c.occupied}")
                ConsoleCommand.Arrive -> {
                    car.halt()
                    demoSpeedMps = 0.0
                    listOf("/vehicle/controls?accelerator=0", "/vehicle/controls?brake=0", "/vehicle/controls?speed=0", "/vehicle/gear?gear=Park")
                }
            }
            paths.forEach { p -> if (request("POST", p) != null) Log.i(tag, "console $p") else Log.w(tag, "console refused $p") }
        }
        // The newest pedal, quietly: the next GET logs the value the console took.
        val pct = pendingAccel.getAndSet(null) ?: return
        if (request("POST", accelPath(pct)) == null) Log.w(tag, "console refused accelerator=$pct")
    }

    private fun accelPath(pct: Int) = "/vehicle/controls?accelerator=${pct.coerceIn(0, 100)}"

    /** Adds the demo car's distance and moves the console odometer each time it passes another 0.1 km. */
    private fun moveOdometer(km: Double) {
        val base = odometerBase ?: return
        demoKm += km
        val reading = floorTenth(base + demoKm)
        if (reading <= odometerPosted + 1e-6) return
        val text = String.format(Locale.ROOT, "%.1f", reading)
        if (request("POST", "/vehicle/odometer?traveled_distance_km=$text") != null) odometerPosted = reading
    }

    private fun floorTenth(km: Double) = floor(km * 10 + 1e-6) / 10

    private fun gearName(g: GearState) = when (g) {
        GearState.PARK -> "Park"
        GearState.REVERSE -> "Reverse"
        GearState.NEUTRAL -> "Neutral"
        GearState.DRIVE -> "Drive"
        GearState.UNKNOWN -> null
    }

    private fun number(re: Regex, body: String) = re.find(body)?.groupValues?.get(1)?.toDoubleOrNull()

    private fun sleepMs(ms: Long) {
        if (ms > 0) Thread.sleep(ms)
    }

    private companion object {
        val ADDRESS = InetSocketAddress("127.0.0.1", 8088)
        // Each poll is a new TCP connection (the console sends Connection: close): 20 ms cost about 11% of a core on
        // the emulator, 30 ms about 7%, for at most 10 ms more pedal latency.
        const val FAST_POLL_MS = 30L
        const val STEADY_POLL_MS = 40L
        const val STEADY_MS = 2000L
        const val SLOW_POLL_MS = 200L
        const val RETRY_MS = 1000L
        const val SESSION_MS = 2000L
        const val TIMEOUT_MS = 300
        const val MIN_POST_MS = 200L
        const val MAX_POST_MS = 250L
        val ACCEL = Regex("\"accelerator_pct\"\\s*:\\s*([0-9.]+)")
        val BRAKE = Regex("\"brake_pct\"\\s*:\\s*([0-9.]+)")
        val ODOMETER = Regex("\"traveled_distance_km\"\\s*:\\s*([0-9.]+)")
    }
}
