package com.downshift.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log

/**
 * Keeps the process in the foreground while the engine runs or a trip is being recorded, so the engine keeps sounding
 * and the trip keeps counting while another app (the navigation, a call) has the screen. Without it Android 14 freezes
 * the cached process within 15 s and both stop (M4, docs/m4/background.txt). A media playback service with a quiet
 * notification; DriveHost starts it from the foreground (the START hold, a trip begun on screen) and stops it.
 */
class DriveService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "주행 중", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val notification = Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Downshift")
            .setContentText("엔진음과 안전 주행 기록이 켜져 있어요")
            .setOngoing(true)
            .setContentIntent(open)
            .build()
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
            } else {
                startForeground(ID, notification)
            }
        }.onFailure {
            Log.w(TAG, "foreground service refused: ${it.message}")
            stopSelf()
        }
        return START_NOT_STICKY
    }

    companion object {
        private const val TAG = "DownshiftLab"
        private const val CHANNEL = "drive"
        private const val ID = 1

        /** False when Android does not let the app start it now (from the background, Android 12+). */
        fun start(context: Context): Boolean = runCatching { context.startForegroundService(Intent(context, DriveService::class.java)) }
            .onFailure { Log.w(TAG, "foreground service not started: ${it.message}") }
            .isSuccess

        fun stop(context: Context) {
            context.stopService(Intent(context, DriveService::class.java))
        }
    }
}
