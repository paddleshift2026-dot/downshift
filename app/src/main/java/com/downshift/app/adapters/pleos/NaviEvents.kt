package com.downshift.app.adapters.pleos

import ai.pleos.playground.navi.data.CameraAlert
import ai.pleos.playground.navi.data.CautionPoint
import ai.pleos.playground.navi.data.TBTInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import android.content.Context
import android.util.Log
import com.downshift.app.DriveEvent
import com.downshift.app.core.trip.PlaceAhead
import com.downshift.app.core.trip.PlaceKinds
import com.downshift.app.core.trip.TurnAhead
import com.downshift.app.core.trip.TurnKinds

/**
 * NaviHelper guidance a trip scores on: the road's speed limit, speed cameras, school zones and the next turn, mapped
 * to the trip's own types; kinds the trip does not judge are dropped. On the emulator only a zero speed limit arrives
 * (the car does not move along the route), so the rest is injected over adb (M0). A failed connection is logged and
 * leaves distance points only.
 */
class NaviEvents(context: Context, private val onEvent: (DriveEvent) -> Unit) {
    private val tag = "DownshiftNavi"
    private val navi = NaviHelper(context.applicationContext)
    private var started = false
    @Volatile private var lastLimit = -1
    @Volatile private var lastTbt = ""

    private val listener = object : NaviHelperEventListener {
        override fun onCurrentRoadSpeedLimit(speedLimit: Int) {
            if (speedLimit != lastLimit) Log.i(tag, "limit $speedLimit")
            lastLimit = speedLimit
            onEvent(DriveEvent.Limit(speedLimit))
        }

        override fun onTBTInfo(tbtInfoList: List<TBTInfo>) {
            val next = tbtInfoList.firstOrNull() ?: return
            val raw = "${next.type.name} ${next.distance} ${next.description}"
            if (raw != lastTbt) Log.i(tag, "tbt $raw (${tbtInfoList.size} ahead)")
            lastTbt = raw
            val (side, highway) = TurnKinds.of(next.type.name) ?: return
            Log.i(tag, "turn $side ${if (highway) "highway" else "city"} ${next.distance}m (${next.type.name})")
            onEvent(DriveEvent.Turn(TurnAhead(side, highway, next.distance)))
        }

        override fun onCameraAlert(cameraAlertList: List<CameraAlert>) {
            cameraAlertList.forEach { c ->
                val kind = PlaceKinds.camera(c.type.name) ?: return@forEach
                Log.i(tag, "place $kind ${c.id} ${c.speedLimit}km/h ${c.remainDistance}m")
                onEvent(DriveEvent.Place(PlaceAhead(c.id, kind, c.speedLimit, c.remainDistance.toDouble())))
            }
        }

        override fun onCautionPointInfo(cautionPointList: List<CautionPoint>) {
            cautionPointList.forEach { p ->
                val kind = PlaceKinds.caution(p.type.name) ?: return@forEach
                Log.i(tag, "place $kind ${p.id} ${p.remainDistance}m")
                onEvent(DriveEvent.Place(PlaceAhead(p.id, kind, 0, p.remainDistance.toDouble())))
            }
        }
    }

    fun start() {
        if (started) return
        started = true
        runCatching {
            navi.initialize(
                {
                    runCatching { navi.addListener(listener) }.onFailure { Log.w(tag, "addListener: ${it.message}") }
                    Log.i(tag, "navi connected")
                },
                { e -> Log.w(tag, "navi connect failed: ${e.message}") },
            )
        }.onFailure { Log.w(tag, "navi init failed: ${it.message}") }
    }

    /** Ends the built-in navigation's guidance (demo hook: an emulator route never moves, so its turns go stale). */
    fun cancelRoute() {
        runCatching { navi.cancelRoute() }.onFailure { Log.w(tag, "cancelRoute: ${it.message}") }
        Log.i(tag, "route cancelled")
    }

    fun stop() {
        if (!started) return
        started = false
        runCatching {
            navi.removeListener(listener)
            navi.release()
        }
    }
}
