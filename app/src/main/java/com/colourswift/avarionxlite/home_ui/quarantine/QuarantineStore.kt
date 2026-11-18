package com.colourswift.avarionxlite.home_ui.quarantine

import android.content.Context
import android.content.pm.PackageManager
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.UUID
import org.json.JSONObject

class QuarantineEntry(
    val id: String,
    val isApp: Boolean,
    val name: String,
    val originalPath: String,
    val packageName: String,
    val size: Long,
    val time: Long
)

class QuarantineStore(context: Context) {

    private val packageManager = context.packageManager
    private val dir = File(context.filesDir, "quarantine").apply { mkdirs() }

    fun list(): List<QuarantineEntry> =
        dir.listFiles { f -> f.extension == META_EXT }.orEmpty()
            .mapNotNull { readMeta(it) }
            .filter { !it.isApp || isInstalled(it.packageName) || discard(it.id) }
            .sortedByDescending { it.time }

    fun quarantineFile(source: File): QuarantineEntry? = runCatching {
        val id = UUID.randomUUID().toString()
        val target = dataFile(id)
        val tmp = File(dir, "$id.tmp")
        FileOutputStream(tmp).use { out ->
            out.write(MAGIC)
            FileInputStream(source).use { it.copyTo(out) }
            out.fd.sync()
        }
        check(tmp.renameTo(target))
        val entry = QuarantineEntry(id, false, source.name, source.absolutePath, "", target.length() - MAGIC.size, System.currentTimeMillis())
        writeMeta(entry)
        source.delete()
        entry
    }.getOrNull()

    fun quarantineApp(packageName: String, appName: String, apkPath: String): QuarantineEntry? = runCatching {
        val id = "app_$packageName"
        val entry = QuarantineEntry(id, true, appName, apkPath, packageName, 0L, System.currentTimeMillis())
        if (!metaFile(id).exists()) writeMeta(entry)
        entry
    }.getOrNull()

    fun restore(id: String): Boolean = runCatching {
        val meta = readMeta(metaFile(id)) ?: return false
        val data = dataFile(id)
        FileInputStream(data).use { input ->
            val header = ByteArray(MAGIC.size)
            check(input.read(header) == MAGIC.size && header.contentEquals(MAGIC))
            val original = File(meta.originalPath)
            original.parentFile?.mkdirs()
            val target = if (original.exists()) {
                File(original.parentFile, "${original.nameWithoutExtension}_restored${if (original.extension.isEmpty()) "" else "." + original.extension}")
            } else {
                original
            }
            val tmp = File(target.path + ".tmp")
            FileOutputStream(tmp).use { out ->
                input.copyTo(out)
                out.fd.sync()
            }
            check(tmp.renameTo(target))
        }
        data.delete()
        metaFile(id).delete()
        true
    }.getOrDefault(false)

    fun delete(id: String) {
        dataFile(id).delete()
        metaFile(id).delete()
    }

    private fun isInstalled(packageName: String): Boolean = try {
        packageManager.getApplicationInfo(packageName, 0)
        true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }

    private fun discard(id: String): Boolean {
        metaFile(id).delete()
        return false
    }

    private fun metaFile(id: String) = File(dir, "$id.$META_EXT")

    private fun dataFile(id: String) = File(dir, "$id.$DATA_EXT")

    private fun readMeta(file: File): QuarantineEntry? = runCatching {
        val json = JSONObject(file.readText())
        QuarantineEntry(
            id = json.getString("id"),
            isApp = json.optBoolean("app", false),
            name = json.optString("name"),
            originalPath = json.optString("originalPath"),
            packageName = json.optString("packageName"),
            size = json.optLong("size"),
            time = json.optLong("time")
        )
    }.getOrNull()

    private fun writeMeta(entry: QuarantineEntry) {
        val json = JSONObject()
            .put("id", entry.id)
            .put("app", entry.isApp)
            .put("name", entry.name)
            .put("originalPath", entry.originalPath)
            .put("packageName", entry.packageName)
            .put("size", entry.size)
            .put("time", entry.time)
        val tmp = File(dir, "${entry.id}.$META_EXT.tmp")
        tmp.writeText(json.toString())
        check(tmp.renameTo(metaFile(entry.id)))
    }

    private companion object {
        const val META_EXT = "qmeta"
        const val DATA_EXT = "vqsafe"
        val MAGIC = byteArrayOf(0x51, 0x53, 0x56, 0x31)
    }
}
