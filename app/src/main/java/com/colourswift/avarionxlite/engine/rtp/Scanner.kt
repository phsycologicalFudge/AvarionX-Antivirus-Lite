package com.colourswift.avarionxlite.engine.rtp

import android.content.Context
import android.os.SystemClock
import com.colourswift.avarionxlite.R
import com.colourswift.avarionxlite.engine.Engine
import com.colourswift.avarionxlite.engine.definitions.Defs
import com.colourswift.avarionxlite.home_ui.quarantine.QuarantineStore
import com.colourswift.avarionxlite.notifications.Alerts
import java.io.File
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import org.json.JSONObject

class Scanner(private val context: Context) {

    private class Target(val path: String, val packageName: String?)

    private val store = QuarantineStore(context)
    private val seenFile = File(context.filesDir, "rt_seen.json")
    private val seen = LinkedHashMap<String, Long>()
    private val pending = ConcurrentLinkedQueue<Target>()
    private val queue = ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, LinkedBlockingQueue(), ThreadPoolExecutor.DiscardPolicy())
    private var lastSetup = 0L

    fun start() {
        submit {
            Defs.sync(context)
            load()
            ready()
        }
    }

    fun file(path: String) {
        enqueue(Target(path, null))
    }

    fun app(packageName: String, apkPath: String) {
        enqueue(Target(apkPath, packageName))
    }

    fun stop() {
        queue.shutdownNow()
    }

    private fun submit(task: () -> Unit) {
        queue.execute { runCatching(task) }
    }

    private fun enqueue(target: Target) {
        pending.add(target)
        submit(::drain)
    }

    private fun drain() {
        val batch = generateSequence { pending.poll() }.take(MAX_BATCH).filter(::eligible).distinctBy { it.path }.toList()
        if (batch.isEmpty() || !ready()) return
        val files = batch.filter { it.packageName == null }.associate { it.path to File(it.path).lastModified() }
        Engine.scan(context, batch.map { it.path }) { path -> batch.first { it.path == path }.let(::detected) }
        remember(files)
    }

    private fun eligible(target: Target): Boolean {
        val file = File(target.path)
        val size = file.length()
        if (size <= 0 || size > MAX_SIZE) return false
        if (target.packageName != null) return target.packageName != context.packageName
        if (file.name.startsWith(".pending-") || file.extension.lowercase(Locale.ROOT) !in ALLOWED) return false
        val previous = seen[target.path]
        return previous == null || file.lastModified() > previous
    }

    private fun detected(target: Target) {
        val packageName = target.packageName
        if (packageName == null) {
            val file = File(target.path)
            val moved = store.quarantineFile(file) != null
            Alerts.threat(context, context.getString(if (moved) R.string.alert_file_ok else R.string.alert_file_failed, file.name))
            return
        }
        val label = runCatching {
            context.packageManager.getApplicationInfo(packageName, 0).loadLabel(context.packageManager).toString()
        }.getOrDefault(packageName)
        val flagged = store.quarantineApp(packageName, label, target.path) != null
        Alerts.threat(context, context.getString(if (flagged) R.string.alert_app_ok else R.string.alert_app_failed, label))
    }

    private fun ready(): Boolean {
        if (Engine.isReady()) return true
        val now = SystemClock.elapsedRealtime()
        if (now - lastSetup < RETRY_MS) return false
        lastSetup = now
        return when (Engine.prepare(context)) {
            Engine.Phase.READY -> true
            Engine.Phase.OFFLINE -> false
            Engine.Phase.UNSUPPORTED -> {
                Alerts.unsupported(context)
                false
            }
        }
    }

    private fun load() {
        runCatching {
            val json = JSONObject(seenFile.readText())
            json.keys().forEach { seen[it] = json.getLong(it) }
        }
    }

    private fun remember(files: Map<String, Long>) {
        if (files.isEmpty()) return
        seen.putAll(files)
        while (seen.size > MAX_SEEN) seen.remove(seen.keys.first())
        runCatching {
            val tmp = File(seenFile.path + ".tmp")
            tmp.writeText(JSONObject(seen).toString())
            tmp.renameTo(seenFile)
        }
    }

    private companion object {
        val ALLOWED = setOf("com", "apk", "zip", "rar", "7z", "pdf", "txt", "md", "json", "exe")
        const val MAX_SIZE = 100L * 1024 * 1024
        const val MAX_SEEN = 1024
        const val RETRY_MS = 60_000L
        const val MAX_BATCH = 32
    }
}
