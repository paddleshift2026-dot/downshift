package com.downshift.app.adapters.pleos

import ai.pleos.playground.navi.data.RouteInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.SeatArea
import ai.pleos.playground.vehicle.constant.area.TurnSignalValue
import ai.pleos.playground.vehicle.constant.control.VehicleGear
import ai.pleos.playground.vehicle.listener.BrakePedalStatusListener
import ai.pleos.playground.vehicle.listener.DistanceDrivenListener
import ai.pleos.playground.vehicle.listener.GearSelectionListener
import ai.pleos.playground.vehicle.listener.SeatOccupancyListener
import ai.pleos.playground.vehicle.listener.TurnSignalStateListener
import android.content.Context
import android.util.Log
import com.downshift.app.core.probe.ProbeLedger
import java.lang.reflect.Proxy

/**
 * Pleos SDK side of the probe: gear, brake pedal and turn signal through register* listeners, and every NaviHelper
 * callback through a dynamic proxy, so speed limits, cameras and caution points show up whatever their parameter types.
 */
class PleosProbe(context: Context, private val ledger: ProbeLedger) {
    private val tag = "DownshiftPleos"
    private val appContext = context.applicationContext
    private val vehicle = Vehicle(appContext)
    private val navi = NaviHelper(appContext)
    private var vehicleUp = false

    private val gearListener = object : GearSelectionListener {
        override fun onSelected(gear: VehicleGear?) = rec("pleos.gear", gear)
        override fun onFailed(e: Exception) = warn("gear", e)
    }
    private val brakeListener = object : BrakePedalStatusListener {
        override fun onBrakePedalStatusUpdated(pressed: Boolean?) = rec("pleos.brake", pressed)
        override fun onFailed(e: Exception) = warn("brake", e)
    }
    private val turnListener = object : TurnSignalStateListener {
        override fun onTurnSignalStateUpdated(value: TurnSignalValue?) = rec("pleos.turn", value)
        override fun onFailed(e: Exception) = warn("turnSignal", e)
    }
    private val distanceListener = object : DistanceDrivenListener {
        override fun onDistanceDrivenUpdated(distance: Float?) = rec("pleos.distance", distance)
        override fun onFailed(e: Exception) = warn("distance", e)
    }
    private val tripListener = object : DistanceDrivenListener {
        override fun onDistanceDrivenUpdated(distance: Float?) = rec("pleos.tripDistance", distance)
        override fun onFailed(e: Exception) = warn("tripDistance", e)
    }
    private val seatListeners = mutableListOf<Pair<SeatArea, SeatOccupancyListener>>()

    private val naviListener = Proxy.newProxyInstance(
        NaviHelperEventListener::class.java.classLoader,
        arrayOf(NaviHelperEventListener::class.java),
    ) { proxy, method, args ->
        when (method.name) {
            "equals" -> proxy === args?.getOrNull(0)
            "hashCode" -> System.identityHashCode(proxy)
            "toString" -> "DownshiftNaviProbe"
            else -> {
                rec("navi.${method.name}", args?.joinToString(" / ") { it.toString().take(200) } ?: "")
                null
            }
        }
    } as NaviHelperEventListener

    fun start() {
        listOf(
            "pleos.gear" to "기어", "pleos.brake" to "브레이크 페달", "pleos.turn" to "방향지시등",
            "pleos.distance" to "누적 주행거리", "pleos.tripDistance" to "이번 트립 거리", "pleos.seat" to "좌석 점유",
        ).forEach { (id, label) -> ledger.expect(id, label, "Pleos Vehicle SDK") }
        listOf(
            "onCurrentRoadSpeedLimit" to "제한속도", "onCameraAlert" to "단속 카메라", "onCautionPointInfo" to "주의구간(스쿨존)",
            "onTBTInfo" to "회전 안내", "onDrivingInfo" to "주행 정보", "onTripInfo" to "트립 정보", "onNavigationStatus" to "내비 상태",
        ).forEach { (m, label) -> ledger.expect("navi.$m", label, "NaviHelper") }
        try {
            vehicle.initialize()
            vehicleUp = true
            guard("gear") { vehicle.getGear().registerGearSelection(gearListener) }
            guard("brake") { vehicle.getBrake().registerBrakePedalStatus(brakeListener) }
            guard("turnSignal") { vehicle.getTurnSignal().registerTurnSignalState(turnListener) }
            guard("distance") { vehicle.getOdometer().registerDistanceDriven(distanceListener) }
            guard("tripDistance") { vehicle.getOdometer().registerTripCurrentDrivingDistance(tripListener) }
            guard("seat") {
                vehicle.getSeat().checkSeatOccupancyCapabilities(
                    { areas: List<SeatArea> ->
                        Log.i(tag, "seat occupancy areas: ${areas.map { a -> a.values.toList() }}")
                        areas.forEach { area -> registerSeat(area) }
                    },
                    { e -> warn("seat.capabilities", e) },
                )
            }
        } catch (e: Exception) {
            warn("vehicle.initialize", e)
        }
        runCatching {
            navi.initialize(
                { runCatching { navi.addListener(naviListener) } },
                { e -> Log.w(tag, "NaviHelper connect failed: ${e.message}") },
            )
        }.onFailure { Log.w(tag, "NaviHelper init failed: ${it.message}") }
    }

    /** Route to Seoul Station so the built-in navigation starts guidance (speed limits, cameras, TBT). */
    fun requestDemoRoute(): Boolean = runCatching {
        navi.requestRoute(RouteInfo(latitude = 37.5559, longitude = 126.9723, poiId = "seoul-station", poiName = "서울역", poiSubId = "0", address = ""))
        true
    }.getOrElse {
        Log.w(tag, "requestRoute failed: ${it.message}")
        false
    }

    private fun registerSeat(area: SeatArea) {
        val label = area.values.toList().toString()
        val l = object : SeatOccupancyListener {
            override fun onSeatOccupancyUpdated(occupied: Boolean?) = rec("pleos.seat", "$label $occupied")
            override fun onFailed(e: Exception) = warn("seat $label", e)
        }
        guard("seat.register $label") {
            vehicle.getSeat().registerSeatOccupancy(area, l)
            seatListeners += area to l
        }
    }

    fun stop() {
        if (vehicleUp) guard("unregister") {
            vehicle.getGear().unregisterGearSelection(gearListener)
            vehicle.getBrake().unregisterBrakePedalStatus(brakeListener)
            vehicle.getTurnSignal().unregisterTurnSignalState(turnListener)
            vehicle.getOdometer().unregisterDistanceDriven(distanceListener)
            vehicle.getOdometer().unregisterTripCurrentDrivingDistance(tripListener)
            seatListeners.forEach { (area, l) -> vehicle.getSeat().unregisterSeatOccupancy(area, l) }
            seatListeners.clear()
            vehicle.release()
        }
        runCatching {
            navi.removeListener(naviListener)
            navi.release()
        }
    }

    private fun rec(id: String, v: Any?) = ledger.record(id, v.toString(), System.currentTimeMillis())

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
