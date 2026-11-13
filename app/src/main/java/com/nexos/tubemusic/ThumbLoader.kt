// TubeMusic — Copyright (C) 2026 David — GPL-3.0-only (ver LICENSE).
package com.nexos.tubemusic

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import android.widget.ImageView
import java.net.HttpURLConnection
import java.net.URL

/** Carga asincrona de caratulas con cache en memoria (sin dependencias). */
object ThumbLoader {
    private val cache = LruCache<String, Bitmap>(60)

    fun load(url: String?, view: ImageView) {
        if (url.isNullOrEmpty()) {
            view.setImageResource(R.drawable.ic_music)
            return
        }
        cache.get(url)?.let {
            view.setImageBitmap(it)
            return
        }
        view.tag = url
        view.setImageResource(R.drawable.ic_music)
        Thread {
            try {
                val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 10_000
                    readTimeout = 15_000
                }
                val bmp = conn.inputStream.use { BitmapFactory.decodeStream(it) }
                conn.disconnect()
                if (bmp != null) {
                    cache.put(url, bmp)
                    view.post { if (view.tag == url) view.setImageBitmap(bmp) }
                }
            } catch (_: Exception) {
            }
        }.start()
    }
}
