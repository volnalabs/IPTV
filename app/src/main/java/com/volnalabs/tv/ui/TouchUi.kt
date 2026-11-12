package com.volnalabs.tv.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.SpannableString
import android.text.Spanned
import android.text.TextWatcher
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.volnalabs.tv.R
import com.volnalabs.tv.data.Channel
import com.volnalabs.tv.data.Programme
import com.volnalabs.tv.data.Repo

/** Список строк без отдельных layout-файлов: строка собирается функцией bind заново. */
class RowsAdapter(private val bind: (Int) -> View, var count: Int = 0) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = object : RecyclerView.ViewHolder(FrameLayout(parent.context).apply {
        layoutParams = RecyclerView.LayoutParams(MATCH, WRAP)
    }) {}
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val box = holder.itemView as FrameLayout
        box.removeAllViews()
        box.addView(bind(position), FrameLayout.LayoutParams(MATCH, WRAP))
    }
    override fun getItemCount() = count
    @SuppressLint("NotifyDataSetChanged")
    fun update(n: Int) { count = n; notifyDataSetChanged() }
}

/**
 * Телефон и планшет (SPEC 7.3): касания вместо пульта, подсказок кнопок, мини-гида и ввода номера нет.
 * Телефон вертикально — видео сверху и вкладки «Каналы / Программа»; горизонтально — видео на весь экран.
 * Планшет 600–899 dp — как телефон, плюс кнопка «Сетка»; от 900 dp — список и программа рядом.
 */
class TouchUi(private val a: MainActivity) {
    private val u: Ui get() = a.ui
    private val h = Handler(Looper.getMainLooper())

    private lateinit var content: LinearLayout
    private lateinit var nowRow: LinearLayout
    private lateinit var plRow: LinearLayout
    private lateinit var tabs: LinearLayout
    private lateinit var chips: LinearLayout
    private lateinit var chipsScroll: HorizontalScrollView
    private lateinit var searchBar: LinearLayout
    private lateinit var searchField: EditText
    private lateinit var listView: RecyclerView
    private lateinit var listAdapter: RowsAdapter
    private lateinit var progCol: LinearLayout
    private lateinit var progDays: LinearLayout
    private lateinit var progView: RecyclerView
    private lateinit var progAdapter: RowsAdapter
    private lateinit var progTop: LinearLayout
    private lateinit var columns: LinearLayout
    private lateinit var listCol: LinearLayout
    private lateinit var stateBox: FrameLayout
    private lateinit var overlay: FrameLayout
    private lateinit var sidePanel: FrameLayout
    private lateinit var gridLayer: FrameLayout

    private var portrait = true
    private var wide = false
    private var tablet = false
    private var epgTab = false
    private var searching = false
    private var group = "all"
    private var list: List<Channel> = emptyList()
    private var progCh: Channel? = null
    private var videoH = 0

