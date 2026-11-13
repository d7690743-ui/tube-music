// TubeMusic — Copyright (C) 2026 David — GPL-3.0-only (ver LICENSE).
package com.nexos.tubemusic

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import java.io.File
import java.io.FileOutputStream

/** Descarga + conversión a MP3 real (FFmpeg/libmp3lame) y guardado en biblioteca. */
object Mp3Convert {

    /** Comando puro (probado en LogicTest): entrada -> MP3 192k con metadatos. */
    fun buildCommand(input: String, output: String, title: String, artist: String): String {
        fun q(s: String) = "'" + s.replace("'", "'\\''") + "'"
        return "-y -i ${q(input)} -codec:a libmp3lame -b:a 192k " +
            "-metadata ${q("title=$title")} -metadata ${q("artist=$artist")} ${q(output)}"
    }

    fun mp3Name(title: String): String = MusicDownload.sanitize(title) + ".mp3"

    /**
     * Descarga un stream a caché, lo convierte a MP3 y lo guarda en
     * Music/TubeMusic. Devuelve la ruta visible. Llama desde hilo de fondo.
     */
    @Throws(Exception::class)
    fun downloadAsMp3(
        context: Context,
        url: String,
        title: String,
        artist: String,
        jobId: Long,
    ): String {
        val tmp = File(context.cacheDir, "dl_${System.currentTimeMillis()}.bin")
        try {
            MusicDownload.downloadToFile(url, tmp) { p ->
                DownloadStore.progress(jobId, p * 70 / 100)
            }
            DownloadStore.note(jobId, "convirtiendo a MP3…")
            val mp3 = convert(context, tmp, title, artist)
            try {
                DownloadStore.progress(jobId, 90)
                return saveMp3(context, mp3, title)
            } finally {
                mp3.delete()
            }
        } finally {
            tmp.delete()
        }
    }

    /** Convierte un archivo ya descargado (content:// o ruta) a MP3 en biblioteca. */
    @Throws(Exception::class)
    fun convertLocal(context: Context, local: String, title: String, artist: String): String {
        val tmp = File(context.cacheDir, "src_${System.currentTimeMillis()}.bin")
        try {
            if (local.startsWith("content://")) {
                context.contentResolver.openInputStream(Uri.parse(local))?.use { input ->
                    FileOutputStream(tmp).use { input.copyTo(it) }
                } ?: throw Exception("No se pudo leer el archivo")
            } else {
                File(local).copyTo(tmp, overwrite = true)
            }
            val mp3 = convert(context, tmp, title, artist)
            try {
                return saveMp3(context, mp3, title)
            } finally {
                mp3.delete()
            }
        } finally {
            tmp.delete()
        }
    }

    /** Convierte input a MP3 temporal con metadatos. Bloqueante: usar en fondo. */
    @Throws(Exception::class)
    fun convert(context: Context, input: File, title: String, artist: String): File {
        val out = File(context.cacheDir, "conv_${System.currentTimeMillis()}.mp3")
        val session = FFmpegKit.execute(buildCommand(input.absolutePath, out.absolutePath, title, artist))
        if (!ReturnCode.isSuccess(session.returnCode) || !out.exists()) {
            out.delete()
            val why = session.failStackTrace?.take(200) ?: session.returnCode.toString()
            throw Exception("Conversión falló: $why")
        }
        return out
    }

    /** Guarda un MP3 ya convertido en Music/TubeMusic. Devuelve ruta visible. */
    @Throws(Exception::class)
    fun saveMp3(context: Context, mp3: File, title: String): String {
        val name = mp3Name(title)
        if (Build.VERSION.SDK_INT >= 29) {
            val values = ContentValues().apply {
                put(MediaStore.Audio.Media.DISPLAY_NAME, name)
                put(MediaStore.Audio.Media.MIME_TYPE, "audio/mpeg")
                put(MediaStore.Audio.Media.RELATIVE_PATH, "Music/TubeMusic")
                put(MediaStore.Audio.Media.IS_PENDING, 1)
            }
            val uri = context.contentResolver.insert(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values
            ) ?: throw Exception("No se pudo crear el archivo")
            context.contentResolver.openOutputStream(uri)?.use { out ->
                mp3.inputStream().use { it.copyTo(out) }
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
            mp3.copyTo(File(dir, name), overwrite = true)
            return File(dir, name).absolutePath
        }
    }
}
