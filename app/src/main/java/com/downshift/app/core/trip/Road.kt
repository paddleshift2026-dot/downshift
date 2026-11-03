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
