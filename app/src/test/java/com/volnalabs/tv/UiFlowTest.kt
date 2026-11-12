package com.volnalabs.tv

import android.view.KeyEvent
import android.view.View
import com.volnalabs.tv.data.Plugins
import com.volnalabs.tv.data.Repo
import com.volnalabs.tv.ui.MainActivity
import com.volnalabs.tv.ui.SettingsActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

/** Ключевые сценарии SPEC, раздел 1, на телевизоре — только пультом. */
@RunWith(RobolectricTestRunner::class)
@LooperMode(LooperMode.Mode.PAUSED)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-land-television")
class UiFlowTest : UiTestBase() {

    private fun main(): MainActivity {
        val a = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        waitSplash(a)
        waitFor(5000, "канал включился") { a.cur != null }
        return a
    }

    /** Сценарий 1. Первый запуск: ссылка → загрузка → первый канал; ошибка оставляет на экране с причиной. */
    @Test fun firstRun() {
        assertNull(Repo.active)
        val s = Robolectric.buildActivity(SettingsActivity::class.java).setup().get()
        idle(300)
        s.key(KeyEvent.KEYCODE_DPAD_CENTER) // «Ввести ссылку пультом»
        idle(200)
        fun setField(text: String) { findEdit(s.root)!!.setText(text); idle(50) }
        setField("$base/missing.m3u")
        s.key(KeyEvent.KEYCODE_DPAD_DOWN); s.key(KeyEvent.KEYCODE_DPAD_CENTER)
        waitFor(10_000, "ошибка ссылки") { textOnScreen(s.root).contains("Сервер ответил 404 — по этой ссылке плейлиста нет.") }
        assertNull(Repo.active)
        setField("$base/pl.m3u")
        s.key(KeyEvent.KEYCODE_DPAD_DOWN); s.key(KeyEvent.KEYCODE_DPAD_CENTER)
        waitFor(10_000, "плейлист сохранён") { s.isFinishing }
        assertEquals("127.0.0.1", Repo.active?.name) // пустое название — имя сервера из ссылки
        assertEquals("$base/pl.m3u", Repo.prefs.playlistUrl) // старый ключ равен активному плейлисту
        waitFor(10_000, "каналы") { Repo.playlist.value.channels.size == 20 }
    }

    /** Сценарии 2–4: включить канал из гида, быстрое переключение, номер канала. */
    @Test fun guideZapNumber() {
        legacyPlaylist()
        assertEquals("Мой плейлист", Repo.active?.name) // миграция прежней ссылки
        val a = main()
        assertEquals("Первый канал", a.cur?.name)
        assertTrue("инфо при запуске", a.t.osdShown())

        // ▲▼: десять нажатий за 2 с — один запуск потока, на десятом канале
        repeat(10) { a.key(KeyEvent.KEYCODE_DPAD_UP, wait = 60) }
        assertEquals("пока жмут — поток не переключается", "Первый канал", a.cur?.name)
        assertEquals(11, a.sel?.number)
        idle(600)
        assertNull(a.sel)
        assertEquals(11, a.cur?.number)

        // Номер «1»: подсказки 1, 10, 11, 12 → ▼ → OK
        a.key(KeyEvent.KEYCODE_1)
        assertTrue(a.t.numOpen())
        a.key(KeyEvent.KEYCODE_DPAD_DOWN)
        a.key(KeyEvent.KEYCODE_DPAD_CENTER)
        idle(200)
        assertFalse(a.t.numOpen())
        assertEquals(10, a.cur?.number)
        // Нет канала с таким номером: через 2 с слой закрывается без переключения
        a.key(KeyEvent.KEYCODE_9); a.key(KeyEvent.KEYCODE_9)
        assertTrue(textOnScreen(a.t.numpad).contains("Канала с номером 99 нет"))
        idle(2300)
        assertFalse(a.t.numOpen())
        assertEquals(10, a.cur?.number)

        // Номер без ведущих нулей
        a.showInfo()
        assertTrue(textOnScreen(a.t.osd).contains("10"))
        assertFalse(textOnScreen(a.t.osd).contains("010"))
        // ◀ в эфире — гид целиком (мини-гида и панели кнопок в эфире нет)
        a.showState(null) // тестовые потоки не играют: без этого OK нажал бы кнопку блока ошибки
        a.key(KeyEvent.KEYCODE_DPAD_LEFT)
        assertEquals("channels", a.zone)
        assertTrue(a.t.guideShown())
        a.key(KeyEvent.KEYCODE_BACK)
        assertEquals("play", a.zone)
        // OK в эфире — тоже гид, фокус на играющем канале
        a.key(KeyEvent.KEYCODE_DPAD_CENTER)
        assertEquals("channels", a.zone)
        assertEquals(a.cur?.key, a.list[a.ci].key)
        assertTrue("в разметке не больше пяти плиток", a.t.gChans.childCount <= 5 + 2)
        // ▲ с «Поиска» — шапка: плейлист один, поэтому фокус сразу на шестерёнке «Настройки»; ◀ на неё же
        a.key(KeyEvent.KEYCODE_DPAD_LEFT)
        assertEquals("groups", a.zone)
        assertTrue("«Настройки» в меню больше нет", a.menu.none { it.id == "settings" })
        a.key(KeyEvent.KEYCODE_DPAD_UP, 10)
        assertEquals(0, a.gi)
        assertEquals("head", a.zone)
        assertEquals(1, a.t.headIndex)
        a.key(KeyEvent.KEYCODE_DPAD_LEFT)
        assertEquals(1, a.t.headIndex)
        a.key(KeyEvent.KEYCODE_DPAD_DOWN)
        assertEquals("groups", a.zone)
        a.key(KeyEvent.KEYCODE_DPAD_DOWN, 3) // «Все каналы»
        a.key(KeyEvent.KEYCODE_DPAD_RIGHT)
        assertEquals("channels", a.zone)
        a.key(KeyEvent.KEYCODE_DPAD_DOWN, 2)
        val target = a.list[a.ci]
        a.key(KeyEvent.KEYCODE_DPAD_CENTER)
        assertEquals("play", a.zone)
        assertEquals(target.key, a.cur?.key)
        assertFalse(a.t.guideShown())
    }

