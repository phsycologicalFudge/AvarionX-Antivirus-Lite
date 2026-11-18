package com.colourswift.avarionxlite.engine.rtp

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Environment
import android.os.FileObserver
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.SystemClock
import android.os.storage.StorageManager
import com.colourswift.avarionxlite.engine.definitions.Defs
import com.colourswift.avarionxlite.notifications.Alerts
import java.io.File

class ProtectionService : Service() {

    private val thread = HandlerThread("rtp").apply { start() }
    private val handler = Handler(thread.looper)
    private val observers = HashMap<String, FileObserver>()
    private val recent = LinkedHashMap<String, Long>()
    private val settling = HashMap<String, Runnable>()
    @Volatile private var scanner: Scanner? = null
    private var mediaReceiver: BroadcastReceiver? = null
    private var packageReceiver: BroadcastReceiver? = null

    private val syncLoop = object : Runnable {
        override fun run() {
            Defs.syncAsync(applicationContext)
            handler.postDelayed(this, SYNC_MS)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(Alerts.PROTECTION_ID, Alerts.protection(this))
        if (!Protection.isEnabled(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (scanner == null) activate()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        mediaReceiver?.let { unregisterReceiver(it) }
        packageReceiver?.let { unregisterReceiver(it) }
        mediaReceiver = null
        packageReceiver = null
        handler.removeCallbacksAndMessages(null)
        handler.post {
            observers.values.forEach { it.stopWatching() }
            observers.clear()
            settling.clear()
        }
        thread.quitSafely()
        scanner?.stop()
        scanner = null
        super.onDestroy()
    }

    private fun activate() {
        scanner = Scanner(this).also { it.start() }
        Protection.arm(this)
        handler.post { watchDownloads() }
        mediaReceiver = listen("file", Intent.ACTION_MEDIA_MOUNTED, Intent.ACTION_MEDIA_UNMOUNTED, Intent.ACTION_MEDIA_EJECT) {
            handler.postDelayed({ watchDownloads() }, REWATCH_DELAY_MS)
        }
        packageReceiver = listen("package", Intent.ACTION_PACKAGE_ADDED, Intent.ACTION_PACKAGE_REPLACED) { intent ->
            val packageName = intent.data?.schemeSpecificPart ?: return@listen
            val replacing = intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)
            if (intent.action == Intent.ACTION_PACKAGE_ADDED && replacing) return@listen
            val apk = runCatching { packageManager.getApplicationInfo(packageName, 0).sourceDir }.getOrNull()
            if (!apk.isNullOrBlank() && fresh("app::$packageName")) scanner?.app(packageName, apk)
        }
        handler.postDelayed(syncLoop, SYNC_MS)
    }

    private fun watchDownloads() {
        observers.values.forEach { it.stopWatching() }
        observers.clear()
        for (dir in downloadDirs()) watchTree(File(dir), 0)
    }

    private fun watchTree(dir: File, depth: Int) {
        watch(dir.path, depth)
        if (depth >= MAX_DEPTH) return
        dir.listFiles()?.filter { it.isDirectory }?.forEach { watchTree(it, depth + 1) }
    }

    private fun watch(dir: String, depth: Int) {
        if (dir in observers || observers.size >= MAX_WATCHED) return
        val observer = object : FileObserver(dir, CREATE or CLOSE_WRITE or MOVED_TO or DELETE_SELF or MOVE_SELF) {
            override fun onEvent(event: Int, name: String?) {
                if (event and (DELETE_SELF or MOVE_SELF) != 0) {
                    handler.post { observers.remove(dir)?.stopWatching() }
                    return
                }
                if (name == null) return
                handler.post { arrived(File(dir, name), depth) }
            }
        }
        observer.startWatching()
        observers[dir] = observer
    }

    private fun arrived(file: File, depth: Int) {
        if (!file.isDirectory) {
            settle(file.path, SystemClock.elapsedRealtime() + SETTLE_TIMEOUT_MS)
            return
        }
        if (depth >= MAX_DEPTH) return
        watch(file.path, depth + 1)
        file.listFiles()?.forEach { arrived(it, depth + 1) }
    }

    private fun settle(path: String, deadline: Long) {
        settling.remove(path)?.let { handler.removeCallbacks(it) }
        val size = File(path).length()
        val task = Runnable {
            settling.remove(path)
            val now = File(path).length()
            if ((now != size || now == 0L) && SystemClock.elapsedRealtime() < deadline) {
                settle(path, deadline)
            } else {
                scanner?.file(path)
            }
        }
        settling[path] = task
        handler.postDelayed(task, SETTLE_MS)
    }

    private fun fresh(key: String): Boolean {
        val now = System.currentTimeMillis()
        val last = recent[key]
        if (last != null && now - last < DEDUPE_MS) return false
        recent[key] = now
        while (recent.size > MAX_RECENT) recent.remove(recent.keys.first())
        return true
    }

    private fun downloadDirs(): List<String> {
        val primary = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        primary.mkdirs()
        return listOf(primary.path) + removableRoots().map { "$it/Download" }.filter { File(it).isDirectory }
    }

    private fun removableRoots(): List<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            getSystemService(StorageManager::class.java).storageVolumes
                .filter { it.isRemovable && it.state in MOUNTED }
                .mapNotNull { it.directory?.absolutePath }
        } else {
            getExternalFilesDirs(null).filterNotNull()
                .filter { Environment.isExternalStorageRemovable(it) && Environment.getExternalStorageState(it) in MOUNTED }
                .map { it.absolutePath.substringBefore("/Android/data/") }
        }

    private fun listen(scheme: String, vararg actions: String, onEvent: (Intent) -> Unit): BroadcastReceiver {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent != null) onEvent(intent)
            }
        }
        val filter = IntentFilter().apply {
            actions.forEach { addAction(it) }
            addDataScheme(scheme)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(receiver, filter, null, handler, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(receiver, filter, null, handler)
        }
        return receiver
    }

    private companion object {
        val MOUNTED = setOf(Environment.MEDIA_MOUNTED, Environment.MEDIA_MOUNTED_READ_ONLY)
        const val DEDUPE_MS = 2_000L
        const val MAX_RECENT = 4096
        const val MAX_DEPTH = 2
        const val MAX_WATCHED = 512
        const val SETTLE_MS = 400L
        const val SETTLE_TIMEOUT_MS = 60_000L
        const val REWATCH_DELAY_MS = 1_500L
        const val SYNC_MS = 30L * 60 * 1000
    }
}
