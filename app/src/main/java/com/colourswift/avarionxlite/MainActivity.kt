package com.colourswift.avarionxlite

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.TextView
import com.colourswift.avarionxlite.boot.Permissions
import com.colourswift.avarionxlite.engine.Engine
import com.colourswift.avarionxlite.engine.rtp.Protection
import com.colourswift.avarionxlite.home_ui.quarantine.QuarantineActivity
import com.colourswift.avarionxlite.home_ui.themes.ProtectionRingView
import com.colourswift.avarionxlite.themes.applyEdgeToEdge

class MainActivity : Activity() {

    private lateinit var ring: ProtectionRingView
    private lateinit var hint: TextView
    private lateinit var holder: View
    private var busy = false
    private var awaitingStorage = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        applyEdgeToEdge(findViewById(R.id.root), findViewById(R.id.content))
        ring = findViewById(R.id.ring)
        hint = findViewById(R.id.hint)
        holder = findViewById(R.id.ringHolder)
        holder.setOnClickListener {
            if (busy) return@setOnClickListener
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            if (Protection.isEnabled(this)) {
                Protection.disable(this)
                render(false, true)
            } else {
                notificationStep()
            }
        }
        findViewById<View>(R.id.quarantineButton).setOnClickListener {
            startActivity(Intent(this, QuarantineActivity::class.java))
        }
        if (savedInstanceState == null) openQuarantineIfAsked(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        openQuarantineIfAsked(intent)
    }

    override fun onResume() {
        super.onResume()
        if (awaitingStorage) {
            awaitingStorage = false
            if (Permissions.storage(this)) setupStep()
        }
        render(Protection.isEnabled(this), false)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        if (requestCode == REQUEST_NOTIFICATIONS) storageStep()
    }

    private fun notificationStep() {
        if (Permissions.notifications(this)) storageStep() else Permissions.askNotifications(this, REQUEST_NOTIFICATIONS)
    }

    private fun storageStep() {
        if (Permissions.storage(this)) {
            setupStep()
        } else {
            awaitingStorage = true
            Permissions.askStorage(this, REQUEST_STORAGE)
        }
    }

    private fun setupStep() {
        if (Engine.isReady()) {
            turnOn()
            return
        }
        busy = true
        hint.setText(R.string.setup_running)
        Thread {
            val phase = runCatching { Engine.prepare(applicationContext) }.getOrDefault(Engine.Phase.OFFLINE)
            runOnUiThread { finishSetup(phase) }
        }.start()
    }

    private fun finishSetup(phase: Engine.Phase) {
        busy = false
        if (isFinishing || isDestroyed) return
        when (phase) {
            Engine.Phase.READY -> turnOn()
            Engine.Phase.OFFLINE -> {
                render(false, false)
                AlertDialog.Builder(this)
                    .setTitle(R.string.setup_offline_title)
                    .setMessage(R.string.setup_offline_body)
                    .setNegativeButton(R.string.cancel, null)
                    .setPositiveButton(R.string.setup_retry) { _, _ -> setupStep() }
                    .show()
            }
            Engine.Phase.UNSUPPORTED -> {
                render(false, false)
                AlertDialog.Builder(this)
                    .setTitle(R.string.setup_unsupported)
                    .setPositiveButton(R.string.ok, null)
                    .show()
            }
        }
    }

    private fun turnOn() {
        Protection.enable(this)
        render(true, true)
    }

    private fun openQuarantineIfAsked(intent: Intent) {
        if (!intent.getBooleanExtra(EXTRA_QUARANTINE, false)) return
        intent.removeExtra(EXTRA_QUARANTINE)
        startActivity(Intent(this, QuarantineActivity::class.java))
    }

    private fun render(enabled: Boolean, animate: Boolean) {
        ring.setProtected(enabled, animate)
        if (!busy) hint.setText(if (enabled) R.string.state_on else R.string.state_off_hint)
        holder.contentDescription = hint.text
    }

    companion object {
        const val EXTRA_QUARANTINE = "open_quarantine"
        private const val REQUEST_NOTIFICATIONS = 1
        private const val REQUEST_STORAGE = 2
    }
}
