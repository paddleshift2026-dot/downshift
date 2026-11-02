package com.downshift.app.adapters.demo

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.downshift.app.adapters.audio.FocusMode
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/** Probe commands, posted by the screen or by `adb shell am broadcast` through [DemoReceiver]. */
sealed class DemoCommand {
    data class Throttle(val value: Double) : DemoCommand()
    data class Focus(val mode: FocusMode) : DemoCommand()
    object Guide : DemoCommand()
    object Route : DemoCommand()
    object Report : DemoCommand()
    data class Loop(val name: String) : DemoCommand()
    object Tone : DemoCommand()
}

object DemoBus {
    private val _commands = MutableSharedFlow<DemoCommand>(extraBufferCapacity = 32)
    val commands: SharedFlow<DemoCommand> = _commands
    fun post(c: DemoCommand) = _commands.tryEmit(c)
}

/**
 * adb shell am broadcast -n com.downshift.app/.adapters.demo.DemoReceiver --es cmd throttle --ef value 0.6
 *   cmd: throttle --ef value 0..1 | focus --es mode none|game|media|sonification | guide | route | report
 *        loop --es name FILE.wav (from debug assets/loops) | tone
 */
class DemoReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val cmd = intent.getStringExtra("cmd") ?: return
        val command = when (cmd) {
            "throttle" -> DemoCommand.Throttle(intent.getFloatExtra("value", 0f).toDouble())
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
}