    /** Сценарии 5–6: запись передачи и избранное через меню удержания OK. */
    @Test fun archiveAndFavourites() {
        legacyPlaylist()
        val a = main()
        a.key(KeyEvent.KEYCODE_DPAD_RIGHT) // программа текущего канала
        assertEquals("epg", a.zone)
        assertTrue(a.prog.isNotEmpty())
        val now = System.currentTimeMillis()
        assertTrue("фокус на «идёт»", a.prog[a.pi].start <= now && a.prog[a.pi].stop > now)
        a.key(KeyEvent.KEYCODE_DPAD_UP) // прошедшая передача со значком записи
        assertTrue(a.prog[a.pi].arch)
        val title = a.prog[a.pi].title
        a.key(KeyEvent.KEYCODE_DPAD_CENTER)
        assertEquals("play", a.zone)
        assertEquals(title, a.arch?.title)
        assertTrue(textOnScreen(a.t.archTag).startsWith("Запись · сегодня"))
        a.key(KeyEvent.KEYCODE_DPAD_CENTER) // пауза и панель с фокусом на «Пауза»
        assertTrue(a.paused)
        assertEquals("ctl", a.zone)
        a.key(KeyEvent.KEYCODE_BACK) // скрыть панель
        a.key(KeyEvent.KEYCODE_BACK) // в эфир
        assertNull(a.arch)
        assertEquals("Прямой эфир", a.toastText)
        // ▲ с первой передачи — строка даты
        a.key(KeyEvent.KEYCODE_DPAD_RIGHT)
        repeat(a.pi + 1) { a.key(KeyEvent.KEYCODE_DPAD_UP) }
        assertEquals("date", a.zone)
        a.key(KeyEvent.KEYCODE_BACK)
        assertEquals("epg", a.zone)
        a.key(KeyEvent.KEYCODE_DPAD_LEFT)
        assertEquals("channels", a.zone)

        // Удержание OK на плитке — меню действий, «Добавить в избранное» → тост
        a.hold()
        assertTrue(a.popOpen)
        assertEquals("Смотреть", a.popTitles.first())
        a.key(KeyEvent.KEYCODE_DPAD_DOWN)
        a.key(KeyEvent.KEYCODE_DPAD_CENTER)
        assertFalse(a.popOpen)
        assertTrue(a.toastText.endsWith("добавлен в избранное"))
        assertEquals(1, Repo.prefs.favorites.size)
        // Зелёная — то же сразу
        a.key(KeyEvent.KEYCODE_PROG_GREEN)
        assertTrue(a.toastText.endsWith("убран из избранного"))
        assertEquals(0, Repo.prefs.favorites.size)
        assertTrue(Repo.prefs.recent.isNotEmpty())
    }

