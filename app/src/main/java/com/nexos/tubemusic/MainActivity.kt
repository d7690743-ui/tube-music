// TubeMusic — Copyright (C) 2026 David — GPL-3.0-only (ver LICENSE).
package com.nexos.tubemusic

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.ContentUris
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.io.File

/** Filas con caratula, titulo, subtitulo y boton opcional de descarga. */
class TrackRowAdapter(
    private val ctx: Activity,
    private val rows: MutableList<Row>,
    private val onTap: (Int) -> Unit,
    private val onDownload: ((Int) -> Unit)?,
) : BaseAdapter() {
    data class Row(val title: String, val sub: String, val thumb: String?, val showDl: Boolean)

    override fun getCount() = rows.size
    override fun getItem(p: Int): Any = rows[p]
    override fun getItemId(p: Int) = p.toLong()

    override fun getView(pos: Int, convert: View?, parent: ViewGroup): View {
        val v = convert ?: ctx.layoutInflater.inflate(R.layout.row_track, parent, false)
        val r = rows[pos]
        v.findViewById<TextView>(R.id.row_title).text = r.title
        v.findViewById<TextView>(R.id.row_sub).text = r.sub
        ThumbLoader.load(r.thumb, v.findViewById(R.id.row_thumb))
        val dl = v.findViewById<ImageButton>(R.id.row_dl)
        dl.visibility = if (r.showDl) View.VISIBLE else View.GONE
        dl.setOnClickListener { onDownload?.invoke(pos) }
        v.setOnClickListener { onTap(pos) }
        return v
    }

    fun refresh(newRows: List<Row>) {
        rows.clear()
        rows.addAll(newRows)
        notifyDataSetChanged()
    }
}

class MainActivity : Activity() {

    private lateinit var queryEdit: EditText
    private lateinit var searchButton: Button
    private lateinit var tabSearch: Button
    private lateinit var tabLibrary: Button
    private lateinit var panelSearch: LinearLayout
    private lateinit var panelLibrary: LinearLayout
    private lateinit var resultsList: ListView
    private lateinit var libraryList: ListView
    private lateinit var searchLoading: ProgressBar
    private lateinit var statusText: TextView
    private lateinit var mini: LinearLayout
    private lateinit var miniThumb: ImageView
    private lateinit var miniTitle: TextView
    private lateinit var miniArtist: TextView
    private lateinit var miniPlay: ImageButton

