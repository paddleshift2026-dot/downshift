package com.downshift.app

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.downshift.app.ui.AppScreen
import com.downshift.app.ui.ProbeScreen
import com.downshift.app.ui.SoundLabScreen
import com.downshift.app.ui.Faint
import com.downshift.app.ui.Ground
import com.downshift.app.ui.Heat

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestCarPermissions()
        setContent { Tabs() }
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

/**
 * The app flow first (first run, garage, start, drive, summary); the sound lab and the M0 probe stay behind a slim
 * development row and only run while shown. The row is there in debug builds only, and not in demo mode, so the
 * judges' release APK and the demo recordings show the app alone.
 */
@Composable
private fun Tabs() {
    val context = LocalContext.current
    val debuggable = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    val demo by (context.applicationContext as DownshiftApp).demo.on.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val devRow = debuggable && !demo
    if (!devRow) tab = 0
    Column(Modifier.fillMaxSize().background(Ground)) {
        if (devRow) {
            Row(Modifier.height(40.dp).padding(start = 48.dp), horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically) {
                listOf("개러지", "사운드 랩", "프로브").forEachIndexed { i, title ->
                    Text(
                        title,
                        color = if (i == tab) Heat else Faint,
                        fontSize = 17.sp,
                        fontWeight = if (i == tab) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier.clickable { tab = i },
                    )
                }
            }
        }
        when (tab) {
            0 -> AppScreen()
            1 -> SoundLabScreen()
            else -> ProbeScreen()
        }
    }
}
