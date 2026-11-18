package com.colourswift.avarionxlite.engine

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.colourswift.avarionxlite.engine.definitions.Defs
import java.io.File
import org.json.JSONObject

object Engine {

    enum class Phase { READY, OFFLINE, UNSUPPORTED }

    private const val STATE_READY = 2
    private const val SETUP_OK = 0
    private const val SETUP_OFFLINE = 1
    private const val CLOUD_TIMEOUT_MS = 20_000

    private val scanLock = Any()
    @Volatile private var initialised = false

    init {
        System.loadLibrary("colourswift_av")
        System.loadLibrary("avlite_jni")
    }

    @JvmStatic private external fun nativeReady(): Int
    @JvmStatic private external fun nativePrep(defs: ByteArray): Int
    @JvmStatic private external fun nativeInitialise(defs: ByteArray): Int
    @JvmStatic private external fun nativeReload(defs: ByteArray): Int
    @JvmStatic private external fun nativeSetDataDir(path: ByteArray)
    @JvmStatic private external fun nativeSetCloud(enabled: Boolean)
    @JvmStatic private external fun nativeCloudBegin(): Int
    @JvmStatic private external fun nativeCloudFinish(timeoutMs: Int): ByteArray?
    @JvmStatic private external fun nativeScan(path: ByteArray): ByteArray?
    @JvmStatic private external fun nativeSetScanLimits(maxConcurrent: Int, maxThreads: Int)

    fun isReady(): Boolean = initialised && nativeReady() == STATE_READY

    @Synchronized
    fun prepare(context: Context): Phase {
        val defs = Defs.ensure(context) ?: return Phase.OFFLINE
        if (!initialised) {
            nativeSetDataDir(bytes(context.filesDir.path))
            nativeSetCloud(true)
            nativeSetScanLimits(2, 0)
            initialised = nativeInitialise(bytes(defs.path)) == 0
        }
        if (isReady()) return Phase.READY
        return when (nativePrep(bytes(defs.path))) {
            SETUP_OK -> Phase.READY
            SETUP_OFFLINE -> Phase.OFFLINE
            else -> Phase.UNSUPPORTED
        }
    }

    fun reload(defs: File) {
        synchronized(scanLock) { nativeReload(bytes(defs.path)) }
    }

    fun scan(context: Context, paths: List<String>, onHit: (String) -> Unit) {
        synchronized(scanLock) {
            val cloud = online(context) && nativeCloudBegin() == 0
            val clean = paths.filterNot { path -> hasHits(nativeScan(bytes(path))).also { hit -> if (hit) onHit(path) } }
            if (cloud) {
                cloudHits(nativeCloudFinish(if (clean.isEmpty()) 0 else CLOUD_TIMEOUT_MS)).filter { it in clean }.forEach(onHit)
            }
        }
    }

    private fun bytes(text: String): ByteArray = text.toByteArray(Charsets.UTF_8)

    private fun online(context: Context): Boolean = runCatching {
        val manager = context.getSystemService(ConnectivityManager::class.java)
        manager.getNetworkCapabilities(manager.activeNetwork)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
    }.getOrDefault(false)

    private fun hasHits(raw: ByteArray?): Boolean {
        if (raw == null) return false
        return runCatching {
            (JSONObject(String(raw, Charsets.UTF_8)).optJSONObject("hits")?.length() ?: 0) > 0
        }.getOrDefault(false)
    }

    private fun cloudHits(raw: ByteArray?): List<String> {
        if (raw == null) return emptyList()
        return runCatching {
            val results = JSONObject(String(raw, Charsets.UTF_8)).optJSONArray("results")
            (0 until (results?.length() ?: 0)).mapNotNull { results?.optJSONObject(it)?.optString("path") }
        }.getOrDefault(emptyList())
    }
}
