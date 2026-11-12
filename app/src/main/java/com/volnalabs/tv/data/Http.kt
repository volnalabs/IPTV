package com.volnalabs.tv.data

import java.io.BufferedInputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.GZIPInputStream

/** Сервер ответил кодом ошибки: по нему интерфейс объясняет, что не так со ссылкой. */
class StatusException(val code: Int) : IOException("сервер ответил $code")

object Http {
    const val USER_AGENT = "IPTV/1.0 (Android TV)"

    /**
     * Открывает поток по ссылке. Сам проходит редиректы (в том числе http↔https,
     * которые HttpURLConnection не обрабатывает) и распаковывает gzip, если файл сжат.
     */
    fun open(url: String, readTimeoutMs: Int = 60_000): InputStream {
        var current = url
        repeat(8) {
            val conn = (URL(current).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = readTimeoutMs
                instanceFollowRedirects = false
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("Accept-Encoding", "gzip")
            }
            val code = conn.responseCode
            if (code in 300..399) {
                val location = conn.getHeaderField("Location") ?: throw IOException("Редирект без адреса ($code)")
                current = URL(URL(current), location).toString()
                conn.disconnect()
                return@repeat
            }
            if (code !in 200..299) {
                conn.disconnect()
                throw StatusException(code)
            }
            return maybeGunzip(conn.inputStream)
        }
        throw IOException("Слишком много перенаправлений")
    }

    fun readText(url: String): String = open(url).use { it.readBytes().toString(Charsets.UTF_8) }

    /** Распаковывает gzip по сигнатуре 1F 8B, независимо от заголовков сервера. */
    fun maybeGunzip(input: InputStream): InputStream {
        val buffered = BufferedInputStream(input, 64 * 1024)
        buffered.mark(2)
        val b1 = buffered.read()
        val b2 = buffered.read()
        buffered.reset()
        return if (b1 == 0x1f && b2 == 0x8b) BufferedInputStream(GZIPInputStream(buffered, 64 * 1024), 64 * 1024) else buffered
    }
}
