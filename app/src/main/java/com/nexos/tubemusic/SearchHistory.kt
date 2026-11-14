// TubeMusic — Copyright (C) 2026 David — GPL-3.0-only (ver LICENSE).
package com.nexos.tubemusic

/** Historial de búsquedas. Lógica pura, sin Android: probado en LogicTest. */
object SearchHistory {

    const val MAX = 10

    /** Agrega al frente, sin duplicados (ignora mayúsculas), tope MAX. Vacío: igual. */
    fun push(history: List<String>, q: String): List<String> {
        val clean = q.trim().replace("\n", " ")
        if (clean.isEmpty()) return history
        return (listOf(clean) + history.filter { !it.equals(clean, ignoreCase = true) }).take(MAX)
    }

    fun serialize(h: List<String>): String = h.joinToString("\n")

    fun parse(s: String): List<String> =
        s.split("\n").map { it.trim() }.filter { it.isNotEmpty() }.take(MAX)
}
