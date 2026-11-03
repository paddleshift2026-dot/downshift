package com.downshift.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.downshift.app.DownshiftApp
import com.downshift.app.core.flow.Screen
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
        Screen.DRIVE -> Placeholder("주행")
        Screen.SUMMARY -> Placeholder("주행 결산")
    }
}

@Composable
private fun Placeholder(name: String) {
    Box(Modifier.fillMaxSize().background(Ground), contentAlignment = Alignment.Center) {
        Text(name, color = Faint, fontSize = 40.sp)
    }
}
