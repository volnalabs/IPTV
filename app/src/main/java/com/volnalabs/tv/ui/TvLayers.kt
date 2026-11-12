package com.volnalabs.tv.ui

import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import coil.load
import com.volnalabs.tv.R
import com.volnalabs.tv.data.Channel
import com.volnalabs.tv.data.Programme
import com.volnalabs.tv.data.Repo

/**
 * Слои телевизора поверх видео: инфо, панель записи, ввод номера, громкость, состояния плеера, гид,
 * сетка «каналы × время» и поиск. Размеры — px холста 1920×1080 (SPEC 3, 4); на Android TV это dp × 2.
 * Каждый слой перерисовывается целиком из состояния MainActivity, как в web-версии.
 */
class TvLayers(private val a: MainActivity) {
    private val u: Ui get() = a.ui
    private val h = Handler(Looper.getMainLooper())

    lateinit var shadeB: View
    lateinit var shadeT: View
    lateinit var osd: LinearLayout
    lateinit var archTag: LinearLayout
    lateinit var topRight: FrameLayout
    lateinit var zapChip: LinearLayout
    lateinit var vol: LinearLayout
    lateinit var timeline: LinearLayout
    lateinit var ctl: LinearLayout
    lateinit var ctlCap: LinearLayout
    lateinit var ctlRow: LinearLayout
    lateinit var stateLayer: FrameLayout
    lateinit var guide: FrameLayout
    lateinit var gTitle: LinearLayout
    lateinit var gClock: LinearLayout
    lateinit var gMenu: LinearLayout
    lateinit var gChans: FrameLayout
    lateinit var gDet: LinearLayout
    lateinit var gHints: FrameLayout
    lateinit var grid: FrameLayout
    lateinit var search: FrameLayout
    lateinit var searchText: TextView
    lateinit var kb: LinearLayout
    lateinit var searchHead: TextView
    lateinit var results: LinearLayout
    lateinit var searchHints: FrameLayout
    lateinit var numpad: LinearLayout
    lateinit var numHints: FrameLayout

    var progKey = ""

