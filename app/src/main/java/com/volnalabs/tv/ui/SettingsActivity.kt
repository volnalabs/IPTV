package com.volnalabs.tv.ui

import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.volnalabs.tv.BuildConfig
import com.volnalabs.tv.R
import com.volnalabs.tv.data.PlaylistInfo
import com.volnalabs.tv.data.Plugins
import com.volnalabs.tv.data.Repo
import com.volnalabs.tv.data.Updater
import com.volnalabs.tv.remote.RemoteInputServer
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Настройки (SPEC 5, 6): разделы «Плейлисты», «Воспроизведение», «Плагины», «Приложение»,
 * формы плейлиста и плагина, карточка плагина, «С телефона» и первый запуск.
 * На телевизоре — две колонки с фокусом пульта, на телефоне — один список с касаниями.
 */
class SettingsActivity : BaseActivity() {

    private companion object {
        /** Левый край колонки настроек и ширина карточки «С телефона» на телевизоре (px холста). */
        const val PANE_L = 584
        const val QR_W = 400
        val QR_VIEWS = setOf("playlists", "playback", "plugins", "app", "plform", "plugadd")
    }

    /** Элемент панели. r — ряд: ▲▼ ходят по рядам, ◀▶ — внутри ряда кнопок. */
    class Item(
        val type: String, val r: Int = 0, val icon: Int = 0, val title: String = "", val desc: String = "",
        val value: String = "", val valueColor: Int = 0, val valueIcon: Int = 0, val sw: Boolean? = null, val chev: Boolean = false,
        val order: Int = 0, val spin: Boolean = false, val off: Boolean = false, val danger: Boolean = false, val primary: Boolean = false,
        val field: String = "", val hint: String = "", val error: String = "", val max: Int = 0,
        val view: (() -> View)? = null, val hold: (() -> Unit)? = null, val del: (() -> Unit)? = null, val action: (() -> Unit)? = null,
    )

    private val scope = MainScope()
    private var view = "playlists"
    private var cat = 0
    private var index = 0
    private var inPane = false
    private var items: List<Item> = emptyList()
    private val fields = HashMap<String, EditText>()

    private lateinit var catsBox: LinearLayout
    private lateinit var pane: LinearLayout
    private lateinit var paneScroll: ScrollView
    private lateinit var hintsBox: FrameLayout
    private lateinit var qrCard: FrameLayout
    private lateinit var headDate: TextView
    private lateinit var title: TextView

    // Форма плейлиста или плагина
    private var formId: String? = null
    private val form = HashMap<String, String>()
    private var formError = ""
    private var busy: Job? = null
    private var plugIndex = -1
    private var updStatus = ""
    private var updColor = 0
    private var manualCheck = false

    private val cats: List<Pair<String, String>> get() = listOfNotNull(
        "playlists" to "Плейлисты", "playback" to "Воспроизведение", "plugins" to "Плагины", "app" to "Приложение")

    /** Приём ссылок с телефона по QR-коду (только телевизор). */
    private val server = RemoteInputServer(
        currentPlaylist = { form["url"] ?: Repo.active?.url.orEmpty() },
        currentEpg = { form["epgUrl"] ?: Repo.active?.epgUrl.orEmpty() },
        onSubmit = { playlist, epg -> runOnUiThread { fromPhone(playlist, epg) } },
        onPlugin = { link -> runOnUiThread { if (view == "plugadd") { form["url"] = link; render(); addPlugin() } else scope.launch { Plugins.add(link)?.let { toast(it, "warn") } } } },
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupRoot(C.BG)
        build()
        setupOverlays()
        val start = intent.getStringExtra("view").orEmpty()
        when {
            start == "onb" || Repo.active == null -> openView(if (tv) "onb" else "onbField")
            start.isNotEmpty() -> openView(start)
            else -> { view = "playlists"; inPane = false }
        }
        scope.launch { Repo.playlists.collect { if (!fieldsFocused()) render() } }
        scope.launch { Repo.playlistState.collect { if (!fieldsFocused()) render() } }
        scope.launch { Plugins.items.collect { if (!fieldsFocused()) render() } }
        scope.launch { Updater.state.collect { onUpdate(it) } }
        render()
    }

    override fun onResume() {
        super.onResume()
        if (tv) startServer()
    }

