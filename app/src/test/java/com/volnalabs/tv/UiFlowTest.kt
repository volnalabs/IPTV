package com.volnalabs.tv

import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.volnalabs.tv.data.Repo
import com.volnalabs.tv.ui.MainActivity
import com.volnalabs.tv.ui.SettingsActivity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import java.net.InetAddress
import java.net.ServerSocket
import kotlin.concurrent.thread
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@RunWith(RobolectricTestRunner::class)
@LooperMode(LooperMode.Mode.PAUSED)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-land-television")
class UiFlowTest {

    private lateinit var server: ServerSocket
    private val routes = HashMap<String, String>()
    private val requests = mutableListOf<String>()

    private fun xmltv(t: Long): String = SimpleDateFormat("yyyyMMddHHmmss Z", Locale.US)
        .apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date(t))

    @Before fun startServer() {
        val now = System.currentTimeMillis()
        val hour = 3_600_000L
        val base = "http://127.0.0.1:%d"
        server = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
        val port = server.localPort
        val m3u = buildString {
            appendLine("""#EXTM3U url-tvg="${base.format(port)}/epg.xml"""")
            var n = 0
            for (g in listOf("Федеральные", "Кино", "Спорт")) for (i in 1..6) {
                n++
                val archive = if (n % 2 == 1) """ catchup="shift" catchup-days="3"""" else ""
                appendLine("""#EXTINF:0 tvg-id="$n"$archive group-title="$g", $g $i""")
                appendLine("${base.format(port)}/live/$n.m3u8")
            }
        }
        val epg = buildString {
            appendLine("""<?xml version="1.0" encoding="utf-8"?><tv>""")
            for (n in 1..18) {
                appendLine("""<channel id="$n"><display-name>Ch $n</display-name></channel>""")
                var t = now - 30 * hour
                while (t < now + 20 * hour) {
                    appendLine("""<programme start="${xmltv(t)}" stop="${xmltv(t + hour)}" channel="$n"><title>Передача ${(t / hour) % 100}</title></programme>""")
                    t += hour
                }
            }
            appendLine("</tv>")
        }
        routes["/pl.m3u"] = m3u
        routes["/epg.xml"] = epg
        thread(isDaemon = true) {
            while (!server.isClosed) {
                val sock = try { server.accept() } catch (e: Exception) { break }
                thread(isDaemon = true) {
                    sock.use { c ->
                        val reader = c.getInputStream().bufferedReader()
                        val line = reader.readLine() ?: return@use
                        while (true) { val h = reader.readLine() ?: break; if (h.isEmpty()) break }
                        val uri = line.split(' ').getOrElse(1) { "/" }
                        synchronized(requests) { requests += uri }
                        val body = routes[uri.substringBefore('?')]
                        val out = c.getOutputStream()
                        if (body == null) out.write("HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray())
                        else {
                            val bytes = body.toByteArray()
                            out.write("HTTP/1.1 200 OK\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray())
                            out.write(bytes)
                        }
                        out.flush()
                    }
                }
            }
        }
    }

    @After fun stop() = server.close()

    private fun idle(ms: Long = 0) {
        val end = System.currentTimeMillis() + ms
        do {
            shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(20))
            Thread.sleep(20)
        } while (System.currentTimeMillis() < end)
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun waitFor(timeoutMs: Long, what: String, cond: () -> Boolean) {
        val end = System.currentTimeMillis() + timeoutMs
        while (!cond()) {
            if (System.currentTimeMillis() > end) throw AssertionError("Не дождались: $what")
            idle(50)
        }
    }

    private fun MainActivity.key(code: Int, times: Int = 1) = repeat(times) {
        val handled = dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
        // Как ViewRootImpl на устройстве: необработанные стрелки двигают фокус.
        val dir = when (code) {
            KeyEvent.KEYCODE_DPAD_UP -> View.FOCUS_UP
            KeyEvent.KEYCODE_DPAD_DOWN -> View.FOCUS_DOWN
            KeyEvent.KEYCODE_DPAD_LEFT -> View.FOCUS_LEFT
            KeyEvent.KEYCODE_DPAD_RIGHT -> View.FOCUS_RIGHT
            else -> 0
        }
        if (!handled && dir != 0) window.decorView.findFocus()?.focusSearch(dir)?.requestFocus(dir)
        dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
        idle(200)
    }

    private fun MainActivity.focusedIn(): Int? {
        val f = window.decorView.findFocus() ?: return null
        var v: View? = f
        while (v != null) {
            val p = v.parent
            if (p is RecyclerView) return p.id
            v = p as? View
        }
        return null
    }

    private fun MainActivity.focusedText(): String {
        val f = window.decorView.findFocus() ?: return "<нет фокуса>"
        if (f is android.widget.EditText) return "<поле поиска>"
        val name = f.findViewById<TextView>(R.id.name) ?: f.findViewById(R.id.title)
        return name?.text?.toString() ?: f.javaClass.simpleName
    }

    @Test fun fullRemoteFlow() {
        val port = server.localPort
        // 1. Настройки: без плейлиста открывается экран настроек
        val settings = Robolectric.buildActivity(SettingsActivity::class.java).setup().get()
        idle(300)
        val remote = settings.findViewById<TextView>(R.id.remoteUrl).text.toString()
        println("UI: settings remote=$remote")
        settings.findViewById<TextView>(R.id.playlistUrl).text = "http://127.0.0.1:$port/pl.m3u"
        settings.findViewById<View>(R.id.save).performClick()
        idle(100)
        assertTrue(settings.isFinishing)

        waitFor(15_000, "плейлист") { Repo.playlist.value.channels.size == 18 }
        waitFor(15_000, "телепрограмма") { Repo.epg.value.byId.size == 18 }
        println("UI: playlist=${Repo.playlist.value.channels.size} epg=${Repo.epg.value.byId.size} state=${Repo.epgState.value}")

        // 2. Главный экран: заставка, затем включается первый канал и показывается плашка
        Repo.prefs.lastUpdateCheck = System.currentTimeMillis() // тест не ходит в сеть за обновлениями
        val a = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        idle(300)
        assertEquals(View.VISIBLE, a.findViewById<View>(R.id.splash).visibility)
        waitFor(20_000, "заставка скрылась") { a.findViewById<View>(R.id.splash).visibility == View.GONE }
        a.findViewById<View>(R.id.message).visibility = View.GONE
        val info = a.findViewById<View>(R.id.infoBar)
        val name = a.findViewById<TextView>(R.id.infoName)
        val panel = a.findViewById<View>(R.id.panel)
        assertEquals("Федеральные 1", name.text.toString())
        idle(500)
        assertEquals(View.VISIBLE, info.visibility)
        println("UI: info now='${a.findViewById<TextView>(R.id.infoNow).text}' next='${a.findViewById<TextView>(R.id.infoNext).text}' badge='${a.findViewById<TextView>(R.id.infoBadge).text}'")
        assertTrue(a.findViewById<TextView>(R.id.infoNow).text.contains("Передача"))

        // 3. Переключение вверх/вниз
        a.key(KeyEvent.KEYCODE_DPAD_UP)
        assertEquals("Федеральные 2", name.text.toString())
        a.key(KeyEvent.KEYCODE_DPAD_DOWN, 2)
        assertEquals("Спорт 6", name.text.toString()) // по кругу во «Все каналы»

        // 4. Номер канала цифрами
        a.key(KeyEvent.KEYCODE_1); a.key(KeyEvent.KEYCODE_0)
        idle(1700)
        assertEquals("Кино 4", name.text.toString())

        // 5. Панель: ОК открывает, фокус на текущем канале
        a.key(KeyEvent.KEYCODE_DPAD_CENTER)
        assertEquals(View.VISIBLE, panel.visibility)
        assertEquals(R.id.channels, a.focusedIn())
        println("UI: panel focus='${a.focusedText()}' title='${a.findViewById<TextView>(R.id.channelsTitle).text}'")
        assertEquals("Кино 4", a.focusedText())

        // вниз по каналам, вправо — в программу (фокус на «сейчас»)
        a.key(KeyEvent.KEYCODE_DPAD_DOWN)
        assertEquals("Кино 5", a.focusedText())
        a.key(KeyEvent.KEYCODE_DPAD_RIGHT)
        assertEquals(R.id.programmes, a.focusedIn())
        println("UI: epg title='${a.findViewById<TextView>(R.id.epgTitle).text}' focus='${a.focusedText()}'")
        // вверх по программе — в прошлое, ОК — архив (у «Кино 5» он есть)
        a.key(KeyEvent.KEYCODE_DPAD_UP, 2)
        val archiveTitle = a.focusedText()
        a.key(KeyEvent.KEYCODE_DPAD_CENTER)
        assertEquals(View.GONE, panel.visibility)
        assertEquals("Кино 5", name.text.toString())
        assertEquals("ЗАПИСЬ", a.findViewById<TextView>(R.id.infoBadge).text.toString())
        println("UI: archive '$archiveTitle' -> '${a.findViewById<TextView>(R.id.infoNow).text}'")
        idle(500)
        val archiveReq = synchronized(requests) { requests.lastOrNull { it.contains("utc=") } }
        println("UI: archive request=$archiveReq")
        assertNotNull(archiveReq)
        assertTrue(archiveReq!!.startsWith("/live/11.m3u8?utc="))

        // перемотка и «Назад» — в эфир
        a.key(KeyEvent.KEYCODE_DPAD_RIGHT)
        a.onBackPressed(); idle(200)
        assertEquals(View.GONE, a.findViewById<TextView>(R.id.infoBadge).visibility)

        // 6. Группы: влево из каналов → группы, вниз, вправо → каналы этой группы
        a.key(KeyEvent.KEYCODE_DPAD_CENTER)
        a.key(KeyEvent.KEYCODE_DPAD_LEFT)
        assertEquals(R.id.groups, a.focusedIn())
        println("UI: group focus='${a.focusedText()}'")
        a.key(KeyEvent.KEYCODE_DPAD_DOWN, 2) // Все → Федеральные → Кино → ... фокус на 3-й группе
        idle(300)
        val group = a.focusedText()
        a.key(KeyEvent.KEYCODE_DPAD_RIGHT)
        assertEquals(R.id.channels, a.focusedIn())
        println("UI: group '$group' -> channel focus='${a.focusedText()}', title='${a.findViewById<TextView>(R.id.channelsTitle).text}'")
        assertTrue(a.focusedText().startsWith(group))

        // 6а. Поиск: влево → группы → «Поиск» (первый пункт) → ввод → вниз к результатам
        a.key(KeyEvent.KEYCODE_DPAD_LEFT)
        a.key(KeyEvent.KEYCODE_DPAD_UP, 6)
        assertEquals("Поиск", a.focusedText())
        a.key(KeyEvent.KEYCODE_DPAD_CENTER)
        val field = a.findViewById<android.widget.EditText>(R.id.searchField)
        assertTrue(field.isFocused)
        field.setText("СПОРТ")
        idle(200)
        println("UI: search hint='${a.findViewById<TextView>(R.id.channelsHint).text}'")
        assertEquals(6, a.findViewById<RecyclerView>(R.id.channels).adapter!!.itemCount)
        a.key(KeyEvent.KEYCODE_DPAD_DOWN)
        assertEquals("Спорт 1", a.focusedText())
        a.key(KeyEvent.KEYCODE_DPAD_UP)
        assertTrue(field.isFocused)
        field.setText("")
        a.key(KeyEvent.KEYCODE_BACK) // пустой поиск + «Назад» → обратно ко всем каналам
        assertEquals(View.GONE, a.findViewById<View>(R.id.searchBox).visibility)
        assertEquals(R.id.channels, a.focusedIn())
        a.key(KeyEvent.KEYCODE_DPAD_LEFT)
        a.key(KeyEvent.KEYCODE_DPAD_DOWN, 2)
        idle(300)
        a.key(KeyEvent.KEYCODE_DPAD_RIGHT)
        assertTrue(a.focusedText().startsWith(group))

        // 7. Избранное: долгое ОК (MENU на канале) → в группах появляется «★ Избранное»
        val fav = a.focusedText()
        a.key(KeyEvent.KEYCODE_MENU)
        assertTrue(Repo.prefs.favorites.isNotEmpty())
        println("UI: favorite added '$fav', toast='${a.findViewById<TextView>(R.id.toast).text}'")

        // ОК на канале — играет, панель закрыта; дальше вверх/вниз ходим внутри группы
        a.key(KeyEvent.KEYCODE_DPAD_CENTER)
        assertEquals(View.GONE, panel.visibility)
        assertEquals(fav, name.text.toString())
        a.key(KeyEvent.KEYCODE_DPAD_UP)
        assertTrue(name.text.toString().startsWith(group))

        // 8. Ошибка потока: сервер отдаёт 404 → сообщение после повторов
        var lastMsg = ""
        try {
            waitFor(40_000, "сообщение об ошибке") {
                val m = a.findViewById<TextView>(R.id.message)
                val t = "${m.visibility == View.VISIBLE}:${m.text}"
                if (t != lastMsg) { println("UI-MSG: $t"); lastMsg = t }
                m.visibility == View.VISIBLE && m.text.contains("Не удалось")
            }
        } catch (e: AssertionError) {
            println("UI-MSG: final requests=" + synchronized(requests) { requests.takeLast(8) })
            throw e
        }
        println("UI: error='${a.findViewById<TextView>(R.id.message).text.toString().replace('\n', ' ')}'")

        // 9. Двойное «Назад» — выход
        a.onBackPressed(); idle(100)
        a.onBackPressed(); idle(100)
        a.onBackPressed(); idle(100)
        println("UI: finishing=${a.isFinishing}")
        assertTrue(a.isFinishing)
    }
}
