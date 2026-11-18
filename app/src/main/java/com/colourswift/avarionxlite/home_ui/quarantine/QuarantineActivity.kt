package com.colourswift.avarionxlite.home_ui.quarantine

import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.text.format.Formatter
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.ListView
import android.widget.TextView
import com.colourswift.avarionxlite.R
import com.colourswift.avarionxlite.themes.applyEdgeToEdge
import java.text.DateFormat
import java.util.Date
import java.util.concurrent.Executors

class QuarantineActivity : Activity() {

    private sealed interface Row {
        class Header(val label: String) : Row
        class AppItem(val entry: QuarantineEntry) : Row
        class FileItem(val entry: QuarantineEntry) : Row
    }

    private val worker = Executors.newSingleThreadExecutor()
    private val selected = HashSet<String>()
    private val dateFormat = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
    private val adapter = RowAdapter()
    private lateinit var store: QuarantineStore
    private lateinit var list: ListView
    private lateinit var empty: View
    private lateinit var actions: View
    private lateinit var selectAll: View
    private lateinit var restore: Button
    private lateinit var delete: Button
    private var rows = emptyList<Row>()
    private var fileIds = emptyList<String>()
    private var icons = emptyMap<String, Drawable?>()
    private var working = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_quarantine)
        applyEdgeToEdge(findViewById(R.id.root), findViewById(R.id.content))
        store = QuarantineStore(this)
        list = findViewById(R.id.list)
        empty = findViewById(R.id.empty)
        actions = findViewById(R.id.actions)
        selectAll = findViewById(R.id.selectAll)
        restore = findViewById(R.id.restore)
        delete = findViewById(R.id.delete)
        list.adapter = adapter
        findViewById<View>(R.id.back).setOnClickListener { finish() }
        selectAll.setOnClickListener {
            if (selected.containsAll(fileIds)) selected.clear() else selected.addAll(fileIds)
            refreshList()
        }
        list.setOnItemClickListener { _, _, position, _ ->
            val row = rows[position]
            if (row is Row.FileItem && !selected.remove(row.entry.id)) selected.add(row.entry.id)
            refreshList()
        }
        restore.setOnClickListener { runOnSelected { id -> store.restore(id) } }
        delete.setOnClickListener { confirmDelete() }
    }

    override fun onResume() {
        super.onResume()
        load()
    }

    override fun onDestroy() {
        worker.shutdown()
        super.onDestroy()
    }

    private fun load() {
        worker.execute {
            val data = store.list()
            val loaded = data.filter { it.isApp }.associate { it.packageName to runCatching { packageManager.getApplicationIcon(it.packageName) }.getOrNull() }
            runOnUiThread { if (!isDestroyed) show(data, loaded) }
        }
    }

    private fun show(data: List<QuarantineEntry>, loaded: Map<String, Drawable?>) {
        icons = loaded
        val apps = data.filter { it.isApp }
        val files = data.filterNot { it.isApp }
        fileIds = files.map { it.id }
        selected.retainAll(fileIds.toSet())
        val next = ArrayList<Row>()
        if (apps.isNotEmpty()) {
            next.add(Row.Header(getString(R.string.apps)))
            apps.forEach { next.add(Row.AppItem(it)) }
        }
        if (files.isNotEmpty()) {
            next.add(Row.Header(getString(R.string.files)))
            files.forEach { next.add(Row.FileItem(it)) }
        }
        rows = next
        empty.visibility = if (next.isEmpty()) View.VISIBLE else View.GONE
        actions.visibility = if (files.isEmpty()) View.GONE else View.VISIBLE
        selectAll.visibility = actions.visibility
        refreshList()
    }

    private fun refreshList() {
        adapter.notifyDataSetChanged()
        val ready = selected.isNotEmpty() && !working
        restore.isEnabled = ready
        delete.isEnabled = ready
        restore.alpha = if (ready) 1f else 0.4f
        delete.alpha = if (ready) 1f else 0.4f
    }

    private fun runOnSelected(action: (String) -> Unit) {
        val ids = selected.toList()
        working = true
        refreshList()
        worker.execute {
            ids.forEach(action)
            runOnUiThread {
                working = false
                if (!isDestroyed) load()
            }
        }
    }

    private fun confirmDelete() {
        AlertDialog.Builder(this)
            .setTitle(R.string.delete_title)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.delete) { _, _ -> runOnSelected { id -> store.delete(id) } }
            .show()
    }

    private fun confirmUninstall(entry: QuarantineEntry) {
        AlertDialog.Builder(this)
            .setTitle(R.string.uninstall_title)
            .setMessage(getString(R.string.uninstall_body, entry.name.ifEmpty { entry.packageName }))
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.uninstall) { _, _ ->
                try {
                    startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:${entry.packageName}")))
                } catch (e: ActivityNotFoundException) {
                }
            }
            .show()
    }

    private fun bindFile(view: View, entry: QuarantineEntry) {
        view.findViewById<CheckBox>(R.id.check).isChecked = entry.id in selected
        view.findViewById<TextView>(R.id.name).text = entry.name.ifEmpty { getString(R.string.unknown_file) }
        view.findViewById<TextView>(R.id.path).apply {
            text = entry.originalPath
            visibility = if (entry.originalPath.isEmpty()) View.GONE else View.VISIBLE
        }
        view.findViewById<TextView>(R.id.meta).text =
            "${Formatter.formatShortFileSize(this, entry.size)} • ${dateFormat.format(Date(entry.time))}"
    }

    private fun bindApp(view: View, entry: QuarantineEntry) {
        view.findViewById<ImageView>(R.id.icon).setImageDrawable(icons[entry.packageName] ?: getDrawable(R.drawable.ic_apps))
        view.findViewById<TextView>(R.id.name).text = entry.name.ifEmpty { getString(R.string.unknown_app) }
        view.findViewById<TextView>(R.id.path).text = entry.packageName
        view.findViewById<TextView>(R.id.meta).text = dateFormat.format(Date(entry.time))
        view.findViewById<View>(R.id.uninstall).setOnClickListener { confirmUninstall(entry) }
    }

    private inner class RowAdapter : BaseAdapter() {

        override fun getCount() = rows.size

        override fun getItem(position: Int) = rows[position]

        override fun getItemId(position: Int) = position.toLong()

        override fun getViewTypeCount() = 3

        override fun getItemViewType(position: Int) = when (rows[position]) {
            is Row.Header -> 0
            is Row.AppItem -> 1
            is Row.FileItem -> 2
        }

        override fun isEnabled(position: Int) = rows[position] is Row.FileItem

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val row = rows[position]
            val view = convertView ?: layoutInflater.inflate(
                when (row) {
                    is Row.Header -> R.layout.item_header
                    is Row.AppItem -> R.layout.item_app
                    is Row.FileItem -> R.layout.item_file
                },
                parent,
                false
            )
            when (row) {
                is Row.Header -> (view as TextView).text = row.label
                is Row.AppItem -> bindApp(view, row.entry)
                is Row.FileItem -> bindFile(view, row.entry)
            }
            return view
        }
    }
}
