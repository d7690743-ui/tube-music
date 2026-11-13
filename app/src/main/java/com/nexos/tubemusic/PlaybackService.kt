// TubeMusic — Copyright (C) 2026 David — GPL-3.0-only (ver LICENSE).
package com.nexos.tubemusic

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import org.schabi.newpipe.extractor.ServiceList

/** Reproductor en segundo plano: cola, MediaPlayer, notificacion y foco de audio. */
class PlaybackService : Service() {

    companion object {
        const val ACTION_PLAY_LIST = "tubemusic.PLAY_LIST"
        const val ACTION_TOGGLE = "tubemusic.TOGGLE"
        const val ACTION_NEXT = "tubemusic.NEXT"
        const val ACTION_PREV = "tubemusic.PREV"
        const val ACTION_SEEK = "tubemusic.SEEK"
        const val ACTION_STOP = "tubemusic.STOP"
        const val EXTRA_TRACKS = "tracks"
        const val EXTRA_INDEX = "index"
        const val EXTRA_POS = "pos"
        private const val CH_ID = "reproduccion"
        private const val NOTIF_ID = 5001

        fun cmd(ctx: Context, action: String, setup: Intent.() -> Unit = {}) {
            val i = Intent(ctx, PlaybackService::class.java).setAction(action).apply(setup)
            if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i) else ctx.startService(i)
        }
    }

    private var player: MediaPlayer? = null
    private val tracks = mutableListOf<Track>()
    private var index = -1
    private var failCount = 0
    private val main = Handler(Looper.getMainLooper())
    private lateinit var audioMan: AudioManager
    private val progressLoop = object : Runnable {
        override fun run() {
            try {
                val p = player
                if (p != null && p.isPlaying) {
                    PlayerBus.position = p.currentPosition
                    PlayerBus.duration = p.duration
                }
            } catch (_: Exception) {
            }
            main.postDelayed(this, 500)
        }
    }
    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        if (change == AudioManager.AUDIOFOCUS_LOSS ||
            change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT
        ) {
            pause()
        }
    }

    override fun onCreate() {
        super.onCreate()
        audioMan = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        ensureChannel()
        startFg(placeholderNotif())
        main.post(progressLoop)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY_LIST -> {
                @Suppress("UNCHECKED_CAST", "DEPRECATION")
                val list = if (Build.VERSION.SDK_INT >= 33) {
                    intent.getSerializableExtra(EXTRA_TRACKS, ArrayList::class.java)
                        as? ArrayList<Track>
                } else {
                    intent.getSerializableExtra(EXTRA_TRACKS) as? ArrayList<Track>
                }
                val idx = intent.getIntExtra(EXTRA_INDEX, 0)
                if (list != null && list.isNotEmpty()) {
                    tracks.clear()
                    tracks.addAll(list)
                    playAt(idx.coerceIn(tracks.indices))
                }
            }
            ACTION_TOGGLE -> toggle()
            ACTION_NEXT -> next()
            ACTION_PREV -> prev()
            ACTION_SEEK -> seekTo(intent.getIntExtra(EXTRA_POS, 0))
            ACTION_STOP -> shutdown()
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        main.removeCallbacks(progressLoop)
        releasePlayer()
        try {
            @Suppress("DEPRECATION")
            audioMan.abandonAudioFocus(focusListener)
        } catch (_: Exception) {
        }
        super.onDestroy()
    }

    private fun playAt(i: Int, resetFail: Boolean = true) {
        if (i !in tracks.indices) return
        index = i
        if (resetFail) failCount = 0
        val track = tracks[i]
        PlayerBus.setTrack(track)
        startFg(buildNotif())
        Thread {
            try {
                val src: Any = if (track.local != null) {
                    if (track.local.startsWith("content://")) Uri.parse(track.local) else track.local
                } else {
                    TubeSession.ensure()
                    val ex = ServiceList.YouTube.getStreamExtractor(
                        track.pageUrl ?: throw Exception("Sin URL")
                    )
                    ex.fetchPage()
                    ex.audioStreams
                        .filter { it.isUrl && it.url != null }
                        .maxByOrNull { it.averageBitrate }
                        ?.url ?: throw Exception("Sin audio disponible")
                }
                main.post { startSource(src) }
            } catch (e: Exception) {
                main.post { onResolveError(e.message ?: "error") }
            }
        }.start()
    }

    private fun startSource(src: Any) {
        try {
            releasePlayer()
            val p = MediaPlayer()
            p.setWakeMode(applicationContext, PowerManager.PARTIAL_WAKE_LOCK)
            when (src) {
                is Uri -> p.setDataSource(applicationContext, src)
                is String -> p.setDataSource(src)
            }
            p.setOnPreparedListener {
                @Suppress("DEPRECATION")
                audioMan.requestAudioFocus(
                    focusListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN
                )
                it.start()
                PlayerBus.playing = true
                PlayerBus.error = null
                try {
                    PlayerBus.duration = it.duration
                } catch (_: Exception) {
                }
                startFg(buildNotif())
            }
            p.setOnCompletionListener { next(auto = true) }
            p.setOnErrorListener { _, what, extra ->
                onResolveError("Error $what/$extra")
                true
            }
            p.prepareAsync()
            player = p
        } catch (e: Exception) {
            onResolveError(e.message ?: "error")
        }
    }

    private fun onResolveError(msg: String) {
        failCount++
        PlayerBus.error = msg
        PlayerBus.playing = false
        if (failCount < 3 && index + 1 in tracks.indices) {
            playAt(index + 1, resetFail = false)
        } else {
            startFg(buildNotif())
        }
    }

    private fun toggle() {
        val p = player
        if (p == null) {
            if (index in tracks.indices) playAt(index)
            else if (tracks.isNotEmpty()) playAt(0)
            return
        }
        try {
            if (p.isPlaying) {
                pause()
            } else {
                p.start()
                PlayerBus.playing = true
                startFg(buildNotif())
            }
        } catch (_: Exception) {
        }
    }

    private fun pause() {
        try {
            player?.let { if (it.isPlaying) it.pause() }
        } catch (_: Exception) {
        }
        PlayerBus.playing = false
        startFg(buildNotif())
    }

    private fun next(auto: Boolean = false) {
        if (index + 1 in tracks.indices) playAt(index + 1)
        else if (auto) pause()
    }

    private fun prev() {
        try {
            val p = player
            if (p != null && p.currentPosition > 3000) {
                p.seekTo(0)
                return
            }
        } catch (_: Exception) {
        }
        if (index - 1 in tracks.indices) playAt(index - 1)
        else {
            try {
                player?.seekTo(0)
            } catch (_: Exception) {
            }
        }
    }

    private fun seekTo(pos: Int) {
        try {
            player?.seekTo(pos)
            PlayerBus.position = pos
        } catch (_: Exception) {
        }
    }

    private fun shutdown() {
        releasePlayer()
        PlayerBus.playing = false
        PlayerBus.hasTrack = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun releasePlayer() {
        try {
            player?.release()
        } catch (_: Exception) {
        }
        player = null
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val ch = NotificationChannel(CH_ID, "Reproducción", NotificationManager.IMPORTANCE_LOW)
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(ch)
        }
    }

    private fun actionIntent(action: String, code: Int): PendingIntent {
        val i = Intent(this, PlaybackService::class.java).setAction(action)
        return PendingIntent.getService(
            this, code, i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun newBuilder(): Notification.Builder {
        return if (Build.VERSION.SDK_INT >= 26) {
            Notification.Builder(this, CH_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
    }

    private fun buildNotif(): Notification {
        val playing = PlayerBus.playing
        val open = PendingIntent.getActivity(
            this, 9001,
            Intent(this, PlayerActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return newBuilder()
            .setContentTitle(PlayerBus.title.ifEmpty { "TubeMusic" })
            .setContentText(PlayerBus.artist.ifEmpty { "Toca para abrir" })
            .setSmallIcon(
                if (playing) android.R.drawable.ic_media_pause
                else android.R.drawable.ic_media_play
            )
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(android.R.drawable.ic_media_previous, "Ant", actionIntent(ACTION_PREV, 9101))
            .addAction(
                if (playing) android.R.drawable.ic_media_pause
                else android.R.drawable.ic_media_play,
                if (playing) "Pausa" else "Play", actionIntent(ACTION_TOGGLE, 9102)
            )
            .addAction(android.R.drawable.ic_media_next, "Sig", actionIntent(ACTION_NEXT, 9103))
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel, "X",
                actionIntent(ACTION_STOP, 9104)
            )
            .build()
    }

    private fun placeholderNotif(): Notification {
        return newBuilder()
            .setContentTitle("TubeMusic")
            .setContentText("Listo para reproducir")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setOngoing(true)
            .build()
    }

    private fun startFg(n: Notification) {
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(NOTIF_ID, n)
        }
    }
}
