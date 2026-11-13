// TubeMusic — Copyright (C) 2026 David — GPL-3.0-only (ver LICENSE).
package com.nexos.tubemusic

import org.schabi.newpipe.extractor.NewPipe

/** Inicializacion unica del extractor, compartida por activities y servicio. */
object TubeSession {
    private var init = false

    @Synchronized
    fun ensure() {
        if (!init) {
            NewPipe.init(TubeDownloader())
            init = true
        }
    }
}
