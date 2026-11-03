package com.downshift.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.downshift.app.DownshiftApp
import com.downshift.app.core.flow.Screen
import com.downshift.app.core.flow.UxGate
import com.downshift.app.ui.drive.DriveScreen
import com.downshift.app.ui.drive.SummaryScreen
import com.downshift.app.ui.garage.GarageScreen
import com.downshift.app.ui.onboard.OnboardScreen
import com.downshift.app.ui.start.StartScreen
import kotlinx.coroutines.delay

/**
 * The app's screens as DriveHost's flow picks them: first run, garage, start, drive, summary. Swapped, not animated.
 * While the car asks for DO only the drive screen shows; the lock view stands in for the others (UxGate), after
 * UxGate.LOCK_DELAY_MS so that leaving park goes straight to the drive screen.
 */
@Composable
fun AppScreen() {
    val app = LocalContext.current.applicationContext as DownshiftApp
    val screen by app.drive.screen.collectAsStateWithLifecycle()
    val ux by app.host.ux.collectAsStateWithLifecycle()
    val blocked = !UxGate.shows(screen, ux)
    var locked by remember { mutableStateOf(blocked) }
    LaunchedEffect(blocked) {
        if (blocked) delay(UxGate.LOCK_DELAY_MS)
        locked = blocked
    }
    if (blocked && locked) {
        DrivingLock()
        return
    }
    when (screen) {
        Screen.ONBOARDING -> OnboardScreen()
        Screen.GARAGE -> GarageScreen()
        Screen.START -> StartScreen()
        Screen.DRIVE -> DriveScreen()
        Screen.SUMMARY -> SummaryScreen()
    }
}
