package com.downshift.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.downshift.app.DownshiftApp
import com.downshift.app.core.flow.Screen
import com.downshift.app.ui.drive.DriveScreen
import com.downshift.app.ui.drive.SummaryScreen
import com.downshift.app.ui.garage.GarageScreen
import com.downshift.app.ui.onboard.OnboardScreen
import com.downshift.app.ui.start.StartScreen

/** The app's screens as DriveHost's flow picks them: first run, garage, start, drive, summary. Swapped, not animated. */
@Composable
fun AppScreen() {
    val drive = (LocalContext.current.applicationContext as DownshiftApp).drive
    val screen by drive.screen.collectAsStateWithLifecycle()
    when (screen) {
        Screen.ONBOARDING -> OnboardScreen()
        Screen.GARAGE -> GarageScreen()
        Screen.START -> StartScreen()
        Screen.DRIVE -> DriveScreen()
        Screen.SUMMARY -> SummaryScreen()
    }
}
