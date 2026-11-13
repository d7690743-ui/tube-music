// TubeMusic — Copyright (C) 2026 David — GPL-3.0-only (ver LICENSE).
package com.nexos.tubemusic

/** Registro de descargas para la pestaña Descargas. Sin Android: probado en LogicTest. */
object DownloadStore {

    enum class State { ACTIVE, DONE, ERROR }

    data class Job(
        val id: Long,
        val title: String,
        val progress: Int,
        val state: State,
        val detail: String,
    )

    private val jobs = mutableListOf<Job>()
    private var nextId = 1L

    @Synchronized
    fun clear() {
        jobs.clear()
        nextId = 1L
    }

    @Synchronized
    fun start(title: String): Long {
        val id = nextId++
        jobs.add(0, Job(id, title, 0, State.ACTIVE, "descargando"))
        return id
    }

    @Synchronized
    fun progress(id: Long, pct: Int) {
        val i = jobs.indexOfFirst { it.id == id }
        if (i >= 0) jobs[i] = jobs[i].copy(progress = pct.coerceIn(0, 100))
    }

    @Synchronized
    fun finish(id: Long, path: String) {
        val i = jobs.indexOfFirst { it.id == id }
        if (i >= 0) jobs[i] = jobs[i].copy(progress = 100, state = State.DONE, detail = path)
    }

    @Synchronized
    fun fail(id: Long, msg: String) {
        val i = jobs.indexOfFirst { it.id == id }
        if (i >= 0) jobs[i] = jobs[i].copy(state = State.ERROR, detail = msg)
    }

    @Synchronized
    fun snapshot(): List<Job> = jobs.toList()
}