    override fun onPause() {
        server.stop()
        super.onPause()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private var serverUrl = ""
    private fun startServer() {
        val ip = RemoteInputServer.localIp()
        serverUrl = if (ip != null && server.start()) "http://$ip:${server.port}" else ""
    }

    // ---------------------------------------------------------------- разметка

    private fun s(tvPx: Int, phoneDp: Int) = if (tv) tvPx else phoneDp

    private fun build() {
        val u = ui
        title = u.text("Настройки", s(44, 20), C.T1, 600)
        headDate = u.text("", 28, C.T2)
        catsBox = u.col()
        pane = u.col()
        paneScroll = ScrollView(this).apply { isVerticalScrollBarEnabled = false; isFocusable = false; addView(pane) }
        hintsBox = u.box()
        if (tv) {
            root.addView(u.row { addView(title, u.lin(0, WRAP, 1f)); addView(headDate) }, u.fr(MATCH, 64, l = 96, t = 54, r = 96))
            root.addView(catsBox, u.fr(440, WRAP, l = 96, t = 150))
            root.addView(paneScroll, u.fr(MATCH, 798, l = PANE_L, t = 150, r = 96))
            // «С телефона» — всегда на виду справа: QR-код без лишних нажатий
            qrCard = u.box { visibility = View.GONE }
            root.addView(qrCard, u.fr(QR_W, WRAP, Gravity.TOP or Gravity.END, r = 96, t = 150))
            root.addView(hintsBox, u.fr(MATCH, 54, Gravity.BOTTOM, l = 96, r = 96, b = 54))
        } else {
            val back = u.icon(R.drawable.ic_chevron_left, 24, C.T1).apply {
                setOnClickListener { onBack() }; contentDescription = "Назад"; u.pad(this, 12, 12, 12, 12)
            }
            root.addView(u.row { setBackgroundColor(C.BG2); addView(back, u.lin(48, 48, l = 4)); addView(title, u.lin(0, WRAP, 1f, l = 4)) }, u.fr(MATCH, 56))
            root.addView(paneScroll, u.fr(MATCH, MATCH, t = 56))
            u.pad(pane, 16, 8, 16, 24)
        }
    }

    private fun fieldsFocused() = currentFocus is EditText

    private fun openView(v: String, focus: Int = 0) {
        view = v
        index = focus
        inPane = v != "playlists" && v != "playback" && v != "plugins" && v != "app" || inPane || !tv
        cat = when (v) { "playback" -> 1; "plugins", "plugin", "plugadd" -> 2; "app" -> 3; else -> 0 }
        if (v == "playlists") this@SettingsActivity.handler.postDelayed({ Repo.prefs.markSeen("playlists") }, 4000)
        render()
    }

    // ---------------------------------------------------------------- содержимое разделов

    private fun plStatus(p: PlaylistInfo): Triple<String, Int, Int> = when {
        p.failed && !(p.id == Repo.activeId && Repo.playlist.value.channels.isNotEmpty()) -> Triple("Не загрузился", C.ERROR, R.drawable.ic_alert)
        p.id == Repo.activeId -> Triple("Активный", C.T2, R.drawable.ic_check)
        else -> Triple("", 0, 0)
    }

    private fun build(v: String): Pair<List<View>, List<Item>> {
        val u = ui
        val head = ArrayList<View>()
        val out = ArrayList<Item>()
        val now = System.currentTimeMillis()
        fun h1(t: String, newKey: String = "") = head.add(u.row {
            addView(u.text(t, s(44, 20), C.T1, 600))
            if (newKey.isNotEmpty() && !Repo.prefs.seen(newKey)) addView(u.newTag(size = s(20, 11)), u.lin(l = s(12, 8)))
        })
        fun lead(t: String) = head.add(u.text(t, s(28, 14), C.T2, lines = 4, lineHeight = s(36, 20)).apply { u.pad(this, 0, s(8, 4), 0, s(32, 12)) })
        when (v) {
            "playlists" -> {
                h1("Плейлисты", "playlists")
                lead("Каналы берутся из активного плейлиста. Переключить его можно и в гиде — сверху слева.")
                Repo.playlists.value.forEach { p ->
                    val (st, color, icon) = plStatus(p)
                    val bad = color == C.ERROR
                    out += Item("row", out.size, R.drawable.ic_list, p.name,
                        if (bad) p.errorMessage + " · " + whenText(p.errorTime, now)
                        else if (tv) listOf(if (p.count > 0) chCount(p.count) else "", midCut(p.url, 30)).filter { it.isNotEmpty() }.joinToString(" · ")
                        else midCut(p.url, 34),
                        value = if (tv) st else listOf(st, if (p.count > 0 && !bad) chCount(p.count) else "").filter { it.isNotEmpty() }.joinToString("   "),
                        valueColor = color, valueIcon = icon, chev = true, hold = { playlistMenu(p) }, action = { openForm(p) })
                }
                out += Item("row", out.size, R.drawable.ic_plus, "Добавить плейлист", "По ссылке от провайдера" + if (tv) " или с телефона" else "", action = { openForm(null) })
                out += Item("view", view = { u.text("Список каналов", s(26, 13), C.T3).apply { u.pad(this, s(28, 4), s(40, 16), s(28, 4), s(12, 6)) } })
                val a = Repo.active
                val loading = Repo.playlistState.value is com.volnalabs.tv.data.LoadState.Loading
                out += Item("row", out.size, R.drawable.ic_refresh, "Обновить каналы",
                    if (loading) "Обновляем список…" else "Активный плейлист" + if (a != null && a.updated > 0) " · обновлён " + whenText(a.updated, now) else "",
                    spin = loading, action = { refreshChannels() })
            }
            "playback" -> {
                h1("Воспроизведение")
                out += Item("row", 0, R.drawable.ic_volume, "Запоминать громкость", "Канал включается с той громкостью, что была при выходе", sw = Repo.prefs.rememberVolume, action = {
                    Repo.prefs.rememberVolume = !Repo.prefs.rememberVolume
                    toast("Запоминать громкость — " + if (Repo.prefs.rememberVolume) "включено" else "выключено"); render()
                })
                out += Item("row", 1, R.drawable.ic_frame, "Убирать чёрные полосы", "Растягивает кадр на весь экран, края могут обрезаться", sw = Repo.prefs.fillScreen, action = {
                    Repo.prefs.fillScreen = !Repo.prefs.fillScreen
                    toast("Убирать чёрные полосы — " + if (Repo.prefs.fillScreen) "включено" else "выключено"); render()
                })
            }
            "app" -> {
                h1("Приложение")
                out += Item("row", 0, R.drawable.ic_download, "Обновлять автоматически", sw = Repo.prefs.autoUpdate, action = {
                    Repo.prefs.autoUpdate = !Repo.prefs.autoUpdate
                    if (Repo.prefs.autoUpdate) Updater.autoCheck(this)
                    toast("Обновлять автоматически — " + if (Repo.prefs.autoUpdate) "включено" else "выключено"); render()
                })
                out += Item("row", 1, R.drawable.ic_refresh, "Проверить обновления", value = updStatus.ifEmpty { "Версия " + BuildConfig.VERSION_NAME },
                    valueColor = if (updColor != 0) updColor else C.T2, action = { checkUpdate() })
                out += Item("view", view = { u.text("Версия " + BuildConfig.VERSION_NAME, s(28, 14), C.T3).apply { u.pad(this, s(28, 4), s(24, 16), 0, 0) } })
            }
            "plugins" -> {
                h1("Плагины")
                lead("Плагин меняет список каналов или адрес потока. Работают по порядку — сверху вниз. Плагин ставится по ссылке на его файл; каталога плагинов нет.")
                Plugins.items.value.forEachIndexed { i, p ->
                    val (st, color, icon) = when { !p.enabled -> Triple("Выключен", C.T3, 0); p.failed -> Triple("Ошибка запуска", C.ERROR, R.drawable.ic_alert); else -> Triple("Работает", C.T2, R.drawable.ic_check) }
                    out += Item("row", out.size, title = p.title, desc = listOf(if (p.version.isNotEmpty()) "Версия " + p.version else "", p.description).filter { it.isNotEmpty() }.joinToString(" · ").ifEmpty { midCut(p.url, 46) },
                        order = i + 1, value = st, valueColor = color, valueIcon = icon, chev = true,
                        hold = { pluginMenu(i) }, del = { askDeletePlugin(i) }, action = { plugIndex = i; openView("plugin") })
                }
                out += Item("row", out.size, R.drawable.ic_plus, "Добавить плагин", "По ссылке на файл .js", action = { form.clear(); formError = ""; openView("plugadd") })
            }
            "plugin" -> {
                val p = Plugins.items.value.getOrNull(plugIndex) ?: return head to out
                h1(p.title)
                if (p.description.isNotEmpty()) lead(p.description)
                if (p.failed) {
                    head += banner(R.drawable.ic_alert, C.ERROR, C.ERROR_SUBTLE, "Плагин не запустился " + whenText(p.errorTime, now).replace(", ", " в "),
                        p.errorMessage + ". " + if (p.errorHook == "stream") "Потоки включаются без него." else "Каналы показаны без него.")
                }
                out += Item("btn", 0, title = if (p.enabled) "Выключить" else "Включить", action = { togglePlugin(plugIndex) })
                out += Item("btn", 0, R.drawable.ic_refresh, "Обновить код", action = { refreshPluginCode(plugIndex) })
                out += Item("btn", 0, R.drawable.ic_trash, "Удалить", danger = true, action = { askDeletePlugin(plugIndex) })
                val all = Plugins.items.value
                out += Item("view", view = { u.col {
                    u.pad(this, 0, s(36, 16), 0, 0)
                    addView(kv("Версия и автор", listOf(p.version, p.author).filter { it.isNotEmpty() }.joinToString(" · ").ifEmpty { "—" }))
                    addView(kv("Файл", p.url, mono = true))
                    addView(kv("Порядок", "${plugIndex + 1} из ${all.size}" + if (plugIndex > 0) " — работает после «${all[plugIndex - 1].title}»" else " — работает первым"))
                    val can = ArrayList<String>()
                    if (p.hasPlaylist) can += "менять список каналов"
                    if (p.hasStream) can += "менять адреса потоков"
                    can += listOf("показывать уведомления", "хранить свои данные")
                    val text = can.joinToString(", ").replaceFirstChar { it.uppercase() } + "." + (if (p.hasStream) "" else " Адреса потоков не меняет.") + (if (p.hasPlaylist) "" else " Список каналов не меняет.")
                    addView(u.row {
                        addView(u.text("Что может плагин", s(30, 15), C.T1, 500))
                        if (!Repo.prefs.seen("plugcan")) addView(u.newTag(size = s(20, 11)), u.lin(l = s(12, 8)))
                    }, u.lin(t = s(28, 16)))
                    addView(u.text(text, s(28, 14), C.T2, lines = 4, lineHeight = s(36, 20)))
                    this@SettingsActivity.handler.postDelayed({ Repo.prefs.markSeen("plugcan") }, 4000)
                } })
            }
            "plugadd" -> {
                h1("Добавить плагин")
                out += Item("field", 0, field = "url", title = "Ссылка на файл плагина", hint = "Файл .js, который написан для IPTV (API версии 1)", error = formError)
                out += Item("view", view = { banner(R.drawable.ic_shield, C.WARNING, C.S1, "Ставьте плагины только из источников, которым доверяете",
                    "Плагин видит список каналов и адреса потоков и может их менять: например, отправить поток через чужой сервер. Ещё он может показывать уведомления и хранить свои данные на устройстве.") })
                out += Item("btn", 1, if (busy != null) 0 else R.drawable.ic_plus, if (busy != null) "Загружаю плагин…" else "Установить", primary = true, spin = busy != null, action = { addPlugin() })
            }
            "plform" -> {
                val p = Repo.playlists.value.firstOrNull { it.id == formId }
                h1(p?.name ?: "Новый плейлист")
                if (p != null) lead(listOfNotNull(if (p.count > 0) chCount(p.count) else null, if (p.updated > 0) "обновлён " + whenText(p.updated, now) else null,
                    if (p.id == Repo.activeId) "активный" else "не активный").joinToString(" · "))
                val chs = Repo.playlist.value.channels.size
                out += Item("field", 0, field = "name", title = "Название", hint = "Пусто — имя сервера из ссылки", max = 40)
                out += Item("field", 1, field = "url", title = "Ссылка на плейлист", error = formError,
                    hint = if (formError.isNotEmpty()) formError + if (chs > 0 && p?.id == Repo.activeId) " Работает прежний список: ${chCount(chs)}." else "" else "Новая ссылка заменит список только после проверки")
                out += Item("field", 2, field = "epgUrl", title = "Ссылка на телепрограмму", hint = "Если пусто — берём ссылку из самого плейлиста (url-tvg)")
                val dirty = formDirty()
                out += Item("btn", 3, if (busy != null) 0 else R.drawable.ic_check, if (busy != null) "Проверяем ссылку…" else "Проверить и сохранить", primary = true, spin = busy != null,
                    off = !dirty && busy == null, action = { checkAndSave() })
                if (p != null) out += Item("btn", 3, R.drawable.ic_trash, "Удалить плейлист", danger = true, off = p.id == Repo.activeId, action = { askDeletePlaylist(p) })
                out += Item("view", view = { u.col {
                    if (p != null && p.id == Repo.activeId) addView(u.text("Сначала сделайте активным другой", s(24, 13), C.T3), u.lin(t = s(16, 8)))
                    if (dirty) addView(u.text("Есть несохранённые изменения", s(28, 14), C.T2), u.lin(t = s(12, 8)))
                } })
            }
            "onb" -> {
                // Первый запуск на телевизоре: ссылка приходит с телефона по QR-коду
                title.text = "Добро пожаловать"
                head += u.row(Gravity.TOP) {
                    addView(u.col {
                        addView(u.text("Добро пожаловать", 64, C.T1, 600).apply { letterSpacing = -0.01f })
                        addView(u.text("Ссылку на плейлист удобнее отправить с телефона — набирать её пультом не нужно.", 30, C.T2, lines = 3, lineHeight = 40), u.lin(MATCH, WRAP, t = 12))
                        listOf("Наведите камеру телефона на код справа", "Вставьте ссылку на плейлист от провайдера", "Нажмите «Отправить на ТВ» — каналы загрузятся сами").forEachIndexed { i, t ->
                            addView(u.row {
                                addView(u.text("${i + 1}", 26, C.T1, 600).apply { gravity = Gravity.CENTER; background = u.rect(C.S2, 24) }, u.lin(48, 48, r = 20))
                                addView(u.text(t, 30, C.T1, lines = 2, lineHeight = 40), u.lin(0, WRAP, 1f))
                            }, u.lin(MATCH, WRAP, t = if (i == 0) 48 else 20))
                        }
                    }, u.lin(0, WRAP, 1f, r = 96))
                    addView(qrBlock(), u.lin(WRAP, WRAP))
                }
                out += Item("btn", 0, R.drawable.ic_keyboard, "Ввести ссылку пультом", action = { openView("onbField") })
            }
            "onbField" -> {
                title.text = "Добро пожаловать"
                if (busy != null) {
                    head += u.col(Gravity.CENTER_HORIZONTAL) {
                        u.pad(this, 0, s(160, 80), 0, 0)
                        addView(Spinner(this@SettingsActivity, u.p(s(6, 3)).toFloat()), u.lin(s(80, 48), s(80, 48)))
                        addView(u.text("Загружаем каналы", s(44, 20), C.T1, 600), u.lin(t = s(32, 16)))
                        addView(u.text(Repo.hostOf(form["url"].orEmpty()) + " · обычно это занимает несколько секунд", s(30, 15), C.T2), u.lin(t = s(12, 8)))
                        addView(u.text("Первый канал включится сам. Телепрограмма догрузится потом.", s(30, 15), C.T2, lines = 3).apply { gravity = Gravity.CENTER }, u.lin(t = s(8, 4)))
                    }
                    return head to out
                }
                h1("Добро пожаловать")
                lead("Вставьте ссылку на плейлист от вашего провайдера — и можно смотреть.")
                out += Item("field", 0, field = "url", title = "Ссылка на плейлист", hint = formError.ifEmpty { "Ссылка должна начинаться с http:// или https://" }, error = formError)
                out += Item("btn", 1, R.drawable.ic_play, "Открыть", primary = true, action = { onboard() })
                if (tv) out += Item("view", view = { u.text("Нажмите OK на поле — откроется клавиатура телевизора.", 24, C.T3).apply { u.pad(this, 0, 28, 0, 0) } })
            }
        }
        return head to out
    }

    private fun kv(key: String, value: String, mono: Boolean = false): View = ui.col {
        addView(View(context).apply { setBackgroundColor(C.LINE) }, ui.lin(MATCH, s(2, 1)))
        addView(ui.row(Gravity.TOP) {
            ui.pad(this, 0, s(14, 10), 0, s(14, 10))
            addView(ui.text(key, s(28, 14), C.T3), ui.lin(s(320, 120), WRAP))
            addView(ui.text(value, s(if (mono) 24 else 28, if (mono) 12 else 14), C.T1, lines = 3).apply { if (mono) typeface = android.graphics.Typeface.MONOSPACE }, ui.lin(0, WRAP, 1f))
        })
    }

    private fun banner(icon: Int, iconColor: Int, bg: Int, title: String, text: String): View = ui.row(Gravity.TOP) {
        background = ui.rect(bg, s(12, 8))
        ui.pad(this, s(32, 16), s(24, 12), s(32, 16), s(24, 12))
        addView(ui.icon(icon, s(36, 20), iconColor), ui.lin(s(36, 20), s(36, 20), r = s(20, 12)))
        addView(ui.col {
            addView(ui.text(title, s(30, 15), C.T1, lines = 3, lineHeight = s(40, 20)))
            addView(ui.text(text, s(28, 14), C.T2, lines = 6, lineHeight = s(36, 20)), ui.lin(MATCH, WRAP, t = s(4, 2)))
        }, ui.lin(0, WRAP, 1f))
    }.also { it.layoutParams = ui.lin(MATCH, WRAP, t = s(24, 12), b = s(8, 4)) }

    /** Карточка «С телефона» справа от разделов (телевизор): код виден сразу. */
    private fun renderQrCard() {
        val show = tv && view in QR_VIEWS
        qrCard.visibility = if (show) View.VISIBLE else View.GONE
        (paneScroll.layoutParams as? FrameLayout.LayoutParams)?.rightMargin = ui.p(if (show) 96 + QR_W + 48 else 96 + 120)
        if (!show) return
        if (serverUrl.isEmpty()) startServer()
        if (qrCard.tag == serverUrl && qrCard.childCount > 0) return
        qrCard.tag = serverUrl
        qrCard.removeAllViews()
        val u = ui
        qrCard.addView(u.col(Gravity.CENTER_HORIZONTAL) {
            background = u.rect(C.S1, 24)
            u.pad(this, 32, 32, 32, 32)
            addView(u.row {
                addView(u.box { background = u.rect(C.S3, 32); addView(u.icon(R.drawable.ic_phone, 32, C.T1), u.fr(32, 32, Gravity.CENTER)) }, u.lin(64, 64, r = 20))
                addView(u.text("С телефона", 32, C.T1, 600))
            }, u.lin(MATCH, WRAP))
            addView(u.text("Наведите камеру на код", 26, C.T2, lines = 2, lineHeight = 34), u.lin(MATCH, WRAP, t = 20))
            addView(ImageView(context).apply {
                background = u.rect(C.PAPER, 20)
                u.pad(this, 20, 20, 20, 20)
                if (serverUrl.isNotEmpty()) setImageBitmap(RemoteInputServer.qr(serverUrl, 400))
                contentDescription = "QR-код для телефона"
            }, u.lin(280, 280, t = 28))
            addView(u.text(serverUrl.ifEmpty { "Нет подключения к сети" }, 28, C.T1, 600), u.lin(t = 24))
            addView(u.text("Телефон и ТВ — в одной сети", 24, C.T3, lines = 2, lineHeight = 30).apply { gravity = Gravity.CENTER }, u.lin(MATCH, WRAP, t = 6))
        }, u.fr(MATCH, WRAP))
    }

    private fun qrBlock(): View = ui.col(Gravity.CENTER_HORIZONTAL) {
        if (serverUrl.isEmpty()) startServer()
        addView(ImageView(context).apply {
            background = ui.rect(C.PAPER, 16)
            ui.pad(this, 24, 24, 24, 24)
            if (serverUrl.isNotEmpty()) setImageBitmap(RemoteInputServer.qr(serverUrl, 400))
        }, ui.lin(320, 320))
        addView(ui.text("Наведите камеру на код", 30, C.T1), ui.lin(t = 24))
        addView(ui.text("или откройте в браузере телефона", 26, C.T2), ui.lin(t = 4))
        addView(ui.text(serverUrl.ifEmpty { "Нет подключения к сети" }, 30, C.T1, 600), ui.lin(t = 8))
        addView(ui.row {
            addView(Spinner(context, ui.p(4).toFloat()), ui.lin(32, 32, r = 16))
            addView(ui.text("Ждём ссылку с телефона…", 28, C.T2))
        }, ui.lin(t = 24))
    }

    // ---------------------------------------------------------------- отрисовка

    private fun render() {
        val u = ui
        if (!::pane.isInitialized) return
        val (head, list) = build(view)
        items = list.filter { it.type != "view" }
        index = index.coerceIn(0, (items.size - 1).coerceAtLeast(0))
        if (tv) headDate.text = headDate(System.currentTimeMillis())
        // Разделы (телевизор): плейлисты · воспроизведение · плагины · приложение
        catsBox.removeAllViews()
        val onb = view == "onb" || view == "onbField"
        catsBox.visibility = if (tv && !onb) View.VISIBLE else View.GONE
        if (tv && !onb) cats.forEachIndexed { i, (id, t) ->
            val sel = !inPane && i == cat
            val act = i == cat
            val color = if (sel) C.TI else if (act) C.T1 else C.T2
            catsBox.addView(u.row {
                background = if (sel) u.rect(C.PAPER, 20) else if (act) u.rect(C.S3, 20) else null
                u.pad(this, 24, 0, 24, 0)
                val icon = when (id) { "playlists" -> R.drawable.ic_list; "playback" -> R.drawable.ic_volume; "plugins" -> R.drawable.ic_plug; else -> R.drawable.ic_info }
                addView(u.icon(icon, 36, color), u.lin(36, 36, r = 20))
                addView(u.text(t, 30, color, if (act) 500 else 400))
                if (id == "playlists" && !Repo.prefs.seen("playlists")) addView(u.newTag(), u.lin(l = 12))
                addView(View(context), u.lin(0, 1, 1f))
                val count = when (id) {
                    "playlists" -> Repo.playlists.value.size.toString()
                    "plugins" -> Plugins.items.value.let { l -> if (l.isEmpty()) "нет" else "${l.count { it.enabled }} из ${l.size}" }
                    else -> ""
                }
                addView(u.text(count, 26, if (sel) C.TI2 else C.T3))
                contentDescription = t
                setOnClickListener { inPane = false; cat = i; openView(id) }
            }, u.lin(MATCH, 80, b = 8))
        }
        (paneScroll.layoutParams as? FrameLayout.LayoutParams)?.let { if (tv) it.leftMargin = u.p(if (onb) 96 else PANE_L) }
        if (tv) renderQrCard()
        paneScroll.requestLayout()

        pane.removeAllViews()
        val keep = HashMap<String, String>()
        fields.forEach { (k, f) -> keep[k] = f.text.toString() }
        fields.clear()
        if (!tv) {
            // Телефон: разделы одним списком с заголовками групп
            if (view == "playlists") { renderPhoneHome(); return }
        }
        head.forEach { pane.addView(it) }
        var k = 0
        var btnRow: LinearLayout? = null
        for (it in list) {
            if (it.type != "btn") btnRow = null
            when (it.type) {
                "view" -> pane.addView(it.view!!())
                "row" -> { pane.addView(row(it, k), u.lin(MATCH, WRAP, b = if (tv) 12 else 0)); k++ }
                "field" -> { addField(it, k); k++ }
                "btn" -> {
                    if (btnRow == null) { btnRow = u.row(); if (!tv) btnRow.orientation = LinearLayout.VERTICAL; pane.addView(btnRow, u.lin(MATCH, WRAP, t = s(32, 16))) }
                    val i = k
                    val sel = tv && inPane && i == index
                    btnRow.addView(button(it.title, it.icon, sel, it.danger, it.off, it.spin).apply {
                        if (it.primary && !it.off && !sel) background = u.rect(if (tv) C.S3 else C.PAPER, s(12, 8))
                        if (it.primary && !it.off && !sel && !tv) (getChildAt(childCount - 1) as? TextView)?.setTextColor(C.TI)
                        setOnClickListener { _ -> if (!it.off) { index = i; inPane = true; it.action?.invoke() } }
                    }, if (tv) u.lin(WRAP, 64, r = 16) else u.lin(MATCH, 48, b = 8))
                    k++
                }
            }
        }
        // Значения полей переживают перерисовку
        fields.forEach { (key, f) -> keep[key]?.let { v -> if (f.text.toString() != v && form.containsKey(key)) f.setText(v) } }
        renderHints()
        // Элемент в фокусе всегда виден
        if (tv) paneScroll.post { pane.findViewWithTag<View>("sel")?.let { v -> paneScroll.smoothScrollTo(0, (v.top - u.p(120)).coerceAtLeast(0)) } }
    }

    /** Строка настройки: иконка или номер · название и описание · значение, статус, переключатель или стрелка. */
    private fun row(it: Item, i: Int): View {
        val u = ui
        val sel = tv && inPane && i == index
        val main = if (it.off) C.TD else if (sel) C.TI else C.T1
        val second = if (sel) C.TI2 else C.T2
        return u.row {
            // Телевизор: строка — скруглённая карточка, значок в круглой подложке
            background = if (sel) u.rect(C.PAPER, s(20, 8)) else if (tv) u.rect(C.S1, 20) else null
            minimumHeight = u.p(s(104, 56))
            u.pad(this, s(24, 4), s(20, 8), s(32, 4), s(20, 8))
            if (sel) tag = "sel"
            val chip = if (sel) 0x14141415 else C.S3
            when {
                it.order > 0 && tv -> addView(u.text(it.order.toString(), 28, if (sel) C.TI2 else C.T2).apply { gravity = Gravity.CENTER; background = u.rect(chip, 32) }, u.lin(64, 64, r = 24))
                it.order > 0 -> addView(u.text(it.order.toString(), 14, C.T3).apply { gravity = Gravity.CENTER }, u.lin(24, WRAP, r = 12))
                it.spin && tv -> addView(u.box { background = u.rect(chip, 32); addView(Spinner(context, u.p(4).toFloat()), u.fr(32, 32, Gravity.CENTER)) }, u.lin(64, 64, r = 24))
                it.spin -> addView(Spinner(context, u.p(2).toFloat()), u.lin(20, 20, l = 2, r = 14))
                it.icon != 0 && tv -> addView(u.box { background = u.rect(chip, 32); addView(u.icon(it.icon, 36, if (sel) C.TI2 else C.T1), u.fr(36, 36, Gravity.CENTER)) }, u.lin(64, 64, r = 24))
                it.icon != 0 -> addView(u.icon(it.icon, 24, second), u.lin(24, 24, r = 16))
            }
            addView(u.col {
                addView(u.text(it.title, s(30, 16), main))
                if (it.desc.isNotEmpty()) addView(u.text(it.desc, s(28, 13), second, lines = 2))
            }, u.lin(0, WRAP, 1f))
            if (it.value.isNotEmpty()) addView(u.row {
                val c = if (sel) (if (it.valueColor == C.ERROR) C.ERROR_ON_PAPER else C.TI2) else if (it.valueColor != 0) it.valueColor else C.T2
                if (it.valueIcon != 0) addView(u.icon(it.valueIcon, s(32, 18), if (it.valueIcon == R.drawable.ic_check && !sel) C.SUCCESS else c), u.lin(s(32, 18), s(32, 18), r = s(10, 6)))
                addView(u.text(it.value, s(28, 13), c))
            }, u.lin(l = s(24, 12)))
            if (it.sw != null) addView(u.switch(it.sw, sel, s(72, 44)), u.lin(s(72, 44), s(40, 24), l = s(24, 12)))
            else if (it.chev) addView(u.icon(R.drawable.ic_chevron_right, s(28, 20), second), u.lin(s(28, 20), s(28, 20), l = s(16, 8)))
            contentDescription = it.title + if (it.value.isNotEmpty()) ", " + it.value else ""
            if (it.sw != null) stateDescription(this, it.sw)
            setOnClickListener { _ -> index = i; inPane = true; it.action?.invoke() }
            if (it.hold != null) setOnLongClickListener { _ -> index = i; inPane = true; it.hold.invoke(); true }
        }.also { v ->
            // Телефон: линия между строками (на телевизоре строки — карточки)
            if (!tv && i > 0 && !sel) v.foreground = topLine()
        }
    }

    private fun stateDescription(v: View, on: Boolean) { if (android.os.Build.VERSION.SDK_INT >= 30) v.stateDescription = if (on) "включено" else "выключено" }

    private fun topLine(): Drawable = object : Drawable() {
        val paint = android.graphics.Paint().apply { color = C.LINE }
        override fun draw(c: android.graphics.Canvas) { c.drawRect(bounds.left.toFloat(), 0f, bounds.right.toFloat(), ui.p(s(2, 1)).toFloat(), paint) }
        override fun setAlpha(alpha: Int) {}
        override fun setColorFilter(cf: android.graphics.ColorFilter?) {}
        @Deprecated("Deprecated in Java")
        override fun getOpacity() = android.graphics.PixelFormat.TRANSLUCENT
    }

    private fun addField(it: Item, i: Int) {
        val u = ui
        val sel = tv && inPane && i == index
        pane.addView(u.text(it.title, s(28, 14), C.T2), u.lin(t = s(24, 12), b = s(8, 6)))
        val f = EditText(this).apply {
            setText(form[it.field].orEmpty())
            setTextSize(TypedValue.COMPLEX_UNIT_PX, u.p(s(30, 16)).toFloat())
            setTextColor(if (sel) C.TI else C.T1)
            setHintTextColor(if (sel) C.TI2 else C.T3)
            hint = when (it.field) { "url" -> if (view == "plugadd") "https://…/plugin.js" else "http://… .m3u или .m3u8"; "epgUrl" -> "Необязательно — если провайдер дал отдельную ссылку"; else -> "" }
            typeface = u.font(400)
            isSingleLine = true
            inputType = if (it.field == "name") InputType.TYPE_CLASS_TEXT else InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            imeOptions = EditorInfo.IME_ACTION_NEXT or EditorInfo.IME_FLAG_NO_EXTRACT_UI
            if (it.max > 0) filters = arrayOf(android.text.InputFilter.LengthFilter(it.max))
            isEnabled = busy == null
            alpha = if (busy != null) 0.4f else 1f
            background = u.rect(if (sel) C.PAPER else C.S2, s(16, 8), if (it.error.isNotEmpty()) C.ERROR else if (sel) C.PAPER else C.LINE_CTL, 2)
            u.pad(this, s(24, 12), 0, s(24, 12), 0)
            // На телевизоре поле получает фокус только по OK — иначе оно забирает себе кнопки пульта
            if (tv) isFocusable = false else isFocusableInTouchMode = true
            if (sel) tag = "sel"
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) { form[it.field] = s.toString(); onFormChanged() }
            })
            setOnFocusChangeListener { v, has ->
                if (has) { index = i; inPane = true; v.background = u.rect(C.S2, s(16, 8), C.PAPER, 2); (v as EditText).setTextColor(C.T1); v.setHintTextColor(C.T3) }
            }
            setOnEditorActionListener { _, _, _ -> blurField(); move(1); true }
        }
        fields[it.field] = f
        pane.addView(f, u.lin(MATCH, s(72, 48)))
        pane.addView(u.row {
            if (it.error.isNotEmpty()) addView(u.icon(R.drawable.ic_alert, s(32, 18), C.ERROR), u.lin(s(32, 18), s(32, 18), r = s(8, 6)))
            addView(u.text(it.hint, s(24, 12), if (it.error.isNotEmpty()) C.ERROR else C.T3, lines = 3))
        }, u.lin(MATCH, WRAP, t = s(8, 4)))
    }

    /** Без полной перерисовки: поле в фокусе нельзя пересоздавать. */
    private fun onFormChanged() {
        if (view != "plform") return
        val save = items.indexOfFirst { it.title == "Проверить и сохранить" }
        if (save >= 0 && items[save].off == formDirty()) this@SettingsActivity.handler.post { if (!fieldsFocused()) render() else pendingRender = true }
    }
    private var pendingRender = false

    private fun renderHints() {
        hintsBox.removeAllViews()
        if (!tv) return
        val cur = items.getOrNull(index)
        val h: Array<Pair<String, String>> = when {
            view == "onb" -> arrayOf("ok" to "Ввести ссылку пультом", "back" to "Выйти")
            !inPane -> arrayOf("ud" to "Выбор", "ok" to "Открыть", "back" to "Назад")
            view == "plform" && formError.isNotEmpty() -> arrayOf("ok" to "Клавиатура", "ud" to "Поля", "back" to "Отменить изменения")
            view == "plform" || view == "plugadd" || view == "onbField" -> arrayOf("ud" to "Поля", "ok" to "Изменить / нажать", "back" to "Отменить")
            view == "plugin" -> arrayOf("lr" to "Кнопки", "ok" to "Нажать", "back" to "К списку")
            view == "plugins" -> arrayOf("ok" to "Открыть", "hold" to if (cur?.hold != null) "Вкл/выкл · Удалить" else "", "back" to "Закрыть")
            view == "playlists" -> arrayOf("ok" to "Открыть", "hold" to if (cur?.hold != null) "Сделать активным · Обновить · Удалить" else "", "l" to "Разделы", "back" to "Назад")
            else -> arrayOf("ud" to "Выбор", "ok" to "Открыть / Переключить", "l" to "Разделы", "back" to "Назад")
        }
        hintsBox.addView(hints(*h), ui.fr(WRAP, MATCH))
    }

    /** Телефон: «Источник», «Воспроизведение», «Приложение», «Плагины» одним списком. */
    private fun renderPhoneHome() {
        val u = ui
        title.text = "Настройки"
        var k = 0
        fun group(t: String) = pane.addView(u.text(t, 13, C.T3).apply { u.pad(this, 4, 20, 4, 6) })
        group("Источник")
        val (_, pl) = build("playlists")
        pl.filter { it.type == "row" }.forEach { pane.addView(row(it, k++), u.lin(MATCH, WRAP)) }
        group("Воспроизведение")
        build("playback").second.forEach { pane.addView(row(it, k++), u.lin(MATCH, WRAP)) }
        group("Приложение")
        build("app").second.filter { it.type == "row" }.forEach { pane.addView(row(it, k++), u.lin(MATCH, WRAP)) }
        group("Плагины")
        val plugs = Plugins.items.value
        pane.addView(row(Item("row", 0, R.drawable.ic_plug, "Плагины", value = if (plugs.isEmpty()) "нет" else "${plugs.count { it.enabled }} из ${plugs.size}", chev = true,
            action = { openView("plugins") }), k++), u.lin(MATCH, WRAP))
        pane.addView(u.text("Версия " + BuildConfig.VERSION_NAME, 13, C.T3).apply { u.pad(this, 4, 20, 4, 0) })
    }

    // ---------------------------------------------------------------- действия

    private fun refreshChannels() {
        Repo.refreshAsync(forceEpg = true)
        scope.launch {
            var seenLoading = false
            Repo.playlistState.collect { st ->
                if (st is com.volnalabs.tv.data.LoadState.Loading) seenLoading = true
                else if (seenLoading) {
                    if (st is com.volnalabs.tv.data.LoadState.Ready) toast("Список обновлён · " + chCount(Repo.playlist.value.channels.size), "ok")
                    else if (st is com.volnalabs.tv.data.LoadState.Error) toast("Не удалось обновить список: " + st.message.replaceFirstChar { it.lowercase() } + " Работает прежний список.", "warn")
                    throw kotlinx.coroutines.CancellationException()
                }
            }
        }
        render()
    }

    private fun openForm(p: PlaylistInfo?) {
        formId = p?.id
        form.clear()
        form["name"] = p?.name.orEmpty(); form["url"] = p?.url.orEmpty(); form["epgUrl"] = p?.epgUrl.orEmpty()
        formError = ""
        openView("plform")
    }

    private fun formDirty(): Boolean {
        val p = Repo.playlists.value.firstOrNull { it.id == formId }
        val n = form["name"].orEmpty().trim(); val u = form["url"].orEmpty().trim(); val e = form["epgUrl"].orEmpty().trim()
        return if (p == null) n.isNotEmpty() || u.isNotEmpty() || e.isNotEmpty() else n != p.name || u != p.url || e != p.epgUrl
    }

    /** Новая ссылка заменяет список только после проверки (SPEC 5.3). */
    private fun checkAndSave() {
        if (busy != null) return
        val url = form["url"].orEmpty().trim()
        if (!url.startsWith("http://", true) && !url.startsWith("https://", true)) { formError = "Ссылка должна начинаться с http:// или https://"; index = 1; render(); return }
        formError = ""
        val id = formId
        val name = form["name"].orEmpty()
        busy = scope.launch {
            val error = Repo.savePlaylist(id, name, url, form["epgUrl"].orEmpty())
            busy = null
            if (error != null) { formError = error; index = 1; render(); return@launch }
            val p = Repo.playlists.value.firstOrNull { it.url == url && (id == null || it.id == id) }
            toast("Плейлист «${p?.name ?: name}» сохранён" + if ((p?.count ?: 0) > 0) " · " + chCount(p!!.count) else "", "ok")
            form.clear()
            openView("playlists", Repo.playlists.value.indexOfFirst { it.id == p?.id }.coerceAtLeast(0))
        }
        render()
    }

    private fun askDeletePlaylist(p: PlaylistInfo) {
        if (p.id == Repo.activeId) return
        openDialog("Удалить плейлист «${p.name}»?", "Избранное останется. Чтобы вернуть плейлист, понадобится его ссылка.", listOf(
            DialogButton("Удалить", danger = true, icon = R.drawable.ic_trash) {
                closeDialog()
                val i = Repo.playlists.value.indexOfFirst { it.id == p.id }
                Repo.deletePlaylist(p.id)
                toast("Плейлист «${p.name}» удалён")
                form.clear()
                openView("playlists", (i).coerceAtMost(Repo.playlists.value.size - 1).coerceAtLeast(0))
            },
            DialogButton("Отмена") { closeDialog() }), focus = 1)
    }

    private fun playlistMenu(p: PlaylistInfo) {
        val active = p.id == Repo.activeId
        openPop(p.name, listOf(
            PopItem("Сделать активным", R.drawable.ic_check, off = active) { activate(p) },
            PopItem("Обновить", R.drawable.ic_refresh) { if (active) refreshChannels() else recheck(p) },
            PopItem("Изменить", R.drawable.ic_edit) { openForm(p) },
            PopItem("Удалить…", R.drawable.ic_trash, danger = true, off = active) { askDeletePlaylist(p) }), 1240, 300, if (active) 1 else 0)
    }

    private fun activate(p: PlaylistInfo) {
        toast("Загружаем «${p.name}»…", "wait")
        scope.launch {
            val error = Repo.activate(p.id)
            if (error == null) toast("Плейлист «${p.name}» · " + chCount(Repo.playlist.value.channels.size), "ok")
            else toast("«${p.name}» не загрузился: " + error.trimEnd('.').replaceFirstChar { it.lowercase() }, "warn")
            render()
        }
    }

    private fun recheck(p: PlaylistInfo) {
        toast("Обновляем «${p.name}»…", "wait")
        scope.launch {
            val error = Repo.recheck(p.id)
            if (error == null) toast("Список обновлён · " + chCount(Repo.playlists.value.firstOrNull { it.id == p.id }?.count ?: 0), "ok")
            else toast("«${p.name}» не загрузился: " + error.trimEnd('.').replaceFirstChar { it.lowercase() }, "warn")
        }
    }

    /** Значения, пришедшие с телефона, подставляются в открытую форму; на первом запуске — сразу открывают плейлист. */
    private fun fromPhone(playlist: String, epg: String) {
        when {
            Repo.active == null || view == "onb" -> { form["url"] = playlist; onboard() }
            formId != null || form.isNotEmpty() -> { form["url"] = playlist; if (epg.isNotEmpty()) form["epgUrl"] = epg; openView("plform", 1); toast("Ссылка пришла с телефона") }
            else -> { openForm(null); form["url"] = playlist; form["epgUrl"] = epg; render(); toast("Ссылка пришла с телефона") }
        }
    }

    private fun onboard() {
        if (busy != null) return
        val url = form["url"].orEmpty().trim()
        if (!url.startsWith("http://", true) && !url.startsWith("https://", true)) { formError = "Ссылка должна начинаться с http:// или https://"; openView("onbField"); return }
        formError = ""
        if (view != "onbField") view = "onbField"
        busy = scope.launch {
            val error = Repo.savePlaylist(null, "", url, "")
            busy = null
            if (error != null) { formError = error; index = 0; render(); return@launch }
            finish()
        }
        render()
    }

    private fun checkUpdate() {
        val ready = Updater.state.value as? Updater.State.Ready
        if (ready != null) startActivity(Intent(this, UpdateActivity::class.java))
        else { manualCheck = true; Updater.check(this) }
    }

    private fun onUpdate(st: Updater.State) {
        val (text, color) = when (st) {
            Updater.State.Idle -> "" to 0
            Updater.State.Checking -> "Проверяю…" to C.T2
            is Updater.State.UpToDate -> "У вас последняя версия" to C.T2
            is Updater.State.Downloading -> "Скачиваю ${st.version}… ${st.percent}%" to C.T2
            is Updater.State.Ready -> "Готова ${st.version} — нажмите ОК" to C.SUCCESS
            is Updater.State.Error -> "Не получилось проверить" to C.ERROR
        }
        updStatus = text; updColor = color
        if (st is Updater.State.Ready && manualCheck) { manualCheck = false; startActivity(Intent(this, UpdateActivity::class.java)) }
        if (view == "app" && !fieldsFocused()) render()
    }

    // ---------- плагины ----------
    private fun addPlugin() {
        if (busy != null) return
        val link = form["url"].orEmpty()
        busy = scope.launch {
            val error = Plugins.add(link)
            busy = null
            if (error != null) { formError = error; index = 0; render(); return@launch }
            val list = Plugins.items.value
            toast("Плагин «${list.last().title}» добавлен", "ok")
            form.clear()
            openView("plugins", list.size - 1)
        }
        render()
    }

    private fun togglePlugin(i: Int) {
        val p = Plugins.items.value.getOrNull(i) ?: return
        scope.launch {
            Plugins.setEnabled(i, !p.enabled)
            toast(if (p.enabled) "Плагин выключен" else "Плагин включён")
            render()
        }
    }

    private fun refreshPluginCode(i: Int) {
        toast("Загружаем код плагина…", "wait")
        scope.launch { toast(Plugins.refreshOne(i)); render() }
    }

    private fun pluginMenu(i: Int) {
        val p = Plugins.items.value.getOrNull(i) ?: return
        openPop(p.title, listOf(
            PopItem(if (p.enabled) "Выключить" else "Включить", if (p.enabled) R.drawable.ic_close else R.drawable.ic_check) { togglePlugin(i) },
            PopItem("Удалить…", R.drawable.ic_trash, danger = true) { askDeletePlugin(i) }), 1240, 300, 0)
    }

    private fun askDeletePlugin(i: Int) {
        val p = Plugins.items.value.getOrNull(i) ?: return
        openDialog("Удалить плагин «${p.title}»?", "Каналы останутся без его изменений. Чтобы вернуть плагин, понадобится ссылка на его файл.", listOf(
            DialogButton("Удалить", danger = true, icon = R.drawable.ic_trash) { closeDialog(); deletePlugin(i) },
            DialogButton("Отмена") { closeDialog() }), focus = 1)
    }

    /** Удаление с «Вернуть» на 6 с; фокус — на следующем плагине, иначе на предыдущем, иначе на «Добавить плагин». */
    private fun deletePlugin(i: Int) {
        scope.launch {
            val item = Plugins.remove(i) ?: return@launch
            openView("plugins", i.coerceAtMost(Plugins.items.value.size - 1).coerceAtLeast(0).let { if (Plugins.items.value.isEmpty()) 0 else it })
            toast("Плагин «${item.title}» удалён", action = ToastAction("Вернуть", {
                scope.launch { Plugins.restore(i, item); toast("Плагин возвращён"); if (view == "plugins") { index = i; render() } }
            }))
        }
    }

    // ---------------------------------------------------------------- кнопки пульта

    override fun holdable(): Boolean = tv && inPane && items.getOrNull(index)?.hold != null

    override fun onHoldOk(): Boolean { val h = items.getOrNull(index)?.hold ?: return false; h(); return true }

    override fun onOk() {
        if (!inPane) { if (items.isNotEmpty()) { inPane = true; index = 0; render() }; return }
        val it = items.getOrNull(index) ?: return
        if (it.off) return
        if (it.type == "field") { focusField(it.field); return }
        it.action?.invoke()
    }

    private fun focusField(name: String) {
        val f = fields[name] ?: return
        if (!f.isEnabled) return
        f.isFocusable = true
        f.isFocusableInTouchMode = true
        f.requestFocus()
        f.setSelection(f.length())
        (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showSoftInput(f, 0)
    }

    private fun blurField() {
        val f = currentFocus as? EditText ?: return
        (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(f.windowToken, 0)
        f.clearFocus()
        if (tv) f.isFocusable = false
        root.requestFocus()
        if (pendingRender) { pendingRender = false; render() }
    }

    override fun typingKey(key: Key, event: KeyEvent): Boolean {
        if (!tv) return false
        if (key != Key.UP && key != Key.DOWN) return false
        if (event.action == KeyEvent.ACTION_DOWN) { blurField(); move(if (key == Key.UP) -1 else 1) }
        return true
    }

    private fun move(dir: Int) {
        if (!tv) return
        val cur = items.getOrNull(index)
        var i = index + dir
        while (i in items.indices) {
            if (items[i].r != cur?.r && !items[i].off) {
                // в ряду кнопок встаём на первую доступную
                while (dir < 0 && i > 0 && items[i - 1].r == items[i].r && !items[i - 1].off) i--
                index = i; break
            }
            i += dir
        }
        render()
    }

    override fun onKey(key: Key, event: KeyEvent) {
        if (!tv) return
        if (!inPane) {
            when (key) {
                Key.UP -> if (cat > 0) { cat--; view = cats[cat].first; index = 0; render() }
                Key.DOWN -> if (cat < cats.size - 1) { cat++; view = cats[cat].first; index = 0; render() }
                Key.RIGHT -> onOk()
                else -> Unit
            }
            if (view == "playlists") this@SettingsActivity.handler.postDelayed({ Repo.prefs.markSeen("playlists") }, 4000)
            return
        }
        val cur = items.getOrNull(index)
        when (key) {
            Key.UP -> move(-1)
            Key.DOWN -> move(1)
            Key.LEFT -> if (index > 0 && items[index - 1].r == cur?.r && !items[index - 1].off) { index--; render() }
                else if (view in listOf("playlists", "playback", "app", "plugins")) { inPane = false; render() }
            Key.RIGHT -> if (index < items.size - 1 && items[index + 1].r == cur?.r && !items[index + 1].off) { index++; render() }
            Key.DEL -> cur?.del?.invoke()
            else -> Unit
        }
    }

    override fun onBack() {
        if (currentFocus is EditText) { blurField(); return }
        when (view) {
            "onb" -> finishAffinity()
            "onbField" -> {
                if (busy != null) { busy?.cancel(); busy = null; render(); return }
                if (Repo.active == null) { if (tv) openView("onb") else finishAffinity() } else finish()
            }
            "plform" -> {
                // Проверка отменяется, изменения остаются в форме; без проверки «Назад» сбрасывает изменения
                if (busy != null) { busy?.cancel(); busy = null; render(); return }
                if (formDirty()) toast("Изменения не сохранены")
                val i = Repo.playlists.value.indexOfFirst { it.id == formId }
                form.clear(); formError = ""
                openView("playlists", if (i >= 0) i else Repo.playlists.value.size)
            }
            "plugadd" -> { if (busy != null) { busy?.cancel(); busy = null; render(); return }; form.clear(); formError = ""; openView("plugins", Plugins.items.value.size) }
            "plugin" -> openView("plugins", plugIndex)
            else -> if (tv && inPane) { inPane = false; render() } else if (!tv && view != "playlists") openView("playlists") else finish()
        }
    }

    override fun onLayerClosed() = render()
}
