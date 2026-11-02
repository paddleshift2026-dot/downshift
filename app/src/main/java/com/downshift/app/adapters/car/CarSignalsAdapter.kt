package com.downshift.app.adapters.car

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.listener.BrakePedalStatusListener
import android.car.Car
import android.car.VehiclePropertyIds
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import com.downshift.app.core.model.FuelKind
import com.downshift.app.core.model.GearState
import com.downshift.app.core.model.VehicleSignals

/**
 * The car signals the engine needs: AAOS speed, gear selector and fuel type, plus the Pleos brake pedal.
 * Callbacks run on a HandlerThread so the UI thread stays free. Every speed sample also goes to onSpeedSample
 * (m/s, event time in elapsed-realtime nanoseconds) for the load estimate.
 */
class CarSignalsAdapter(context: Context, private val onSpeedSample: (Double, Long) -> Unit = { _, _ -> }) {
    private val tag = "DownshiftCar"
    private val appContext = context.applicationContext
    private val thread = HandlerThread("DownshiftCar")
    private var car: Car? = null
    private var properties: CarPropertyManager? = null
    private val callbacks = mutableListOf<CarPropertyManager.CarPropertyEventCallback>()
    private val vehicle = Vehicle(appContext)
    private var vehicleUp = false

    @Volatile var speedMps = 0.0
        private set
    @Volatile var gear = GearState.UNKNOWN
        private set
    @Volatile var brakePressed = false
        private set
    @Volatile var fuel = FuelKind.UNKNOWN
        private set

    val signals: VehicleSignals get() = VehicleSignals(speedMps, gear, brakePressed, fuel)

    private val brakeListener = object : BrakePedalStatusListener {
        override fun onBrakePedalStatusUpdated(pressed: Boolean?) {
            brakePressed = pressed == true
        }

        override fun onFailed(e: Exception) = warn("brake", e)
    }

    fun start() {
        thread.start()
        val c = runCatching { Car.createCar(appContext, Handler(thread.looper)) }.getOrNull()
        if (c == null) {
            Log.w(tag, "Car service unavailable")
        } else {
            car = c
            val pm = c.getCarManager(Car.PROPERTY_SERVICE) as? CarPropertyManager
            properties = pm
            if (pm != null) {
                guard("fuel") { fuel = FuelKind.from(intsOf(pm.getProperty<Any>(VehiclePropertyIds.INFO_FUEL_TYPE, 0)?.value)) }
                val speedRate = guardOr("speed config", 10f) { pm.getCarPropertyConfig(VehiclePropertyIds.PERF_VEHICLE_SPEED)?.maxSampleRate ?: 10f }
                subscribe(pm, "speed", VehiclePropertyIds.PERF_VEHICLE_SPEED, speedRate) { v ->
                    val mps = (v.value as? Number)?.toDouble() ?: return@subscribe
                    speedMps = mps
                    onSpeedSample(mps, v.timestamp)
                }
                subscribe(pm, "gear", VehiclePropertyIds.GEAR_SELECTION, 0f) { v ->
                    gear = GearState.fromAaos((v.value as? Number)?.toInt() ?: 0)
                }
            }
        }
        try {
            vehicle.initialize()
            vehicleUp = true
            guard("brake") { vehicle.getBrake().registerBrakePedalStatus(brakeListener) }
        } catch (e: Exception) {
            warn("vehicle.initialize", e)
        }
        Log.i(tag, "car signals started: fuel=$fuel")
    }

    fun stop() {
        callbacks.forEach { cb -> runCatching { properties?.unregisterCallback(cb) } }
        callbacks.clear()
        runCatching { car?.disconnect() }
        car = null
        if (vehicleUp) guard("unregister") {
            vehicle.getBrake().unregisterBrakePedalStatus(brakeListener)
            vehicle.release()
        }
        vehicleUp = false
        thread.quitSafely()
    }

    private fun subscribe(pm: CarPropertyManager, what: String, id: Int, rate: Float, onValue: (CarPropertyValue<*>) -> Unit) {
        val cb = object : CarPropertyManager.CarPropertyEventCallback {
            override fun onChangeEvent(value: CarPropertyValue<*>) = onValue(value)

            override fun onErrorEvent(propId: Int, areaId: Int) {
                Log.w(tag, "$what error area $areaId")
            }
        }
        guard(what) {
            pm.registerCallback(cb, id, rate)
            callbacks += cb
        }
    }

    private fun intsOf(value: Any?): IntArray? = when (value) {
        is IntArray -> value
        is Array<*> -> value.mapNotNull { (it as? Number)?.toInt() }.toIntArray()
        is Number -> intArrayOf(value.toInt())
        else -> null
    }

    private inline fun guard(what: String, block: () -> Unit) {
        try {
            block()
        } catch (e: Exception) {
            warn(what, e)
        }
    }

    private inline fun <T> guardOr(what: String, fallback: T, block: () -> T): T = try {
        block()
    } catch (e: Exception) {
        warn(what, e)
        fallback
    }

    private fun warn(what: String, e: Exception) {
        Log.w(tag, "$what: ${e.message}")
    }
}
