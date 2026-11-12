package com.volnalabs.tv

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import com.volnalabs.tv.data.Repo
import com.volnalabs.tv.ui.BaseActivity
import com.volnalabs.tv.ui.MainActivity
import org.junit.After
import org.junit.Before
import org.robolectric.Shadows.shadowOf
import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.concurrent.thread

/**
 * Общая часть тестов интерфейса: маленький HTTP-сервер с плейлистами, телепрограммой и логотипами,
 * ожидание главного потока, нажатия кнопок пульта и снимки экрана.
 */
abstract class UiTestBase {
    protected lateinit var server: ServerSocket
    protected val routes = HashMap<String, ByteArray>()
    protected val base get() = "http://127.0.0.1:${server.localPort}"
    protected val outDir = File(System.getenv("IPTV_SHOTS") ?: "build/shots")

    protected open val channels = listOf(
        "Федеральные" to listOf("Первый канал", "Россия 1", "НТВ", "Пятый канал", "Россия К", "Матч ТВ", "ТВ Центр", "Звезда"),
        "Кино" to listOf("Кинопремьера", "Киносвидание", "Кинохит", "Наше новое кино", "Комедия", "Мужское кино"),
        "Спорт" to listOf("Матч! Арена", "Футбол 1", "Бокс ТВ"),
        "Детские" to listOf("Карусель", "Мульт", "Детский мир"),
    )
    protected val titles = listOf(
        "Новости", "Вечерний выпуск", "Документальный фильм «Северный путь»", "Сериал «Тихая гавань». 12-я серия",
        "Футбол. Премьер-лига. «Звезда» — «Волна»", "Художественный фильм «Дорога домой»", "Футбол. Обзор тура", "Новости культуры",
        "Ток-шоу «Вечер»", "Мультфильмы", "Концерт симфонического оркестра", "Сериал «Улицы». 23-я серия",
    )

    private fun xmltv(t: Long): String = SimpleDateFormat("yyyyMMddHHmmss Z", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date(t))
    protected fun res(name: String) = javaClass.classLoader!!.getResourceAsStream("shots/$name")!!.readBytes()

    private fun m3u(list: List<Pair<String, List<String>>>, logos: Boolean): String {
        val sb = StringBuilder("#EXTM3U url-tvg=\"$base/epg.xml\"\n")
        var n = 0
        for ((group, names) in list) for (name in names) {
            n++
            val archive = if (n % 3 != 0) " catchup=\"shift\" catchup-days=\"7\"" else ""
            val logo = if (logos) " tvg-logo=\"$base/logo${(n - 1) % 24 + 1}.png\"" else ""
            sb.append("#EXTINF:0 tvg-id=\"$n\"$logo$archive group-title=\"$group\", $name\n$base/live/$n.m3u8\n")
        }
        return sb.toString()
    }

