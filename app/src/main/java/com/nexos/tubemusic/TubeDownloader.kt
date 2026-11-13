// TubeMusic — Copyright (C) 2026 David — GPL-3.0-only (ver LICENSE).
package com.nexos.tubemusic

import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import java.net.HttpURLConnection
import java.net.URL

/**
 * Downloader para NewPipeExtractor basado en HttpURLConnection (sin dependencias).
 * Sigue redirecciones manualmente para soportar POST (API InnerTube de YouTube).
 */
class TubeDownloader : Downloader() {

    companion object {
        const val UA = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"
        private const val MAX_REDIRECTS = 5
    }

    override fun execute(request: Request): Response {
        var url = request.url()
        var method = request.httpMethod()
        var data = request.dataToSend()
        var redirects = 0
        while (true) {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = method
                connectTimeout = 15_000
                readTimeout = 60_000
                instanceFollowRedirects = false
                setRequestProperty("User-Agent", UA)
                request.headers()?.forEach { (name, values) ->
                    values?.forEachIndexed { i, v ->
                        if (v != null) {
                            if (i == 0) setRequestProperty(name, v) else addRequestProperty(name, v)
                        }
                    }
                }
                if (data != null && (method == "POST" || method == "PUT")) {
                    doOutput = true
                    outputStream.use { it.write(data) }
                }
            }
            val code = conn.responseCode
            val location = conn.getHeaderField("Location")
            if ((code == 301 || code == 302 || code == 303 || code == 307 || code == 308) &&
                location != null && redirects < MAX_REDIRECTS
            ) {
                redirects++
                url = URL(URL(url), location).toString()
                if (code == 303 || ((code == 301 || code == 302) && method == "POST")) {
                    method = "GET"
                    data = null
                }
                conn.disconnect()
                continue
            }
            try {
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                val body = try {
                    stream?.readBytes()?.toString(Charsets.UTF_8) ?: ""
                } catch (_: Exception) {
                    ""
                }
                val headers = conn.headerFields
                    ?.filterKeys { it != null }
                    ?.mapValues { it.value ?: emptyList() }
                    ?: emptyMap()
                return Response(code, conn.responseMessage ?: "", headers, body, conn.url.toString())
            } finally {
                conn.disconnect()
            }
        }
    }
}
