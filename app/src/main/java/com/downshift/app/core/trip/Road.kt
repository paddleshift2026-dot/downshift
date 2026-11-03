package com.downshift.app.core.trip

/** Which way a turn goes. */
enum class Side { LEFT, RIGHT }

/** What the turn-signal stalk says. */
enum class Signal {
    OFF, LEFT, RIGHT;

    fun shows(side: Side): Boolean = (this == LEFT && side == Side.LEFT) || (this == RIGHT && side == Side.RIGHT)
}

/** The navigation's next manoeuvre: the signal it needs, whether it is a highway one (100 m lead) and metres to go. */
data class TurnAhead(val side: Side, val highway: Boolean, val remainM: Double)

enum class PlaceKind { SCHOOL_ZONE, CAMERA, SECTION_START, SECTION_END }

/** A school zone, speed camera or section-camera end the navigation announced; limitKph is 0 when it gave none. */
data class PlaceAhead(val id: String, val kind: PlaceKind, val limitKph: Int, val remainM: Double)

/** NaviHelper CameraType and CautionPointType names (navi-data 3.0.4) to the places a trip judges. */
object PlaceKinds {
    private val SPEED_CAMERAS = setOf(
        "SPEED_CAMERA_FIX", "SPEED_CAMERA_MOV", "SPEED_CAMERA_REAR", "SPEED_CAMERA_TRAFFIC_LIGHT",
        "SPEED_CAMERA_SCHOOL_ZONE", "SPEED_CAMERA_SCHOOL_ZONE_TRAFFIC_LIGHT", "VARIABLE_SPEED_CAMERA",
    )

    fun camera(typeName: String): PlaceKind? = when (typeName) {
        "SPEED_CAMERA_SECTION_START" -> PlaceKind.SECTION_START
        "SPEED_CAMERA_SECTION_END" -> PlaceKind.SECTION_END
        in SPEED_CAMERAS -> PlaceKind.CAMERA
        else -> null // SECTION_SPEED_ALERT is a reminder inside a section, not a place
    }

    fun caution(typeName: String): PlaceKind? = if (typeName == "SCHOOL_ZONE") PlaceKind.SCHOOL_ZONE else null
}
