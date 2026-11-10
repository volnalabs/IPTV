package com.volnalabs.tv.remote

import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import kotlin.concurrent.thread

/**
 * Маленький веб-сервер в локальной сети: открываешь страницу на телефоне,
 * вставляешь ссылку на плейлист — и она приходит на ТВ. Набирать длинную ссылку
 * пультом не нужно.
 */
class RemoteInputServer(
    private val currentPlaylist: () -> String,
    private val currentEpg: () -> String,
    private val onSubmit: (playlist: String, epg: String) -> Unit,
) {
    private var server: ServerSocket? = null
    @Volatile private var running = false

    var port: Int = 0
        private set

    fun start(): Boolean {
        if (running) return true
        for (p in PORTS) {
            try {
                server = ServerSocket(p)
                port = p
                break
            } catch (e: Exception) {
                Log.w(TAG, "port $p busy")
            }
        }
        val s = server ?: return false
        running = true
        thread(name = "iptv-remote", isDaemon = true) {
            while (running) {
                val client = try { s.accept() } catch (e: Exception) { break }
                thread(isDaemon = true) { handle(client) }
            }
        }
        return true
    }

    fun stop() {
        running = false
        runCatching { server?.close() }
        server = null
    }

    private fun handle(socket: Socket) {
        socket.use { sock ->
            sock.soTimeout = 15_000
            val input = sock.getInputStream()
            val reader = BufferedReader(InputStreamReader(input, Charsets.UTF_8))
            val requestLine = reader.readLine() ?: return
            var contentLength = 0
            while (true) {
                val line = reader.readLine() ?: break
                if (line.isEmpty()) break
                if (line.startsWith("Content-Length:", ignoreCase = true)) {
                    contentLength = line.substringAfter(':').trim().toIntOrNull() ?: 0
                }
            }
            val method = requestLine.substringBefore(' ')
            val html = if (method == "POST") {
                val buf = CharArray(contentLength.coerceIn(0, 64 * 1024))
                var read = 0
                while (read < buf.size) {
                    val n = reader.read(buf, read, buf.size - read)
                    if (n < 0) break
                    read += n
                }
                val form = parseForm(String(buf, 0, read))
                val playlist = form["playlist"].orEmpty().trim()
                val epg = form["epg"].orEmpty().trim()
                if (playlist.startsWith("http", ignoreCase = true)) {
                    onSubmit(playlist, epg)
                    page("Готово ✓", "<p class='ok'>Ссылка отправлена на телевизор. Каналы загрузятся через несколько секунд.</p><p><a href='/'>Изменить ещё раз</a></p>")
                } else {
                    page("Ошибка", "<p class='err'>Ссылка должна начинаться с http:// или https://</p><p><a href='/'>Назад</a></p>")
                }
            } else {
                page("IPTV", form(currentPlaylist(), currentEpg()))
            }
            val body = html.toByteArray(Charsets.UTF_8)
            val out = sock.getOutputStream()
            out.write(
                ("HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\n" +
                    "Content-Length: ${body.size}\r\nConnection: close\r\n\r\n").toByteArray()
            )
            out.write(body)
            out.flush()
        }
    }

    private fun parseForm(body: String): Map<String, String> =
        body.split('&').mapNotNull { pair ->
            val k = pair.substringBefore('=', "")
            if (k.isEmpty()) null
            else URLDecoder.decode(k, "UTF-8") to URLDecoder.decode(pair.substringAfter('=', ""), "UTF-8")
        }.toMap()

    private fun esc(s: String) = s.replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;")

    private fun form(playlist: String, epg: String) = """
        <form method="post">
          <label>Ссылка на плейлист (M3U)</label>
          <input name="playlist" type="url" required placeholder="http://…/playlist.m3u8" value="${esc(playlist)}">
          <label>Телепрограмма (необязательно)</label>
          <input name="epg" type="url" placeholder="Возьмётся из плейлиста" value="${esc(epg)}">
          <button type="submit">Отправить на ТВ</button>
        </form>
    """.trimIndent()

    private fun page(title: String, content: String) = """
        <!doctype html><html lang="ru"><head><meta charset="utf-8">
        <meta name="viewport" content="width=device-width,initial-scale=1">
        <title>$title</title>
        <style>
          body{margin:0;font-family:system-ui,-apple-system,sans-serif;background:#0b1220;color:#e8eefc;
               display:flex;justify-content:center;padding:24px}
          main{width:100%;max-width:520px}
          h1{font-size:24px;margin:8px 0 20px}
          label{display:block;font-size:14px;color:#9fb0d0;margin:16px 0 6px}
          input{width:100%;box-sizing:border-box;padding:14px;border-radius:12px;border:1px solid #2a3a5c;
                background:#121c30;color:#fff;font-size:16px}
          button{margin-top:24px;width:100%;padding:16px;border:0;border-radius:12px;background:#3b82f6;
                 color:#fff;font-size:17px;font-weight:600}
          .ok{color:#7ee2a8;font-size:18px}.err{color:#ff8a8a}a{color:#8ab4ff}
        </style></head><body><main><h1>📺 $title</h1>$content</main></body></html>
    """.trimIndent()

    companion object {
        private const val TAG = "IptvRemote"
        private val PORTS = listOf(8321, 8322, 8323, 18321)

        /** IPv4-адрес ТВ в домашней сети. */
        fun localIp(): String? = runCatching {
            NetworkInterface.getNetworkInterfaces().toList()
                .filter { it.isUp && !it.isLoopback }
                .flatMap { it.inetAddresses.toList() }
                .filterIsInstance<Inet4Address>()
                .sortedByDescending { it.isSiteLocalAddress }
                .firstOrNull()?.hostAddress
        }.getOrNull()

        fun qr(text: String, size: Int): Bitmap {
            val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size, mapOf(EncodeHintType.MARGIN to 1))
            val pixels = IntArray(size * size)
            for (y in 0 until size) for (x in 0 until size) {
                pixels[y * size + x] = if (matrix[x, y]) Color.BLACK else Color.WHITE
            }
            return Bitmap.createBitmap(pixels, size, size, Bitmap.Config.RGB_565)
        }
    }
}
