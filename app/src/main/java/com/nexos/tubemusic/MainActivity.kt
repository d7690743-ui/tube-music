// TubeMusic — Copyright (C) 2026 David — GPL-3.0-only (ver LICENSE).
package com.nexos.tubemusic

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.ContentUris
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
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
    private lateinit var tabDownloads: Button
    private lateinit var tabPlayer: Button
    private lateinit var panelSearch: LinearLayout
    private lateinit var panelLibrary: LinearLayout
    private lateinit var panelDownloads: LinearLayout
    private lateinit var resultsList: ListView
    private lateinit var libraryList: ListView
    private lateinit var downloadsList: ListView
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
    private lateinit var downloadsAdapter: TrackRowAdapter
    private var tab = TAB_SEARCH
    private var alive = true
    private var lastMiniVersion = -1L
    private val ui = Handler(Looper.getMainLooper())
    private val miniLoop = object : Runnable {
        override fun run() {
            if (!alive) return
            refreshMini()
            if (tab == TAB_DOWNLOADS) refreshDownloads()
            ui.postDelayed(this, 500)
        }
    }

    companion object {
        const val TAB_SEARCH = 0
        const val TAB_LIBRARY = 1
        const val TAB_DOWNLOADS = 2
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        queryEdit = findViewById(R.id.query)
        searchButton = findViewById(R.id.search)
        tabSearch = findViewById(R.id.tab_search)
        tabLibrary = findViewById(R.id.tab_library)
        tabDownloads = findViewById(R.id.tab_downloads)
        tabPlayer = findViewById(R.id.tab_player)
        panelSearch = findViewById(R.id.panel_search)
        panelLibrary = findViewById(R.id.panel_library)
        panelDownloads = findViewById(R.id.panel_downloads)
        resultsList = findViewById(R.id.results)
        libraryList = findViewById(R.id.library)
        downloadsList = findViewById(R.id.downloads)
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
        libraryList.onItemLongClickListener =
            AdapterView.OnItemLongClickListener { _, _, pos, _ ->
                showLibraryMenu(pos)
                true
            }
        downloadsAdapter = TrackRowAdapter(this, mutableListOf(), onTap = {}, onDownload = null)
        downloadsList.adapter = downloadsAdapter

        searchButton.setOnClickListener { doSearch() }
        tabSearch.setOnClickListener { setTab(TAB_SEARCH) }
        tabLibrary.setOnClickListener { setTab(TAB_LIBRARY) }
        tabDownloads.setOnClickListener { setTab(TAB_DOWNLOADS) }
        tabPlayer.setOnClickListener {
            startActivity(Intent(this, PlayerActivity::class.java))
        }
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

        setTab(TAB_SEARCH)
        ui.post(miniLoop)
    }

    override fun onDestroy() {
        alive = false
        ui.removeCallbacks(miniLoop)
        super.onDestroy()
    }

    private fun setTab(t: Int) {
        tab = t
        panelSearch.visibility = if (t == TAB_SEARCH) View.VISIBLE else View.GONE
        panelLibrary.visibility = if (t == TAB_LIBRARY) View.VISIBLE else View.GONE
        panelDownloads.visibility = if (t == TAB_DOWNLOADS) View.VISIBLE else View.GONE
        paintTab(tabSearch, t == TAB_SEARCH)
        paintTab(tabLibrary, t == TAB_LIBRARY)
        paintTab(tabDownloads, t == TAB_DOWNLOADS)
        if (t == TAB_LIBRARY) loadLibrary()
        if (t == TAB_DOWNLOADS) refreshDownloads()
    }

    private fun paintTab(b: Button, selected: Boolean) {
        b.setBackgroundColor(getColor(if (selected) R.color.green else R.color.surface2))
        b.setTextColor(getColor(if (selected) android.R.color.black else R.color.white))
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
                        MediaStore.Audio.Media.DISPLAY_NAME,
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
                        val nI = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
                        while (c.moveToNext()) {
                            val display = c.getString(nI) ?: ""
                            list.add(
                                Track(
                                    title = c.getString(tI) ?: "Sin título",
                                    artist = c.getString(aI) ?: "?",
                                    local = ContentUris.withAppendedId(
                                        uri, c.getLong(idI)
                                    ).toString(),
                                    suffix = display.substringAfterLast('.', "").lowercase()
                                        .ifEmpty { null },
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
                                    local = it.absolutePath,
                                    suffix = it.extension.lowercase().ifEmpty { null },
                                )
                            )
                        }
                }
                runOnUiThread {
                    libTracks.clear()
                    libTracks.addAll(list)
                    libAdapter.refresh(list.map {
                        val sub = if (it.suffix != null) "${it.artist} • ${it.suffix}" else it.artist
                        TrackRowAdapter.Row(it.title, sub, null, false)
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
                runOnUiThread {
                    FormatPicker.show(
                        this, title, audios,
                        onPick = { chosen, needsConvert ->
                            if (needsConvert) {
                                Toast.makeText(this, "Descargo y convierto a MP3…", Toast.LENGTH_SHORT).show()
                                doDownloadMp3(title, item.uploaderName ?: "?", chosen)
                            } else {
                                doDownload(title, chosen)
                            }
                        },
                    )
                }
            } catch (e: Exception) {
                runOnUiThread { statusText.text = "Error: ${e.message}" }
            } finally {
                runOnUiThread { searchButton.isEnabled = true }
            }
        }.start()
    }

    private fun doDownload(title: String, audio: AudioStream) {
        searchButton.isEnabled = false
        statusText.text = "Descargando: $title"
        val jobId = DownloadStore.start(title)
        Thread {
            try {
                val streamUrl = audio.url ?: throw Exception("Stream sin URL directa")
                val ruta = MusicDownload.download(
                    this, streamUrl, title, audio.format?.suffix ?: "m4a"
                ) { p -> DownloadStore.progress(jobId, p) }
                DownloadStore.finish(jobId, ruta)
                runOnUiThread {
                    statusText.text = "Guardado en $ruta"
                    Toast.makeText(this, "Descarga completa", Toast.LENGTH_SHORT).show()
                    if (tab == TAB_LIBRARY) loadLibrary()
                    if (tab == TAB_DOWNLOADS) refreshDownloads()
                }
            } catch (e: Exception) {
                DownloadStore.fail(jobId, e.message ?: "error")
                runOnUiThread { statusText.text = "Error: ${e.message}" }
            } finally {
                runOnUiThread { searchButton.isEnabled = true }
            }
        }.start()
    }

    private fun doDownloadMp3(title: String, artist: String, audio: AudioStream) {
        searchButton.isEnabled = false
        statusText.text = "Descargando: $title"
        val jobId = DownloadStore.start(title)
        setTab(TAB_DOWNLOADS)
        Thread {
            try {
                val streamUrl = audio.url ?: throw Exception("Stream sin URL directa")
                val ruta = Mp3Convert.downloadAsMp3(this, streamUrl, title, artist, jobId)
                DownloadStore.finish(jobId, ruta)
                runOnUiThread {
                    statusText.text = "Guardado en $ruta"
                    Toast.makeText(this, "MP3 listo", Toast.LENGTH_SHORT).show()
                    refreshDownloads()
                }
            } catch (e: Exception) {
                DownloadStore.fail(jobId, e.message ?: "error")
                runOnUiThread { statusText.text = "Error: ${e.message}" }
            } finally {
                runOnUiThread { searchButton.isEnabled = true }
            }
        }.start()
    }

    // ---- menú biblioteca ----

    private fun showLibraryMenu(pos: Int) {
        if (pos !in libTracks.indices) return
        val t = libTracks[pos]
        val opts = mutableListOf("Reproducir", "Eliminar")
        if (t.suffix?.lowercase() != "mp3") opts.add(1, "Convertir a MP3")
        AlertDialog.Builder(this)
            .setTitle(t.title)
            .setItems(opts.toTypedArray()) { _, which ->
                when (opts[which]) {
                    "Reproducir" -> playLibrary(pos)
                    "Convertir a MP3" -> convertLibraryTrack(t)
                    "Eliminar" -> deleteTrack(pos)
                }
            }
            .show()
    }

    private fun convertLibraryTrack(t: Track) {
        val local = t.local ?: return
        val jobId = DownloadStore.start(t.title)
        DownloadStore.note(jobId, "convirtiendo a MP3…")
        statusText.text = "Convirtiendo: ${t.title}"
        setTab(TAB_DOWNLOADS)
        Thread {
            try {
                val ruta = Mp3Convert.convertLocal(this, local, t.title, t.artist)
                DownloadStore.finish(jobId, ruta)
                runOnUiThread {
                    statusText.text = "Guardado en $ruta"
                    Toast.makeText(this, "MP3 listo", Toast.LENGTH_SHORT).show()
                    refreshDownloads()
                }
            } catch (e: Exception) {
                DownloadStore.fail(jobId, e.message ?: "error")
                runOnUiThread { statusText.text = "Error: ${e.message}" }
            }
        }.start()
    }

    private fun deleteTrack(pos: Int) {
        if (pos !in libTracks.indices) return
        val t = libTracks[pos]
        AlertDialog.Builder(this)
            .setTitle("Eliminar")
            .setMessage("¿Borrar \"${t.title}\" de Descargas?")
            .setPositiveButton("Borrar") { _, _ ->
                Thread {
                    try {
                        val local = t.local ?: throw Exception("Sin archivo")
                        if (local.startsWith("content://")) {
                            val n = contentResolver.delete(Uri.parse(local), null, null)
                            if (n <= 0) throw Exception("No se pudo borrar")
                        } else {
                            if (!File(local).delete()) throw Exception("No se pudo borrar")
                        }
                        runOnUiThread {
                            Toast.makeText(this, "Eliminado", Toast.LENGTH_SHORT).show()
                            loadLibrary()
                        }
                    } catch (e: Exception) {
                        runOnUiThread {
                            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                }.start()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    // ---- descargas ----

    private fun refreshDownloads() {
        downloadsAdapter.refresh(DownloadStore.snapshot().map {
            val sub = when (it.state) {
                DownloadStore.State.ACTIVE -> "${it.progress}% • descargando"
                DownloadStore.State.DONE -> "listo • ${it.detail}"
                DownloadStore.State.ERROR -> "error • ${it.detail}"
            }
            TrackRowAdapter.Row(it.title, sub, null, false)
        })
    }
}
