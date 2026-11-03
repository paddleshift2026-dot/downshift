package com.downshift.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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

/**
 * The app's screens as DriveHost's flow picks them: first run, garage, start, drive, summary. Swapped, not animated.
 * While the car asks for DO only the drive screen shows; the lock view stands in for the others (UxGate).
 */
@Composable
fun AppScreen() {
    val app = LocalContext.current.applicationContext as DownshiftApp
    val screen by app.drive.screen.collectAsStateWithLifecycle()
    val ux by app.host.ux.collectAsStateWithLifecycle()
    if (!UxGate.shows(screen, ux)) {
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
