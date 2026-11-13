// TubeMusic — Copyright (C) 2026 David — GPL-3.0-only (ver LICENSE).
package com.nexos.tubemusic

import java.io.Serializable

/** Pista reproducible: stream de YouTube (pageUrl) o archivo local (local). */
data class Track(
    val title: String,
    val artist: String,
    val thumbUrl: String? = null,
    val pageUrl: String? = null,
    /** Uri content:// o ruta de archivo cuando es local, null si es stream. */
    val local: String? = null,
) : Serializable

/** 187000 ms -> "3:07". */
fun fmtDurMs(ms: Int): String {
    if (ms <= 0) return "0:00"
    val s = ms / 1000
    return "%d:%02d".format(s / 60, s % 60)
}

/** 260 s -> "4:20", negativo -> "?". */
fun fmtDurSec(seconds: Long): String {
    if (seconds < 0) return "?"
    return "%d:%02d".format(seconds / 60, seconds % 60)
}
