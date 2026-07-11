package com.singr.node

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(
                Config.NOTIF_CHANNEL,
                "SingR Node",
                NotificationManager.IMPORTANCE_LOW,
            ).apply { description = "SingR node keep-alive" }
            getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }
    }
}
