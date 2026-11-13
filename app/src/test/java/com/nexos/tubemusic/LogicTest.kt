// TubeMusic — Copyright (C) 2026 David — GPL-3.0-only (ver LICENSE).
package com.nexos.tubemusic

import org.junit.Assert.assertEquals
import org.junit.Test

/** Pruebas unitarias de logica pura (sin Android): nombres de archivo y duraciones. */
class LogicTest {

    @Test
    fun sanitize_quitaCaracteresProhibidos() {
        assertEquals("a_b_c", MusicDownload.sanitize("a/b:c"))
        assertEquals("hola mundo", MusicDownload.sanitize("hola mundo"))
    }

    @Test
    fun sanitize_vacioDevuelveAudio() {
        assertEquals("audio", MusicDownload.sanitize("   "))
    }

    @Test
    fun fmtDurMs_formateaMinutos() {
        assertEquals("0:00", fmtDurMs(0))
        assertEquals("0:05", fmtDurMs(5000))
        assertEquals("3:07", fmtDurMs(187000))
    }

    @Test
    fun fmtDurSec_manejaDesconocido() {
        assertEquals("?", fmtDurSec(-1))
        assertEquals("4:20", fmtDurSec(260))
    }
}
