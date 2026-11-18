package com.colourswift.avarionxlite.engine.rtp

import android.content.Context
import android.content.Intent
import com.colourswift.avarionxlite.engine.definitions.UpdateJob

object Protection {

    private const val PREFS = "lite"
    private const val KEY = "protectionEnabled"

    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY, false)

    fun enable(context: Context) {
        store(context, true)
        context.startForegroundService(Intent(context, ProtectionService::class.java))
    }

    fun disable(context: Context) {
        store(context, false)
        context.stopService(Intent(context, ProtectionService::class.java))
        RtpWakeJob.cancel(context)
        UpdateJob.cancel(context)
    }

    fun arm(context: Context) {
        RtpWakeJob.schedule(context)
        UpdateJob.schedule(context)
    }

    private fun store(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY, enabled).apply()
    }
}
