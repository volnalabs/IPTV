package com.volnalabs.tv

import android.view.KeyEvent
import com.volnalabs.tv.data.Plugins
import com.volnalabs.tv.data.Repo
import com.volnalabs.tv.ui.MainActivity
import com.volnalabs.tv.ui.SettingsActivity
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode

/**
 * Снимки экранов нового интерфейса для сверки с эталонами docs/design/handoff/screens.
 * Папка — переменная IPTV_SHOTS (по умолчанию build/shots). Видео заменено картинкой-заглушкой.
 */
@RunWith(RobolectricTestRunner::class)
@LooperMode(LooperMode.Mode.PAUSED)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ScreenshotTest : UiTestBase() {

    private fun main(): MainActivity {
        val a = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        waitSplash(a)
        waitFor(5000, "канал") { a.cur != null }
        a.fakeVideo()
        idle(300)
        return a
    }

    private fun plugins() {
        RuntimeEnvironment.getApplication().getSharedPreferences("iptv_plugins", 0).edit().putString("list",
            """[{"url":"https://example.org/iptv/sort.js","enabled":false,"code":"","id":"sort","name":"Сортировка по алфавиту","version":"1.2","description":"Ставит каналы по алфавиту внутри групп"},
               {"url":"https://logos.example/iptv/logo-catalog.js","enabled":false,"code":"","id":"logos","name":"Логотипы из каталога","version":"0.9","author":"logos.example",
                "description":"Подставляет логотипы каналов, которых нет в плейлисте.","hasPlaylist":true,
                "errorTime":${System.currentTimeMillis() - 600_000},"errorMessage":"TypeError: Cannot read properties of undefined (reading «tvg»)","errorHook":"playlist"}]""").commit()
        Plugins.init(RuntimeEnvironment.getApplication())
    }

    @Test
    @Config(sdk = [34], qualifiers = "w960dp-h540dp-land-television-xhdpi")
    fun tv() {
        val first = Robolectric.buildActivity(SettingsActivity::class.java).setup().get()
        shot(first, "tv-01-onboarding")
        first.key(KeyEvent.KEYCODE_DPAD_CENTER)
        shot(first, "tv-02-onboarding-field")
        first.finish()

        legacyPlaylist()
        val a = main()
        shot(a, "tv-10-osd")
        a.key(KeyEvent.KEYCODE_DPAD_UP, 3, wait = 60)
        a.fakeVideo(); shot(a, "tv-12-zap")
        idle(600)
        a.key(KeyEvent.KEYCODE_1)
        a.fakeVideo(); shot(a, "tv-13-number")
        a.key(KeyEvent.KEYCODE_BACK)
        a.key(KeyEvent.KEYCODE_DPAD_LEFT) // ◀ в эфире — гид целиком
        a.key(KeyEvent.KEYCODE_DPAD_DOWN, 4)
        a.fakeVideo(); shot(a, "tv-20-guide")
        a.key(KeyEvent.KEYCODE_DPAD_RIGHT)
        a.fakeVideo(); shot(a, "tv-21-programme")
        a.key(KeyEvent.KEYCODE_DPAD_LEFT)
        a.hold() // удержание OK — сразу в избранное
        a.fakeVideo(); shot(a, "tv-22-fav")
        a.key(KeyEvent.KEYCODE_DPAD_LEFT)
        a.fakeVideo(); shot(a, "tv-23-menu")
        a.key(KeyEvent.KEYCODE_DPAD_UP, 5) // шапка: «Телепрограмма» и шестерёнка «Настройки»
        a.key(KeyEvent.KEYCODE_DPAD_RIGHT)
        a.fakeVideo(); shot(a, "tv-24-settings-button")
        a.key(KeyEvent.KEYCODE_DPAD_LEFT); a.key(KeyEvent.KEYCODE_DPAD_CENTER) // сетка
        a.key(KeyEvent.KEYCODE_DPAD_DOWN, 3); a.key(KeyEvent.KEYCODE_DPAD_RIGHT, 2)
        a.fakeVideo(); shot(a, "tv-30-grid")
        a.key(KeyEvent.KEYCODE_BACK); a.key(KeyEvent.KEYCODE_BACK); a.key(KeyEvent.KEYCODE_BACK)
        a.key(KeyEvent.KEYCODE_PROG_RED)
        a.t.typeText("футбол"); idle(200)
        a.key(KeyEvent.KEYCODE_DPAD_DOWN, 2); a.key(KeyEvent.KEYCODE_DPAD_RIGHT, 3)
        shot(a, "tv-31-search")
        a.key(KeyEvent.KEYCODE_DPAD_RIGHT, 5)
        shot(a, "tv-31b-results")
        a.key(KeyEvent.KEYCODE_DPAD_CENTER)
        a.fakeVideo(); shot(a, "tv-32-player-after-search")
        a.key(KeyEvent.KEYCODE_BACK)
        a.key(KeyEvent.KEYCODE_DPAD_RIGHT)
        a.key(KeyEvent.KEYCODE_DPAD_UP)
        a.key(KeyEvent.KEYCODE_DPAD_CENTER)
        a.key(KeyEvent.KEYCODE_DPAD_CENTER)
        a.fakeVideo(); shot(a, "tv-33-archive")
        a.key(KeyEvent.KEYCODE_DPAD_RIGHT, 3) // перемотка по шкале: время над бегунком
        a.fakeVideo(); shot(a, "tv-33b-archive-scrub")
        a.showState("error"); a.t.hideInfo()
        shot(a, "tv-34-error")

        plugins()
        val s = Robolectric.buildActivity(SettingsActivity::class.java).setup().get()
        shot(s, "tv-40-settings")
        s.key(KeyEvent.KEYCODE_DPAD_CENTER)
        s.key(KeyEvent.KEYCODE_DPAD_CENTER)
        findEdits(s.root)[1].setText("$base/missing.m3u"); idle(100)
        s.key(KeyEvent.KEYCODE_DPAD_DOWN, 3); s.key(KeyEvent.KEYCODE_DPAD_CENTER)
        idle(1500)
        shot(s, "tv-41-playlist-error")
        s.key(KeyEvent.KEYCODE_BACK); s.key(KeyEvent.KEYCODE_BACK)
        s.key(KeyEvent.KEYCODE_DPAD_DOWN)
        s.key(KeyEvent.KEYCODE_DPAD_CENTER)
        shot(s, "tv-42-playback")
        s.key(KeyEvent.KEYCODE_BACK)
        s.key(KeyEvent.KEYCODE_DPAD_DOWN)
        s.key(KeyEvent.KEYCODE_DPAD_CENTER)
        shot(s, "tv-43-plugins")
        s.key(KeyEvent.KEYCODE_DPAD_DOWN)
        s.key(KeyEvent.KEYCODE_DPAD_CENTER)
        shot(s, "tv-44-plugin")
        s.key(KeyEvent.KEYCODE_DPAD_RIGHT, 2)
        s.key(KeyEvent.KEYCODE_DPAD_CENTER)
        shot(s, "tv-45-plugin-delete")
        s.key(KeyEvent.KEYCODE_BACK); s.key(KeyEvent.KEYCODE_BACK)
        s.key(KeyEvent.KEYCODE_DPAD_DOWN)
        s.key(KeyEvent.KEYCODE_DPAD_CENTER)
        shot(s, "tv-46-plugin-add")
        s.key(KeyEvent.KEYCODE_BACK); s.key(KeyEvent.KEYCODE_BACK)
        s.key(KeyEvent.KEYCODE_DPAD_DOWN)
        s.key(KeyEvent.KEYCODE_DPAD_CENTER)
        shot(s, "tv-47-app")
    }

    @Test
    @Config(sdk = [34], qualifiers = "w390dp-h844dp-port-xxhdpi")
    fun phone() {
        val first = Robolectric.buildActivity(SettingsActivity::class.java).setup().get()
        shot(first, "phone-01-onboarding")
        first.finish()
        legacyPlaylist()
        val a = main()
        shot(a, "phone-10-channels")
        a.touchUi!!.showProgramme(a.cur!!, 0)
        a.fakeVideo(); shot(a, "phone-11-programme")
        plugins()
        val s = Robolectric.buildActivity(SettingsActivity::class.java).setup().get()
        shot(s, "phone-20-settings")
    }

    @Test
    @Config(sdk = [34], qualifiers = "w360dp-h740dp-port-xhdpi")
    fun phone360() {
        legacyPlaylist()
        val a = main()
        shot(a, "phone-12-channels-360")
    }

    @Test
    @Config(sdk = [34], qualifiers = "w844dp-h390dp-land-xxhdpi")
    fun phoneLandscape() {
        legacyPlaylist()
        val a = main()
        a.showInfo()
        a.fakeVideo(); shot(a, "phone-13-landscape")
    }

    @Test
    @Config(sdk = [34], qualifiers = "w1280dp-h800dp-land-xhdpi")
    fun tablet() {
        legacyPlaylist()
        val a = main()
        shot(a, "tablet-1280")
    }

    @Test
    @Config(sdk = [34], qualifiers = "w768dp-h1024dp-port-xhdpi")
    fun tabletPortrait() {
        legacyPlaylist()
        val a = main()
        a.touchUi!!.showProgramme(a.cur!!, 0)
        a.fakeVideo(); shot(a, "tablet-768")
    }

    private fun findEdits(v: android.view.View): List<android.widget.EditText> {
        val out = ArrayList<android.widget.EditText>()
        fun walk(x: android.view.View) { if (x is android.widget.EditText) out += x; if (x is android.view.ViewGroup) for (i in 0 until x.childCount) walk(x.getChildAt(i)) }
        walk(v)
        return out
    }
}
