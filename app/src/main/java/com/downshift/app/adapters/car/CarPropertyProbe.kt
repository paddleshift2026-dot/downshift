package com.downshift.app.adapters.car

import android.car.Car
import android.car.VehiclePropertyIds
import android.car.hardware.CarPropertyConfig
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.content.Context
import android.util.Log
import com.downshift.app.core.probe.ProbeLedger

/**
 * AAOS standard properties: dumps every config this app can see, then subscribes to the ones Downshift may use.
 * Names resolve through VehiclePropertyIds at run time, so a constant the SDK stub lacks is reported, not a compile error.
 * getPropertyList() only returns what this app may read: compare with `cmd car_service get-property-config` (shell view).
 */
class CarPropertyProbe(context: Context, private val ledger: ProbeLedger) {
    private val tag = "DownshiftCar"
    private val appContext = context.applicationContext
    private var car: Car? = null
    private var properties: CarPropertyManager? = null
    private val callbacks = mutableListOf<CarPropertyManager.CarPropertyEventCallback>()

    private val wanted = listOf(
        "PERF_VEHICLE_SPEED" to "차속",
        "ACCELERATOR_PEDAL_COMPRESSION_PERCENTAGE" to "가속 페달",
        "BRAKE_PEDAL_COMPRESSION_PERCENTAGE" to "브레이크 페달 깊이",
        "ENGINE_RPM" to "엔진 rpm",
        "GEAR_SELECTION" to "기어 선택",
        "CURRENT_GEAR" to "현재 기어",
        "EV_BATTERY_INSTANTANEOUS_CHARGE_RATE" to "배터리 순간 충방전",
        "PERF_ODOMETER" to "누적 주행거리",
        "TURN_SIGNAL_STATE" to "방향지시등",
        "SEAT_OCCUPANCY" to "좌석 점유",
        "INFO_FUEL_TYPE" to "연료 타입",
        "IGNITION_STATE" to "시동 상태",
        "PARKING_BRAKE_ON" to "주차 브레이크",
    )

    fun start(): String {
        wanted.forEach { (name, label) -> ledger.expect("aaos.$name", label, "AAOS $name") }
        val c = runCatching { Car.createCar(appContext) }.getOrNull()
        if (c == null) {
            Log.w(tag, "Car service unavailable")
            return ""
        }
        car = c
        val pm = c.getCarManager(Car.PROPERTY_SERVICE) as? CarPropertyManager ?: return ""
        properties = pm
        val configs = runCatching { pm.propertyList }.getOrElse {
            Log.w(tag, "propertyList: ${it.message}")
            emptyList()
        }
        Log.i(tag, "visible property configs: ${configs.size}")
        val visible = configs.associateBy { it.propertyId }
        wanted.forEach { (name, _) -> subscribe(pm, name, visible) }
        return configs.joinToString("\n") { cfg ->
            "0x%08x %s access=%d change=%d rate=%.1f..%.1f areas=%s".format(
                cfg.propertyId, VehiclePropertyIds.toString(cfg.propertyId), cfg.access, cfg.changeMode,
                cfg.minSampleRate, cfg.maxSampleRate, cfg.areaIds.joinToString(","),
            )
        }
    }

    private fun subscribe(pm: CarPropertyManager, name: String, visible: Map<Int, CarPropertyConfig<*>>) {
        val key = "aaos.$name"
        val id = runCatching { VehiclePropertyIds::class.java.getField(name).getInt(null) }.getOrNull()
        if (id == null) {
            Log.i(tag, "prop $name id=none")
            ledger.unsupported(key, "이 SDK에 상수 없음")
            return
        }
        Log.i(tag, "prop $name id=0x%08x supported=%b".format(id, id in visible))
        val cfg = visible[id]
        if (cfg == null) {
            ledger.unsupported(key, "앱에 안 보임 (차량 미제공 또는 권한 없음)")
            return
        }
        val rate = if (cfg.changeMode == CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_CONTINUOUS) cfg.maxSampleRate.coerceAtMost(10f) else 0f
        val cb = object : CarPropertyManager.CarPropertyEventCallback {
            override fun onChangeEvent(value: CarPropertyValue<*>) {
                val v = value.value.let { if (it is Array<*>) it.joinToString(",") else it.toString() }
                ledger.record(key, if (value.areaId != 0) "area ${value.areaId}: $v" else v, System.currentTimeMillis())
            }

            override fun onErrorEvent(propId: Int, areaId: Int) {
                Log.w(tag, "$name error area $areaId")
            }
        }
        try {
            pm.registerCallback(cb, id, rate)
            callbacks += cb
        } catch (e: SecurityException) {
            ledger.deny(key, e.message?.take(120) ?: "SecurityException")
        } catch (e: Exception) {
            ledger.unsupported(key, e.message?.take(120) ?: e.javaClass.simpleName)
        }
    }

    fun stop() {
        callbacks.forEach { cb -> runCatching { properties?.unregisterCallback(cb) } }
        callbacks.clear()
        runCatching { car?.disconnect() }
    }
}
