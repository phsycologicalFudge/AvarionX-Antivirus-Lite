package com.colourswift.avarionxlite.notifications

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.colourswift.avarionxlite.MainActivity
import com.colourswift.avarionxlite.R

object Alerts {

    const val PROTECTION_ID = 1

    private const val PROTECTION_CHANNEL = "protection"
    private const val ALERT_CHANNEL = "alerts"
    private const val SUMMARY_ID = 2
    private const val SUMMARY_COUNT = "count"
    private const val THREAT = "threat"
    private const val MAX_SINGLE = 5

    @Volatile private var channelsReady = false

    fun protection(context: Context): Notification {
        manager(context)
        return Notification.Builder(context, PROTECTION_CHANNEL)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(context.getString(R.string.fgs_text))
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(open(context, 0, false))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    fun threat(context: Context, text: String) {
        val manager = manager(context)
        val active = manager.activeNotifications.filter { it.notification.channelId == ALERT_CHANNEL }
        val summary = active.firstOrNull { it.id == SUMMARY_ID }
        val singles = active.filter { it.id != SUMMARY_ID && it.notification.extras.getBoolean(THREAT) }
        if (summary == null && singles.size < MAX_SINGLE) {
            post(context, context.getString(R.string.alert_title), text, true)
            return
        }
        val count = (summary?.notification?.extras?.getInt(SUMMARY_COUNT) ?: 0) + singles.size + 1
        val notification = Notification.Builder(context, ALERT_CHANNEL)
            .setContentTitle(context.getString(R.string.alert_title))
            .setContentText(context.resources.getQuantityString(R.plurals.alert_total, count, count))
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(open(context, SUMMARY_ID, true))
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setExtras(Bundle().apply { putInt(SUMMARY_COUNT, count) })
            .build()
        manager.notify(SUMMARY_ID, notification)
        singles.forEach { manager.cancel(it.id) }
    }

    fun unsupported(context: Context) {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        @Suppress("DEPRECATION") val code = info.versionCode
        val key = "unsupportedNotified_${info.versionName}_$code"
        val prefs = context.getSharedPreferences("lite", Context.MODE_PRIVATE)
        if (prefs.getBoolean(key, false)) return
        prefs.edit().putBoolean(key, true).apply()
        post(context, context.getString(R.string.app_name), context.getString(R.string.setup_unsupported), false)
    }

    private fun post(context: Context, title: String, text: String, threat: Boolean) {
        val id = System.currentTimeMillis().toInt() and Int.MAX_VALUE or 4
        val notification = Notification.Builder(context, ALERT_CHANNEL)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(open(context, id, threat))
            .setAutoCancel(true)
            .setExtras(Bundle().apply { putBoolean(THREAT, threat) })
            .build()
        manager(context).notify(id, notification)
    }

    private fun open(context: Context, requestCode: Int, openQuarantine: Boolean): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        if (openQuarantine) intent.putExtra(MainActivity.EXTRA_QUARANTINE, true)
        return PendingIntent.getActivity(context, requestCode, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun manager(context: Context): NotificationManager {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (!channelsReady) {
            manager.createNotificationChannels(
                listOf(
                    NotificationChannel(PROTECTION_CHANNEL, context.getString(R.string.channel_protection), NotificationManager.IMPORTANCE_LOW).apply { setShowBadge(false) },
                    NotificationChannel(ALERT_CHANNEL, context.getString(R.string.channel_alerts), NotificationManager.IMPORTANCE_HIGH)
                )
            )
            channelsReady = true
        }
        return manager
    }
}
