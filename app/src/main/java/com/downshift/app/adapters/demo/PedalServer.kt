package com.downshift.app.adapters.demo

import android.content.Context
import android.util.Log
import com.downshift.app.core.demo.PageMessage
import com.downshift.app.core.demo.PageState
import com.downshift.app.core.demo.PedalMix
import com.downshift.app.core.demo.PedalProtocol
import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoWSD
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.ByteArrayInputStream
import java.io.IOException
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/** What the tune tab shows about the web pedal. */
data class PedalStatus(val running: Boolean = false, val clients: Int = 0, val pct: Int = 0, val failed: Boolean = false)

/**
 * The web pedal (PRD feature G): a page on 127.0.0.1:8790 inside the emulator, reached from the laptop through
 * `adb forward tcp:8790 tcp:8790`, where a judge presses the pedal with the space bar, the slider, the mouse or a game
 * pedal, and the presenter runs the drive's scenes while the Pleos home covers the app. Pedal values reach onPedal only
 * when they change; with several pages open the deepest press drives, and a page that goes quiet or closes lets go of
 * its own press (PedalMix). Bound to the loopback only and run by DemoHost only in demo mode, so nothing on a car's
 * network reaches it.
 */
class PedalServer(
    private val context: Context,
    private val onPedal: (Int) -> Unit,
    private val onScene: (String) -> Unit,
    private val state: () -> PageState,
) : NanoWSD(HOST, PORT) {
    private val tag = "DownshiftPedal"
    private val mix = PedalMix<Client>()
    private val sockets = CopyOnWriteArraySet<Client>()
    private var timer: ScheduledExecutorService? = null
    private var ticks = 0 // timer thread only
    private val _status = MutableStateFlow(PedalStatus())
    val status: StateFlow<PedalStatus> = _status.asStateFlow()

    inner class Client(handshake: IHTTPSession) : WebSocket(handshake) {
        override fun onOpen() {
            sockets += this
            Log.i(tag, "web pedal page connected (${sockets.size})")
            publish()
        }

        override fun onClose(code: WebSocketFrame.CloseCode?, reason: String?, initiatedByRemote: Boolean) = left(this)

        override fun onMessage(message: WebSocketFrame) {
            val now = System.currentTimeMillis()
            when (val m = PedalProtocol.parse(message.textPayload ?: return)) {
                is PageMessage.Pedal -> mix.onPedal(this, m.pct, now)?.let { press(it, now) }
                is PageMessage.Ping -> {
                    mix.onMessage(this, now)
                    runCatching { send(PedalProtocol.pong(m.id)) }
                }
                is PageMessage.Scene -> {
                    mix.onMessage(this, now)
                    Log.i(tag, "web scene ${m.name}")
                    onScene(m.name)
                }
                null -> Unit
            }
        }

        override fun onPong(pong: WebSocketFrame?) {}

        override fun onException(exception: IOException?) {
            Log.w(tag, "socket: ${exception?.message}")
            left(this)
        }
    }

    override fun openWebSocket(handshake: IHTTPSession): WebSocket = Client(handshake)

    override fun serveHttp(session: IHTTPSession): Response = when (session.uri) {
        "/", "/index.html" -> page()
        else -> newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "not found")
    }

    /** Opens the page; false, with the status marked failed, when the port cannot be had. */
    fun startServer(): Boolean = runCatching {
        start(NanoHTTPD.SOCKET_READ_TIMEOUT, false)
        timer = Executors.newSingleThreadScheduledExecutor { r -> Thread(r, "DownshiftPedal").apply { isDaemon = true } }.also {
            it.scheduleWithFixedDelay(::tick, TICK_MS, TICK_MS, TimeUnit.MILLISECONDS)
        }
        _status.value = PedalStatus(running = true)
        Log.i(tag, "web pedal on http://$HOST:$PORT")
        true
    }.getOrElse {
        Log.w(tag, "web pedal could not start: ${it.message}")
        _status.value = PedalStatus(failed = true)
        false
    }

    fun stopServer() {
        timer?.shutdownNow()
        timer = null
        mix.clear()?.let { press(it, System.currentTimeMillis()) }
        sockets.clear()
        runCatching { stop() }
        _status.value = PedalStatus()
        Log.i(tag, "web pedal off")
    }

    /** Every TICK_MS: a quiet page lets go of the pedal; while a page is connected, the car's state and a keep-alive ping. */
    private fun tick() {
        val now = System.currentTimeMillis()
        mix.tick(now)?.let {
            Log.i(tag, "web pedal released (quiet)")
            press(it, now)
        }
        if (sockets.isEmpty()) return
        ticks++
        if (ticks % STATE_EVERY == 0) {
            val json = PedalProtocol.state(state())
            sockets.forEach { s -> runCatching { s.send(json) }.onFailure { left(s) } }
        }
        if (ticks % PING_EVERY == 0) sockets.forEach { s -> runCatching { s.ping(PING) }.onFailure { left(s) } }
    }

    private fun press(pct: Int, now: Long) {
        Log.i(tag, "web pedal pct=$pct at=$now")
        onPedal(pct)
        publish()
    }

    private fun left(c: Client) {
        if (!sockets.remove(c)) return
        Log.i(tag, "web pedal page left (${sockets.size})")
        mix.onClose(c)?.let {
            Log.i(tag, "web pedal released (closed)")
            press(it, System.currentTimeMillis())
        }
        publish()
    }

    private fun publish() {
        _status.value = PedalStatus(running = true, clients = sockets.size, pct = mix.pct)
    }

    private fun page(): Response {
        val bytes = context.assets.open("pedal/index.html").use { it.readBytes() }
        return newFixedLengthResponse(Response.Status.OK, "text/html; charset=utf-8", ByteArrayInputStream(bytes), bytes.size.toLong())
    }

    companion object {
        const val HOST = "127.0.0.1"
        const val PORT = 8790
        private const val TICK_MS = 100L
        private const val STATE_EVERY = 2 // 200 ms
        private const val PING_EVERY = 20 // 2 s: NanoWSD drops sockets idle for its read timeout
        private val PING = byteArrayOf()
    }
}
