package com.volnalabs.tv

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.widget.TextView
import com.volnalabs.tv.data.Repo
import com.volnalabs.tv.ui.MainActivity
import com.volnalabs.tv.ui.SettingsActivity
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode
import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.concurrent.thread

/**
 * Снимает скриншоты экранов в разрешении 1920×1080 на тестовых данных.
 * Запуск: IPTV_SHOTS=/путь ./gradlew :app:testDebugUnitTest --tests '*ScreenshotTest*'
 * Видео заменено картинкой-заглушкой, логотипы — нейтральные плашки.
 */
@RunWith(RobolectricTestRunner::class)
@LooperMode(LooperMode.Mode.PAUSED)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-land-television-xhdpi")
class ScreenshotTest {

    private lateinit var server: ServerSocket
    private val routes = HashMap<String, ByteArray>()
    private val outDir = File(System.getenv("IPTV_SHOTS") ?: "build/shots")

    private val channels = listOf(
        "Федеральные" to listOf("Первый канал", "Россия 1", "НТВ", "Пятый канал", "Россия К", "Россия 24", "ТВ Центр", "Звезда"),
        "Кино" to listOf("Кинопремьера", "Киносвидание", "Кинохит", "Наше новое кино", "Комедия", "Мужское кино"),
        "Спорт" to listOf("Матч! ТВ", "Матч! Арена", "Футбол 1", "Бокс ТВ"),
        "Детские" to listOf("Карусель", "Мульт", "Детский мир"),
        "Познавательные" to listOf("Моя планета", "Наука", "История"),
    )
    private val titles = listOf(
        "Новости", "Доброе утро", "Документальный фильм «Северный путь»", "Сериал «Тихая гавань». 12-я серия",
        "Вечерний выпуск", "Художественный фильм «Дорога домой»", "Футбол. Обзор тура", "Время новостей",
        "Ток-шоу «Мнение»", "Мультфильмы", "Погода", "Сериал «Город у моря». 3-я серия", "Концерт", "Итоги дня",
    )

