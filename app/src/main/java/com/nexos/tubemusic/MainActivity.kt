// TubeMusic — Copyright (C) 2026 David — GPL-3.0-only (ver LICENSE).
package com.nexos.tubemusic

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.StreamInfoItem

class MainActivity : Activity() {

    private lateinit var queryEdit: EditText
    private lateinit var searchButton: Button
    private lateinit var resultsList: ListView
    private lateinit var progressBar: ProgressBar
    private lateinit var statusText: TextView

    private val items = mutableListOf<StreamInfoItem>()
    private lateinit var adapter: ArrayAdapter<String>
    private var pipeInit = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        queryEdit = findViewById(R.id.query)
        searchButton = findViewById(R.id.search)
        resultsList = findViewById(R.id.results)
        progressBar = findViewById(R.id.progress)
        statusText = findViewById(R.id.status)

        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, mutableListOf<String>())
        resultsList.adapter = adapter

        searchButton.setOnClickListener { doSearch() }
        resultsList.setOnItemClickListener { _, _, pos, _ ->
            if (pos in items.indices) showFormats(items[pos])
        }

        if (Build.VERSION.SDK_INT < 29 &&
            checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), 4001)
        }
    }

    @Synchronized
    private fun ensurePipe() {
        if (!pipeInit) {
            NewPipe.init(TubeDownloader())
            pipeInit = true
        }
    }

    private fun setBusy(busy: Boolean) {
        searchButton.isEnabled = !busy
    }

    private fun doSearch() {
        val q = queryEdit.text.toString().trim()
        if (q.isEmpty()) {
            Toast.makeText(this, "Escribe algo para buscar", Toast.LENGTH_SHORT).show()
            return
        }
        setBusy(true)
        statusText.text = "Buscando..."
        Thread {
            try {
                ensurePipe()
                val extractor = ServiceList.YouTube.getSearchExtractor(q)
                extractor.fetchPage()
                val found = extractor.initialPage.items.filterIsInstance<StreamInfoItem>()
                runOnUiThread {
                    items.clear()
                    items.addAll(found)
                    refreshAdapter()
                    statusText.text = if (found.isEmpty()) {
                        "Sin resultados."
                    } else {
                        "${found.size} resultados. Toca uno para descargar el audio."
                    }
                }
            } catch (e: Exception) {
                runOnUiThread { statusText.text = "Error: ${e.message}" }
            } finally {
                runOnUiThread { setBusy(false) }
            }
        }.start()
    }

    private fun refreshAdapter() {
        adapter.clear()
        adapter.addAll(items.map { "${it.name}\n${it.uploaderName} • ${fmtDur(it.duration)}" })
        adapter.notifyDataSetChanged()
    }

    private fun fmtDur(seconds: Long): String {
        if (seconds < 0) return "?"
        return "%d:%02d".format(seconds / 60, seconds % 60)
    }

    private fun showFormats(item: StreamInfoItem) {
        setBusy(true)
        statusText.text = "Obteniendo audio de: ${item.name}"
        Thread {
            try {
                ensurePipe()
                val extractor = ServiceList.YouTube.getStreamExtractor(item.url)
                extractor.fetchPage()
                val audios = extractor.audioStreams
                    .sortedByDescending { it.averageBitrate }
                    .take(6)
                if (audios.isEmpty()) throw Exception("Sin streams de audio")
                runOnUiThread { pickFormatDialog(item.name, audios) }
            } catch (e: Exception) {
                runOnUiThread { statusText.text = "Error: ${e.message}" }
            } finally {
                runOnUiThread { setBusy(false) }
            }
        }.start()
    }

    private fun pickFormatDialog(title: String, audios: List<AudioStream>) {
        val labels = audios.map {
            "${it.format.name} • ${it.averageBitrate / 1000} kbps"
        }.toTypedArray()
        var elegido = 0
        AlertDialog.Builder(this)
            .setTitle("Descargar audio")
            .setSingleChoiceItems(labels, 0) { _, which -> elegido = which }
            .setPositiveButton("Descargar") { _, _ -> doDownload(title, audios[elegido]) }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun doDownload(title: String, audio: AudioStream) {
        setBusy(true)
        progressBar.progress = 0
        statusText.text = "Descargando: $title"
        Thread {
            try {
                val ruta = MusicDownload.download(
                    this, audio.url, title, audio.format.suffix
                ) { p -> runOnUiThread { progressBar.progress = p } }
                runOnUiThread {
                    statusText.text = "Guardado en $ruta"
                    Toast.makeText(this, "Descarga completa", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                runOnUiThread { statusText.text = "Error: ${e.message}" }
            } finally {
                runOnUiThread { setBusy(false) }
            }
        }.start()
    }
}
