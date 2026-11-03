package com.downshift.app.adapters.pleos

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.SeatArea
import ai.pleos.playground.vehicle.constant.area.SeatValue
import ai.pleos.playground.vehicle.constant.area.TurnSignalValue
import ai.pleos.playground.vehicle.listener.DistanceDrivenListener
import ai.pleos.playground.vehicle.listener.SeatOccupancyListener
import ai.pleos.playground.vehicle.listener.TurnSignalStateListener
import android.content.Context
import android.util.Log
import com.downshift.app.core.trip.Signal
import java.util.concurrent.ConcurrentHashMap

/**
 * The Pleos Vehicle SDK signals a trip needs: the turn-signal stalk, the odometer (km, handed to onOdometer) and
 * whether anyone sits beside or behind the driver. Start it only while it is needed: once initialised, the SDK kept
 * about 7% of a core busy on the emulator's main thread (M1). A seat callback only says "changed" and the seat is read
 * again. Against the Virtual Console every seat reads the opposite of the console's own state (all seats empty there
 * read occupied, M3; M0 took this for a lagging callback), so invertSeats() flips them while the console is in use.
 * Failures are logged and leave the last values.
 */
class PleosVehicle(context: Context, private val invertSeats: () -> Boolean, private val onOdometer: (Double) -> Unit) {
    private val tag = "DownshiftPleos"
    private val appContext = context.applicationContext
    @Volatile private var vehicle: Vehicle? = null
    private val seats = ConcurrentHashMap<String, Boolean>()
    private val seatListeners = mutableListOf<Pair<SeatArea, SeatOccupancyListener>>()

    @Volatile var signal = Signal.OFF
        private set
    @Volatile var passenger = false
        private set
    val running: Boolean get() = vehicle != null

    private val turnListener = object : TurnSignalStateListener {
        override fun onTurnSignalStateUpdated(value: TurnSignalValue?) = setSignal(value)
        override fun onFailed(e: Exception) = warn("turn signal", e)
    }
    private val odometerListener = object : DistanceDrivenListener {
        override fun onDistanceDrivenUpdated(distance: Float?) = odometer(distance)
        override fun onFailed(e: Exception) = warn("odometer", e)
    }

    fun start() {
        if (vehicle != null) return
        val v = Vehicle(appContext)
        try {
            v.initialize()
        } catch (e: Exception) {
            warn("initialize", e)
            return
        }
        vehicle = v
        guard("turn signal") {
            v.getTurnSignal().registerTurnSignalState(turnListener)
            v.getTurnSignal().getCurrentTurnSignalState({ setSignal(it) }, { e -> warn("turn signal now", e) })
        }
        guard("odometer") {
            v.getOdometer().registerDistanceDriven(odometerListener)
            v.getOdometer().getCurrentDistanceDriven({ odometer(it) }, { e -> warn("odometer now", e) })
        }
        guard("seats") {
            v.getSeat().checkSeatOccupancyCapabilities(
                { areas: List<SeatArea> -> areas.filterNot(::isDriver).forEach { watchSeat(v, it) } },
                { e -> warn("seat areas", e) },
            )
        }
        Log.i(tag, "vehicle signals on")
    }

    /** Asks for the odometer now (a trip's starting reading); the answer goes to onOdometer like any other. */
    fun readOdometer() {
        val v = vehicle ?: return
        guard("odometer now") { v.getOdometer().getCurrentDistanceDriven({ odometer(it) }, { e -> warn("odometer now", e) }) }
    }

    fun stop() {
        val v = vehicle ?: return
        vehicle = null
        guard("unregister") {
            v.getTurnSignal().unregisterTurnSignalState(turnListener)
            v.getOdometer().unregisterDistanceDriven(odometerListener)
            synchronized(seatListeners) {
                seatListeners.forEach { (area, l) -> v.getSeat().unregisterSeatOccupancy(area, l) }
                seatListeners.clear()
            }
            v.release()
        }
        signal = Signal.OFF
        Log.i(tag, "vehicle signals off")
    }

    private fun watchSeat(v: Vehicle, area: SeatArea) {
        val l = object : SeatOccupancyListener {
            override fun onSeatOccupancyUpdated(occupied: Boolean?) = readSeat(v, area) // the value lags: read it again
            override fun onFailed(e: Exception) = warn("seat ${name(area)}", e)
        }
        guard("seat ${name(area)}") {
            v.getSeat().registerSeatOccupancy(area, l)
            synchronized(seatListeners) { seatListeners += area to l }
        }
        readSeat(v, area)
    }

    private fun readSeat(v: Vehicle, area: SeatArea) = guard("seat read ${name(area)}") {
        v.getSeat().getCurrentSeatOccupancy(area, { occupied ->
            val now = (occupied == true) != invertSeats()
            if (seats.put(name(area), now) != now) Log.i(tag, "seat ${name(area)} $now")
            val anyone = seats.values.any { it }
            if (anyone != passenger) {
                passenger = anyone
                Log.i(tag, "passenger $anyone")
            }
        }, { e -> warn("seat read ${name(area)}", e) })
    }

    private fun setSignal(value: TurnSignalValue?) {
        val s = when (value) {
            TurnSignalValue.LEFT -> Signal.LEFT
            TurnSignalValue.RIGHT -> Signal.RIGHT
            else -> Signal.OFF
        }
        if (s != signal) Log.i(tag, "signal $s")
        signal = s
    }

    private fun odometer(km: Float?) {
        if (km == null) return
        Log.i(tag, "odometer $km")
        onOdometer(km.toDouble())
    }

    private fun isDriver(area: SeatArea) = area.values.toList() == listOf(SeatValue.ROW_1_LEFT)

    private fun name(area: SeatArea) = area.values.joinToString("+")

    private inline fun guard(what: String, block: () -> Unit) {
        try {
            block()
        } catch (e: Exception) {
            warn(what, e)
        }
    }

    private fun warn(what: String, e: Exception) {
        Log.w(tag, "$what: ${e.message}")
    }
}