    /** Сценарий 7 и сетка: поиск передачи по телепрограмме, сетка «каналы × время». */
    @Test fun searchAndGrid() {
        legacyPlaylist()
        val a = main()
        a.key(KeyEvent.KEYCODE_PROG_RED)
        assertEquals("search", a.zone)
        // Своя экранная клавиатура: OK на «А» вводит букву, «Стереть» с пульта — стирает, цифры пульта — в строку
        a.key(KeyEvent.KEYCODE_DPAD_CENTER)
        assertEquals("А", a.query)
        a.key(KeyEvent.KEYCODE_DEL)
        assertEquals("", a.query)
        a.key(KeyEvent.KEYCODE_DPAD_DOWN, 6); a.key(KeyEvent.KEYCODE_DPAD_RIGHT) // «EN»
        assertEquals(1, a.t.kc)
        a.key(KeyEvent.KEYCODE_DPAD_LEFT); a.key(KeyEvent.KEYCODE_DPAD_CENTER) // переключение раскладки
        assertTrue(textOnScreen(a.t.kb).contains("РУ"))
        a.key(KeyEvent.KEYCODE_DPAD_CENTER)
        assertTrue(textOnScreen(a.t.kb).contains("EN"))
        a.key(KeyEvent.KEYCODE_DPAD_UP, 6)
        a.t.typeText("футбол")
        idle(200)
        assertTrue(a.foundSections.any { it.name == "Передачи" && it.count > 0 })
        assertTrue(a.foundSections.any { it.name == "Записи" && it.count > 0 })
        a.key(KeyEvent.KEYCODE_DPAD_RIGHT, 8) // с последней клавиши ряда — к результатам
        assertEquals("results", a.zone)
        a.key(KeyEvent.KEYCODE_DPAD_LEFT)
        assertEquals("search", a.zone)
        a.key(KeyEvent.KEYCODE_DPAD_RIGHT)
        assertEquals("results", a.zone)
        val f = a.found[a.fi]
        a.key(KeyEvent.KEYCODE_DPAD_CENTER)
        assertEquals(f.ch?.key, a.cur?.key)
        assertEquals("play", a.zone)

        // Удержание OK → «Найти в сетке телепрограммы»: сетка, фокус на текущей передаче играющего канала
        a.hold()
        a.key(KeyEvent.KEYCODE_DPAD_DOWN, a.popTitles.indexOf("Найти в сетке телепрограммы"))
        a.key(KeyEvent.KEYCODE_DPAD_CENTER)
        assertEquals("grid", a.zone)
        val cells = countCells(a.t.grid)
        assertTrue("в сетке не больше 40 ячеек: $cells", cells in 1..40)
        a.key(KeyEvent.KEYCODE_DPAD_DOWN, 3)
        a.key(KeyEvent.KEYCODE_DPAD_RIGHT, 2)
        a.key(KeyEvent.KEYCODE_BACK)
        assertEquals("channels", a.zone)
        a.key(KeyEvent.KEYCODE_BACK)
        assertEquals("play", a.zone)

        // «Назад» в чистом плеере: скрыть инфо, тост, выход за 2 с
        a.t.hideInfo()
        a.key(KeyEvent.KEYCODE_BACK)
        assertEquals("Нажмите «Назад» ещё раз, чтобы выйти", a.toastText)
        a.key(KeyEvent.KEYCODE_BACK)
        assertTrue(a.isFinishing)
    }

    /** Сценарии 8–9: смена плейлиста в гиде; новая ссылка заменяет список только после проверки. */
    @Test fun playlists() {
        legacyPlaylist()
        val s = Robolectric.buildActivity(SettingsActivity::class.java).setup().get()
        idle(300)
        s.key(KeyEvent.KEYCODE_DPAD_CENTER) // в раздел «Плейлисты»
        s.key(KeyEvent.KEYCODE_DPAD_CENTER) // форма «Мой плейлист»
        val url = findEdits(s.root)[1]
        url.setText("$base/missing.m3u")
        idle(100)
        s.key(KeyEvent.KEYCODE_DPAD_DOWN, 3)
        s.key(KeyEvent.KEYCODE_DPAD_CENTER) // «Проверить и сохранить»
        waitFor(10_000, "ошибка 404") { textOnScreen(s.root).contains("Сервер ответил 404 — по этой ссылке плейлиста нет. Работает прежний список: 20 каналов.") }
        assertEquals("$base/pl.m3u", Repo.active?.url)
        assertEquals(20, Repo.playlist.value.channels.size)
        s.key(KeyEvent.KEYCODE_BACK)
        assertEquals("Изменения не сохранены", s.toastText)

        // Добавить второй плейлист
        s.key(KeyEvent.KEYCODE_DPAD_DOWN)
        s.key(KeyEvent.KEYCODE_DPAD_CENTER)
        val edits = findEdits(s.root)
        edits[0].setText("Спорт"); edits[1].setText("$base/sport.m3u")
        idle(100)
        s.key(KeyEvent.KEYCODE_DPAD_DOWN, 3)
        s.key(KeyEvent.KEYCODE_DPAD_CENTER)
        waitFor(10_000, "сохранён") { Repo.playlists.value.size == 2 }
        assertEquals("Плейлист «Спорт» сохранён · 4 канала", s.toastText)
        assertEquals("Мой плейлист", Repo.active?.name) // активный не меняется

        // Гид: ▲ с «Поиска» → заголовок-плейлист → OK → «Спорт»
        val a = main()
        a.showState(null)
        a.key(KeyEvent.KEYCODE_DPAD_CENTER) // OK в эфире — гид
        a.key(KeyEvent.KEYCODE_DPAD_LEFT)
        a.key(KeyEvent.KEYCODE_DPAD_UP, 6)
        assertEquals("head", a.zone)
        a.key(KeyEvent.KEYCODE_DPAD_CENTER)
        assertEquals(listOf("Мой плейлист", "Спорт", "Управлять плейлистами"), a.popTitles)
        a.key(KeyEvent.KEYCODE_DPAD_DOWN)
        a.key(KeyEvent.KEYCODE_DPAD_CENTER)
        waitFor(10_000, "сменился плейлист") { Repo.activeId == "p2" && a.playlist.channels.size == 4 }
        assertEquals("$base/sport.m3u", Repo.prefs.playlistUrl)
    }

