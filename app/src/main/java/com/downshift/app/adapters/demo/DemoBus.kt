package com.downshift.app.adapters.demo

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.downshift.app.adapters.audio.FocusMode
import com.downshift.app.core.build.Build
import com.downshift.app.core.build.EngineId
import com.downshift.app.core.build.ExhaustPart
import com.downshift.app.core.build.IntakePart
import com.downshift.app.core.build.TransmissionPart
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/** Garage, sound lab and probe commands, posted by the screens or by `adb shell am broadcast` through [DemoReceiver]. */
sealed class DemoCommand {
    /** Demo mode (PRD feature G): on or off, the dashed demo buttons shown or not, the distance lapse. */
    data class Demo(val on: Boolean? = null, val buttons: Boolean? = null, val lapse: Int? = null) : DemoCommand()
    /** Garage moves for rehearsals and screenshots: add, reset, unlock, equip, preview or tab. */
    data class GarageAction(val action: String, val part: String? = null, val points: Int? = null, val tab: String? = null) : DemoCommand()
    /** Trip and situation events the emulator's navigation cannot send, plus the flow's buttons, for rehearsals and checks. */
    data class Drive(
        val event: String,
        val value: String? = null,
        val kph: Int? = null,
        val m: Int? = null,
        val len: Int? = null,
        val dir: String? = null,
        val road: String? = null,
        val to: String? = null,
    ) : DemoCommand()
    /** Manual throttle 0..1; null clears it so the console pedal or the estimate drives again. */
    data class Throttle(val value: Double?) : DemoCommand()
    data class Focus(val mode: FocusMode) : DemoCommand()
    object Guide : DemoCommand()
    object Route : DemoCommand()
    object Report : DemoCommand()
    data class Loop(val name: String) : DemoCommand()
    object Tone : DemoCommand()
    object EngineStart : DemoCommand()
    object EngineStop : DemoCommand()
    data class Record(val on: Boolean, val name: String) : DemoCommand()

    /** Changes only the parts and sliders that are given. */
    data class BuildChange(
        val engine: EngineId? = null,
        val intake: IntakePart? = null,
        val exhaust: ExhaustPart? = null,
        val transmission: TransmissionPart? = null,
        val pops: Double? = null,
        val redlineOffset: Int? = null,
        val volume: Double? = null,
    ) : DemoCommand() {
        fun applyTo(b: Build) = b.copy(
            engine = engine ?: b.engine,
            intake = intake ?: b.intake,
            exhaust = exhaust ?: b.exhaust,
            transmission = transmission ?: b.transmission,
            pops = pops ?: b.pops,
            redlineOffset = redlineOffset ?: b.redlineOffset,
            volume = volume ?: b.volume,
        )
    }
}

object DemoBus {
    private val _commands = MutableSharedFlow<DemoCommand>(extraBufferCapacity = 32)
    val commands: SharedFlow<DemoCommand> = _commands
    fun post(c: DemoCommand) = _commands.tryEmit(c)
}

/**
 * adb shell am broadcast -n com.downshift.app/.adapters.demo.DemoReceiver --es cmd start
 * (the receiver takes broadcasts from adb's shell and the system only: android.permission.DUMP)
 *   demo:      demo --es value on|off [--es buttons on|off] [--ei lapse 20]
 *              garage add/reset and the drive injections that change points (limit, camera, section, school, turn,
 *              lapse) need demo mode on, so the real wallet changes only through real driving (DemoPolicy)
 *   garage:    garage --es action add [--ei points 1000] | garage --es action reset (the demo state)
 *              garage --es action unlock|equip|preview --es part ID | garage --es action tab --es tab engine|intake|exhaust|trans|tune
 *   drive:     drive --es event agree | screen --es to garage|start|safety
 *              drive --es event limit --ei kph 50 (0 = unknown) | camera --ei kph 50 --ei m 300
 *              drive --es event section --ei kph 80 --ei m 100 --ei len 1000 | school --ei m 150 [--ei kph 30]
 *              drive --es event turn --es dir left|right [--es road highway] --ei m 120
 *              drive --es event quiet --es value on|off | guide | music | mute --es value on|off
 *              drive --es event lapse --ei value 20 (1 = off) | pleos --es value off|auto | route --es value cancel
 *   sound lab: start | stop | throttle --ef value 0..1 | throttle --es value clear
 *              build [--es engine I4|V8|V12] [--es intake NA|TURBO1|TURBO2|TURBO3|SUPERCHARGER] [--es exhaust STOCK|SPORT|TRACK]
 *                    [--es trans AT|DCT|MT] [--ef pops 0..1] [--ei redline -500..500] [--ef volume 0..1]
 *              record --es value on|off --es name NAME   (saved to files/NAME.wav)
 *   probe:     focus --es mode none|game|media|sonification | guide | route | report
 *              loop --es name FILE.wav (from debug assets/loops) | tone
 */
class DemoReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val cmd = intent.getStringExtra("cmd") ?: return
        @Suppress("DEPRECATION")
        val value = intent.extras?.get("value")
        val command = when (cmd) {
            "demo" -> DemoCommand.Demo(
                on = (value as? String)?.let { it == "on" },
                buttons = intent.getStringExtra("buttons")?.let { it == "on" },
                lapse = intOrNull(intent, "lapse"),
            )
            "garage" -> DemoCommand.GarageAction(
                action = intent.getStringExtra("action") ?: return,
                part = intent.getStringExtra("part"),
                points = if (intent.hasExtra("points")) intent.getIntExtra("points", 1000) else null,
                tab = intent.getStringExtra("tab"),
            )
            "drive" -> DemoCommand.Drive(
                event = intent.getStringExtra("event") ?: return,
                value = value?.toString(),
                kph = intOrNull(intent, "kph"),
                m = intOrNull(intent, "m"),
                len = intOrNull(intent, "len"),
                dir = intent.getStringExtra("dir"),
                road = intent.getStringExtra("road"),
                to = intent.getStringExtra("to"),
            )
            "throttle" -> DemoCommand.Throttle(if (value == "clear") null else (value as? Number)?.toDouble() ?: 0.0)
            "start" -> DemoCommand.EngineStart
            "stop" -> DemoCommand.EngineStop
            "build" -> DemoCommand.BuildChange(
                engine = enumExtra<EngineId>(intent, "engine"),
                intake = enumExtra<IntakePart>(intent, "intake"),
                exhaust = enumExtra<ExhaustPart>(intent, "exhaust"),
                transmission = intent.getStringExtra("trans")?.let { if (it.equals("MT", true)) "MT_BLIP" else it }
                    ?.let { t -> TransmissionPart.entries.firstOrNull { it.name.equals(t, ignoreCase = true) } },
                pops = if (intent.hasExtra("pops")) intent.getFloatExtra("pops", 0.5f).toDouble() else null,
                redlineOffset = if (intent.hasExtra("redline")) intent.getIntExtra("redline", 0) else null,
                volume = if (intent.hasExtra("volume")) intent.getFloatExtra("volume", 0.8f).toDouble() else null,
            )
            "record" -> DemoCommand.Record(on = value != "off", name = intent.getStringExtra("name") ?: "recording")
            "focus" -> DemoCommand.Focus(
                FocusMode.entries.firstOrNull { it.name.equals(intent.getStringExtra("mode"), ignoreCase = true) } ?: FocusMode.NONE,
            )
            "guide" -> DemoCommand.Guide
            "route" -> DemoCommand.Route
            "report" -> DemoCommand.Report
            "loop" -> DemoCommand.Loop(intent.getStringExtra("name") ?: return)
            "tone" -> DemoCommand.Tone
            else -> null
        } ?: return
        Log.i("DownshiftDemo", "command $cmd -> $command")
        DemoBus.post(command)
    }

    private fun intOrNull(intent: Intent, key: String): Int? = if (intent.hasExtra(key)) intent.getIntExtra(key, 0) else null

    private inline fun <reified E : Enum<E>> enumExtra(intent: Intent, key: String): E? =
        intent.getStringExtra(key)?.let { s -> enumValues<E>().firstOrNull { it.name.equals(s, ignoreCase = true) } }
}