    fun build() {
        val root = a.root
        stateLayer = u.box { visibility = View.GONE }
        root.addView(stateLayer, u.fr(MATCH, MATCH))
        shadeB = View(a).apply { visibility = View.GONE; background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(0, 0x8C000000.toInt(), 0xDB000000.toInt())) }
        root.addView(shadeB, u.fr(MATCH, 713, Gravity.BOTTOM))
        shadeT = View(a).apply { visibility = View.GONE; background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(0x80000000.toInt(), 0)) }
        root.addView(shadeT, u.fr(MATCH, 324))
        osd = u.row(Gravity.BOTTOM) { visibility = View.GONE }
        root.addView(osd, u.fr(MATCH, WRAP, Gravity.BOTTOM, l = 36, r = 96, b = 54))
        timeline = u.row { visibility = View.GONE }
        root.addView(timeline, u.fr(MATCH, 36, Gravity.BOTTOM, l = 96, r = 96, b = 170))
        ctlCap = u.row()
        ctlRow = u.row()
        ctl = u.col { visibility = View.GONE; addView(ctlCap, u.lin(MATCH, 36, b = 14)); addView(ctlRow, u.lin(MATCH, 64)) }
        root.addView(ctl, u.fr(MATCH, WRAP, Gravity.BOTTOM, l = 96, r = 96, b = 54))
        archTag = u.row()
        root.addView(archTag, u.fr(WRAP, 52, l = 96, t = 54))
        topRight = u.box { visibility = View.GONE }
        root.addView(topRight, u.fr(WRAP, 54, Gravity.TOP or Gravity.END, r = 96, t = 54))
        zapChip = u.row { visibility = View.GONE; background = u.rect(C.SCRIM, 12); u.pad(this, 20, 0, 20, 0) }
        root.addView(zapChip, u.fr(WRAP, 56, l = 96, t = 54))
        vol = u.row { visibility = View.GONE }
        root.addView(vol, u.fr(WRAP, WRAP, Gravity.TOP or Gravity.END, r = 96, t = 54))
        buildGuide()
        grid = u.box { visibility = View.GONE; setBackgroundColor(C.SCRIM_GUIDE); isClickable = true }
        root.addView(grid, u.fr(MATCH, MATCH))
        buildSearch()
        numpad = u.col { visibility = View.GONE; background = u.rect(C.S2, 20, C.LINE, 2); u.pad(this, 16, 32, 16, 24) }
        root.addView(numpad, u.fr(720, WRAP, Gravity.TOP or Gravity.END, r = 96, t = 54))
        numHints = u.box { visibility = View.GONE }
        root.addView(numHints, u.fr(WRAP, 54, Gravity.BOTTOM or Gravity.END, r = 96, b = 54))
    }

    // ================================================================ общие куски

    /** Строка канала (поиск, ввод номера): номер · логотип · название и передача · «играет». */
    fun chRow(c: Channel, sel: Boolean, act: Boolean = false, digits: Int = 0, name: CharSequence? = null, bar: Boolean = true): LinearLayout {
        val now = System.currentTimeMillis()
        val e = a.epg.forChannel(c)
        val p = e?.indexAt(now) ?: -1
        val playing = a.cur?.key == c.key
        return u.row {
            background = if (sel) u.rect(C.PAPER, 12) else if (act) u.rect(C.S3, 12) else null
            u.pad(this, 12, 0, 28, 0)
            val num = c.number.toString()
            val no = if (digits > 0) SpannableString(num).apply {
                setSpan(StyleSpan(android.graphics.Typeface.BOLD), 0, minOf(digits, num.length), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                setSpan(ForegroundColorSpan(if (sel) C.TI else C.T1), 0, minOf(digits, num.length), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            } else num
            addView(u.text(no, 28, if (sel) C.TI2 else C.T3, 500).apply { gravity = Gravity.END }, u.lin(72, WRAP, r = 24))
            addView(u.logo(c, 112, 84, 12), u.lin(112, 84, r = 24))
            addView(u.col {
                addView(u.text(name ?: c.name, 32, if (sel) C.TI else C.T1, 500))
                val sub = if (p >= 0) e!!.titles[p] else if (a.epg.byId.isNotEmpty()) "Нет телепрограммы" else c.group
                addView(u.text(sub, 28, if (sel) C.TI2 else if (p < 0) C.T3 else C.T2))
                if (p >= 0 && bar) addView(u.bar((now - e!!.starts[p]).toFloat() / (e.stops[p] - e.starts[p]).coerceAtLeast(1), if (sel) C.TRACK_ON_PAPER else C.TRACK,
                    if (playing) (if (sel) C.ACCENT_ON_PAPER else C.ACCENT) else if (sel) C.TI2 else C.T3, 4), u.lin(MATCH, 4, t = 8))
            }, u.lin(0, WRAP, 1f))
            if (playing) addView(u.icon(R.drawable.ic_playing, 36, if (sel) C.ACCENT_ON_PAPER else C.ACCENT), u.lin(36, 36, l = 16))
            if (a.isFav(c)) addView(u.icon(R.drawable.ic_star_filled, 36, if (sel) C.TI2 else C.T2), u.lin(36, 36, l = 16))
            contentDescription = "${c.number}, ${c.name}" + if (p >= 0) ", сейчас: ${e!!.titles[p]}" else ""
        }
    }

    private fun state(icon: Int, title: String, text: String): LinearLayout = u.col {
        u.pad(this, 0, 40, 0, 0)
        addView(u.icon(icon, 56, C.T2), u.lin(56, 56))
        addView(u.text(title, 36, C.T1, 600, lines = 2, lineHeight = 44), u.lin(MATCH, WRAP, t = 16))
        addView(u.text(text, 30, C.T2, lines = 4, lineHeight = 40), u.lin(MATCH, WRAP, t = 8))
    }

    private fun live(sel: Boolean = false) = u.row {
        val c = if (sel) C.ACCENT_ON_PAPER else C.ACCENT
        addView(View(a).apply { background = u.rect(c, 7) }, u.lin(14, 14, r = 10))
        addView(u.text("Эфир", 24, c, 600))
    }

    private fun newTagIf(feature: String, label: String = "новое"): View? = if (Repo.prefs.seen(feature)) null else u.newTag(label)

    private fun setHints(box: FrameLayout, vararg items: Pair<String, String>, onVideo: Boolean = false) {
        box.removeAllViews()
        box.addView(a.hints(*items, onVideo = onVideo), u.fr(WRAP, MATCH))
    }

    private fun spinner(size: Int, stroke: Int) = Spinner(a, u.p(stroke).toFloat()).apply { layoutParams = LinearLayout.LayoutParams(u.p(size), u.p(size)) }

    // ================================================================ инфо (OSD), верхняя строка, состояния

    fun osdShown() = osd.visibility == View.VISIBLE
    private val hideInfoTask = Runnable { hideInfo() }

    fun showInfo() {
        if (a.cur == null || !(a.zone == "play" || a.zone == "ctl")) return
        renderOsd()
        osd.visibility = View.VISIBLE
        shadeB.visibility = View.VISIBLE
        renderTopRight()
        h.removeCallbacks(hideInfoTask)
        if (!(a.arch != null && a.paused)) h.postDelayed(hideInfoTask, 5000)
    }

    fun hideInfo() {
        h.removeCallbacks(hideInfoTask)
        osd.visibility = View.GONE
        if (ctl.visibility != View.VISIBLE) shadeB.visibility = View.GONE
        renderTopRight()
    }

    private fun archText(p: Programme) = "Запись · " + whenText(p.start, System.currentTimeMillis())

    private fun archTagView(p: Programme) = u.row {
        background = u.rect(C.PAPER, 10)
        u.pad(this, 18, 0, 18, 0)
        addView(u.icon(R.drawable.ic_history, 30, C.TI), u.lin(30, 30, r = 12))
        addView(u.text(archText(p), 28, C.TI, 600))
    }

    fun renderOsd() {
        val ch = a.sel ?: a.cur ?: return
        val now = System.currentTimeMillis()
        val arch = if (a.sel == null) a.arch else null
        val e = a.epg.forChannel(ch)
        val p = e?.indexAt(now) ?: -1
        osd.removeAllViews()
        // ◀ — список каналов (в записи стрелки перематывают, поэтому её нет)
        osd.addView(u.box {
            if (arch == null) {
                addView(u.icon(R.drawable.ic_chevron_left, 48, C.T2), u.fr(48, 48, Gravity.CENTER))
                contentDescription = "Список каналов"
                setOnClickListener { openGuide() }
            }
        }, u.lin(48, 140, r = 12))
        osd.addView(u.logo(ch, 200, 140, 20, C.OSD_TILE, 16).also { it.background = u.rect(C.OSD_TILE, 20) }, u.lin(200, 140, r = 36))
        osd.addView(u.col(Gravity.CENTER_HORIZONTAL) {
            minimumWidth = u.p(132)
            addView(u.icon(R.drawable.ic_chevron_up, 40, C.T2), u.lin(40, 40))
            addView(u.text(a.numText(ch.number), 64, C.T1, 500).apply { gravity = Gravity.CENTER_HORIZONTAL }, u.lin(MATCH, WRAP))
            addView(u.icon(R.drawable.ic_chevron_down, 40, C.T2), u.lin(40, 40))
        }, u.lin(WRAP, WRAP, r = 36))
        osd.addView(u.col {
            addView(u.row {
                addView(u.text(ch.group, 28, C.T2), u.lin(r = 16))
                if (arch != null) addView(archTagView(arch), u.lin(WRAP, 48))
                else addView(live().apply { background = u.rect(0x8C000000.toInt(), 8); u.pad(this, 14, 6, 16, 6) })
            })
            addView(u.text(ch.name, 72, C.T1, 700).apply { letterSpacing = -0.015f }, u.lin(MATCH, WRAP))
            addView(u.row {
                if (arch != null) {
                    addView(u.text(clock(arch.start) + " – " + clock(arch.stop), 30, C.T2), u.lin(r = 24))
                    addView(u.text(arch.title, 30, C.T1), u.lin(WRAP, WRAP, r = 24).apply { width = WRAP })
                    addView(u.bar(a.position().toFloat() / a.archLength().coerceAtLeast(1), C.OSD_TRACK, C.PAPER, 6), u.lin(260, 6))
                } else if (p >= 0) {
                    addView(u.text(clock(e!!.starts[p]) + " – " + clock(e.stops[p]), 30, C.T2), u.lin(r = 24))
                    addView(u.text(e.titles[p].ifEmpty { "Без названия" }, 30, C.T1).apply { maxWidth = u.p(640) }, u.lin(r = 24))
                    addView(u.bar((now - e.starts[p]).toFloat() / (e.stops[p] - e.starts[p]).coerceAtLeast(1), C.OSD_TRACK, C.ACCENT, 6), u.lin(260, 6))
                } else addView(u.text(if (a.epgLoading) "Телепрограмма загружается…" else "Для этого канала нет телепрограммы", 30, C.T2))
            }, u.lin(MATCH, WRAP))
        }, u.lin(0, WRAP, 1f))
        // ▶ — программа канала
        if (arch == null) osd.addView(u.box {
            addView(u.icon(R.drawable.ic_chevron_right, 48, C.T2), u.fr(48, 48, Gravity.CENTER))
            contentDescription = "Программа канала"
            setOnClickListener { a.cur?.let { showProgramme(it) } }
        }, u.lin(48, 140, l = 16)) // по центру, как ◀ у логотипа
        (osd.layoutParams as FrameLayout.LayoutParams).bottomMargin = u.p(if (ctl.visibility == View.VISIBLE) (if (a.arch != null) 240 else 200) else 54)
    }

    fun renderTopRight() {
        topRight.removeAllViews()
        val ctlOn = ctl.visibility == View.VISIBLE
        if (ctlOn) topRight.addView(a.hints("lr" to "Выбор", "ok" to "Нажать", "back" to "Скрыть", onVideo = true), u.fr(WRAP, MATCH))
        else topRight.addView(u.text(clock(System.currentTimeMillis()), 40, C.T1, 500), u.fr(WRAP, WRAP, Gravity.CENTER_VERTICAL))
        topRight.visibility = if (ctlOn || osdShown() && vol.visibility != View.VISIBLE) View.VISIBLE else View.GONE
    }

    fun renderArchTag() {
        archTag.removeAllViews()
        val p = a.arch ?: return
        archTag.addView(archTagView(p), u.lin(WRAP, 48))
        if (a.paused) archTag.addView(u.text("Пауза", 28, C.T1, 600).apply { background = u.rect(C.S3, 10); u.pad(this, 18, 0, 18, 0); gravity = Gravity.CENTER_VERTICAL }, u.lin(WRAP, 48, l = 12))
    }

    fun showZap() {
        zapChip.removeAllViews()
        zapChip.addView(u.icon(R.drawable.ic_chevron_up, 28, C.T2), u.lin(28, 28))
        zapChip.addView(u.icon(R.drawable.ic_chevron_down, 28, C.T2), u.lin(28, 28, r = 16))
        zapChip.addView(u.text("включится через 0,4 с после последнего нажатия", 28, C.T2))
        (zapChip.layoutParams as FrameLayout.LayoutParams).topMargin = u.p(if (a.arch != null) 118 else 54)
        zapChip.visibility = View.VISIBLE
    }

    fun hideZap() { zapChip.visibility = View.GONE }

    /** Загрузка, буфер и ошибки (SPEC 4.4). */
    fun renderState() {
        val name = a.state
        val ch = a.cur
        stateLayer.removeAllViews()
        if (name == null || ch == null) { stateLayer.visibility = View.GONE; return }
        stateLayer.visibility = View.VISIBLE
        stateLayer.setBackgroundColor(if (name == "buffer" || name == "noimage") 0 else 0xFF000000.toInt())
        fun chRowSmall() = u.row(Gravity.CENTER_VERTICAL) {
            addView(u.logo(ch, 96, 72, 12), u.lin(96, 72, r = 20))
            addView(u.text(ch.number.toString(), 28, C.T2), u.lin(r = 20))
            addView(u.text(ch.name, 28, C.T1))
        }
        if (name == "loading" || name == "buffer") {
            stateLayer.addView(u.col(Gravity.CENTER_HORIZONTAL) {
                addView(spinner(80, 6))
                addView(u.text("Подгружаем поток…", 28, C.T2), u.lin(t = 24))
            }, u.fr(WRAP, WRAP, Gravity.CENTER))
            if (name == "loading" && !osdShown()) stateLayer.addView(chRowSmall(), u.fr(WRAP, WRAP, Gravity.BOTTOM, l = 96, b = 54))
            return
        }
        val (icon, title, text) = when (name) {
            "error" -> Triple(R.drawable.ic_warning, "Канал не включился", "Нет связи с сервером провайдера. OK — повторить, ▲▼ — другой канал, ◀ — список.")
            "denied" -> Triple(R.drawable.ic_alert, "Канал недоступен", "Провайдер не отдаёт этот поток (ошибка ${a.stateCode}). Обычно это ограничение подписки.")
            else -> Triple(R.drawable.ic_alert, "Есть звук, нет изображения", "Канал передаёт только звук или изображение ещё не пришло.")
        }
        val buttons = when (name) { "error" -> listOf("Повторить", "Список каналов"); "denied" -> listOf("Другой канал"); else -> listOf("Повторить") }
        stateLayer.addView(u.col(Gravity.CENTER_HORIZONTAL) {
            u.pad(this, 48, 64, 48, 64)
            addView(chRowSmall(), u.lin(b = 32))
            addView(u.icon(icon, 56, if (name == "error") C.WARNING else C.ERROR), u.lin(56, 56))
            addView(u.text(title, 36, C.T1, 600), u.lin(t = 16))
            addView(u.text(text, 30, C.T2, lines = 3, lineHeight = 40).apply { gravity = Gravity.CENTER }, u.lin(MATCH, WRAP, t = 12))
            addView(u.row(Gravity.CENTER) {
                buttons.forEachIndexed { i, t -> addView(a.button(t, focused = i == 0), u.lin(WRAP, 64, l = if (i > 0) 16 else 0)) }
            }, u.lin(t = 32))
            contentDescription = title
        }, u.fr(960, WRAP, Gravity.CENTER))
    }

    // ================================================================ панель управления (SPEC 4.2)

    class Btn(val id: String, val icon: Int, val title: String, val off: Boolean = false, val soft: Boolean = false, val on: Boolean = false, val wide: Boolean = false, val end: Boolean = false, val isNew: String = "")
    var ctlIndex = 0
    private var btns: List<Btn> = emptyList()
    private val ctlTask = Runnable { closeCtl() }

    /** Панель кнопок есть только у записи: перемотка, пауза, возврат в эфир. В эфире ◀ — список, ▶ — программа. */
    private fun ctlButtons(): List<Btn> = listOf(
        Btn("rew", R.drawable.ic_rewind, "−30 с", off = a.position() < 1),
        Btn("pause", if (a.paused) R.drawable.ic_play else R.drawable.ic_pause, if (a.paused) "Продолжить" else "Пауза"),
        Btn("fwd", R.drawable.ic_forward, "+30 с", off = a.archLength() - a.position() < 30, end = true),
        Btn("live", R.drawable.ic_back, "Вернуться в эфир", wide = true, end = true),
        Btn("info", R.drawable.ic_info, "О передаче"),
        Btn("grid", R.drawable.ic_guide, "Телепрограмма — сетка каналов", off = a.epg.byId.isEmpty(), isNew = "grid"),
    )

    fun renderCtl() {
        btns = ctlButtons()
        ctlIndex = ctlIndex.coerceIn(0, btns.size - 1)
        while (btns[ctlIndex].off && ctlIndex > 0) ctlIndex--
        ctlCap.removeAllViews()
        val cur = btns[ctlIndex]
        if (a.zone == "ctl") {
            ctlCap.addView(u.text(cur.title, 28, C.T1))
            if (cur.isNew.isNotEmpty() && !Repo.prefs.seen(cur.isNew)) ctlCap.addView(u.newTag(), u.lin(l = 12))
        }
        ctlRow.removeAllViews()
        btns.forEachIndexed { i, b ->
            val sel = i == ctlIndex && a.zone == "ctl"
            val color = if (sel) C.TI else if (b.off || b.soft) C.TD else if (b.on) C.PAPER else C.T1
            ctlRow.addView(u.row(Gravity.CENTER) {
                background = if (sel) u.rect(C.PAPER, 12) else if (b.off || b.soft) null else u.rect(C.CTL_BTN, 12)
                if (b.wide) u.pad(this, 24, 0, 24, 0)
                addView(u.icon(b.icon, 36, color), u.lin(36, 36))
                if (b.wide) addView(u.text(b.title, 28, color), u.lin(l = 12))
                contentDescription = b.title
                setOnClickListener { ctlIndex = i; ctlPress(i) }
            }, u.lin(if (b.wide) WRAP else 64, 64, r = if (b.end && i < btns.size - 1) 16 else 12))
            if (b.end && i < btns.size - 1) ctlRow.addView(View(a).apply { setBackgroundColor(C.CTL_SEP) }, u.lin(2, 64, r = 28))
        }
        renderTimeline()
    }

    fun renderTimeline() {
        if (a.arch == null || ctl.visibility != View.VISIBLE) { timeline.visibility = View.GONE; return }
        val len = a.archLength().coerceAtLeast(1)
        val pos = a.position().coerceAtMost(len)
        timeline.removeAllViews()
        timeline.addView(u.text(duration(pos), 28, C.T2))
        val track = u.box {
            addView(u.bar(pos.toFloat() / len, C.TRACK, C.PAPER, 8), u.fr(MATCH, 8, Gravity.CENTER_VERTICAL))
        }
        timeline.addView(track, u.lin(0, 32, 1f, l = 24, r = 24))
        track.post {
            val knob = View(a).apply { background = u.rect(C.PAPER, 16) }
            track.addView(knob, FrameLayout.LayoutParams(u.p(32), u.p(32), Gravity.CENTER_VERTICAL).apply { leftMargin = ((track.width - u.p(32)) * pos / len).toInt() })
        }
        timeline.addView(u.text("−" + duration(len - pos), 28, C.T2))
        timeline.visibility = View.VISIBLE
    }

    fun armCtl() {
        h.removeCallbacks(ctlTask)
        if (!(a.arch != null && a.paused)) h.postDelayed(ctlTask, 4000)
    }

    fun openCtl(focus: String? = null) {
        if (a.cur == null || a.arch == null || !(a.zone == "play" || a.zone == "ctl")) return
        a.zone = "ctl"
        val b = ctlButtons()
        ctlIndex = b.indexOfFirst { it.id == (focus ?: "pause") }.coerceAtLeast(0)
        ctl.visibility = View.VISIBLE; shadeB.visibility = View.VISIBLE; shadeT.visibility = View.VISIBLE
        renderCtl()
        showInfo()
        armCtl()
    }

    fun closeCtl() {
        h.removeCallbacks(ctlTask)
        if (ctl.visibility != View.VISIBLE) return
        ctl.visibility = View.GONE; shadeT.visibility = View.GONE; timeline.visibility = View.GONE
        if (a.zone == "ctl") a.zone = "play"
        if (!osdShown()) shadeB.visibility = View.GONE
        else renderOsd()
        renderTopRight()
    }

    fun ctlPress(i: Int) {
        val b = btns.getOrNull(i) ?: return
        if (b.off) return
        armCtl()
        when (b.id) {
            "info" -> { closeCtl(); showInfo() }
            "grid" -> openGrid(a.cur)
            "rew" -> { a.seek(-30); renderCtl() }
            "fwd" -> { a.seek(30); renderCtl() }
            "pause" -> a.togglePause()
            "live" -> { closeCtl(); a.goLive() }
        }
    }

    fun keyCtl(key: Key): Boolean {
        armCtl()
        when (key) {
            Key.LEFT -> { var i = ctlIndex - 1; while (i >= 0) { if (!btns[i].off) { ctlIndex = i; break }; i-- }; renderCtl() }
            Key.RIGHT -> { var i = ctlIndex + 1; while (i < btns.size) { if (!btns[i].off) { ctlIndex = i; break }; i++ }; renderCtl() }
            Key.UP, Key.DOWN -> closeCtl()
            else -> return false
        }
        return true
    }

    // ================================================================ громкость

    private val volTask = Runnable { muteTag() }

    fun showVol(percent: Int) {
        Repo.prefs.markSeen("volume")
        vol.removeAllViews()
        vol.background = u.rect(C.SCRIM, 20)
        u.pad(vol, 32, 0, 32, 0)
        vol.minimumHeight = u.p(88)
        val m = a.muted
        vol.addView(u.icon(if (m) R.drawable.ic_mute else R.drawable.ic_volume, 40, C.T1), u.lin(40, 40, r = 20))
        vol.addView(u.text(if (m) "Без звука" else "Громкость", 30, if (m) C.T2 else C.T1, 500), u.lin(r = 20))
        vol.addView(u.bar(percent / 100f, C.TRACK, if (m) C.TD else C.PAPER, 8), u.lin(360, 8, r = 20))
        vol.addView(u.text(percent.toString(), 30, C.T1, 500).apply { gravity = Gravity.END; minWidth = u.p(56) })
        (vol.layoutParams as FrameLayout.LayoutParams).topMargin = u.p(if (ctl.visibility == View.VISIBLE) 118 else 54)
        vol.visibility = View.VISIBLE
        renderTopRight()
        h.removeCallbacks(volTask)
        h.postDelayed(volTask, 2000)
    }

    /** Значок «Без звука» остаётся, пока звук выключен. */
    private fun muteTag() {
        vol.removeAllViews()
        if (!a.muted) { vol.visibility = View.GONE; renderTopRight(); return }
        vol.minimumHeight = u.p(72)
        u.pad(vol, 24, 0, 24, 0)
        vol.addView(u.icon(R.drawable.ic_mute, 40, C.T2), u.lin(40, 40, r = 20))
        vol.addView(u.text("Без звука", 30, C.T2, 500))
        renderTopRight()
    }

    // ================================================================ ввод номера

    private var digits = ""
    private var numItems: List<Channel> = emptyList()
    private var numSel = 0
    private val numTask = Runnable { numCommit() }
    private var numBar: View? = null
    private var numStart = 0L
    private var numLength = 1L
    /** Полоска таймера убывает линейно: обновляем её сами, а не аниматором, — так она идёт по тем же часам, что и таймер. */
    private val numBarTask = object : Runnable {
        override fun run() {
            val left = 1f - (android.os.SystemClock.uptimeMillis() - numStart).toFloat() / numLength
            numBar?.scaleX = left.coerceIn(0f, 1f)
            if (left > 0 && numOpen()) h.postDelayed(this, 50)
        }
    }

    fun numOpen() = numpad.visibility == View.VISIBLE

    /** Каналы, чей номер начинается с набранных цифр: сам номер, потом 10–19, потом 100–199 и так далее. */
    private fun numMatches(d: String, max: Int): List<Channel> {
        val n = d.toIntOrNull() ?: return emptyList()
        val all = a.playlist.channels
        val out = ArrayList<Channel>()
        if (n in 1..all.size) out += all[n - 1]
        var mult = 10
        while (n.toLong() * mult <= all.size && out.size < max) {
            var k = n * mult
            while (k < (n + 1) * mult && k <= all.size && out.size < max) { out += all[k - 1]; k++ }
            mult *= 10
        }
        return out
    }

    fun typeDigit(d: Int) {
        val total = a.playlist.channels.size
        if (total == 0 || d < 0) return
        if (digits.length >= total.toString().length) digits = "" // номер набран целиком — начинаем новый
        if (digits.isEmpty() && d == 0) { if (numOpen()) numClose(); return } // номера начинаются с единицы
        digits += d
        numUpdate()
    }

    private fun numUpdate() {
        val n = digits.toInt()
        numItems = numMatches(digits, 4)
        numSel = 0
        if (!numOpen()) { hideInfo(); closeCtl() }
        renderNum()
        // Продолжения у номера нет — переключаем почти сразу; есть из чего выбрать — даём время прочитать
        numArm(if (numItems.isEmpty()) 2000 else if (n * 10 > a.playlist.channels.size) 1000 else 3000)
    }

    private fun renderNum() {
        numpad.removeAllViews()
        numpad.addView(u.row(Gravity.END or Gravity.CENTER_VERTICAL) {
            addView(u.text(digits, 112, C.T1, 600))
            addView(View(a).apply { setBackgroundColor(C.PAPER) }, u.lin(6, 92, l = 10))
        }, u.lin(MATCH, 112, l = 24, r = 24, b = 24))
        if (numItems.isEmpty()) numpad.addView(u.text("Канала с номером ${digits.toInt()} нет", 30, C.T2), u.lin(l = 24, r = 24))
        numItems.forEachIndexed { i, c -> numpad.addView(chRow(c, i == numSel, digits = digits.length, bar = false), u.lin(MATCH, 104, b = 2)) }
        val bar = View(a).apply { setBackgroundColor(C.T3); pivotX = 0f }
        numBar = bar
        numpad.addView(bar, u.lin(MATCH, 4, l = 24, t = 24, r = 24))
        setHints(numHints, "ud" to if (numItems.size > 1) "Выбрать" else "", "ok" to if (numItems.isNotEmpty()) "Включить" else "", "l" to "Стереть", "back" to "Отмена", onVideo = true)
        numpad.visibility = View.VISIBLE
        numHints.visibility = View.VISIBLE
    }

    /** Отсчёт до переключения: полоска убывает, по окончании включается выбранный канал. */
    private fun numArm(ms: Long) {
        h.removeCallbacks(numTask)
        h.postDelayed(numTask, ms)
        numStart = android.os.SystemClock.uptimeMillis(); numLength = ms
        h.removeCallbacks(numBarTask); h.post(numBarTask)
    }

    fun numClose() {
        h.removeCallbacks(numTask)
        digits = ""
        numpad.visibility = View.GONE
        numHints.visibility = View.GONE
    }

    fun numCommit() {
        val ch = numItems.getOrNull(numSel)
        numClose()
        if (ch != null) a.tuneTo(ch, a.playlist.channels, "all")
    }

    fun numKey(key: Key, event: android.view.KeyEvent): Boolean {
        when (key) {
            Key.DIGIT -> typeDigit(a.digit(event.keyCode))
            Key.UP, Key.DOWN -> if (numItems.size > 1) { numSel = (numSel + if (key == Key.DOWN) 1 else -1).coerceIn(0, numItems.size - 1); renderNum(); numArm(3000) }
            Key.LEFT, Key.DEL -> { digits = digits.dropLast(1); if (digits.isEmpty()) numClose() else numUpdate() }
            Key.RIGHT -> Unit
            else -> { numClose(); return false }
        }
        return true
    }

    // ================================================================ гид (SPEC 3.1, 8.1)

    private fun buildGuide() {
        guide = u.box { visibility = View.GONE; setBackgroundColor(C.SCRIM_GUIDE); isClickable = true }
        gTitle = u.row()
        gClock = u.row()
        guide.addView(u.row {
            addView(gTitle, u.lin(WRAP, 72, l = -20))
            addView(View(a), u.lin(0, 1, 1f))
            addView(gClock)
        }, u.fr(MATCH, 88, l = 96, t = 40, r = 96))
        gMenu = u.col()
        gChans = u.box { clipChildren = true }
        gDet = u.col()
        guide.addView(u.row(Gravity.TOP) {
            addView(gMenu, u.lin(450, MATCH, r = 56))
            addView(gChans, u.lin(520, MATCH))
            addView(gDet, u.lin(0, MATCH, 1f, l = 38))
        }, u.fr(MATCH, 798, l = 96, t = 150, r = 96))
        gHints = u.box()
        guide.addView(gHints, u.fr(MATCH, 54, Gravity.BOTTOM, l = 96, r = 96, b = 54))
        a.root.addView(guide, u.fr(MATCH, MATCH))
    }

    fun guideShown() = guide.visibility == View.VISIBLE

    fun openGuide(zone: String? = null) {
        if (a.playlist.channels.isEmpty() && Repo.active == null) return
        closeLayers()
        val g = Repo.prefs.lastGroup ?: "all"
        val gl = a.listOf(g)
        a.selectMenu(if (gl != null && gl.any { it.key == a.cur?.key }) g else "all")
        a.zone = zone ?: if (a.list.isNotEmpty()) "channels" else "groups"
        progKey = ""
        guide.visibility = View.VISIBLE
        renderGuide()
    }

    fun closeGuide() {
        a.zone = "play"
        guide.visibility = View.GONE
    }

    /** Программа канала в гиде: фокус на текущей передаче (жёлтая, GUIDE, ▶ в плеере, «Программа канала»). */
    fun showProgramme(ch: Channel, at: Long = 0): Boolean {
        if (!guideShown()) openGuide("channels")
        var i = a.list.indexOfFirst { it.key == ch.key }
        if (i < 0) { a.selectMenu("all"); i = a.list.indexOfFirst { it.key == ch.key } }
        a.ci = i.coerceAtLeast(0)
        a.zone = "channels"
        progKey = ""
        if (!a.loadProg(ch)) { renderGuide(); a.toast(if (a.epgLoading) "Телепрограмма ещё загружается…" else "Для этого канала телепрограммы нет"); return false }
        progKey = progKeyFor(ch)
        if (at > 0) { a.loadProg(ch, dayStart(at)); a.prog.indexOfFirst { it.start == at }.takeIf { it >= 0 }?.let { a.pi = it } }
        a.zone = "epg"
        renderGuide()
        return true
    }

    private fun progKeyFor(ch: Channel) = ch.key + "|" + a.epg.updated + "|" + (if (a.arch != null && a.cur?.key == ch.key) a.arch?.start else 0) + "|" + System.currentTimeMillis() / MIN

    /** Подробности следуют за выбранным каналом — программу видно, ещё не заходя в неё. */
    private fun syncEpg() {
        if (a.zone == "epg" || a.zone == "date") return
        val ch = a.list.getOrNull(a.ci) ?: a.cur
        val key = if (ch == null) "" else progKeyFor(ch)
        if (key == progKey) return
        progKey = key
        a.loadProg(ch)
    }

    fun renderGuide() {
        if (!guideShown()) return
        syncEpg()
        renderHead()
        renderMenu()
        renderTiles()
        renderDetails()
        renderHints()
    }

    private var guideQueued = false
    /** При удержании ▲▼ перерисовываем не чаще одного кадра. */
    private fun renderGuideSoon() {
        if (guideQueued) return
        guideQueued = true
        guide.postOnAnimation { guideQueued = false; renderGuide() }
    }

    fun renderHead() {
        val pl = Repo.active
        val many = Repo.playlists.value.size > 1
        val sel = a.zone == "head"
        gTitle.removeAllViews()
        gTitle.background = if (sel) u.rect(C.PAPER, 16) else null
        u.pad(gTitle, 20, 0, 20, 0)
        val c = if (sel) C.TI else C.T1
        gTitle.addView(u.icon(R.drawable.ic_list, 36, c), u.lin(36, 36, r = 16))
        gTitle.addView(u.text(pl?.name ?: "IPTV", 44, c).apply { maxWidth = u.p(900) })
        if (many) gTitle.addView(u.icon(R.drawable.ic_chevron_down, 36, if (sel) C.TI else C.T2), u.lin(36, 36, l = 16))
        newTagIf("playlists")?.let { gTitle.addView(it, u.lin(l = 16)) }
        gTitle.contentDescription = pl?.name
        val now = System.currentTimeMillis()
        gClock.removeAllViews()
        gClock.addView(u.text(clock(now), 48, C.T1, 600), u.lin(r = 16))
        gClock.addView(u.col {
            addView(u.text(dateText(now) + " " + yearText(now), 24, C.T2))
            addView(u.text(weekday(now), 24, C.T2))
        })
    }

    private fun menuRow(i: Int): LinearLayout {
        val m = a.menu[i]
        val sel = i == a.gi && a.zone == "groups"
        val act = m.id == a.menuId
        val n = m.list.size
        val here = a.cur?.group?.let { m.id == "g:$it" } == true
        val color = if (sel) C.TI else if (act) C.T1 else C.T2
        return u.row {
            background = if (sel) u.rect(C.PAPER, 16) else if (act) u.rect(C.S3, 16) else null
            u.pad(this, 24, 0, 24, 0)
            addView(u.icon(m.icon, 32, color), u.lin(32, 32, r = 20))
            addView(u.text(m.title, 30, color, if (sel || act) 500 else 400), u.lin(WRAP, WRAP).apply { width = WRAP })
            if (m.isNew.isNotEmpty()) newTagIf(m.isNew)?.let { addView(it, u.lin(l = 12)) }
            addView(View(a), u.lin(0, 1, 1f))
            if (here) addView(View(a).apply { background = u.rect(if (sel) C.ACCENT_ON_PAPER else C.ACCENT, 6) }, u.lin(12, 12, l = 16))
            if (!m.action && !(n == 0 && (m.id == "fav" || m.id == "recent"))) addView(u.text(n.toString(), 28, if (sel) C.TI2 else if (act) C.T2 else C.T3), u.lin(l = 16))
            contentDescription = m.title
        }
    }

    fun renderMenu() {
        gMenu.removeAllViews()
        val last = a.menu.size - 1
        if (last < 0) return
        val top = 4
        for (i in 0 until minOf(top, last)) gMenu.addView(menuRow(i), u.lin(MATCH, 70))
        val n = last - top
        if (n > 0) {
            val rows = 5
            val selG = if (a.gi in top until last) a.gi - top else (a.menuIndex(a.menuId) - top).coerceAtLeast(0)
            val start = (selG - rows + 1).coerceAtLeast(0).let { s -> if (selG < s) selG else s }.coerceIn(0, (n - rows).coerceAtLeast(0))
            gMenu.addView(u.text("Группы", 24, C.T3).apply { gravity = Gravity.BOTTOM; u.pad(this, 24, 0, 24, 8) }, u.lin(MATCH, 52))
            for (i in start until minOf(n, start + rows)) gMenu.addView(menuRow(top + i), u.lin(MATCH, 70))
        }
        gMenu.addView(View(a).apply { setBackgroundColor(C.LINE) }, u.lin(MATCH, 2, l = 24, t = 12, r = 24, b = 12))
        gMenu.addView(menuRow(last), u.lin(MATCH, 70))
    }

    private fun emptyChannels(): View = when (a.menuId) {
        "fav" -> state(R.drawable.ic_star, "В избранном пока пусто", "Удержите OK на канале или нажмите зелёную кнопку, чтобы добавить его сюда.")
        "recent" -> state(R.drawable.ic_history, "Здесь появятся каналы, которые вы смотрели", "Последние 7 дней, до 20 каналов.")
        else -> state(R.drawable.ic_alert, "В плейлисте нет каналов", "Ссылка открылась, но в файле нет ни одного канала. Проверьте ссылку у провайдера.")
    }

    /** Плитки каналов: фокус на второй позиции, список двигается под ним. В разметке — не больше пяти плиток. */
    fun renderTiles() {
        gChans.removeAllViews()
        val n = a.list.size
        if (n == 0) { gChans.addView(emptyChannels(), u.fr(MATCH, WRAP, l = 40, t = 20, r = 20)); return }
        val focus = a.zone == "channels"
        val ci = a.ci
        val from = (ci - 1).coerceAtLeast(0)
        val to = minOf(n - 1, ci + if (ci == 0) 3 else 2)
        for (i in from..to) {
            val c = a.list[i]
            val playing = a.cur?.key == c.key
            val sel = i == ci && focus
            val y = if (ci == 0) 18 + i * 264 else 152 + (i - ci) * 264
            val row = u.box {
                val no = c.number.toString()
                addView(u.text(no, if (no.length >= 5) 32 else if (no.length == 4) 40 else 48, if (sel) C.T1 else C.T3, 600).apply {
                    gravity = Gravity.END or Gravity.CENTER_VERTICAL; u.pad(this, 0, 0, if (no.length >= 4) 20 else 24, 0) }, u.fr(136, MATCH))
                val ring = u.box {
                    background = when {
                        sel -> u.rect(0, 34, C.PAPER, 6)
                        playing -> android.graphics.drawable.InsetDrawable(u.rect(0, 32, C.ACCENT_RING, 4), u.p(2))
                        else -> null
                    }
                    addView(tile(c, playing), u.fr(340, 236, l = 14, t = 14))
                }
                addView(ring, u.fr(368, 264, l = 122))
                contentDescription = "${c.number}, ${c.name}"
                if (sel) isSelected = true
            }
            gChans.addView(row, u.fr(476, 264, t = y - 14))
        }
        // Затухание сверху и снизу колонки
        gChans.addView(View(a).apply { background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(0xF50D0D0E.toInt(), 0)) }, u.fr(MATCH, if (focus) 60 else 120))
        gChans.addView(View(a).apply { background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(0, 0xF50D0D0E.toInt())) }, u.fr(MATCH, 120, Gravity.BOTTOM))
    }

    private fun tile(c: Channel, playing: Boolean): FrameLayout = u.box {
        background = u.rect(C.S3H, 24)
        val letters = u.text(initials(c.name), 76, C.T1, 600).apply { letterSpacing = -0.02f; gravity = Gravity.CENTER }
        addView(u.col(Gravity.CENTER_HORIZONTAL) {
            addView(u.box {
                addView(letters, u.fr(MATCH, MATCH))
                if (!c.logo.isNullOrEmpty()) addView(ImageView(a).apply {
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    load(c.logo) { crossfade(false); listener(onSuccess = { _, _ -> letters.visibility = View.INVISIBLE }) }
                }, u.fr(MATCH, MATCH))
            }, u.lin(240, 110))
            addView(u.text(c.name, 26, C.T2).apply { maxWidth = u.p(290) }, u.lin(t = 10))
        }, u.fr(MATCH, WRAP, Gravity.CENTER))
        addView(u.row {
            if (playing) addView(u.icon(R.drawable.ic_playing, 30, C.ACCENT), u.lin(30, 30, l = 12))
            if (a.isFav(c)) addView(u.icon(R.drawable.ic_star_filled, 30, C.T1), u.lin(30, 30, l = 12))
        }, u.fr(WRAP, WRAP, Gravity.TOP or Gravity.END, t = 16, r = 18))
    }

    private fun progRow(i: Int, focus: Boolean): LinearLayout {
        val p = a.prog[i]
        val now = System.currentTimeMillis()
        val isNow = p.start <= now && p.stop > now
        val past = p.stop <= now
        val playing = a.arch != null && a.cur?.key == a.epgCh?.key && a.arch?.start == p.start
        val sel = i == a.pi && focus
        return u.row(Gravity.TOP) {
            background = if (sel) u.rect(C.PAPER, 14) else null
            u.pad(this, 18, 12, 18, 12)
            addView(u.text(clock(p.start), 30, if (sel) C.TI2 else C.T2, lineHeight = 40), u.lin(116, WRAP))
            val icon = if (isNow || playing) R.drawable.ic_play_filled else if (past && p.arch) R.drawable.ic_history else 0
            addView(u.box { if (icon != 0) addView(u.icon(icon, 28, if (isNow || playing) (if (sel) C.ACCENT_ON_PAPER else C.ACCENT) else if (sel) C.TI2 else C.T2), u.fr(28, 28, t = 6)) }, u.lin(48, 40))
            addView(u.col {
                addView(u.text(p.title, 30, if (sel) C.TI else if (past) C.T2 else C.T1, lineHeight = 40))
                if (isNow) addView(u.bar((now - p.start).toFloat() / (p.stop - p.start).coerceAtLeast(1), if (sel) C.TRACK_ON_PAPER else C.TRACK,
                    if (sel) C.ACCENT_ON_PAPER else C.ACCENT, 6), u.lin(MATCH, 6, t = 14))
                if (isNow && p.desc.isNotEmpty()) addView(u.text(p.desc, 26, if (sel) C.TI2 else C.T2, lines = 4, lineHeight = 36), u.lin(MATCH, WRAP, t = 14))
            }, u.lin(0, WRAP, 1f))
            contentDescription = clock(p.start) + ", " + p.title
        }
    }

    fun renderDetails() {
        gDet.removeAllViews()
        val ch = a.epgCh ?: return
        val now = System.currentTimeMillis()
        val e = a.epg.forChannel(ch)
        val p = e?.indexAt(now) ?: -1
        val inEpg = a.zone == "epg"
        gDet.addView(u.row {
            if (ch.hasArchive) addView(u.text("Архив · ${ch.catchupDays} дн", 24, C.T1, 500).apply { background = u.rect(C.S3H, 8); gravity = Gravity.CENTER_VERTICAL; u.pad(this, 14, 0, 14, 0) }, u.lin(WRAP, 40, r = 16))
            addView(u.text(if (p >= 0) e!!.titles[p] else ch.group, 30, C.T2))
        }, u.lin(MATCH, WRAP, l = 18, t = 10))
        gDet.addView(u.text(ch.name, 76, C.T1, 700).apply { letterSpacing = -0.015f }, u.lin(MATCH, WRAP, l = 18, t = 6))
        if (a.prog.isEmpty()) {
            if (a.epgLoading) gDet.addView(u.row {
                addView(spinner(32, 4), u.lin(32, 32, r = 16))
                addView(u.text(a.epgStatus.ifEmpty { "Загружаю программу…" }, 28, C.T2))
            }, u.lin(l = 18, t = 48))
            else if (a.epg.byId.isNotEmpty() || Repo.epgSourceUrls().isNotEmpty()) gDet.addView(state(R.drawable.ic_guide, "Для этого канала телепрограммы нет", "Провайдер не передаёт расписание. Канал можно смотреть."), u.lin(MATCH, WRAP, l = 18, t = 8))
            else gDet.addView(state(R.drawable.ic_guide, "Телепрограммы нет", "В плейлисте нет ссылки на телепрограмму. Её можно указать в настройках плейлиста."), u.lin(MATCH, WRAP, l = 18, t = 8))
            return
        }
        val di = a.days.indexOf(a.day)
        val dsel = a.zone == "date"
        gDet.addView(u.row {
            background = if (dsel) u.rect(C.PAPER, 12) else null
            u.pad(this, 18, 0, 18, 0)
            val c = if (dsel) C.TI else C.T2
            if (dsel && di > 0) addView(u.icon(R.drawable.ic_chevron_left, 28, c), u.lin(28, 28, r = 14))
            addView(u.text(dayTitle(a.day, now), 28, c))
            if (dsel && di < a.days.size - 1) addView(u.icon(R.drawable.ic_chevron_right, 28, c), u.lin(28, 28, l = 14))
            contentDescription = dayTitle(a.day, now)
        }, u.lin(WRAP, 56, l = if (dsel) 148 else 166, t = 34, b = 10))
        val cur = a.prog.getOrNull(a.pi)
        if (cur != null && cur.desc.isNotEmpty() && !Repo.prefs.seen("desc")) {
            gDet.addView(u.newTag("описание — новое"), u.lin(l = 6, b = 4))
            h.postDelayed({ Repo.prefs.markSeen("desc") }, 4000)
        }
        val n = a.prog.size
        val visible = 5
        // Видно одну прошедшую, текущую и следующие; в программе фокус держится второй строкой
        val start = (a.pi - 1).coerceIn(0, (n - visible).coerceAtLeast(0))
        for (i in start until minOf(n, start + visible)) gDet.addView(progRow(i, inEpg), u.lin(MATCH, WRAP, b = 4))
    }

    fun renderHints() {
        val ch = a.list.getOrNull(a.ci)
        when {
            a.popOpen -> setHints(gHints, "ud" to "Выбор", "ok" to "Выбрать", "back" to "Закрыть")
            a.zone == "channels" -> setHints(gHints, "ok" to if (ch != null) "Смотреть" else "", "hold" to if (ch != null) "Действия" else "",
                "r" to if (ch != null && a.prog.isNotEmpty()) "Программа" else "", "l" to "Меню", "0–9" to "Номер", "back" to "Закрыть")
            a.zone == "groups" -> setHints(gHints, "ud" to "Раздел", "ok" to "Открыть", "u" to if (Repo.playlists.value.size > 1) "сверху Плейлист" else "", "back" to "Закрыть")
            a.zone == "epg" -> setHints(gHints, "ud" to "Передачи", "ok" to "Смотреть", "u" to "сверху День", "l" to "К каналам", "back" to "Закрыть")
            a.zone == "date" -> setHints(gHints, "lr" to "День", "d" to "К передачам", "back" to "К передачам")
            a.zone == "head" -> setHints(gHints, "ok" to "Сменить плейлист", "d" to "К меню", "back" to "К меню")
        }
    }

    fun openPlaylistMenu() {
        val pls = Repo.playlists.value
        val items = pls.map { p ->
            val bad = p.failed && p.id != Repo.activeId
            PopItem(p.name, if (p.id == Repo.activeId) R.drawable.ic_check else 0, sub = if (bad) "не загрузился" else if (p.count > 0) chCount(p.count) else "", off = bad) {
                if (p.id != Repo.activeId) a.activatePlaylist(p.id) { ok -> if (ok && guideShown()) openGuide() }
            }
        } + PopItem(sep = true) + PopItem("Управлять плейлистами", R.drawable.ic_settings) { a.openSettings("playlists") }
        h.postDelayed({ Repo.prefs.markSeen("playlists") }, 4000)
        a.openPop("", items, 96, 136, pls.indexOfFirst { it.id == Repo.activeId }.coerceAtLeast(0))
        renderHints()
    }

    /** Меню действий с каналом (G3). from — откуда открыто: guide, results, grid. */
    fun openChannelMenu(ch: Channel, from: String, x: Int, y: Int) {
        val fav = a.isFav(ch)
        val items = arrayListOf(
            PopItem("Смотреть", R.drawable.ic_play, "OK") { a.tuneTo(ch, if (from == "guide") a.list else null, if (from == "guide") a.menuId else "all") },
            PopItem(if (fav) "Убрать из избранного" else "Добавить в избранное", if (fav) R.drawable.ic_star_filled else R.drawable.ic_star, "зелёная") { a.toggleFav(ch) })
        if (a.epg.forChannel(ch) != null) items += PopItem("Программа канала", R.drawable.ic_guide, "▶") { showProgramme(ch) }
        if (a.epg.byId.isNotEmpty()) items += PopItem("Найти в сетке телепрограммы", R.drawable.ic_grid) { openGrid(ch) }
        a.openPop(ch.name + " · " + a.numText(ch.number), items, x, y, 0)
        renderHints()
    }

    private fun shiftDay(dir: Int) {
        val i = a.days.indexOf(a.day) + dir
        if (i < 0 || i >= a.days.size) return
        a.loadProg(a.epgCh, a.days[i])
        renderGuide()
    }

    fun keyGuide(key: Key) {
        when (a.zone) {
            "head" -> when (key) { Key.DOWN -> { a.zone = "groups"; a.gi = 0; renderGuide() }; Key.OK -> openPlaylistMenu(); else -> Unit }
            "groups" -> keyGroups(key)
            "channels" -> keyChannels(key)
            "epg" -> keyEpg(key)
            "date" -> when (key) { Key.LEFT -> shiftDay(-1); Key.RIGHT -> shiftDay(1); Key.DOWN -> { a.zone = "epg"; a.pi = 0; renderGuide() }; else -> Unit }
        }
    }

    private fun keyGroups(key: Key) {
        val last = a.menu.size - 1
        when (key) {
            Key.UP -> if (a.gi > 0) a.gi-- else if (Repo.playlists.value.size > 1) { a.zone = "head"; renderGuide(); return } else return
            Key.DOWN -> if (a.gi < last) a.gi++ else return
            Key.CH_UP -> a.gi = (a.gi - 5).coerceAtLeast(0)
            Key.CH_DOWN -> a.gi = (a.gi + 5).coerceAtMost(last)
            Key.OK, Key.RIGHT -> {
                val m = a.menu[a.gi]
                when (m.id) {
                    "settings" -> { if (key == Key.OK) a.openSettings() }
                    "search" -> openSearch()
                    else -> {
                        a.selectMenu(m.id)
                        if (a.list.isNotEmpty()) a.zone = "channels" // пустой раздел — фокус остаётся в меню
                        renderGuide()
                    }
                }
                return
            }
            else -> return
        }
        // При движении по разделам сразу показываем их каналы
        val m = a.menu[a.gi]
        if (!m.action) {
            a.menuId = m.id; a.list = a.listOf(m.id) ?: emptyList()
            a.ci = a.list.indexOfFirst { it.key == a.cur?.key }.coerceAtLeast(0)
            if (m.id == "recent") Repo.prefs.markSeen("recent")
        }
        renderGuideSoon()
    }

    private fun keyChannels(key: Key) {
        val n = a.list.size
        when (key) {
            Key.UP -> if (a.ci > 0) a.ci-- else return
            Key.DOWN -> if (a.ci < n - 1) a.ci++ else return
            Key.CH_UP -> a.ci = (a.ci - 7).coerceAtLeast(0)
            Key.CH_DOWN -> a.ci = (a.ci + 7).coerceAtMost(n - 1)
            Key.LEFT -> { a.zone = "groups"; a.gi = a.menuIndex(a.menuId); renderGuide(); return }
            Key.RIGHT -> { if (a.prog.isNotEmpty() && a.epgCh?.key == a.list.getOrNull(a.ci)?.key) { a.zone = "epg"; renderGuide() }; return }
            else -> return
        }
        renderGuideSoon()
    }

    private fun keyEpg(key: Key) {
        val n = a.prog.size
        when (key) {
            Key.UP -> if (a.pi > 0) a.pi-- else { a.zone = "date"; renderGuide(); return }
            Key.DOWN -> if (a.pi < n - 1) a.pi++ else return
            Key.CH_UP -> a.pi = (a.pi - 5).coerceAtLeast(0)
            Key.CH_DOWN -> a.pi = (a.pi + 5).coerceAtMost(n - 1)
            Key.LEFT -> { a.zone = "channels"; progKey = ""; renderGuide(); return }
            Key.OK -> { val ch = a.epgCh; val p = a.prog.getOrNull(a.pi); if (ch != null && p != null) a.chooseProgramme(p, ch); return }
            else -> return
        }
        renderGuideSoon()
    }

    // ================================================================ сетка «каналы × время» (SPEC 3.4)

    var gridHead = -1
    var gridList: List<Channel> = emptyList()
    var gridRow = 0
    private var gridGroup = "all"
    private var gridBack = "play"
    private var t0 = 0L
    private var ft = 0L
    private val span = 150L * MIN
    private val gridW = 1348

    private fun half(t: Long) = t - Math.floorMod(t, 30 * MIN)
    private fun gridDays(now: Long): List<Long> { val d = dayStart(now); return listOf(d - DAY, d, d + DAY, d + 2 * DAY) }

    fun openGrid(ch: Channel?) {
        if (a.playlist.channels.isEmpty()) return
        if (a.epg.byId.isEmpty()) { a.toast(if (a.epgLoading) "Телепрограмма ещё загружается…" else "Телепрограмма не загружена"); return }
        val back = if (a.zone == "ctl") "play" else a.zone
        var g = Repo.prefs.lastGroup ?: "all"
        var l = a.listOf(g)
        val target = ch ?: a.cur
        if (l == null || l.none { it.key == target?.key }) { g = "all"; l = a.playlist.channels }
        closeCtl(); hideInfo()
        guide.visibility = View.GONE // гид под сеткой не просвечивает; вернётся по «Назад»
        gridBack = if (back == "grid") gridBack else back
        gridList = l; gridGroup = g
        gridRow = l.indexOfFirst { it.key == target?.key }.coerceAtLeast(0)
        val now = System.currentTimeMillis()
        t0 = half(now) - 30 * MIN; ft = now; gridHead = -1
        a.zone = "grid"
        h.postDelayed({ Repo.prefs.markSeen("grid") }, 4000)
        grid.visibility = View.VISIBLE
        renderGrid()
    }

    fun closeGrid() {
        grid.visibility = View.GONE
        a.zone = gridBack
        if (a.inGuide()) guide.visibility = View.VISIBLE
        if (a.zone == "play") showInfo()
        renderZone()
    }

    /** Номер передачи канала в «колонке времени»; -1 — программы нет. */
    private fun gridProg(ch: Channel): Int {
        val e = a.epg.forChannel(ch) ?: return -1
        if (e.size == 0) return -1
        var lo = 0; var hi = e.size - 1; var found = 0
        while (lo <= hi) { val mid = (lo + hi) ushr 1; if (e.starts[mid] <= ft) { found = mid; lo = mid + 1 } else hi = mid - 1 }
        if (e.stops[found] <= t0 && found + 1 < e.size) found++
        return if (e.stops[found] > t0 && e.starts[found] < t0 + span) found else -1
    }

    private fun gridStatus(s: Long, e: Long, arch: Boolean, now: Long): String = when {
        s <= now && e > now -> "идёт сейчас, до " + clock(e)
        s > now -> ((s - now) / MIN).let { if (it <= 180) "начнётся через $it мин" else dayTitle(s, now).lowercase() }
        dayDiff(s, now) == 0 -> if (arch) "запись есть" else "сегодня"
        else -> (if (dayDiff(s, now) == -1) "вчера" else dateText(s)) + if (arch) " · запись есть" else ""
    }

    fun renderGrid() {
        grid.removeAllViews()
        val now = System.currentTimeMillis()
        val t1 = t0 + span
        val scale = gridW.toFloat() / span
        val days = gridDays(now)
        val curDay = dayStart(ft)
        grid.addView(u.row {
            addView(u.text("Телепрограмма", 44, C.T1, 600))
            newTagIf("grid")?.let { addView(it, u.lin(l = 12)) }
            addView(View(a), u.lin(40, 1))
            days.forEachIndexed { k, d ->
                val sel = gridHead == k
                addView(u.text(dayTab(d, now), 28, if (sel) C.TI else if (d == curDay) C.T1 else C.T2, if (d == curDay) 500 else 400).apply {
                    background = if (sel) u.rect(C.PAPER, 12) else if (d == curDay) u.rect(C.S2, 12) else null
                    gravity = Gravity.CENTER_VERTICAL; u.pad(this, 28, 0, 28, 0)
                }, u.lin(WRAP, 64, r = 8))
            }
            val gsel = gridHead == days.size
            addView(u.row {
                background = if (gsel) u.rect(C.PAPER, 12) else null
                u.pad(this, 28, 0, 28, 0)
                addView(u.text("Группа", 28, if (gsel) C.TI else C.T2), u.lin(r = 12))
                addView(u.text(a.menuTitle(gridGroup), 28, if (gsel) C.TI else C.T1, 500), u.lin(r = 12))
                addView(u.icon(R.drawable.ic_chevron_down, 28, if (gsel) C.TI else C.T2), u.lin(28, 28))
            }, u.lin(WRAP, 64, l = 24))
            addView(View(a), u.lin(0, 1, 1f))
            addView(u.text(clock(now), 40, C.T1, 500))
        }, u.fr(MATCH, 64, l = 96, t = 54, r = 96))

        val fch = gridList.getOrNull(gridRow)
        val fe = fch?.let { a.epg.forChannel(it) }
        val fp = fch?.let { gridProg(it) } ?: -1
        val line1 = if (fch == null) "" else "${fch.number} · ${fch.name}" + if (fp >= 0) " · ${clock(fe!!.starts[fp])}–${clock(fe.stops[fp])} · " +
            gridStatus(fe.starts[fp], fe.stops[fp], fch.hasArchive && fe.starts[fp] >= now - fch.catchupDays * DAY, now) else ""
        grid.addView(u.col {
            addView(u.text(line1, 28, C.T2))
            addView(u.text(if (fp >= 0) fe!!.titles[fp].ifEmpty { "Без названия" } else if (fch != null) "Нет телепрограммы" else "", 36, C.T1, 600), u.lin(t = 4))
        }, u.fr(MATCH, WRAP, l = 96, t = 146, r = 96))

        val body = u.box()
        grid.addView(body, u.fr(MATCH, MATCH, l = 96, t = 262, r = 96, b = 132))
        var t = t0
        while (t < t1) { body.addView(u.text(clock(t), 26, C.T3, 500), u.fr(WRAP, WRAP, l = 380 + ((t - t0) * scale).toInt(), t = 12)); t += 30 * MIN }
        val n = gridList.size
        val rows = 6
        val top = (gridRow - rows + 1).coerceAtLeast(0).let { s -> minOf(s, gridRow) }.coerceIn(0, (n - rows).coerceAtLeast(0))
        var cells = 0
        for ((r, i) in (top until minOf(n, top + rows)).withIndex()) {
            val c = gridList[i]
            val y = 56 + r * 106
            body.addView(View(a).apply { setBackgroundColor(C.LINE) }, u.fr(MATCH, 2, t = y))
            body.addView(u.row {
                u.pad(this, 12, 0, 20, 0)
                addView(u.text(c.number.toString(), 28, C.T3).apply { gravity = Gravity.END }, u.lin(56, WRAP, r = 20))
                addView(u.logo(c, 72, 54, 8), u.lin(72, 54, r = 20))
                addView(u.text(c.name, 30, if (a.cur?.key == c.key) C.ACCENT else C.T1, 500), u.lin(0, WRAP, 1f))
            }, u.fr(380, 104, t = y + 2))
            val e = a.epg.forChannel(c)
            val sel = if (i == gridRow && gridHead < 0) gridProg(c) else -1
            var drawn = 0
            if (e != null) {
                var k = e.firstEndingAfter(t0)
                while (k < e.size && e.starts[k] < t1 && drawn < 7 && cells < 40) {
                    val s = maxOf(e.starts[k], t0)
                    val en = minOf(e.stops[k], t1)
                    val w = ((en - s) * scale).toInt() - 8
                    if (w >= 4) {
                        val past = e.stops[k] <= now
                        val isNow = e.starts[k] <= now && e.stops[k] > now
                        val arch = past && c.hasArchive && e.starts[k] >= now - c.catchupDays * DAY
                        body.addView(cell(k == sel, past, isNow, arch, e.starts[k] < t0, w, e.titles[k], e.starts[k], e.stops[k], if (isNow) (now - s).toFloat() / (en - s) else 0f),
                            u.fr(w, 91, l = 380 + ((s - t0) * scale).toInt(), t = y + 2 + 6))
                        drawn++; cells++
                    }
                    k++
                }
            }
            if (drawn == 0) {
                cells++
                body.addView(u.text("Нет телепрограммы — канал можно смотреть, расписания нет", 28, if (i == gridRow && gridHead < 0) C.TI else C.T3).apply {
                    background = if (i == gridRow && gridHead < 0) u.rect(C.PAPER, 10) else u.rect(0, 10, C.S1, 2)
                    tag = "cell"
                    u.pad(this, 20, 14, 20, 0)
                }, u.fr(gridW - 8, 91, l = 380, t = y + 8))
            }
        }
        if (now in t0 until t1) {
            val x = 380 + ((now - t0) * scale).toInt()
            body.addView(View(a).apply { setBackgroundColor(C.ACCENT) }, u.fr(3, MATCH, l = x, t = 44))
            body.addView(View(a).apply { background = u.rect(C.ACCENT, 8) }, u.fr(15, 15, l = x - 6, t = 38))
        }
        grid.addView(a.hints("ud" to "Каналы", "lr" to "Время", "ok" to "Включить канал", "hold" to "Действия", "CH±" to "Страница", "back" to "Закрыть"), u.fr(WRAP, 54, Gravity.BOTTOM, l = 96, b = 54))
    }

    private fun cell(sel: Boolean, past: Boolean, isNow: Boolean, arch: Boolean, cut: Boolean, w: Int, title: String, s: Long, e: Long, part: Float): FrameLayout = u.box {
        background = when { sel -> u.rect(C.PAPER, 10); past -> u.rect(0, 10, C.S1, 2); isNow -> u.rect(C.S2, 10); else -> u.rect(C.S1, 10) }
        val main = if (sel) C.TI else if (past) C.T2 else C.T1
        val second = if (sel) C.TI2 else C.T3
        val col = u.col { u.pad(this, 20, 14, 20, 0) }
        fun withIcon(text: String, size: Int, color: Int) = u.row {
            if (arch) addView(u.icon(R.drawable.ic_history, 24, if (sel) C.TI2 else C.T2), u.lin(24, 24, r = 8))
            if (text.isNotEmpty()) addView(u.text(text, size, color))
        }
        when {
            w >= 230 -> { col.addView(withIcon((if (cut) "← " else "") + title.ifEmpty { "Без названия" }, 28, main)); col.addView(u.text(clock(s) + "–" + clock(e), 24, second)) }
            w >= 160 -> col.addView(withIcon("до " + clock(e), 24, second))
            arch && w >= 44 -> col.addView(withIcon("", 24, second))
        }
        addView(col, u.fr(MATCH, MATCH))
        if (isNow) addView(View(a).apply { setBackgroundColor(if (sel) C.ACCENT_ON_PAPER else C.ACCENT) }, u.fr((w * part).toInt().coerceAtLeast(1), 4, Gravity.BOTTOM))
        contentDescription = title
        tag = "cell"
    }

    private fun gridMove(dir: Int) {
        val ch = gridList.getOrNull(gridRow) ?: return
        val e = a.epg.forChannel(ch)
        val p = gridProg(ch)
        val now = System.currentTimeMillis()
        val minT = dayStart(now) - 14 * DAY
        val maxT = dayStart(now) + 3 * DAY
        if (e == null || p < 0) {
            val nt = t0 + dir * 30 * MIN
            if (nt < minT || nt > maxT) return
            t0 = nt; ft += dir * 30 * MIN
            return
        }
        val q = p + dir
        if (q < 0 || q >= e.size) return
        if (dir > 0) while (e.starts[q] >= t0 + span - 30 * MIN) t0 += 60 * MIN
        else if (e.starts[q] < t0) t0 = half(maxOf(e.starts[q], e.stops[q] - 90 * MIN))
        ft = maxOf(e.starts[q], t0) + MIN
    }

    fun gridChoose() {
        val ch = gridList.getOrNull(gridRow) ?: return
        val e = a.epg.forChannel(ch)
        val p = gridProg(ch)
        val now = System.currentTimeMillis()
        if (e != null && p >= 0 && e.stops[p] <= now && ch.hasArchive && e.starts[p] >= now - ch.catchupDays * DAY) {
            closeLayers()
            a.play(ch, e[p])
        } else a.tuneTo(ch, gridList, gridGroup)
    }

    private fun gridGroupMenu() {
        val items = arrayListOf(PopItem("Все каналы", if (gridGroup == "all") R.drawable.ic_check else 0) { gridSetGroup("all") })
        a.menu.filter { it.id.startsWith("g:") }.take(8).forEach { m ->
            items += PopItem(m.title, if (gridGroup == m.id) R.drawable.ic_check else 0, sub = m.list.size.toString()) { gridSetGroup(m.id) }
        }
        a.openPop("Группа", items, 1160, 126, 0)
    }

    private fun gridSetGroup(id: String) {
        val keep = gridList.getOrNull(gridRow)
        gridGroup = id
        gridList = a.listOf(id) ?: a.playlist.channels
        gridRow = gridList.indexOfFirst { it.key == keep?.key }.coerceAtLeast(0)
        gridHead = -1
        renderGrid()
    }

    fun keyGrid(key: Key) {
        val n = gridList.size
        val now = System.currentTimeMillis()
        val tabs = gridDays(now).size
        if (gridHead >= 0) {
            when (key) {
                Key.LEFT -> gridHead = (gridHead - 1).coerceAtLeast(0)
                Key.RIGHT -> gridHead = (gridHead + 1).coerceAtMost(tabs)
                Key.DOWN -> gridHead = -1
                Key.OK -> {
                    if (gridHead == tabs) { gridGroupMenu(); return }
                    val d = gridDays(now)[gridHead]
                    val tod = ft - dayStart(ft)
                    ft = if (d == dayStart(now)) now else d + tod
                    t0 = half(ft) - 30 * MIN
                    gridHead = -1
                }
                else -> return
            }
            renderGrid()
            return
        }
        when (key) {
            Key.UP -> if (gridRow > 0) gridRow-- else gridHead = gridDays(now).indexOf(dayStart(ft)).coerceAtLeast(0)
            Key.DOWN -> if (gridRow < n - 1) gridRow++ else return
            Key.CH_UP -> gridRow = (gridRow - 6).coerceAtLeast(0)
            Key.CH_DOWN -> gridRow = (gridRow + 6).coerceAtMost(n - 1)
            Key.LEFT -> gridMove(-1)
            Key.RIGHT -> gridMove(1)
            else -> return
        }
        grid.postOnAnimation { if (a.zone == "grid") renderGrid() }
    }

    // ================================================================ поиск (SPEC 3.5): поле + экранная клавиатура

    private var searchBack = "play"

    /** Клавиша экранной клавиатуры: t — надпись, w — ширина в клавишах, act — действие. */
    private class KbKey(val t: String, val w: Int = 1, val act: String = "", val icon: Int = 0, val label: String = "") { val sv get() = act.isNotEmpty() }
    private var kbEn = false
    var kr = 0
    var kc = 0

    private fun kbRows(): List<List<KbKey>> {
        val letters = if (kbEn) "ABCDEFGHIJKLMNOPQRSTUVWXYZ" else "АБВГДЕЖЗИЙКЛМНОПРСТУФХЦЧШЩЪЫЬЭЮЯ"
        val rows = letters.chunked(8).map { r -> r.map { KbKey(it.toString()) } }.toMutableList()
        rows += (1..8).map { KbKey(it.toString()) }
        rows += listOf(KbKey("9"), KbKey("0"), KbKey("Пробел", 3, "space"), KbKey("", 2, "bksp", R.drawable.ic_backspace, "Стереть"))
        rows += listOf(KbKey(if (kbEn) "РУ" else "EN", 2, "lang"), KbKey("Очистить", 3, "clear"))
        return rows
    }

    private fun buildSearch() {
        search = u.box { visibility = View.GONE; setBackgroundColor(C.BG); isClickable = true }
        searchHead = u.text("", 28, C.T2)
        search.addView(u.row {
            addView(u.text("Поиск", 44, C.T1, 600))
            addView(View(a), u.lin(0, 1, 1f))
            addView(searchHead)
        }, u.fr(MATCH, 64, l = 96, t = 54, r = 96))
        searchText = u.text("", 40, C.T1, 500).apply { ellipsize = android.text.TextUtils.TruncateAt.START }
        search.addView(u.row {
            background = u.rect(C.S2, 12)
            u.pad(this, 28, 0, 28, 0)
            addView(u.icon(R.drawable.ic_search, 40, C.T2), u.lin(40, 40, r = 20))
            addView(searchText, u.lin(WRAP, WRAP).apply { width = WRAP })
            addView(View(a).apply { setBackgroundColor(C.PAPER) }, u.lin(4, 44, l = 4))
        }, u.fr(640, 96, l = 96, t = 150))
        kb = u.col()
        search.addView(kb, u.fr(648, WRAP, l = 92, t = 266))
        search.addView(u.text("Цифры — поиск по номеру канала. Пульт с цифрами тоже работает.", 24, C.T3, lines = 3, lineHeight = 32), u.fr(640, WRAP, l = 96, t = 850))
        results = u.col()
        search.addView(results, u.fr(MATCH, MATCH, l = 820, t = 150, r = 96, b = 132))
        searchHints = u.box()
        search.addView(searchHints, u.fr(MATCH, 54, Gravity.BOTTOM, l = 96, r = 96, b = 54))
        a.root.addView(search, u.fr(MATCH, MATCH))
    }

    fun openSearch() {
        if (a.playlist.channels.isEmpty()) return
        val back = if (guideShown()) "groups" else "play"
        closeLayers()
        searchBack = back
        a.query = ""; a.fi = 0
        kbEn = false; kr = 0; kc = 0
        a.zone = "search"
        search.visibility = View.VISIBLE
        a.runSearch()
        renderSearch()
    }

    fun closeSearch() {
        search.visibility = View.GONE
        a.query = ""; a.found = emptyList() // истории запросов нет
        a.zone = "play"
        if (searchBack == "groups") { openGuide("groups"); a.gi = 0; renderGuide() } else showInfo()
    }

    /** Дописывает символы в строку поиска (клавиша экранной клавиатуры, цифра пульта, буква с клавиатуры). */
    fun typeText(t: String) {
        if (a.query.length >= 40) return
        a.query = (a.query + t).take(40)
        queryChanged()
    }

    private fun queryChanged() {
        a.fi = 0
        a.runSearch()
        renderSearch()
    }

    /** OK на экранной клавиатуре. */
    fun kbPress() {
        val k = kbRows().getOrNull(kr)?.getOrNull(kc) ?: return
        when (k.act) {
            "space" -> if (a.query.isNotEmpty() && !a.query.endsWith(" ")) typeText(" ")
            "bksp" -> { a.query = a.query.dropLast(1); queryChanged() }
            "clear" -> { a.query = ""; queryChanged() }
            "lang" -> { kbEn = !kbEn; kr = kbRows().size - 1; kc = 0; renderSearch() }
            else -> typeText(if (a.query.isEmpty()) k.t else k.t.lowercase())
        }
    }

    private fun renderKb() {
        kb.removeAllViews()
        val focus = a.zone == "search"
        kbRows().forEachIndexed { r, row ->
            kb.addView(u.row {
                row.forEachIndexed { c, k ->
                    val sel = focus && r == kr && c == kc
                    addView(u.box {
                        background = if (sel) u.rect(C.PAPER, 12, C.PAPER, 2) else u.rect(C.S2, 12, C.LINE_CTL, 2)
                        if (k.icon != 0) addView(u.icon(k.icon, 36, if (sel) C.TI else C.T2), u.fr(36, 36, Gravity.CENTER))
                        else addView(u.text(k.t, if (k.sv) 26 else 32, if (sel) C.TI else if (k.sv) C.T2 else C.T1, 500).apply { gravity = Gravity.CENTER }, u.fr(MATCH, MATCH))
                        contentDescription = k.label.ifEmpty { k.t }
                        setOnClickListener { a.zone = "search"; kr = r; kc = c; kbPress(); renderSearch() }
                    }, u.lin(k.w * 80 - 8, 72, l = 4, t = 4, r = 4, b = 4))
                }
            })
        }
    }

    fun keySearch(key: Key, event: android.view.KeyEvent) {
        val rows = kbRows()
        val row = rows[kr]
        when (key) {
            Key.DIGIT -> { typeText(a.digit(event.keyCode).toString()); return }
            Key.DEL -> { a.query = a.query.dropLast(1); queryChanged(); return }
            Key.LEFT -> if (kc > 0) kc-- else return
            Key.RIGHT -> if (kc < row.size - 1) kc++ else if (a.found.isNotEmpty()) a.zone = "results" else return
            Key.UP, Key.DOWN -> {
                val r = kr + if (key == Key.UP) -1 else 1
                if (r < 0 || r >= rows.size) return
                // остаёмся в той же колонке: ищем клавишу, которая накрывает ту же позицию
                val x = row.take(kc).sumOf { it.w }
                var acc = 0
                var col = rows[r].size - 1
                for ((i, k) in rows[r].withIndex()) { if (x < acc + k.w) { col = i; break }; acc += k.w }
                kr = r; kc = col
            }
            Key.GREEN, Key.YELLOW -> return
            else -> return
        }
        renderSearch()
    }

    /** Подсветка совпадения: жирно и подчёркнуто. */
    private fun hl(text: String, sel: Boolean): CharSequence {
        val q = a.query.trim()
        if (q.isEmpty()) return text
        val at = text.lowercase().replace('ё', 'е').indexOf(q.lowercase().replace('ё', 'е'))
        if (at < 0) return text
        return SpannableString(text).apply {
            setSpan(StyleSpan(android.graphics.Typeface.BOLD), at, at + q.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            setSpan(UnderlineSpan(), at, at + q.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            setSpan(ForegroundColorSpan(if (sel) C.TI else C.T1), at, at + q.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }

    private fun resultRow(f: Found, sel: Boolean): View {
        val now = System.currentTimeMillis()
        if (f.type == "ch") {
            val q = a.query.trim()
            val num = q.isNotEmpty() && q.all { it.isDigit() } && f.ch!!.number.toString().startsWith(q)
            return chRow(f.ch!!, sel, digits = if (num) q.length else 0, name = if (num) f.ch.name else hl(f.ch.name, sel), bar = false)
        }
        return u.row {
            background = if (sel) u.rect(C.PAPER, 12) else null
            u.pad(this, 24, 0, 28, 0)
            if (f.type == "prog") addView(u.text(clock(f.start), 30, if (sel) C.TI2 else C.T2), u.lin(96, WRAP, r = 24))
            else addView(u.box { addView(u.icon(if (f.type == "group") R.drawable.ic_folder else R.drawable.ic_list, 36, if (sel) C.TI2 else C.T2), u.fr(36, 36)) }, u.lin(96, 36, r = 24))
            val sub = when (f.type) {
                "prog" -> f.ch!!.name + " · " + when {
                    f.start <= now && f.stop > now -> "идёт сейчас, до " + clock(f.stop)
                    f.rec -> (when (dayDiff(f.start, now)) { 0 -> "сегодня"; -1 -> "вчера"; else -> dateText(f.start) }) + " · запись есть"
                    else -> when (dayDiff(f.start, now)) { 0 -> "сегодня"; 1 -> "завтра"; else -> dateText(f.start) }
                }
                "group" -> chCount(a.menu.firstOrNull { it.id == f.id }?.list?.size ?: 0)
                else -> Repo.playlists.value.firstOrNull { it.id == f.id }?.let { if (it.id == Repo.activeId) "Активный" else if (it.count > 0) chCount(it.count) else "" }.orEmpty()
            }
            addView(u.col {
                addView(u.text(hl(f.title.ifEmpty { "Без названия" }, sel), 30, if (sel) C.TI else C.T1, lineHeight = 44))
                addView(u.text(sub, 28, if (sel) C.TI2 else C.T2))
            }, u.lin(0, WRAP, 1f))
            if (f.type == "prog" && f.start <= now && f.stop > now) addView(live(sel), u.lin(l = 24))
            else if (f.rec) addView(u.icon(R.drawable.ic_history, 36, if (sel) C.TI2 else C.T2), u.lin(36, 36, l = 24))
            contentDescription = f.title
        }
    }

    fun renderSearch() {
        searchHead.text = headDate(System.currentTimeMillis())
        searchText.text = a.query
        renderKb()
        results.removeAllViews()
        val focus = a.zone == "results"
        val q = a.query.trim()
        if (q.isNotEmpty()) {
            if (a.found.isEmpty() && a.foundSections.none { it.status.isNotEmpty() }) {
                results.addView(state(R.drawable.ic_search, "Ничего не нашлось", "Попробуйте короче или наберите номер канала цифрами."), u.lin(MATCH, WRAP, l = 24))
            } else {
                // Окно результатов: строка в фокусе всегда видна
                var budget = 798 - 16
                var skip = 0
                if (focus) {
                    var used = 0
                    for (s in a.foundSections) { used += 64; for (i in s.from until s.from + s.count) { used += 106; if (i == a.fi && used > budget) skip = used - budget } }
                }
                var y = 0
                for (s in a.foundSections) {
                    y += 64
                    if (y > skip) results.addView(u.row(Gravity.BOTTOM) {
                        u.pad(this, 24, 16, 24, 0)
                        addView(u.text(s.name, 28, C.T1, 600), u.lin(r = 16))
                        addView(u.text(if (s.status.isNotEmpty()) "" else (if (s.note.isNotEmpty()) s.note + if (s.count > 0) " · " else " · не найдено" else "") +
                            if (s.count > 0) s.count.toString() else if (s.note.isEmpty()) "не найдено" else "", 28, C.T3))
                        if (s.isNew.isNotEmpty() && s.count > 0) newTagIf(s.isNew)?.let { addView(it, u.lin(l = 10)) }
                    }, u.lin(MATCH, 64))
                    if (s.status.isNotEmpty() && y > skip) results.addView(u.row { addView(spinner(32, 4), u.lin(32, 32, r = 16)); addView(u.text(s.status, 28, C.T2)) }, u.lin(l = 24, t = 8))
                    for (i in s.from until s.from + s.count) {
                        y += 106
                        if (y > skip) results.addView(resultRow(a.found[i], focus && i == a.fi), u.lin(MATCH, 104, b = 2))
                    }
                }
                if (focus) h.postDelayed({ Repo.prefs.markSeen("progsearch") }, 4000)
            }
        }
        val f = a.found.getOrNull(a.fi)
        if (!focus) setHints(searchHints, "ok" to "Ввести букву", "r" to if (a.found.isNotEmpty()) "К результатам" else "", "0–9" to "Цифры", "back" to "Закрыть")
        else setHints(searchHints, "ok" to if (f?.rec == true) "Смотреть запись" else "Смотреть", "hold" to if (f?.ch != null) "Действия" else "",
            "l" to "К клавиатуре", "back" to "К клавиатуре")
    }

    fun keyResults(key: Key) {
        when (key) {
            Key.UP -> if (a.fi > 0) a.fi-- else return
            Key.DOWN -> if (a.fi < a.found.size - 1) a.fi++ else return
            Key.LEFT -> a.zone = "search"
            Key.GREEN -> { a.found.getOrNull(a.fi)?.ch?.let { a.toggleFav(it) }; return }
            else -> return
        }
        renderSearch()
    }

    // ================================================================ общее

    /** Закрывает все слои с фокусом поверх плеера: два фокусируемых слоя одновременно не открыты. */
    fun closeLayers() {
        if (numOpen()) numClose()
        closeCtl()
        grid.visibility = View.GONE; guide.visibility = View.GONE; search.visibility = View.GONE
        a.closePop()
        hideInfo()
        a.zone = "play"
    }

    fun renderZone() {
        when (a.zone) {
            "groups", "channels", "epg", "date", "head" -> renderGuide()
            "grid" -> renderGrid()
            "search", "results" -> renderSearch()
            "ctl" -> renderCtl()
        }
    }

    private var minute = 0L
    fun tick() {
        val now = System.currentTimeMillis()
        if (osdShown()) { renderOsd(); renderTopRight() }
        if (ctl.visibility == View.VISIBLE && a.arch != null) renderTimeline()
        if (now / MIN != minute) {
            minute = now / MIN
            if (guideShown()) { progKey = ""; renderGuide() }
            if (a.zone == "grid") renderGrid()
        }
    }
}

/** Номер канала как есть: «5», «1465» — без ведущих нулей. */
@Suppress("UnusedReceiverParameter")
fun MainActivity.numText(n: Int): String = n.toString()