    /** Сценарий 10: плагин — карточка, выключение, удаление через диалог и «Вернуть». */
    @Test fun pluginDelete() {
        legacyPlaylist()
        RuntimeEnvironment.getApplication().getSharedPreferences("iptv_plugins", 0).edit().putString("list",
            """[{"url":"$base/a.js","enabled":false,"code":"","id":"sort","name":"Сортировка по алфавиту","version":"1.2","description":"Ставит каналы по алфавиту"},
               {"url":"$base/b.js","enabled":false,"code":"","id":"logos","name":"Логотипы из каталога","version":"0.9","author":"logos.example",
                "errorTime":${System.currentTimeMillis()},"errorMessage":"TypeError: Cannot read properties of undefined","errorHook":"playlist"}]""").commit()
        Plugins.init(RuntimeEnvironment.getApplication())
        val s = Robolectric.buildActivity(SettingsActivity::class.java).setup().get()
        idle(300)
        s.key(KeyEvent.KEYCODE_DPAD_DOWN, 2) // «Плагины»
        s.key(KeyEvent.KEYCODE_DPAD_CENTER)
        val text = textOnScreen(s.root)
        assertTrue(text, text.contains("Сортировка по алфавиту") && text.contains("Добавить плагин"))
        assertTrue("«Добавить» в конце", text.indexOf("Добавить плагин") > text.indexOf("Логотипы из каталога"))
        s.key(KeyEvent.KEYCODE_DPAD_DOWN)
        s.key(KeyEvent.KEYCODE_DPAD_CENTER) // карточка второго плагина
        assertTrue(textOnScreen(s.root).contains("Плагин не запустился"))
        s.key(KeyEvent.KEYCODE_DPAD_RIGHT, 2)
        s.key(KeyEvent.KEYCODE_DPAD_CENTER) // «Удалить» → диалог, фокус на «Отмена»
        assertTrue(s.dialogOpen)
        s.key(KeyEvent.KEYCODE_DPAD_CENTER)
        assertFalse(s.dialogOpen)
        assertEquals(2, Plugins.items.value.size)
        s.key(KeyEvent.KEYCODE_DPAD_CENTER)
        s.key(KeyEvent.KEYCODE_DPAD_LEFT)
        s.key(KeyEvent.KEYCODE_DPAD_CENTER)
        waitFor(3000, "удалён") { Plugins.items.value.size == 1 }
        assertTrue(s.toastText.startsWith("Плагин «Логотипы из каталога» удалён"))
        s.key(KeyEvent.KEYCODE_DPAD_CENTER) // OK — вернуть
        waitFor(3000, "возвращён") { Plugins.items.value.size == 2 }
        assertEquals("Логотипы из каталога", Plugins.items.value[1].name)
    }

    private fun findEdit(v: View): android.widget.EditText? = findEdits(v).firstOrNull()

    private fun findEdits(v: View): List<android.widget.EditText> {
        val out = ArrayList<android.widget.EditText>()
        fun walk(x: View) { if (x is android.widget.EditText) out += x; if (x is android.view.ViewGroup) for (i in 0 until x.childCount) walk(x.getChildAt(i)) }
        walk(v)
        return out
    }

    private fun textOnScreen(v: View): String {
        val sb = StringBuilder()
        fun walk(x: View) {
            if (x.visibility != View.VISIBLE) return
            if (x is android.widget.TextView) sb.append(x.text).append(' ')
            if (x is android.view.ViewGroup) for (i in 0 until x.childCount) walk(x.getChildAt(i))
        }
        walk(v)
        return sb.toString()
    }

    private fun countCells(v: View): Int {
        var n = 0
        fun walk(x: View) {
            if (x.tag == "cell") n++
            if (x is android.view.ViewGroup) for (i in 0 until x.childCount) walk(x.getChildAt(i))
        }
        walk(v)
        return n
    }
}
