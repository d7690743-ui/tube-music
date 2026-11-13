// TubeMusic — Copyright (C) 2026 David — GPL-3.0-only (ver LICENSE).
package com.nexos.tubemusic

/** Selección de stream de audio. Lógica pura, sin Android: probada en LogicTest. */
object AudioPick {

    fun isMp3(suffix: String): Boolean = suffix.lowercase() == "mp3"

    /**
     * Elige índice de descarga en listas paralelas suffixes[i] <-> bitrates[i].
     * - Con preferMp3 y algún mp3 disponible: el mp3 de mayor bitrate.
     * - Si no: el de mayor bitrate. Vacía -> -1.
     */
    fun pickIndex(suffixes: List<String>, bitrates: List<Int>, preferMp3: Boolean): Int {
        val n = minOf(suffixes.size, bitrates.size)
        if (n <= 0) return -1
        if (preferMp3) {
            var best = -1
            for (i in 0 until n) {
                if (isMp3(suffixes[i]) && (best < 0 || bitrates[i] > bitrates[best])) best = i
            }
            if (best >= 0) return best
        }
        var best = 0
        for (i in 1 until n) {
            if (bitrates[i] > bitrates[best]) best = i
        }
        return best
    }

    /** true si pedir MP3 tuvo que caer a otro formato (YouTube casi nunca da MP3). */
    fun fellBack(suffixes: List<String>, preferMp3: Boolean): Boolean =
        preferMp3 && suffixes.none { isMp3(it) }
}
