// TubeMusic — Copyright (C) 2026 David — GPL-3.0-only (ver LICENSE).
package com.nexos.tubemusic

/** Estado de reproduccion que publica el servicio; las activities lo leen por polling. */
object PlayerBus {
    @Volatile var hasTrack = false
    @Volatile var title = ""
    @Volatile var artist = ""
    @Volatile var thumbUrl: String? = null
    @Volatile var pageUrl: String? = null
    @Volatile var playing = false
    @Volatile var position = 0
    @Volatile var duration = 0
    @Volatile var error: String? = null
    /** Crece en cada pista nueva; sirve para recargar la caratula. */
    @Volatile var version = 0L

    fun setTrack(t: Track) {
        hasTrack = true
        title = t.title
        artist = t.artist
        thumbUrl = t.thumbUrl
        pageUrl = t.pageUrl
        playing = false
        position = 0
        duration = 0
        error = null
        version++
    }
}
