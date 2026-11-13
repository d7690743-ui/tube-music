// TubeMusic — Copyright (C) 2026 David — GPL-3.0-only (ver LICENSE).
package com.nexos.tubemusic

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import org.schabi.newpipe.extractor.ServiceList

/** Reproductor completo estilo Spotify: caratula, seek, controles y descarga. */
class PlayerActivity : Activity() {

    private lateinit var artView: ImageView
    private lateinit var titleView: TextView
    private lateinit var artistView: TextView
    private lateinit var seekBar: SeekBar
    private lateinit var curView: TextView
    private lateinit var totalView: TextView
    private lateinit var playButton: ImageButton
    private lateinit var downloadButton: Button

    private var alive = true
    private var seeking = false
    private var lastVersion = -1L
    private var lastError: String? = null
    private val ui = Handler(Looper.getMainLooper())
    private val loop = object : Runnable {
        override fun run() {
            if (!alive) return
            refresh()
            ui.postDelayed(this, 500)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)

        artView = findViewById(R.id.art)
        titleView = findViewById(R.id.title)
        artistView = findViewById(R.id.artist)
        seekBar = findViewById(R.id.seek)
        curView = findViewById(R.id.cur)
        totalView = findViewById(R.id.total)
        playButton = findViewById(R.id.play)
        downloadButton = findViewById(R.id.download)

        playButton.setOnClickListener {
            PlaybackService.cmd(this, PlaybackService.ACTION_TOGGLE)
        }
        findViewById<ImageButton>(R.id.next).setOnClickListener {
            PlaybackService.cmd(this, PlaybackService.ACTION_NEXT)
        }
        findViewById<ImageButton>(R.id.prev).setOnClickListener {
            PlaybackService.cmd(this, PlaybackService.ACTION_PREV)
        }
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                if (fromUser) curView.text = fmtDurMs(p)
            }

            override fun onStartTrackingTouch(s: SeekBar?) {
                seeking = true
            }

            override fun onStopTrackingTouch(s: SeekBar?) {
                seeking = false
                PlaybackService.cmd(this@PlayerActivity, PlaybackService.ACTION_SEEK) {
                    putExtra(PlaybackService.EXTRA_POS, s?.progress ?: 0)
                }
            }
        })
        downloadButton.setOnClickListener { downloadCurrent() }
        ui.post(loop)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    override fun onDestroy() {
        alive = false
        ui.removeCallbacks(loop)
        super.onDestroy()
    }

    private fun refresh() {
        if (!PlayerBus.hasTrack) return
        titleView.text = PlayerBus.title
        artistView.text = PlayerBus.artist
        if (PlayerBus.version != lastVersion) {
            lastVersion = PlayerBus.version
            ThumbLoader.load(PlayerBus.thumbUrl, artView)
        }
        playButton.setImageResource(
            if (PlayerBus.playing) android.R.drawable.ic_media_pause
            else android.R.drawable.ic_media_play
        )
        if (!seeking) {
            seekBar.max = PlayerBus.duration.coerceAtLeast(0)
            seekBar.progress = PlayerBus.position.coerceAtLeast(0)
            curView.text = fmtDurMs(PlayerBus.position)
        }
        totalView.text = fmtDurMs(PlayerBus.duration)
        downloadButton.visibility = if (PlayerBus.pageUrl != null) View.VISIBLE else View.GONE
        val err = PlayerBus.error
        if (err != null && err != lastError) {
            lastError = err
            Toast.makeText(this, "Error: $err", Toast.LENGTH_LONG).show()
        }
    }

    private fun downloadCurrent() {
        val url = PlayerBus.pageUrl ?: return
        val title = PlayerBus.title
        downloadButton.isEnabled = false
        Toast.makeText(this, "Descargando mejor calidad...", Toast.LENGTH_SHORT).show()
        Thread {
            try {
                TubeSession.ensure()
                val ex = ServiceList.YouTube.getStreamExtractor(url)
                ex.fetchPage()
                val best = ex.audioStreams
                    .filter { it.isUrl && it.url != null }
                    .maxByOrNull { it.averageBitrate }
                    ?: throw Exception("Sin audio")
                MusicDownload.download(
                    this,
                    best.url ?: throw Exception("Sin URL"),
                    title,
                    best.format?.suffix ?: "m4a"
                ) { }
                runOnUiThread {
                    Toast.makeText(this, "Guardado en Music/TubeMusic", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            } finally {
                runOnUiThread { downloadButton.isEnabled = true }
            }
        }.start()
    }
}