    private val items = mutableListOf<StreamInfoItem>()
    private lateinit var resultsAdapter: TrackRowAdapter
    private val libTracks = mutableListOf<Track>()
    private lateinit var libAdapter: TrackRowAdapter
    private var onLibrary = false
    private var alive = true
    private var lastMiniVersion = -1L
    private val ui = Handler(Looper.getMainLooper())
    private val miniLoop = object : Runnable {
        override fun run() {
            if (!alive) return
            refreshMini()
            ui.postDelayed(this, 500)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        queryEdit = findViewById(R.id.query)
        searchButton = findViewById(R.id.search)
        tabSearch = findViewById(R.id.tab_search)
        tabLibrary = findViewById(R.id.tab_library)
        panelSearch = findViewById(R.id.panel_search)
        panelLibrary = findViewById(R.id.panel_library)
        resultsList = findViewById(R.id.results)
        libraryList = findViewById(R.id.library)
        searchLoading = findViewById(R.id.search_loading)
        statusText = findViewById(R.id.status)
        mini = findViewById(R.id.mini)
        miniThumb = findViewById(R.id.mini_thumb)
        miniTitle = findViewById(R.id.mini_title)
        miniArtist = findViewById(R.id.mini_artist)
        miniPlay = findViewById(R.id.mini_play)

        resultsAdapter = TrackRowAdapter(
            this, mutableListOf(),
            onTap = { pos -> playSearchList(pos) },
            onDownload = { pos -> if (pos in items.indices) showFormats(items[pos]) },
        )
        resultsList.adapter = resultsAdapter
        libAdapter = TrackRowAdapter(
            this, mutableListOf(),
            onTap = { pos -> playLibrary(pos) },
            onDownload = null,
        )
        libraryList.adapter = libAdapter

        searchButton.setOnClickListener { doSearch() }
        tabSearch.setOnClickListener { setTab(false) }
        tabLibrary.setOnClickListener { setTab(true) }
        mini.setOnClickListener {
            startActivity(Intent(this, PlayerActivity::class.java))
        }
        miniPlay.setOnClickListener {
            PlaybackService.cmd(this, PlaybackService.ACTION_TOGGLE)
        }

        if (Build.VERSION.SDK_INT < 29 &&
            checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), 4001)
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 4002)
        }

        setTab(false)
        ui.post(miniLoop)
    }

    override fun onDestroy() {
        alive = false
        ui.removeCallbacks(miniLoop)
        super.onDestroy()
    }

    private fun setTab(lib: Boolean) {
        onLibrary = lib
        panelSearch.visibility = if (lib) View.GONE else View.VISIBLE
        panelLibrary.visibility = if (lib) View.VISIBLE else View.GONE
        val green = getColor(R.color.green)
        val dark = getColor(R.color.surface2)
        val black = getColor(android.R.color.black)
        val white = getColor(R.color.white)
        tabSearch.setBackgroundColor(if (lib) dark else green)
        tabSearch.setTextColor(if (lib) white else black)
        tabLibrary.setBackgroundColor(if (lib) green else dark)
        tabLibrary.setTextColor(if (lib) black else white)
        if (lib) loadLibrary()
    }

    // ---- busqueda ----

    private fun doSearch() {
        val q = queryEdit.text.toString().trim()
        if (q.length < 2) {
            Toast.makeText(this, "Escribe al menos 2 letras", Toast.LENGTH_SHORT).show()
            return
        }
        searchButton.isEnabled = false
        searchLoading.visibility = View.VISIBLE
        statusText.text = "Buscando..."
        Thread {
            try {
                TubeSession.ensure()
                val extractor = ServiceList.YouTube.getSearchExtractor(q)
                extractor.fetchPage()
                val found = extractor.initialPage.items.filterIsInstance<StreamInfoItem>()
                runOnUiThread {
                    items.clear()
                    items.addAll(found)
                    resultsAdapter.refresh(found.map {
                        TrackRowAdapter.Row(
                            title = it.name ?: "Sin título",
                            sub = "${it.uploaderName ?: "?"} • ${fmtDurSec(it.duration)}",
                            thumb = it.thumbnails.firstOrNull()?.url,
                            showDl = true,
                        )
                    })
                    statusText.text = if (found.isEmpty()) {
                        "Sin resultados."
                    } else {
                        "${found.size} resultados. Toca para reproducir, ⬇ para descargar."
                    }
                }
            } catch (e: Exception) {
                runOnUiThread { statusText.text = "Error: ${e.message}" }
            } finally {
                runOnUiThread {
                    searchButton.isEnabled = true
                    searchLoading.visibility = View.GONE
                }
            }
        }.start()
    }

    private fun playSearchList(pos: Int) {
        if (pos !in items.indices) return
        val list = ArrayList(items.map {
            Track(
                title = it.name ?: "Sin título",
                artist = it.uploaderName ?: "?",
                thumbUrl = it.thumbnails.firstOrNull()?.url,
                pageUrl = it.url,
            )
        })
        PlaybackService.cmd(this, PlaybackService.ACTION_PLAY_LIST) {
            putExtra(PlaybackService.EXTRA_TRACKS, list)
            putExtra(PlaybackService.EXTRA_INDEX, pos)
        }
        startActivity(Intent(this, PlayerActivity::class.java))
    }

    // ---- biblioteca ----

    private fun loadLibrary() {
        statusText.text = "Cargando biblioteca..."
        Thread {
            val list = mutableListOf<Track>()
            try {
                if (Build.VERSION.SDK_INT >= 29) {
                    val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                    val proj = arrayOf(
                        MediaStore.Audio.Media._ID,
                        MediaStore.Audio.Media.TITLE,
                        MediaStore.Audio.Media.ARTIST,
                        MediaStore.Audio.Media.DURATION,
                    )
                    contentResolver.query(
                        uri, proj,
                        MediaStore.Audio.Media.RELATIVE_PATH + " LIKE ?",
                        arrayOf("%TubeMusic%"),
                        MediaStore.Audio.Media.DATE_ADDED + " DESC"
                    )?.use { c ->
                        val idI = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                        val tI = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                        val aI = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                        val dI = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                        while (c.moveToNext()) {
                            list.add(
                                Track(
                                    title = c.getString(tI) ?: "Sin título",
                                    artist = c.getString(aI) ?: "?",
                                    local = ContentUris.withAppendedId(
                                        uri, c.getLong(idI)
                                    ).toString(),
                                )
                            )
                        }
                    }
                } else {
                    @Suppress("DEPRECATION")
                    val dir = File(
                        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
                        "TubeMusic"
                    )
                    dir.listFiles()
                        ?.filter {
                            it.isFile && it.extension.lowercase() in
                                setOf("m4a", "mp3", "webm", "opus", "ogg", "mp4")
                        }
                        ?.sortedByDescending { it.lastModified() }
                        ?.forEach {
                            list.add(
                                Track(
                                    title = it.nameWithoutExtension,
                                    artist = "Descargado",
                                    local = it.absolutePath
                                )
                            )
                        }
                }
                runOnUiThread {
                    libTracks.clear()
                    libTracks.addAll(list)
                    libAdapter.refresh(list.map {
                        TrackRowAdapter.Row(it.title, it.artist, null, false)
                    })
                    statusText.text = if (list.isEmpty()) {
                        "Biblioteca vacía. Descarga algo con ⬇."
                    } else {
                        "${list.size} descargadas."
                    }
                }
            } catch (e: Exception) {
                runOnUiThread { statusText.text = "Error: ${e.message}" }
            }
        }.start()
    }

    private fun playLibrary(pos: Int) {
        if (pos !in libTracks.indices) return
        PlaybackService.cmd(this, PlaybackService.ACTION_PLAY_LIST) {
            putExtra(PlaybackService.EXTRA_TRACKS, ArrayList(libTracks))
            putExtra(PlaybackService.EXTRA_INDEX, pos)
        }
        startActivity(Intent(this, PlayerActivity::class.java))
    }

    // ---- mini reproductor ----

    private fun refreshMini() {
        if (!PlayerBus.hasTrack) {
            mini.visibility = View.GONE
            return
        }
        mini.visibility = View.VISIBLE
        miniTitle.text = PlayerBus.title
        miniArtist.text = PlayerBus.artist
        if (PlayerBus.version != lastMiniVersion) {
            lastMiniVersion = PlayerBus.version
            ThumbLoader.load(PlayerBus.thumbUrl, miniThumb)
        }
        miniPlay.setImageResource(
            if (PlayerBus.playing) android.R.drawable.ic_media_pause
            else android.R.drawable.ic_media_play
        )
    }

    // ---- descarga ----

    private fun showFormats(item: StreamInfoItem) {
        searchButton.isEnabled = false
        statusText.text = "Obteniendo audio de: ${item.name}"
        Thread {
            try {
                TubeSession.ensure()
                val extractor = ServiceList.YouTube.getStreamExtractor(item.url)
                extractor.fetchPage()
                val audios = extractor.audioStreams
                    .filter { it.isUrl && it.url != null }
                    .sortedByDescending { it.averageBitrate }
                    .take(6)
                if (audios.isEmpty()) throw Exception("Sin streams de audio")
                val title = item.name ?: "audio"
                runOnUiThread { pickFormatDialog(title, audios) }
            } catch (e: Exception) {
                runOnUiThread { statusText.text = "Error: ${e.message}" }
            } finally {
                runOnUiThread { searchButton.isEnabled = true }
            }
        }.start()
    }

    private fun pickFormatDialog(title: String, audios: List<AudioStream>) {
        val labels = audios.map {
            "${it.format?.name ?: "?"} • ${it.averageBitrate / 1000} kbps"
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
        searchButton.isEnabled = false
        statusText.text = "Descargando: $title"
        Thread {
            try {
                val streamUrl = audio.url ?: throw Exception("Stream sin URL directa")
                val ruta = MusicDownload.download(
                    this, streamUrl, title, audio.format?.suffix ?: "m4a"
                ) { }
                runOnUiThread {
                    statusText.text = "Guardado en $ruta"
                    Toast.makeText(this, "Descarga completa", Toast.LENGTH_SHORT).show()
                    if (onLibrary) loadLibrary()
                }
            } catch (e: Exception) {
                runOnUiThread { statusText.text = "Error: ${e.message}" }
            } finally {
                runOnUiThread { searchButton.isEnabled = true }
            }
        }.start()
    }
}
