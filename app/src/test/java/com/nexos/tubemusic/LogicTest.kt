// TubeMusic — Copyright (C) 2026 David — GPL-3.0-only (ver LICENSE).
package com.nexos.tubemusic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

    @Test
    fun mp3Command_incluyeLameYMetadatos() {
        val cmd = Mp3Convert.buildCommand("/c/in.m4a", "/c/out.mp3", "Mi rola", "Artista")
        assertTrue(cmd.contains("libmp3lame"))
        assertTrue(cmd.contains("192k"))
        assertTrue(cmd.contains("Mi rola") && cmd.contains("Artista"))
        assertTrue(cmd.contains("/c/in.m4a") && cmd.contains("/c/out.mp3"))
    }

    @Test
    fun mp3Command_escapaComillas() {
        val cmd = Mp3Convert.buildCommand("/c/a'b.m4a", "/c/o.mp3", "t", "a")
        assertTrue(!cmd.contains("a'b.m4a"))
    }

    @Test
    fun mp3Name_sanitiza() {
        assertEquals("a_b.mp3", Mp3Convert.mp3Name("a/b"))
    }

    @Test
    fun store_noteActualizaDetalle() {
        DownloadStore.clear()
        val id = DownloadStore.start("x")
        DownloadStore.note(id, "convirtiendo a MP3")
        val jobs = DownloadStore.snapshot()
        assertEquals("convirtiendo a MP3", jobs[0].detail)
        assertEquals(DownloadStore.State.ACTIVE, jobs[0].state)
    }

    @Test
    fun history_pushAgregaAlFrente() {
        assertEquals(listOf("b", "a"), SearchHistory.push(listOf("a"), "b"))
    }

    @Test
    fun history_pushMueveDuplicadoAlFrente() {
        assertEquals(listOf("A", "b"), SearchHistory.push(listOf("b", "a"), "A"))
    }

    @Test
    fun history_pushLimitaDiezEIgronaVacio() {
        val h = (1..12).map { "q$it" }
        val out = SearchHistory.push(h, "nueva")
        assertEquals(10, out.size)
        assertEquals("nueva", out[0])
        assertEquals(h, SearchHistory.push(h, "   "))
    }

    @Test
    fun history_serializaYLee() {
        val h = listOf("rock", "salsa dura")
        assertEquals(h, SearchHistory.parse(SearchHistory.serialize(h)))
        assertEquals(emptyList<String>(), SearchHistory.parse(""))
    }
}