    @Before fun startServer() {
        server = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
        val now = System.currentTimeMillis()
        val epg = StringBuilder("<?xml version=\"1.0\" encoding=\"utf-8\"?><tv>\n")
        var n = 0
        for ((_, names) in channels) for (name in names) {
            n++
            epg.append("<channel id=\"$n\"><display-name>$name</display-name></channel>\n")
            if (n == 7) continue // у «ТВ Центра» нет телепрограммы
            var t = now - 30 * 3_600_000L - (n * 7 % 50) * 60_000L
            var k = n
            while (t < now + 20 * 3_600_000L) {
                val len = (35 + (k * 37 % 5) * 15) * 60_000L
                val desc = if (k % 2 == 0) "<desc>Экспедиция проходит Северным морским путём от Мурманска до Чукотки: ледоколы, портовые города и люди, которые живут там круглый год.</desc>" else ""
                epg.append("<programme start=\"${xmltv(t)}\" stop=\"${xmltv(t + len)}\" channel=\"$n\"><title>${titles[k % titles.size]}</title>$desc</programme>\n")
                t += len; k++
            }
        }
        epg.append("</tv>")
        routes["/epg.xml"] = epg.toString().toByteArray()
        routes["/pl.m3u"] = m3u(channels, logos = false).toByteArray()
        routes["/logos.m3u"] = m3u(channels, logos = true).toByteArray()
        routes["/sport.m3u"] = m3u(listOf("Спорт" to listOf("Матч! Арена", "Футбол 1", "Бокс ТВ", "Матч ТВ")), logos = false).toByteArray()
        routes["/empty.m3u"] = "#EXTM3U\n".toByteArray()
        for (i in 1..24) runCatching { routes["/logo$i.png"] = res("logo$i.png") }
        thread(isDaemon = true) {
            while (!server.isClosed) {
                val sock = try { server.accept() } catch (e: Exception) { break }
                thread(isDaemon = true) {
                    sock.use { c ->
                        val reader = c.getInputStream().bufferedReader()
                        val line = reader.readLine() ?: return@use
                        while (true) { val h = reader.readLine() ?: break; if (h.isEmpty()) break }
                        val uri = line.split(' ').getOrElse(1) { "/" }.substringBefore('?')
                        val body = routes[uri]
                        val out = c.getOutputStream()
                        if (body == null) out.write("HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray())
                        else { out.write("HTTP/1.1 200 OK\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n".toByteArray()); out.write(body) }
                        out.flush()
                    }
                }
            }
        }
        outDir.mkdirs()
    }

    @After fun stopServer() = server.close()

    protected fun idle(ms: Long = 0) {
        val end = System.currentTimeMillis() + ms
        do {
            shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(20))
            Thread.sleep(20)
        } while (System.currentTimeMillis() < end)
    }

    protected fun waitFor(timeoutMs: Long, what: String, cond: () -> Boolean) {
        val end = System.currentTimeMillis() + timeoutMs
        while (!cond()) {
            if (System.currentTimeMillis() > end) throw AssertionError("Не дождались: $what")
            idle(50)
        }
    }

    /** Плейлист «Мой плейлист» как после миграции со старой версии: ссылка лежит в прежнем ключе playlist_url. */
    protected fun legacyPlaylist(path: String = "/pl.m3u") {
        Repo.prefs.playlistUrl = base + path
        Repo.init(org.robolectric.RuntimeEnvironment.getApplication())
        Repo.prefs.lastUpdateCheck = System.currentTimeMillis() // тест не ходит в сеть за обновлениями
        waitFor(15_000, "плейлист") { Repo.playlist.value.channels.isNotEmpty() }
        waitFor(15_000, "телепрограмма") { Repo.epg.value.byId.isNotEmpty() }
    }

    protected fun BaseActivity.key(code: Int, times: Int = 1, wait: Long = 120) = repeat(times) {
        dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
        dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
        idle(wait)
    }

    /** Удержание OK 650 мс — меню действий. */
    protected fun BaseActivity.hold() {
        dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER))
        idle(800)
        dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_CENTER))
        idle(100)
    }

    protected fun waitSplash(a: MainActivity) = waitFor(20_000, "заставка скрылась") { a.splashDone && a.splash.visibility == View.GONE }

    /** Вместо видео — статичный кадр, чтобы было видно, как интерфейс лежит поверх картинки. Тестовые потоки не играют. */
    protected fun MainActivity.fakeVideo() {
        val bytes = res("frame.jpg")
        playerView.visibility = View.INVISIBLE
        root.background = BitmapDrawable(resources, BitmapFactory.decodeByteArray(bytes, 0, bytes.size))
        showState(null)
    }

    protected fun shot(activity: android.app.Activity, name: String) {
        idle(400)
        val v = activity.window.decorView
        val bmp = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888)
        // Аппаратный рендер всего окна — как на устройстве (View.draw в Robolectric отдаёт устаревшие слои).
        var done = false
        android.view.PixelCopy.request(activity.window, bmp, { done = true }, android.os.Handler(Looper.getMainLooper()))
        val end = System.currentTimeMillis() + 5000
        while (!done && System.currentTimeMillis() < end) idle(20)
        File(outDir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        println("SHOT: $name ${v.width}x${v.height} copied=$done")
    }
}