    private fun xmltv(t: Long): String = SimpleDateFormat("yyyyMMddHHmmss Z", Locale.US)
        .apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date(t))

    private fun res(name: String) = javaClass.classLoader!!.getResourceAsStream("shots/$name")!!.readBytes()

    @Before fun setUp() {
        server = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
        val base = "http://127.0.0.1:${server.localPort}"
        val now = System.currentTimeMillis()
        val m3u = StringBuilder("#EXTM3U url-tvg=\"$base/epg.xml\"\n")
        val epg = StringBuilder("<?xml version=\"1.0\" encoding=\"utf-8\"?><tv>\n")
        var n = 0
        for ((group, names) in channels) for (name in names) {
            n++
            val archive = if (n % 3 != 0) " catchup=\"shift\" catchup-days=\"3\"" else ""
            m3u.append("#EXTINF:0 tvg-id=\"$n\" tvg-logo=\"$base/logo${(n - 1) % 24 + 1}.png\"$archive group-title=\"$group\", $name\n$base/live/$n.m3u8\n")
            epg.append("<channel id=\"$n\"><display-name>$name</display-name></channel>\n")
            var t = now - 30 * 3_600_000L - (n * 7 % 50) * 60_000L
            var k = n
            while (t < now + 20 * 3_600_000L) {
                val len = (35 + (k * 37 % 5) * 15) * 60_000L
                epg.append("<programme start=\"${xmltv(t)}\" stop=\"${xmltv(t + len)}\" channel=\"$n\"><title>${titles[k % titles.size]}</title></programme>\n")
                t += len; k++
            }
        }
        epg.append("</tv>")
        routes["/pl.m3u"] = m3u.toString().toByteArray()
        routes["/epg.xml"] = epg.toString().toByteArray()
        for (i in 1..24) routes["/logo$i.png"] = res("logo$i.png")

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
                        else {
                            out.write("HTTP/1.1 200 OK\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n".toByteArray())
                            out.write(body)
                        }
                        out.flush()
                    }
                }
            }
        }
        outDir.mkdirs()
    }

    @After fun tearDown() = server.close()

    private fun idle(ms: Long = 0) {
        val end = System.currentTimeMillis() + ms
        do {
            shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(20))
            Thread.sleep(20)
        } while (System.currentTimeMillis() < end)
    }

    private fun MainActivity.key(code: Int, times: Int = 1) = repeat(times) {
        val handled = dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
        val dir = when (code) {
            KeyEvent.KEYCODE_DPAD_UP -> View.FOCUS_UP
            KeyEvent.KEYCODE_DPAD_DOWN -> View.FOCUS_DOWN
            KeyEvent.KEYCODE_DPAD_LEFT -> View.FOCUS_LEFT
            KeyEvent.KEYCODE_DPAD_RIGHT -> View.FOCUS_RIGHT
            else -> 0
        }
        if (!handled && dir != 0) window.decorView.findFocus()?.focusSearch(dir)?.requestFocus(dir)
        dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
        idle(150)
    }

    private fun shot(activity: android.app.Activity, name: String) {
        idle(500)
        // Тестовые потоки не играют — прячем сообщение об этом, чтобы оно не лезло в кадр.
        activity.findViewById<View?>(R.id.message)?.visibility = View.GONE
        activity.findViewById<View?>(R.id.loading)?.visibility = View.GONE
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

    /** Вместо видео — статичный кадр, чтобы было видно, как интерфейс лежит поверх картинки. */
    private fun MainActivity.fakeVideo() {
        val frame = BitmapFactory.decodeByteArray(res("frame.jpg"), 0, res("frame.jpg").size)
        findViewById<View>(R.id.player).visibility = View.INVISIBLE
        findViewById<View>(R.id.root).background = BitmapDrawable(resources, frame)
        findViewById<View>(R.id.message).visibility = View.GONE
        findViewById<View>(R.id.loading).visibility = View.GONE
    }

    private fun waitSplash(a: MainActivity) {
        val end = System.currentTimeMillis() + 20_000
        while (a.findViewById<View>(R.id.splash).visibility != View.GONE && System.currentTimeMillis() < end) idle(100)
    }

    private fun focusName(a: android.app.Activity): String {
        val f = a.window.decorView.findFocus() ?: return "<нет>"
        return (f.findViewById<TextView>(R.id.name) ?: f.findViewById(R.id.title))?.text?.toString() ?: f.javaClass.simpleName
    }

    @Test fun screens() {
        val port = server.localPort
        Repo.prefs.lastChannelKey = null
        // Автообновление включено, как у пользователя; отметка «только что проверяли» — чтобы тест не ходил в сеть.
        Repo.prefs.autoUpdate = true
        Repo.prefs.lastUpdateCheck = System.currentTimeMillis()

        // Первый запуск: настройки без плейлиста
        val s = Robolectric.buildActivity(SettingsActivity::class.java).setup().get()
        idle(300)
        shot(s, "08_settings_first_run")
        s.findViewById<TextView>(R.id.playlistUrl).text = "http://127.0.0.1:$port/pl.m3u"
        s.findViewById<View>(R.id.save).performClick()
        val end = System.currentTimeMillis() + 20_000
        while (Repo.epg.value.byId.isEmpty() && System.currentTimeMillis() < end) idle(100)

        // Заставка
        val a = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        idle(1100)
        shot(a, "01_splash")
        waitSplash(a)

        // Эфир с плашкой
        a.key(KeyEvent.KEYCODE_DPAD_UP)
        a.fakeVideo(); idle(1200)
        a.key(KeyEvent.KEYCODE_INFO); a.key(KeyEvent.KEYCODE_INFO)
        a.fakeVideo()
        shot(a, "02_live")

        // Номер канала цифрами: набрали «1» — видно, какие каналы подходят
        a.key(KeyEvent.KEYCODE_1)
        a.fakeVideo()
        shot(a, "02_number")
        a.key(KeyEvent.KEYCODE_BACK)

        // Список каналов
        a.key(KeyEvent.KEYCODE_DPAD_CENTER)
        a.key(KeyEvent.KEYCODE_DPAD_DOWN, 2)
        idle(1200)
        a.fakeVideo()
        println("SHOT-FOCUS channels: ${focusName(a)}")
        shot(a, "03_channels")

        // Телепрограмма канала
        a.key(KeyEvent.KEYCODE_DPAD_RIGHT)
        a.key(KeyEvent.KEYCODE_DPAD_UP, 2)
        a.fakeVideo()
        println("SHOT-FOCUS programmes: ${focusName(a)}")
        shot(a, "04_programme")

        // Запись из архива
        a.key(KeyEvent.KEYCODE_DPAD_CENTER)
        idle(300)
        a.fakeVideo()
        a.key(KeyEvent.KEYCODE_DPAD_RIGHT)
        a.fakeVideo()
        shot(a, "05_archive")
        a.onBackPressed(); idle(200)

        // Группы
        a.key(KeyEvent.KEYCODE_DPAD_CENTER)
        a.key(KeyEvent.KEYCODE_DPAD_LEFT)
        a.key(KeyEvent.KEYCODE_DPAD_DOWN, 2)
        idle(800)
        a.fakeVideo()
        println("SHOT-FOCUS groups: ${focusName(a)}")
        shot(a, "06_groups")

        // Поиск
        a.key(KeyEvent.KEYCODE_DPAD_UP, 3)
        println("SHOT-FOCUS search entry: ${focusName(a)}")
        a.key(KeyEvent.KEYCODE_DPAD_CENTER)
        a.findViewById<android.widget.EditText>(R.id.searchField).setText("кино")
        idle(800)
        a.key(KeyEvent.KEYCODE_DPAD_DOWN)
        idle(600)
        a.fakeVideo()
        println("SHOT-FOCUS search: ${focusName(a)}")
        shot(a, "07_search")

        // Настройки с плейлистом
        Repo.prefs.autoUpdate = true
        Repo.prefs.lastUpdateCheck = System.currentTimeMillis()
        val s2 = Robolectric.buildActivity(SettingsActivity::class.java).setup().get()
        idle(500)
        shot(s2, "09_settings")

        // Окно обновления (состояние «скачано» подставляем вручную)
        val field = com.volnalabs.tv.data.Updater::class.java.getDeclaredField("_state").apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        (field.get(com.volnalabs.tv.data.Updater) as kotlinx.coroutines.flow.MutableStateFlow<com.volnalabs.tv.data.Updater.State>).value =
            com.volnalabs.tv.data.Updater.State.Ready("1.0.7", File(outDir, "none.apk"))
        val u = Robolectric.buildActivity(com.volnalabs.tv.ui.UpdateActivity::class.java).setup().get()
        idle(400)
        shot(u, "10_update")
    }

    /** Телефон: управление касаниями, узкий экран. */
    @Test
    @Config(sdk = [34], qualifiers = "w780dp-h360dp-land-xhdpi")
    fun phone() {
        val app = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.app.Application>()
        shadowOf(app.packageManager).setSystemFeature(android.content.pm.PackageManager.FEATURE_TOUCHSCREEN, true)
        Repo.prefs.lastUpdateCheck = System.currentTimeMillis()
        Repo.setSources("http://127.0.0.1:${server.localPort}/pl.m3u", "")
        val end = System.currentTimeMillis() + 20_000
        while (Repo.epg.value.byId.isEmpty() && System.currentTimeMillis() < end) idle(100)

        val a = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        waitSplash(a)
        a.fakeVideo(); idle(800); a.fakeVideo()
        shot(a, "p1_live")
        val panel = a.findViewById<View>(R.id.panel)
        a.findViewById<View>(R.id.root).performClick(); idle(600)
        org.junit.Assert.assertEquals("касание открывает панель", View.VISIBLE, panel.visibility)
        org.junit.Assert.assertEquals(View.VISIBLE, a.findViewById<View>(R.id.touchBar).visibility)
        a.fakeVideo(); shot(a, "p2_channels")
        org.junit.Assert.assertEquals("разделы на телефоне — «таблетками», а не колонкой", View.GONE, a.findViewById<View>(R.id.groupsColumn).visibility)
        a.findViewById<View>(R.id.tabEpg).performClick(); idle(600)
        org.junit.Assert.assertEquals(View.VISIBLE, a.findViewById<View>(R.id.epgColumn).visibility)
        org.junit.Assert.assertEquals(View.GONE, a.findViewById<View>(R.id.channelsColumn).visibility)
        a.fakeVideo(); shot(a, "p3_programme")
        a.findViewById<View>(R.id.tabChannels).performClick(); idle(400)
        org.junit.Assert.assertEquals(View.GONE, a.findViewById<View>(R.id.epgColumn).visibility)
        val chips = a.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.chips)
        org.junit.Assert.assertEquals(View.VISIBLE, chips.visibility)
        chips.getChildAt(2).performClick(); idle(600)
        org.junit.Assert.assertEquals("Кино", a.findViewById<TextView>(R.id.channelsTitle).text.toString())
        a.fakeVideo(); shot(a, "p4_group")
        // касание канала включает его и закрывает панель
        val list = a.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.channels)
        list.getChildAt(2).performClick(); idle(400)
        org.junit.Assert.assertEquals(View.GONE, panel.visibility)
        a.findViewById<View>(R.id.root).performClick(); idle(400)
        panel.performClick(); idle(200)
        org.junit.Assert.assertEquals("касание мимо карточек закрывает панель", View.GONE, panel.visibility)

        val s = Robolectric.buildActivity(SettingsActivity::class.java).setup().get()
        idle(500)
        shot(s, "p5_settings")
    }

    /** Телефон вертикально: видео сверху, список каналов всегда под ним, QR-кода в настройках нет. */
    @Test
    @Config(sdk = [34], qualifiers = "w360dp-h760dp-port-xhdpi")
    fun phonePortrait() {
        val app = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.app.Application>()
        shadowOf(app.packageManager).setSystemFeature(android.content.pm.PackageManager.FEATURE_TOUCHSCREEN, true)
        Repo.prefs.lastUpdateCheck = System.currentTimeMillis()
        Repo.setSources("http://127.0.0.1:${server.localPort}/pl.m3u", "")
        val end = System.currentTimeMillis() + 20_000
        while (Repo.epg.value.byId.isEmpty() && System.currentTimeMillis() < end) idle(100)

        val a = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        waitSplash(a)
        val frame = BitmapFactory.decodeByteArray(res("frame.jpg"), 0, res("frame.jpg").size)
        val player = a.findViewById<View>(R.id.player)
        val cover = android.widget.ImageView(a).apply { setImageBitmap(frame); scaleType = android.widget.ImageView.ScaleType.CENTER_CROP }
        a.findViewById<android.widget.FrameLayout>(R.id.root).addView(cover, 1, android.widget.FrameLayout.LayoutParams(player.layoutParams.width, player.layoutParams.height))
        idle(800)
        val panel = a.findViewById<View>(R.id.panel)
        val channels = a.findViewById<View>(R.id.channelsColumn)
        org.junit.Assert.assertEquals("список каналов виден сразу", View.VISIBLE, panel.visibility)
        org.junit.Assert.assertTrue("видео занимает верх экрана", player.height in 1 until a.window.decorView.height / 2)
        org.junit.Assert.assertEquals("плашка поверх видео не нужна", View.GONE, a.findViewById<View>(R.id.infoBar).visibility)
        shot(a, "v1_channels")
        // касание канала включает его, список остаётся на месте
        val list = a.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.channels)
        list.getChildAt(2).performClick(); idle(400)
        org.junit.Assert.assertEquals(View.VISIBLE, panel.visibility)
        a.findViewById<View>(R.id.tabEpg).performClick(); idle(600)
        org.junit.Assert.assertEquals(View.VISIBLE, a.findViewById<View>(R.id.epgColumn).visibility)
        org.junit.Assert.assertEquals("программа занимает место списка", View.GONE, channels.visibility)
        shot(a, "v2_programme")
        a.onBackPressed(); idle(300)
        org.junit.Assert.assertEquals("«Назад» возвращает к каналам", View.VISIBLE, channels.visibility)
        // группы — ряд «таблеток» над списком
        val chips = a.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.chips)
        org.junit.Assert.assertEquals(View.VISIBLE, chips.visibility)
        chips.getChildAt(2).performClick(); idle(500)
        org.junit.Assert.assertEquals("Кино", a.findViewById<TextView>(R.id.channelsTitle).text.toString())
        org.junit.Assert.assertEquals(6, list.adapter!!.itemCount)
        shot(a, "v3_group")
        // звёздочка в строке — кнопка «в избранное»
        list.getChildAt(1).findViewById<View>(R.id.marks).performClick(); idle(300)
        org.junit.Assert.assertEquals(1, Repo.prefs.favorites.size)
        // поиск: значок в шапке, поле вместо «таблеток»; ищет и по номеру
        a.findViewById<View>(R.id.touchSearch).performClick(); idle(300)
        org.junit.Assert.assertEquals(View.VISIBLE, a.findViewById<View>(R.id.searchBox).visibility)
        org.junit.Assert.assertEquals(View.GONE, chips.visibility)
        a.findViewById<android.widget.EditText>(R.id.searchField).setText("2"); idle(300)
        org.junit.Assert.assertEquals("Россия 1", list.getChildAt(0).findViewById<TextView>(R.id.name).text.toString())
        shot(a, "v4_search")
        a.onBackPressed(); idle(300)
        org.junit.Assert.assertEquals("«Назад» закрывает поиск", View.GONE, a.findViewById<View>(R.id.searchBox).visibility)
        org.junit.Assert.assertEquals(View.VISIBLE, chips.visibility)
        Repo.prefs.favorites = emptySet()

        val s = Robolectric.buildActivity(SettingsActivity::class.java).setup().get()
        idle(500)
        org.junit.Assert.assertEquals("на телефоне QR-кода нет", View.GONE, s.findViewById<View>(R.id.qrBox).visibility)
        shot(s, "v5_settings")
        val pl = Robolectric.buildActivity(com.volnalabs.tv.ui.PluginsActivity::class.java).setup().get()
        idle(400)
        shot(pl, "v6_plugins")
    }
}
