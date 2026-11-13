// TubeMusic — Copyright (C) 2026 David — GPL-3.0-only (ver LICENSE).
package com.nexos.tubemusic

import android.app.Activity
import android.app.AlertDialog
import org.schabi.newpipe.extractor.stream.AudioStream

/**
 * Diálogo de formato con opción MP3 primero.
 * Llama onPick(streamElegido, necesitaConversion). Lista vacía: no hace nada.
 */
object FormatPicker {

    fun show(
        activity: Activity,
        title: String,
        audios: List<AudioStream>,
        onPick: (AudioStream, Boolean) -> Unit,
        onCancel: () -> Unit = {},
    ) {
        if (audios.isEmpty()) return
        val suffixes = audios.map { it.format?.suffix ?: "m4a" }
        val bitrates = audios.map { it.averageBitrate }
        val labels = mutableListOf("MP3 (convierte si hace falta)")
        labels.addAll(audios.map { "${it.format?.name ?: "?"} • ${it.averageBitrate / 1000} kbps" })
        var elegido = 0
        AlertDialog.Builder(activity)
            .setTitle("Descargar: $title")
            .setSingleChoiceItems(labels.toTypedArray(), 0) { _, which -> elegido = which }
            .setPositiveButton("Descargar") { _, _ ->
                if (elegido == 0) {
                    val idx = AudioPick.pickIndex(suffixes, bitrates, true).coerceAtLeast(0)
                    onPick(audios[idx], !AudioPick.isMp3(suffixes[idx]))
                } else {
                    onPick(audios[elegido - 1], false)
                }
            }
            .setNegativeButton("Cancelar") { _, _ -> onCancel() }
            .setOnCancelListener { onCancel() }
            .show()
    }
}
