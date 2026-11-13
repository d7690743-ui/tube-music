// TubeMusic — Copyright (C) 2026 David — GPL-3.0-only (ver LICENSE).
package com.nexos.tubemusic

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL

/** Descarga un stream de audio a Music/TubeMusic con progreso 0-100. */
object MusicDownload {

    fun sanitize(name: String): String =
        name.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().take(120).ifEmpty { "audio" }

    @Throws(Exception::class)
    fun download(
        context: Context,
        url: String,
        title: String,
        suffix: String,
        onProgress: (Int) -> Unit,
    ): String {
        val name = sanitize(title) + "." + suffix.lowercase()
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 60_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", TubeDownloader.UA)
        }
        try {
            if (conn.responseCode !in 200..299) throw Exception("HTTP ${conn.responseCode}")
            val total = conn.contentLengthLong
            if (Build.VERSION.SDK_INT >= 29) {
                val values = ContentValues().apply {
                    put(MediaStore.Audio.Media.DISPLAY_NAME, name)
                    put(MediaStore.Audio.Media.MIME_TYPE, mime(suffix))
                    put(MediaStore.Audio.Media.RELATIVE_PATH, "Music/TubeMusic")
                    put(MediaStore.Audio.Media.IS_PENDING, 1)
                }
                val uri = context.contentResolver.insert(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values
                ) ?: throw Exception("No se pudo crear el archivo")
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    conn.inputStream.use { input -> copy(input, out, total, onProgress) }
                } ?: throw Exception("No se pudo escribir el archivo")
                val done = ContentValues().apply {
                    put(MediaStore.Audio.Media.IS_PENDING, 0)
                }
                context.contentResolver.update(uri, done, null, null)
                return "Music/TubeMusic/$name"
            } else {
                @Suppress("DEPRECATION")
                val dir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
                    "TubeMusic",
                )
                if (!dir.exists()) dir.mkdirs()
                val dest = File(dir, name)
                FileOutputStream(dest).use { out ->
                    conn.inputStream.use { input -> copy(input, out, total, onProgress) }
                }
                return dest.absolutePath
            }
        } finally {
            conn.disconnect()
        }
    }

    /** Descarga una URL a un archivo directo (caché). Para convertir después. */
    @Throws(Exception::class)
    fun downloadToFile(url: String, dest: File, onProgress: (Int) -> Unit) {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 60_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", TubeDownloader.UA)
        }
        try {
            if (conn.responseCode !in 200..299) throw Exception("HTTP ${conn.responseCode}")
            FileOutputStream(dest).use { out ->
                conn.inputStream.use { input -> copy(input, out, conn.contentLengthLong, onProgress) }
            }
        } finally {
            conn.disconnect()
        }
    }

    private fun copy(input: InputStream, out: OutputStream, total: Long, onProgress: (Int) -> Unit) {
        val buf = ByteArray(64 * 1024)
        var done = 0L
        var last = -1
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            out.write(buf, 0, n)
            done += n
            if (total > 0) {
                val p = (done * 100 / total).toInt().coerceAtMost(100)
                if (p != last) {
                    last = p
                    onProgress(p)
                }
            }
        }
        onProgress(100)
    }

    private fun mime(suffix: String) = when (suffix.lowercase()) {
        "m4a", "mp4" -> "audio/mp4"
        "webm" -> "audio/webm"
        "mp3" -> "audio/mpeg"
        "opus" -> "audio/opus"
        "ogg", "oga" -> "audio/ogg"
        else -> "audio/*"
    }
}
