package com.colourswift.avarionxlite.boot

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.colourswift.avarionxlite.engine.rtp.Protection
import com.colourswift.avarionxlite.engine.rtp.ProtectionService

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        if (!Protection.isEnabled(context)) return
        runCatching { context.startForegroundService(Intent(context, ProtectionService::class.java)) }
    }
}
