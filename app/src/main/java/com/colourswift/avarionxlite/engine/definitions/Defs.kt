package com.colourswift.avarionxlite.engine.definitions

import android.content.Context
import com.colourswift.avarionxlite.engine.Engine
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import org.json.JSONObject

object Defs {

    private const val BASE = "https://github.com/phsycologicalfudge/AVDatabase/releases/latest/download/"
    private const val PREFS = "lite"
    private const val VERSION_KEY = "defsVersion"
    private const val EMPTY_VERSION = "0.0.0"

    private val executor = Executors.newSingleThreadExecutor()

    fun file(context: Context): File = File(context.filesDir, "defs.cs")

    @Synchronized
    fun ensure(context: Context): File? {
        val target = file(context)
        if (target.exists()) return target
        val seeded = runCatching {
            context.assets.open("defs/defs.cs").use { write(target, it) }
        }.isSuccess
        return if (seeded || download(context)) target else null
    }

    fun syncAsync(context: Context) {
        val app = context.applicationContext
        executor.execute { runCatching { sync(app) } }
    }

    @Synchronized
    fun sync(context: Context) {
        val remote = remoteVersion() ?: return
        val target = file(context)
        if (target.exists() && !newer(remote, localVersion(context))) return
        if (!download(context)) return
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(VERSION_KEY, remote).apply()
        Engine.reload(target)
    }

    private fun localVersion(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(VERSION_KEY, EMPTY_VERSION) ?: EMPTY_VERSION

    private fun newer(remote: String, local: String): Boolean {
        val a = parts(remote)
        val b = parts(local)
        for (i in 0 until 3) {
            if (a[i] != b[i]) return a[i] > b[i]
        }
        return false
    }

    private fun parts(version: String): List<Int> {
        val numbers = version.split('.').map { it.toIntOrNull() ?: 0 }
        return List(3) { numbers.getOrElse(it) { 0 } }
    }

    private fun remoteVersion(): String? = runCatching {
        val connection = open("${BASE}version.json")
        try {
            check(connection.responseCode == 200)
            val text = connection.inputStream.bufferedReader().use { it.readText() }
            JSONObject(text).optString("version").ifEmpty { null }
        } finally {
            connection.disconnect()
        }
    }.getOrNull()

    private fun download(context: Context): Boolean = runCatching {
        val connection = open("${BASE}defs_lite.cs?t=${System.currentTimeMillis()}")
        try {
            check(connection.responseCode == 200)
            connection.inputStream.use { write(file(context), it) }
        } finally {
            connection.disconnect()
        }
        true
    }.getOrDefault(false)

    private fun open(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 30_000
            setRequestProperty("User-Agent", "AvarionXLite/1.0 (Android)")
        }

    private fun write(target: File, input: InputStream) {
        val tmp = File(target.path + ".tmp")
        FileOutputStream(tmp).use {
            input.copyTo(it)
            it.fd.sync()
        }
        check(tmp.length() > 0 && tmp.renameTo(target))
    }
}
