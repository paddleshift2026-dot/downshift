package com.downshift.app

import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.downshift.app.ui.ProbeScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestCarPermissions()
        setContent { ProbeScreen() }
    }

    /** Runtime grants for the AAOS properties; whatever stays denied shows up in the probe ledger. */
    private fun requestCarPermissions() {
        val wanted = listOf(
            "android.car.permission.CAR_SPEED", "android.car.permission.CAR_ENERGY",
            "android.car.permission.CAR_POWERTRAIN", "android.car.permission.CAR_ENGINE_DETAILED",
            "android.car.permission.CAR_MILEAGE", "android.car.permission.CAR_INFO",
            "android.car.permission.CAR_EXTERIOR_LIGHTS", "android.car.permission.CONTROL_CAR_SEATS",
        ).filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (wanted.isNotEmpty()) runCatching { ActivityCompat.requestPermissions(this, wanted.toTypedArray(), 1) }
    }
}