    fun build() {
        val root = a.root
        stateBox = u.box { visibility = View.GONE }
        root.addView(stateBox, u.fr(MATCH, MATCH))
        overlay = u.box { visibility = View.GONE }
        root.addView(overlay, u.fr(MATCH, MATCH))
        a.playerView.setOnClickListener { tapVideo() }
        a.playerView.setOnLongClickListener { a.cur?.let { channelMenu(it) }; true }
        stateBox.setOnClickListener { tapVideo() }

        content = u.col { setBackgroundColor(C.BG) }
        nowRow = u.row { u.pad(this, 16, 12, 16, 12); setOnClickListener { a.showInfo() } }
        plRow = u.row { u.pad(this, 8, 0, 4, 0) }
        tabs = u.row()
        chips = u.row { u.pad(this, 12, 0, 12, 0) }
        chipsScroll = HorizontalScrollView(a).apply { isHorizontalScrollBarEnabled = false; addView(chips) }
        searchField = EditText(a).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f); setTextColor(C.T1); setHintTextColor(C.T3)
            hint = "Название, номер или передача"; isSingleLine = true; background = null
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            typeface = u.font(400)
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, af: Int) {}
                override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
                override fun afterTextChanged(s: Editable?) { if (searching) { a.query = s.toString(); a.runSearch(); renderList() } }
            })
            setOnEditorActionListener { v, _, _ -> hideKeyboard(v); true }
        }
        searchBar = u.row {
            visibility = View.GONE
            background = u.rect(C.S2, 8)
            u.pad(this, 12, 0, 4, 0)
            addView(u.icon(R.drawable.ic_search, 20, C.T2), u.lin(20, 20, r = 8))
            addView(searchField, u.lin(0, 48, 1f))
            addView(iconButton(R.drawable.ic_close, "Закрыть поиск") { closeSearch() })
        }
        listAdapter = RowsAdapter({ i -> listRow(i) })
        listView = RecyclerView(a).apply { layoutManager = LinearLayoutManager(a); adapter = listAdapter; itemAnimator = null; clipToPadding = false }
        listCol = u.col {
            addView(chipsScroll, u.lin(MATCH, 48))
            addView(searchBar, u.lin(MATCH, 48, l = 12, t = 4, r = 12, b = 4))
            addView(listView, u.lin(MATCH, 0, 1f))
        }
        progAdapter = RowsAdapter({ i -> progRow(i) })
        progView = RecyclerView(a).apply { layoutManager = LinearLayoutManager(a); adapter = progAdapter; itemAnimator = null }
        progDays = u.row { u.pad(this, 12, 0, 12, 0) }
        progTop = u.row { u.pad(this, 16, 8, 8, 0) }
        progCol = u.col {
            addView(progTop, u.lin(MATCH, WRAP))
            addView(HorizontalScrollView(a).apply { isHorizontalScrollBarEnabled = false; addView(progDays) }, u.lin(MATCH, 48))
            addView(progView, u.lin(MATCH, 0, 1f))
        }
        columns = u.row(Gravity.TOP) {
            addView(listCol, u.lin(0, MATCH, 1f))
            addView(progCol, u.lin(0, MATCH, 1f))
        }
        content.addView(nowRow, u.lin(MATCH, WRAP))
        content.addView(plRow, u.lin(MATCH, 48))
        content.addView(tabs, u.lin(MATCH, 48))
        content.addView(columns, u.lin(MATCH, 0, 1f))
        root.addView(content, u.fr(MATCH, MATCH))

        sidePanel = u.box { visibility = View.GONE; setBackgroundColor(C.SCRIM); setOnClickListener { closeSide() } }
        root.addView(sidePanel, u.fr(MATCH, MATCH))
        gridLayer = u.box { visibility = View.GONE; setBackgroundColor(C.BG); isClickable = true }
        root.addView(gridLayer, u.fr(MATCH, MATCH))
        layout()
    }

    private fun iconButton(icon: Int, label: String, size: Int = 24, color: Int = C.T1, click: () -> Unit): View = u.box {
        addView(u.icon(icon, size, color), u.fr(size, size, Gravity.CENTER))
        contentDescription = label
        background = ripple()
        setOnClickListener { click() }
        layoutParams = ViewGroup.LayoutParams(u.p(48), u.p(48))
    }

    /** Нажатие строки — подсветка 100 мс цвета surface-3. */
    private fun ripple() = android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(C.S3), null, android.graphics.drawable.ColorDrawable(0xFFFFFFFF.toInt()))

    // ---------------------------------------------------------------- раскладка

    fun layout() {
        val cfg = a.resources.configuration
        portrait = cfg.orientation == Configuration.ORIENTATION_PORTRAIT
        tablet = cfg.smallestScreenWidthDp >= 600
        wide = cfg.screenWidthDp >= 900 && tablet
        val w = cfg.screenWidthDp
        val landscapeVideoOnly = !portrait && !tablet
        videoH = if (landscapeVideoOnly) MATCH else u.p(minOf(w * 9 / 16, if (tablet && !portrait) cfg.screenHeightDp * 45 / 100 else w * 9 / 16))
        a.playerView.layoutParams = FrameLayout.LayoutParams(MATCH, videoH, Gravity.TOP)
        stateBox.layoutParams = FrameLayout.LayoutParams(MATCH, videoH, Gravity.TOP)
        overlay.layoutParams = FrameLayout.LayoutParams(MATCH, videoH, Gravity.TOP)
        content.visibility = if (landscapeVideoOnly) View.GONE else View.VISIBLE
        (content.layoutParams as FrameLayout.LayoutParams).topMargin = if (landscapeVideoOnly) 0 else videoH
        content.requestLayout()
        immersive(landscapeVideoOnly)
        tabs.visibility = if (wide) View.GONE else View.VISIBLE
        progCol.visibility = if (wide || epgTab) View.VISIBLE else View.GONE
        listCol.visibility = if (wide || !epgTab) View.VISIBLE else View.GONE
        hideOverlay()
        onData()
    }

    private fun immersive(on: Boolean) {
        val win = a.window
        if (Build.VERSION.SDK_INT >= 30) {
            val c = win.insetsController ?: return
            if (on) { c.hide(WindowInsets.Type.systemBars()); c.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE }
            else c.show(WindowInsets.Type.systemBars())
        } else {
            @Suppress("DEPRECATION")
            win.decorView.systemUiVisibility = if (on) (View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY) else 0
        }
    }

    // ---------------------------------------------------------------- данные

    fun onPlaylists() { renderPlaylistRow() }

    fun onData() {
        if (!::content.isInitialized) return
        if (a.listOf(group) == null) group = "all"
        list = a.listOf(group) ?: emptyList()
        renderNow()
        renderPlaylistRow()
        renderTabs()
        renderChips()
        renderList()
        renderProg()
    }

    fun onEpg() { if (::content.isInitialized) { renderNow(); renderList(); renderProg() } }

    fun onPlay() { renderNow(); listAdapter.notifyDataSetChanged(); if (!epgTab || wide) renderProg(); if (overlay.visibility == View.VISIBLE) showOverlay() }

    fun onState() {
        val name = a.state
        stateBox.removeAllViews()
        if (name == null) { stateBox.visibility = View.GONE; return }
        stateBox.visibility = View.VISIBLE
        stateBox.setBackgroundColor(if (name == "buffer" || name == "noimage") 0 else 0xFF000000.toInt())
        if (name == "loading" || name == "buffer") {
            stateBox.addView(u.col(Gravity.CENTER_HORIZONTAL) {
                addView(Spinner(a, u.p(3).toFloat()), u.lin(40, 40))
                addView(u.text("Подгружаем поток…", 13, C.T2), u.lin(t = 12))
            }, u.fr(WRAP, WRAP, Gravity.CENTER))
            return
        }
        val (title, text) = when (name) {
            "error" -> "Канал не включился" to "Нет связи с сервером провайдера. Повторите или выберите другой канал."
            "denied" -> "Канал недоступен" to "Провайдер не отдаёт этот поток (ошибка ${a.stateCode}). Обычно это ограничение подписки."
            else -> "Есть звук, нет изображения" to "Канал передаёт только звук или изображение ещё не пришло."
        }
        stateBox.addView(u.col(Gravity.CENTER_HORIZONTAL) {
            u.pad(this, 24, 0, 24, 0)
            addView(u.icon(if (name == "error") R.drawable.ic_warning else R.drawable.ic_alert, 32, if (name == "error") C.WARNING else C.ERROR), u.lin(32, 32))
            addView(u.text(title, 17, C.T1, 600), u.lin(t = 8))
            addView(u.text(text, 13, C.T2, lines = 3).apply { gravity = Gravity.CENTER }, u.lin(t = 4))
            addView(a.button(if (name == "denied") "Другой канал" else "Повторить", focused = true).apply {
                setOnClickListener { if (name == "denied") { if (!portrait && !tablet) openSide() } else a.retryPlay() }
            }, u.lin(WRAP, 44, t = 12))
            contentDescription = title
        }, u.fr(MATCH, WRAP, Gravity.CENTER))
    }

    private var lastMinute = 0L
    fun tick() {
        if (!::content.isInitialized) return
        val m = System.currentTimeMillis() / MIN
        if (m != lastMinute) { lastMinute = m; renderNow(); listAdapter.notifyDataSetChanged(); renderProg() }
        if (overlay.visibility == View.VISIBLE && a.arch != null) showOverlay(rearm = false)
    }

    // ---------------------------------------------------------------- строка «сейчас», плейлист, вкладки, группы

    private fun renderNow() {
        nowRow.removeAllViews()
        val ch = a.cur ?: return
        val now = System.currentTimeMillis()
        val e = a.epg.forChannel(ch)
        val p = e?.indexAt(now) ?: -1
        nowRow.addView(u.logo(ch, 56, 42, 6), u.lin(56, 42, r = 12))
        nowRow.addView(u.col {
            addView(u.row {
                if (a.arch != null) addView(u.text("Запись", 12, C.TI, 600).apply { background = u.rect(C.PAPER, 4); u.pad(this, 6, 1, 6, 1) }, u.lin(r = 8))
                else addView(u.row { addView(View(a).apply { background = u.rect(C.ACCENT, 4) }, u.lin(8, 8, r = 4)); addView(u.text("Эфир", 12, C.ACCENT, 600)) }, u.lin(r = 8))
                addView(u.text(a.arch?.title ?: if (p >= 0) e!!.titles[p] else ch.group, 15, C.T1, 500), u.lin(0, WRAP, 1f))
            })
            addView(u.text("${ch.number} · ${ch.name}" + if (a.arch == null && p >= 0) " · до " + clock(e!!.stops[p]) else "", 13, C.T2), u.lin(t = 2))
        }, u.lin(0, WRAP, 1f))
        if (a.arch != null) nowRow.addView(a.button("В эфир", R.drawable.ic_back).apply { setOnClickListener { a.goLive() } }, u.lin(WRAP, 40))
    }

    private fun renderPlaylistRow() {
        if (!::plRow.isInitialized) return
        plRow.removeAllViews()
        val pl = Repo.active
        val many = Repo.playlists.value.size > 1
        plRow.addView(u.row {
            background = ripple()
            u.pad(this, 8, 0, 8, 0)
            addView(u.icon(R.drawable.ic_list, 20, C.T2), u.lin(20, 20, r = 8))
            addView(u.text((pl?.name ?: "IPTV") + " · " + a.playlist.channels.size, 15, C.T1, 500))
            if (many) addView(u.icon(R.drawable.ic_chevron_down, 20, C.T2), u.lin(20, 20, l = 4))
            if (!Repo.prefs.seen("playlists")) addView(u.newTag(size = 11), u.lin(l = 8))
            contentDescription = pl?.name
            if (many) setOnClickListener { playlistMenu() }
        }, u.lin(WRAP, 48))
        plRow.addView(View(a), u.lin(0, 1, 1f))
        plRow.addView(iconButton(R.drawable.ic_search, "Поиск") { openSearch() })
        plRow.addView(iconButton(R.drawable.ic_settings, "Настройки") { a.openSettings() })
    }

    private fun playlistMenu() {
        h.postDelayed({ Repo.prefs.markSeen("playlists") }, 4000)
        val items = Repo.playlists.value.map { p ->
            val bad = p.failed && p.id != Repo.activeId
            PopItem(p.name, if (p.id == Repo.activeId) R.drawable.ic_check else R.drawable.ic_list, sub = if (bad) "не загрузился" else if (p.count > 0) chCount(p.count) else "", off = bad) {
                a.activatePlaylist(p.id) { group = "all"; onData() }
            }
        } + PopItem(sep = true) + PopItem("Управлять плейлистами", R.drawable.ic_settings) { a.openSettings("playlists") }
        a.openPop("Плейлисты", items, 0, 0)
    }

    private fun renderTabs() {
        tabs.removeAllViews()
        for ((i, t) in listOf("Каналы", "Программа").withIndex()) {
            val sel = (i == 1) == epgTab
            tabs.addView(u.col(Gravity.CENTER_HORIZONTAL) {
                background = ripple()
                addView(u.text(t, 15, if (sel) C.T1 else C.T2, if (sel) 600 else 400), u.lin(t = 13))
                addView(View(a).apply { setBackgroundColor(if (sel) C.T1 else 0) }, u.lin(MATCH, 2, t = 12))
                contentDescription = t
                setOnClickListener { selectTab(i == 1) }
            }, u.lin(0, 48, 1f))
        }
    }

    private fun selectTab(epg: Boolean) {
        if (epg && searching) closeSearch()
        epgTab = epg
        if (epg) progCh = a.cur
        layout()
    }

    private fun chip(label: String, count: String, sel: Boolean, isNew: Boolean = false, click: () -> Unit): View = u.row(Gravity.CENTER) {
        background = if (sel) u.rect(C.PAPER, 16) else u.rect(C.S2, 16)
        u.pad(this, 12, 0, 12, 0)
        addView(u.text(label, 13, if (sel) C.TI else C.T1, 500))
        if (count.isNotEmpty()) addView(u.text(count, 13, if (sel) C.TI2 else C.T3), u.lin(l = 6))
        if (isNew) addView(u.newTag(size = 10), u.lin(l = 6))
        contentDescription = label
        setOnClickListener { click() }
    }.also { it.layoutParams = u.lin(WRAP, 32, r = 8, t = 8) }

    private fun renderChips() {
        chips.removeAllViews()
        fun add(id: String, label: String, n: Int, isNew: Boolean = false) = chips.addView(chip(label, n.toString(), group == id, isNew) {
            group = id
            if (id == "recent") Repo.prefs.markSeen("recent")
            Repo.prefs.lastGroup = id
            onData()
            listView.scrollToPosition(list.indexOfFirst { it.key == a.cur?.key }.coerceAtLeast(0))
        })
        add("all", "Все", a.playlist.channels.size)
        add("fav", "Избранное", a.favList().size)
        add("recent", "Недавние", a.recentList().size, !Repo.prefs.seen("recent"))
        a.menu.filter { it.id.startsWith("g:") }.forEach { add(it.id, it.title, it.list.size) }
    }

    // ---------------------------------------------------------------- список каналов и поиск

    private fun renderList() {
        if (searching) listAdapter.update(searchRows().size) else listAdapter.update(if (list.isEmpty()) 1 else list.size)
    }

    private fun emptyListState(): View = u.col {
        u.pad(this, 24, 32, 24, 32)
        val (icon, t, x) = when (group) {
            "fav" -> Triple(R.drawable.ic_star, "В избранном пока пусто", "Нажмите звезду в строке канала, чтобы добавить его сюда.")
            "recent" -> Triple(R.drawable.ic_history, "Здесь появятся каналы, которые вы смотрели", "Последние 7 дней, до 20 каналов.")
            else -> Triple(R.drawable.ic_alert, "В плейлисте нет каналов", "Ссылка открылась, но в файле нет ни одного канала. Проверьте ссылку у провайдера.")
        }
        addView(u.icon(icon, 32, C.T2), u.lin(32, 32))
        addView(u.text(t, 17, C.T1, 600, lines = 2), u.lin(t = 12))
        addView(u.text(x, 15, C.T2, lines = 3), u.lin(t = 4))
    }

    /** Строка канала 64 dp: номер · логотип · название и передача · звезда (отдельная цель 48×48). */
    private fun channelRow(c: Channel, name: CharSequence? = null, from: List<Channel>? = null, groupId: String = "all"): View {
        val now = System.currentTimeMillis()
        val e = a.epg.forChannel(c)
        val p = e?.indexAt(now) ?: -1
        val playing = a.cur?.key == c.key
        val fav = a.isFav(c)
        return u.row {
            background = ripple()
            minimumHeight = u.p(64)
            u.pad(this, 8, 0, 0, 0)
            addView(u.text(c.number.toString(), 13, C.T3).apply { gravity = Gravity.END }, u.lin(32, WRAP, r = 8))
            addView(u.logo(c, 56, 42, 6), u.lin(56, 42, r = 12))
            addView(u.col {
                addView(u.text(name ?: c.name, 16, C.T1, 500))
                addView(u.text(if (p >= 0) e!!.titles[p] else if (a.epg.byId.isNotEmpty()) "Нет телепрограммы" else c.group, 13, if (p >= 0) C.T2 else C.T3))
                if (p >= 0) addView(u.bar((now - e!!.starts[p]).toFloat() / (e.stops[p] - e.starts[p]).coerceAtLeast(1), C.TRACK, if (playing) C.ACCENT else C.T3, 3), u.lin(MATCH, 3, t = 4))
            }, u.lin(0, WRAP, 1f))
            if (playing) addView(u.icon(R.drawable.ic_playing, 18, C.ACCENT), u.lin(18, 18, l = 8))
            addView(iconButton(if (fav) R.drawable.ic_star_filled else R.drawable.ic_star, if (fav) "Убрать из избранного" else "В избранное", 20, if (fav) C.T1 else C.T2) { a.toggleFav(c) })
            contentDescription = "${c.number}, ${c.name}" + if (p >= 0) ", сейчас: ${e!!.titles[p]}" else ""
            setOnClickListener { a.tuneTo(c, from ?: list, groupId); closeSide() }
            setOnLongClickListener { channelMenu(c); true }
        }
    }

    private fun listRow(i: Int): View {
        if (searching) return searchRows().getOrNull(i)?.invoke() ?: View(a)
        if (list.isEmpty()) return emptyListState()
        return channelRow(list[i], groupId = group)
    }

    private fun channelMenu(c: Channel) {
        val fav = a.isFav(c)
        val items = arrayListOf(
            PopItem("Смотреть", R.drawable.ic_play) { a.tuneTo(c, list, group) },
            PopItem(if (fav) "Убрать из избранного" else "Добавить в избранное", if (fav) R.drawable.ic_star_filled else R.drawable.ic_star) { a.toggleFav(c) })
        if (a.epg.forChannel(c) != null) items += PopItem("Программа канала", R.drawable.ic_guide) { showProgramme(c, 0) }
        if (tablet && a.epg.byId.isNotEmpty()) items += PopItem("Найти в сетке телепрограммы", R.drawable.ic_grid) { openGrid(c) }
        a.openPop(c.name + " · " + c.number, items, 0, 0)
    }

    private fun openSearch() {
        if (!portrait && !tablet) return
        if (epgTab && !wide) { epgTab = false; layout() }
        searching = true
        a.query = ""; a.runSearch()
        chipsScroll.visibility = View.GONE
        searchBar.visibility = View.VISIBLE
        searchField.setText("")
        searchField.requestFocus()
        (a.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showSoftInput(searchField, 0)
        renderList()
    }

    private fun closeSearch() {
        searching = false
        a.query = ""; a.found = emptyList() // истории запросов нет
        hideKeyboard(searchField)
        searchField.clearFocus()
        searchBar.visibility = View.GONE
        chipsScroll.visibility = View.VISIBLE
        renderList()
    }

    private fun hideKeyboard(v: View) = (a.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(v.windowToken, 0)

    private fun hl(text: String): CharSequence {
        val q = a.query.trim()
        val at = text.lowercase().replace('ё', 'е').indexOf(q.lowercase().replace('ё', 'е'))
        if (q.isEmpty() || at < 0) return text
        return SpannableString(text).apply {
            setSpan(StyleSpan(android.graphics.Typeface.BOLD), at, at + q.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            setSpan(UnderlineSpan(), at, at + q.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }

    /** Результаты поиска по разделам: заголовки и строки. */
    private fun searchRows(): List<() -> View> {
        val out = ArrayList<() -> View>()
        if (a.query.isBlank()) return out
        if (a.found.isEmpty() && a.foundSections.none { it.status.isNotEmpty() }) {
            out += { u.col { u.pad(this, 24, 32, 24, 0)
                addView(u.text("Ничего не нашлось", 17, C.T1, 600))
                addView(u.text("Попробуйте короче или наберите номер канала цифрами.", 15, C.T2, lines = 2), u.lin(t = 4)) } }
            return out
        }
        val now = System.currentTimeMillis()
        for (s in a.foundSections) {
            out += { u.row { u.pad(this, 16, 16, 16, 6)
                addView(u.text(s.name, 15, C.T1, 600), u.lin(r = 8))
                addView(u.text(if (s.status.isNotEmpty()) s.status else listOf(s.note, if (s.count > 0) s.count.toString() else "не найдено").filter { it.isNotEmpty() }.joinToString(" · "), 13, C.T3, lines = 2), u.lin(0, WRAP, 1f))
                if (s.isNew.isNotEmpty() && s.count > 0 && !Repo.prefs.seen(s.isNew)) addView(u.newTag(size = 10), u.lin(l = 6)) } }
            for (i in s.from until s.from + s.count) {
                val f = a.found[i]
                out += {
                    if (f.type == "ch") channelRow(f.ch!!, hl(f.ch.name), a.playlist.channels)
                    else u.row {
                        background = ripple()
                        minimumHeight = u.p(56)
                        u.pad(this, 16, 6, 16, 6)
                        addView(if (f.type == "prog") u.text(clock(f.start), 13, C.T2) else u.icon(if (f.type == "group") R.drawable.ic_folder else R.drawable.ic_list, 20, C.T2), u.lin(48, WRAP, r = 8))
                        addView(u.col {
                            addView(u.text(hl(f.title.ifEmpty { "Без названия" }), 15, C.T1))
                            val sub = when (f.type) {
                                "prog" -> f.ch!!.name + " · " + when { f.start <= now && f.stop > now -> "идёт сейчас, до " + clock(f.stop); f.rec -> "запись есть"; else -> dayTab(f.start, now).lowercase() }
                                "group" -> chCount(a.menu.firstOrNull { it.id == f.id }?.list?.size ?: 0)
                                else -> if (f.id == Repo.activeId) "Активный" else ""
                            }
                            if (sub.isNotEmpty()) addView(u.text(sub, 13, C.T2))
                        }, u.lin(0, WRAP, 1f))
                        if (f.rec) addView(u.icon(R.drawable.ic_history, 18, C.T2), u.lin(18, 18, l = 8))
                        contentDescription = f.title
                        setOnClickListener { hideKeyboard(searchField); a.resultChoose(f); if (f.type != "prog" || f.rec || f.start <= now) closeSearch() }
                        if (f.ch != null) setOnLongClickListener { channelMenu(f.ch); true }
                    }
                }
            }
        }
        h.postDelayed({ Repo.prefs.markSeen("progsearch") }, 4000)
        return out
    }

    fun showGroup(id: String) { closeSearch(); group = id; epgTab = false; layout() }

    // ---------------------------------------------------------------- программа

    fun showProgramme(ch: Channel, start: Long) {
        if (searching) closeSearch()
        progCh = ch
        epgTab = true
        layout()
        if (start > 0) { a.loadProg(ch, dayStart(start)); progAdapter.update(a.prog.size); a.prog.indexOfFirst { it.start == start }.takeIf { it >= 0 }?.let { progView.scrollToPosition(it) } }
    }

    private fun renderProg() {
        if (!::progCol.isInitialized || progCol.visibility != View.VISIBLE) return
        val ch = progCh ?: a.cur
        val day = if (a.epgCh?.key == ch?.key) a.day else null
        a.loadProg(ch, day)
        progTop.removeAllViews()
        progTop.addView(u.text("Программа", 15, C.T1, 600), u.lin(r = 8))
        if (ch != null) progTop.addView(u.text(ch.name + if (ch.hasArchive) " · записи за ${plural(ch.catchupDays, "день", "дня", "дней")}" else "", 13, C.T2), u.lin(0, WRAP, 1f))
        if (tablet && a.epg.byId.isNotEmpty()) progTop.addView(a.button("Сетка", R.drawable.ic_grid).apply { setOnClickListener { openGrid(ch) } }, u.lin(WRAP, 40))
        progDays.removeAllViews()
        val now = System.currentTimeMillis()
        a.days.forEach { d -> progDays.addView(chip(dayTab(d, now), "", d == a.day) { a.loadProg(ch, d); renderProg() }) }
        progAdapter.update(if (a.prog.isEmpty()) 1 else a.prog.size + 1)
        if (a.day == dayStart(now)) progView.scrollToPosition((a.pi - 1).coerceAtLeast(0))
    }

    private fun progRow(i: Int): View {
        val ch = progCh ?: a.cur
        if (a.prog.isEmpty()) return u.col {
            u.pad(this, 16, 24, 16, 24)
            addView(u.text(if (a.epgLoading) a.epgStatus.ifEmpty { "Загружаю программу…" } else "Для этого канала телепрограммы нет", 17, C.T1, 600, lines = 2))
            if (!a.epgLoading) addView(u.text("Провайдер не передаёт расписание. Канал можно смотреть.", 15, C.T2, lines = 2), u.lin(t = 4))
        }
        if (i == a.prog.size) return u.text("Прошедшие передачи с часами — можно смотреть в записи.", 12, C.T3, lines = 2).apply { u.pad(this, 16, 12, 16, 16) }
        val p = a.prog[i]
        val now = System.currentTimeMillis()
        val isNow = p.start <= now && p.stop > now
        val past = p.stop <= now
        return u.row(Gravity.CENTER_VERTICAL) {
            background = ripple()
            minimumHeight = u.p(52)
            u.pad(this, 16, 6, 16, 6)
            addView(u.text(clock(p.start), 13, C.T2), u.lin(48, WRAP, r = 12))
            addView(u.col {
                addView(u.text(p.title, 15, if (past) C.T2 else C.T1, if (isNow) 500 else 400))
                if (isNow) addView(u.bar((now - p.start).toFloat() / (p.stop - p.start).coerceAtLeast(1), C.TRACK, C.ACCENT, 3), u.lin(MATCH, 3, t = 4))
                if (isNow && p.desc.isNotEmpty()) addView(u.text(p.desc, 13, C.T2, lines = 3), u.lin(t = 4))
            }, u.lin(0, WRAP, 1f))
            if (isNow) addView(u.row { addView(View(a).apply { background = u.rect(C.ACCENT, 4) }, u.lin(8, 8, r = 4)); addView(u.text("Эфир", 12, C.ACCENT, 600)) }, u.lin(l = 8))
            else if (past && p.arch) addView(u.icon(R.drawable.ic_history, 18, C.T2), u.lin(18, 18, l = 8))
            contentDescription = clock(p.start) + ", " + p.title
            setOnClickListener { ch?.let { a.chooseProgramme(p, it) } }
        }
    }

    // ---------------------------------------------------------------- видео: касание показывает инфо и панель

    private val hideTask = Runnable { hideOverlay() }

    private fun tapVideo() {
        if (a.state == "error" || a.state == "noimage") { a.retryPlay(); return }
        if (overlay.visibility == View.VISIBLE) hideOverlay() else showOverlay()
    }

    fun showInfo() = showOverlay()

    private fun showOverlay(rearm: Boolean = true) {
        val ch = a.cur ?: return
        val now = System.currentTimeMillis()
        val e = a.epg.forChannel(ch)
        val p = e?.indexAt(now) ?: -1
        overlay.removeAllViews()
        overlay.setOnClickListener { hideOverlay() }
        overlay.addView(View(a).apply { background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(0, 0x9E000000.toInt())) }, u.fr(MATCH, MATCH))
        val arch = a.arch
        overlay.addView(u.col {
            u.pad(this, 16, 0, 8, 8)
            addView(u.row {
                addView(u.logo(ch, 40, 30, 4), u.lin(40, 30, r = 12))
                addView(u.col {
                    addView(u.text("${ch.number} · ${ch.name}", 16, C.T1, 600))
                    addView(u.text(arch?.let { "Запись · " + whenText(it.start, now) + " · " + it.title } ?: if (p >= 0) clock(e!!.starts[p]) + " – " + clock(e.stops[p]) + "  " + e.titles[p] else "Для этого канала нет телепрограммы", 13, C.T2))
                }, u.lin(0, WRAP, 1f))
            })
            if (arch != null) addView(scrubber(), u.lin(MATCH, 48))
            addView(u.row {
                if (arch != null) {
                    addView(iconButton(R.drawable.ic_rewind, "−30 с") { a.seek(-30); showOverlay() })
                    addView(iconButton(if (a.paused) R.drawable.ic_play else R.drawable.ic_pause, if (a.paused) "Продолжить" else "Пауза") { a.togglePause() })
                    addView(iconButton(R.drawable.ic_forward, "+30 с") { a.seek(30); showOverlay() })
                    addView(a.button("Вернуться в эфир", R.drawable.ic_back).apply { setOnClickListener { a.goLive() } }, u.lin(WRAP, 40, l = 8))
                } else {
                    if (!portrait && !tablet) addView(iconButton(R.drawable.ic_list, "Каналы") { openSide() })
                    addView(iconButton(R.drawable.ic_chevron_down, "Пред.") { a.zap(-1) })
                    addView(iconButton(R.drawable.ic_chevron_up, "След.") { a.zap(1) })
                    val fav = a.isFav(ch)
                    addView(iconButton(if (fav) R.drawable.ic_star_filled else R.drawable.ic_star, if (fav) "Убрать из избранного" else "В избранное", color = if (fav) C.PAPER else C.T1) { a.toggleFav(ch); showOverlay() })
                }
                addView(View(a), u.lin(0, 1, 1f))
                val full = !portrait && !tablet
                addView(iconButton(if (full) R.drawable.ic_exit_fs else R.drawable.ic_fullscreen, if (full) "Выйти из полноэкранного режима" else "На весь экран") { toggleFull() })
            }, u.lin(MATCH, 48))
        }, u.fr(MATCH, WRAP, Gravity.BOTTOM))
        overlay.visibility = View.VISIBLE
        if (rearm) { h.removeCallbacks(hideTask); if (!(a.arch != null && a.paused)) h.postDelayed(hideTask, 4000) }
    }

    private fun hideOverlay() { h.removeCallbacks(hideTask); overlay.visibility = View.GONE }

    /** Шкала записи: перетаскивание бегунка (область касания 48 dp), время над бегунком во время жеста. */
    @SuppressLint("ClickableViewAccessibility")
    private fun scrubber(): View {
        val len = a.archLength().coerceAtLeast(1)
        val pos = a.position().coerceAtMost(len)
        val label = u.text("", 12, C.T1, 600)
        val bar = u.bar(pos.toFloat() / len, C.TRACK, C.PAPER, 4)
        return u.box {
            addView(label, u.fr(WRAP, WRAP))
            addView(u.row {
                addView(u.text(duration(pos), 12, C.T2), u.lin(r = 8))
                addView(bar, u.lin(0, 4, 1f))
                addView(u.text("−" + duration(len - pos), 12, C.T2), u.lin(l = 8))
            }, u.fr(MATCH, WRAP, Gravity.CENTER_VERTICAL))
            setOnTouchListener { v, ev ->
                val x0 = bar.left.toFloat()
                val part = ((ev.x - x0) / bar.width.coerceAtLeast(1)).coerceIn(0f, 1f)
                h.removeCallbacks(hideTask)
                when (ev.action) {
                    MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> { label.text = duration((part * len).toLong()); label.translationX = ev.x - label.width / 2f }
                    MotionEvent.ACTION_UP -> { a.player.seekTo((part * len * 1000).toLong()); label.text = ""; h.postDelayed(hideTask, 4000); v.performClick() }
                }
                true
            }
        }
    }

    private fun toggleFull() {
        a.requestedOrientation = if (!portrait && !tablet) ActivityInfo.SCREEN_ORIENTATION_PORTRAIT else ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        // После поворота руками возвращаем свободный поворот
        h.postDelayed({ a.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_USER }, 3000)
    }

    /** Горизонтально на телефоне: список каналов поверх видео по кнопке «Каналы». */
    private fun openSide() {
        hideOverlay()
        sidePanel.removeAllViews()
        val rv = RecyclerView(a).apply {
            setBackgroundColor(C.BG)
            layoutManager = LinearLayoutManager(a)
            itemAnimator = null
            adapter = RowsAdapter({ i -> channelRow(list[i], groupId = group) }, list.size)
            scrollToPosition(list.indexOfFirst { it.key == a.cur?.key }.coerceAtLeast(0))
            isClickable = true
        }
        sidePanel.addView(rv, u.fr(360, MATCH))
        sidePanel.visibility = View.VISIBLE
    }

    private fun closeSide() { sidePanel.visibility = View.GONE }

    // ---------------------------------------------------------------- сетка на планшете (SPEC 3.4)

    private fun openGrid(focus: Channel?) {
        if (!tablet) return
        val now = System.currentTimeMillis()
        val t0 = now - now % (30 * MIN) - 60 * MIN
        val t1 = t0 + 12 * 60 * MIN
        val perMin = 4f // dp в минуту
        val rows = (a.listOf(group) ?: a.playlist.channels).take(60)
        h.postDelayed({ Repo.prefs.markSeen("grid") }, 4000)
        gridLayer.removeAllViews()
        val names = u.col()
        val cells = u.box()
        val scale = u.box()
        var t = t0
        while (t < t1) { scale.addView(u.text(clock(t), 12, C.T3, 500), u.fr(WRAP, WRAP, l = ((t - t0) / MIN * perMin).toInt(), t = 8)); t += 30 * MIN }
        rows.forEachIndexed { r, c ->
            names.addView(u.row {
                u.pad(this, 8, 0, 8, 0)
                addView(u.text(c.number.toString(), 12, C.T3).apply { gravity = Gravity.END }, u.lin(28, WRAP, r = 8))
                addView(u.text(c.name, 14, if (a.cur?.key == c.key) C.ACCENT else C.T1, 500), u.lin(0, WRAP, 1f))
                foreground = android.graphics.drawable.ColorDrawable(0); background = ripple()
                setOnClickListener { gridLayer.visibility = View.GONE; a.tuneTo(c, rows, group) }
            }, u.lin(MATCH, 64))
            val e = a.epg.forChannel(c)
            if (e == null) cells.addView(u.text("Нет телепрограммы — канал можно смотреть, расписания нет", 13, C.T3).apply { background = u.rect(0, 6, C.S1, 1); u.pad(this, 10, 8, 10, 0) },
                u.fr(((t1 - t0) / MIN * perMin).toInt() - 4, 56, t = r * 64 + 4))
            else {
                var k = e.firstEndingAfter(t0)
                while (k < e.size && e.starts[k] < t1) {
                    val s = maxOf(e.starts[k], t0); val en = minOf(e.stops[k], t1)
                    val w = ((en - s) / MIN * perMin).toInt() - 4
                    val isNow = e.starts[k] <= now && e.stops[k] > now
                    val past = e.stops[k] <= now
                    val arch = past && c.hasArchive && e.starts[k] >= now - c.catchupDays * DAY
                    val prog = Programme(e.starts[k], e.stops[k], e.titles[k])
                    if (w > 4) cells.addView(u.col {
                        background = if (past) u.rect(0, 6, C.S1, 1) else u.rect(if (isNow) C.S2 else C.S1, 6)
                        u.pad(this, 10, 8, 10, 0)
                        if (w >= 115) { addView(u.text((if (e.starts[k] < t0) "← " else "") + prog.title, 13, if (past) C.T2 else C.T1)); addView(u.text(clock(prog.start) + "–" + clock(prog.stop), 12, C.T3)) }
                        else if (w >= 80) addView(u.text("до " + clock(prog.stop), 12, C.T3))
                        contentDescription = prog.title
                        setOnClickListener {
                            gridLayer.visibility = View.GONE
                            if (arch) a.play(c, prog) else a.tuneTo(c, rows, group)
                        }
                    }, u.fr(w, 56, l = ((s - t0) / MIN * perMin).toInt(), t = r * 64 + 4))
                    k++
                }
            }
        }
        val nowX = ((now - t0) / MIN * perMin).toInt()
        cells.addView(View(a).apply { setBackgroundColor(C.ACCENT) }, u.fr(2, rows.size * 64, l = nowX))
        val timeW = ((t1 - t0) / MIN * perMin).toInt()
        val hs = HorizontalScrollView(a).apply {
            isHorizontalScrollBarEnabled = false
            addView(u.col { addView(scale, u.lin(timeW, 32)); addView(cells, u.lin(timeW, rows.size * 64)) })
            post { scrollTo(u.p(maxOf(0, nowX - 120)), 0) }
        }
        val body = ScrollView(a).apply { addView(u.row(Gravity.TOP) { addView(u.col { addView(View(a), u.lin(MATCH, 32)); addView(names) }, u.lin(200, WRAP)); addView(hs, u.lin(0, WRAP, 1f)) }) }
        gridLayer.addView(u.col {
            addView(u.row {
                u.pad(this, 8, 0, 16, 0)
                addView(iconButton(R.drawable.ic_chevron_left, "Назад") { gridLayer.visibility = View.GONE })
                addView(u.text("Телепрограмма", 18, C.T1, 600), u.lin(l = 4))
                if (!Repo.prefs.seen("grid")) addView(u.newTag(size = 11), u.lin(l = 8))
                addView(View(a), u.lin(0, 1, 1f))
                addView(u.text(a.menuTitle(group), 14, C.T2))
            }, u.lin(MATCH, 56))
            addView(body, u.lin(MATCH, 0, 1f))
        }, u.fr(MATCH, MATCH))
        gridLayer.visibility = View.VISIBLE
        val row = rows.indexOfFirst { it.key == focus?.key }
        if (row > 3) body.post { body.scrollTo(0, u.p((row - 3) * 64)) }
    }

    /** Системный «назад»: снимает поиск, сетку, список поверх видео, вкладку программы; иначе — выход. */
    fun onBack(): Boolean = when {
        gridLayer.visibility == View.VISIBLE -> { gridLayer.visibility = View.GONE; true }
        sidePanel.visibility == View.VISIBLE -> { closeSide(); true }
        searching -> { closeSearch(); true }
        a.arch != null -> { a.goLive(); true }
        epgTab && !wide -> { selectTab(false); true }
        else -> false
    }
}
