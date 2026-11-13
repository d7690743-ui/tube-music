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

    @Test
    fun pick_prefiereMp3CuandoExiste() {
        val i = AudioPick.pickIndex(listOf("m4a", "mp3", "webm"), listOf(128000, 64000, 160000), true)
        assertEquals(1, i)
    }

    @Test
    fun pick_mp3DeMayorBitrate() {
        val i = AudioPick.pickIndex(listOf("mp3", "mp3"), listOf(64000, 128000), true)
        assertEquals(1, i)
    }

    @Test
    fun pick_sinMp3CaeAlMejor() {
        val i = AudioPick.pickIndex(listOf("m4a", "webm"), listOf(128000, 160000), true)
        assertEquals(1, i)
    }

    @Test
    fun pick_sinPreferenciaEligeMejor() {
        val i = AudioPick.pickIndex(listOf("mp3", "m4a"), listOf(64000, 128000), false)
        assertEquals(1, i)
    }

    @Test
    fun pick_vaciaDevuelveMenosUno() {
        assertEquals(-1, AudioPick.pickIndex(emptyList(), emptyList(), true))
    }

    @Test
    fun fellBack_detectaFaltaDeMp3() {
        assertEquals(true, AudioPick.fellBack(listOf("m4a", "webm"), true))
        assertEquals(false, AudioPick.fellBack(listOf("m4a", "mp3"), true))
        assertEquals(false, AudioPick.fellBack(listOf("m4a"), false))
    }

    @Test
    fun store_cicloCompleto() {
        DownloadStore.clear()
        val id = DownloadStore.start("cancion")
        DownloadStore.progress(id, 45)
        DownloadStore.finish(id, "Music/TubeMusic/cancion.m4a")
        val jobs = DownloadStore.snapshot()
        assertEquals(1, jobs.size)
        assertEquals(100, jobs[0].progress)
        assertEquals(DownloadStore.State.DONE, jobs[0].state)
    }

    @Test
    fun store_errorYOrden() {
        DownloadStore.clear()
        val a = DownloadStore.start("a")
        val b = DownloadStore.start("b")
        DownloadStore.fail(a, "red")
        val jobs = DownloadStore.snapshot()
        assertEquals("b", jobs[0].title)
        assertEquals(DownloadStore.State.ERROR, jobs[1].state)
        assertEquals("red", jobs[1].detail)
    }
}
