package com.volnalabs.tv.ui

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.volnalabs.tv.R

/** Кнопки пульта в виде, удобном для логики экранов. */
enum class Key { UP, DOWN, LEFT, RIGHT, OK, BACK, CH_UP, CH_DOWN, RED, GREEN, YELLOW, BLUE, GUIDE, INFO, SEARCH, LAST, LIST, PLAY_PAUSE, PLAY, PAUSE, REW, FF, MENU, VOL_UP, VOL_DOWN, MUTE, DEL, DIGIT, OTHER }

class DialogButton(val title: String, val danger: Boolean = false, val icon: Int = 0, val action: () -> Unit)
class PopItem(val title: String = "", val icon: Int = 0, val key: String = "", val sub: String = "", val danger: Boolean = false, val off: Boolean = false,
    val sep: Boolean = false, val toggle: Boolean? = null, val keep: Boolean = false, val action: () -> Unit = {})
class ToastAction(val label: String, val run: () -> Unit, val expire: () -> Unit = {})

/**
 * Общая основа экранов: корневой слой, масштаб, тосты, диалоги, всплывающее меню, подсказки кнопок.
 * Фокус ведётся самим экраном (одна зона — один выбранный элемент), а не системой: так переходы
 * совпадают с таблицами SPEC 8.1 на всех устройствах, а «Назад» всегда снимает ровно один слой.
 */
abstract class BaseActivity : Activity() {
    protected val handler = Handler(Looper.getMainLooper())
    lateinit var ui: Ui
    lateinit var root: FrameLayout
    val tv: Boolean by lazy { isTv() }

    private lateinit var popLayer: FrameLayout
    private lateinit var dialogLayer: FrameLayout
    private lateinit var toastLayer: FrameLayout

    /** Строит корневой слой. На телевизоре единица макета — px холста 1920×1080, на телефоне — dp. */
    protected fun setupRoot(background: Int) {
        val dm = resources.displayMetrics
        ui = Ui(this, if (tv) minOf(dm.widthPixels / 1920f, dm.heightPixels / 1080f) else dm.density)
        // Корень держит фокус сам: экраны ведут выбор без системного фокуса (см. описание класса)
        root = FrameLayout(this).apply { setBackgroundColor(background); isFocusable = true; if (tv) isFocusableInTouchMode = true }
        setContentView(root)
        root.requestFocus()
    }

    /** Слои поверх содержимого экрана добавляются последними, чтобы быть выше остальных. */
    protected fun setupOverlays() {
        popLayer = ui.box { visibility = View.GONE }
        dialogLayer = ui.box { visibility = View.GONE; setBackgroundColor(C.SCRIM); isClickable = true }
        toastLayer = ui.box { visibility = View.GONE }
        root.addView(popLayer, ui.fr(MATCH, MATCH))
        root.addView(dialogLayer, ui.fr(MATCH, MATCH))
        root.addView(toastLayer, ui.fr(MATCH, MATCH))
        popLayer.setOnClickListener { closePop() }
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    // ---------- тосты ----------
    private var toastAction: ToastAction? = null
    private val hideToastTask = Runnable { hideToast() }
    /** Где показывать тост: в плеере — вверху (не перекрывает инфо), в остальных слоях — над подсказками. */
    protected open val toastOnTop: Boolean get() = false

    /** Уведомление на 3 с; с действием («Вернуть») — 6 с. Новое заменяет старое. kind: ok, err, warn, fav, wait. */
    fun toast(text: String, kind: String = "", action: ToastAction? = null, ms: Long = 0) {
        toastAction?.expire?.invoke()
        toastAction = action
        val icon = when (kind) { "ok" -> R.drawable.ic_check to C.SUCCESS; "err" -> R.drawable.ic_alert to C.ERROR; "warn" -> R.drawable.ic_warning to C.WARNING
            "fav" -> R.drawable.ic_star_filled to C.T1; else -> null }
        val u = ui
        val t = if (tv) 30 else 15
        val box = u.row {
            background = u.rect(C.S2, if (tv) 20 else 10, C.LINE, if (tv) 2 else 1)
            if (tv) { u.pad(this, 40, 16, 40, 16); minimumHeight = u.p(80) } else { u.pad(this, 16, 10, 16, 10); minimumHeight = u.p(44) }
            if (icon != null) addView(u.icon(icon.first, if (tv) 36 else 20, icon.second), u.lin(if (tv) 36 else 20, if (tv) 36 else 20, r = if (tv) 16 else 10))
            addView(u.text(text + if (action != null && tv) " · OK — " + action.label.lowercase() else "", t, C.T1, lines = 3, lineHeight = t * 4 / 3), u.lin(WRAP, WRAP, 1f))
            if (action != null && !tv) addView(u.text(action.label, 14, C.T1, 600).apply {
                background = u.rect(C.S3, 6); u.pad(this, 12, 8, 12, 8); setOnClickListener { toastAct() } }, u.lin(l = 12))
        }
        toastLayer.removeAllViews()
        val top = toastOnTop
        toastLayer.addView(box, FrameLayout.LayoutParams(WRAP, WRAP, Gravity.CENTER_HORIZONTAL or if (top) Gravity.TOP else Gravity.BOTTOM).apply {
            if (tv) { topMargin = u.p(80); bottomMargin = u.p(150); leftMargin = u.p(410); rightMargin = u.p(410) }
            else { topMargin = u.p(16); bottomMargin = u.p(24); leftMargin = u.p(16); rightMargin = u.p(16) }
        })
        toastLayer.contentDescription = text
        toastLayer.visibility = View.VISIBLE
        handler.removeCallbacks(hideToastTask)
        handler.postDelayed(hideToastTask, if (ms > 0) ms else if (action != null) 6000 else 3000)
    }

    fun hideToast() {
        handler.removeCallbacks(hideToastTask)
        toastLayer.visibility = View.GONE
        val a = toastAction
        toastAction = null
        a?.expire?.invoke()
    }

    val toastText: String get() = if (toastLayer.visibility == View.VISIBLE) toastLayer.contentDescription?.toString().orEmpty() else ""

    /** OK при тосте с действием выполняет это действие («Вернуть»). */
    protected fun toastAct(): Boolean {
        val a = toastAction ?: return false
        toastAction = null
        handler.removeCallbacks(hideToastTask)
        toastLayer.visibility = View.GONE
        a.run()
        return true
    }

    // ---------- диалог ----------
    private var dialogButtons: List<DialogButton> = emptyList()
    private var dialogIndex = 0
    private var dialogBack: (() -> Unit)? = null
    private var dialogRow: LinearLayout? = null
    var dialogBusy = false
    val dialogOpen: Boolean get() = dialogLayer.visibility == View.VISIBLE
    var dialogProgress: View? = null
        private set

    /** Диалог: фокус заперт на кнопках, «Назад» = отмена. Свой вид вместо системного AlertDialog. */
    fun openDialog(title: String, text: String, buttons: List<DialogButton>, focus: Int = 0, progress: Float = -1f, onBack: (() -> Unit)? = null) {
        val u = ui
        dialogButtons = buttons; dialogIndex = focus; dialogBack = onBack; dialogBusy = false
        val row = u.row()
        dialogRow = row
        val box = u.col {
            background = u.rect(C.S2, if (tv) 20 else 12)
            isClickable = true
            val pad = if (tv) 48 else 20
            u.pad(this, pad, pad, pad, pad)
            addView(u.text(title, if (tv) 44 else 17, C.T1, 600, lines = 3, lineHeight = if (tv) 52 else 22), u.lin(MATCH, WRAP, b = if (tv) 16 else 8))
            addView(u.text(text, if (tv) 30 else 15, C.T2, lines = 8, lineHeight = if (tv) 40 else 22), u.lin(MATCH, WRAP))
            if (progress >= 0) addView(u.bar(progress, C.TRACK, C.PAPER, if (tv) 6 else 3).also { dialogProgress = it }, u.lin(MATCH, if (tv) 6 else 3, t = if (tv) 32 else 16))
            if (!tv) row.orientation = LinearLayout.VERTICAL
            addView(row, u.lin(MATCH, WRAP, t = if (tv) 40 else 20))
        }
        dialogLayer.removeAllViews()
        dialogLayer.addView(box, FrameLayout.LayoutParams(if (tv) u.p(960) else MATCH, WRAP, Gravity.CENTER).apply { if (!tv) { leftMargin = u.p(16); rightMargin = u.p(16) } })
        dialogLayer.visibility = View.VISIBLE
        renderDialog()
    }

    private fun renderDialog() {
        val row = dialogRow ?: return
        row.removeAllViews()
        dialogButtons.forEachIndexed { i, b ->
            row.addView(button(b.title, b.icon, i == dialogIndex && tv, b.danger).apply { setOnClickListener { if (!dialogBusy) b.action() } },
                if (tv) ui.lin(WRAP, 64, r = 16) else ui.lin(MATCH, 48, b = 8))
        }
    }

    fun closeDialog() {
        dialogLayer.visibility = View.GONE
        dialogProgress = null
        onLayerClosed()
    }

    /** Кнопка: покой — surface-3, фокус — «бумага», опасная — красный текст, недоступная — без фона. */
    fun button(title: String, icon: Int = 0, focused: Boolean = false, danger: Boolean = false, off: Boolean = false, spin: Boolean = false): LinearLayout {
        val u = ui
        val color = if (off) C.TD else if (danger) (if (focused) C.ERROR_ON_PAPER else C.ERROR) else if (focused) C.TI else C.T1
        return u.row(Gravity.CENTER) {
            background = u.rect(if (focused) C.PAPER else if (off) 0 else C.S3, if (tv) 12 else 8)
            u.pad(this, if (tv) 32 else 16, 0, if (tv) 32 else 16, 0)
            val s = if (tv) 36 else 20
            if (spin) addView(Spinner(ctx = context, stroke = u.p(if (tv) 4 else 2).toFloat()), u.lin(s, s, r = if (tv) 16 else 8))
            else if (icon != 0) addView(u.icon(icon, s, color), u.lin(s, s, r = if (tv) 16 else 8))
            addView(u.text(title, if (tv) 28 else 15, color))
            contentDescription = title
        }
    }

    // ---------- всплывающее меню: плейлисты, действия с каналом, удержание OK в настройках ----------
    private var popItems: List<PopItem> = emptyList()
    private var popIndex = 0
    private var popBox: LinearLayout? = null
    private var popTitle = ""
    val popOpen: Boolean get() = popLayer.visibility == View.VISIBLE

    /** Меню в точке (x, y) холста; на телефоне — нижняя шторка. Фокус при открытии — на пункте [focus]. */
    fun openPop(title: String, items: List<PopItem>, x: Int, y: Int, focus: Int = 0) {
        val u = ui
        popItems = items; popTitle = title
        popIndex = focus
        while (popIndex < items.size - 1 && (items[popIndex].sep || items[popIndex].off)) popIndex++
        val box = u.col { background = u.rect(C.S2, if (tv) 20 else 16, C.LINE, if (tv) 2 else 0); isClickable = true; val p = if (tv) 12 else 8; u.pad(this, p, p, p, p) }
        popBox = box
        popLayer.removeAllViews()
        if (tv) popLayer.addView(box, FrameLayout.LayoutParams(WRAP, WRAP).apply { leftMargin = u.p(x); topMargin = u.p(y) })
        else { popLayer.setBackgroundColor(0x9E000000.toInt()); popLayer.addView(box, FrameLayout.LayoutParams(MATCH, WRAP, Gravity.BOTTOM)) }
        popLayer.visibility = View.VISIBLE
        renderPop()
    }

    private fun renderPop() {
        val box = popBox ?: return
        val u = ui
        box.removeAllViews()
        box.minimumWidth = u.p(if (tv) 520 else 0)
        if (popTitle.isNotEmpty()) box.addView(u.text(popTitle, if (tv) 24 else 13, C.T3).apply { if (tv) u.pad(this, 24, 12, 24, 8) else u.pad(this, 12, 8, 12, 8) })
        popItems.forEachIndexed { i, it ->
            if (it.sep) { box.addView(View(this).apply { setBackgroundColor(C.LINE) }, u.lin(MATCH, if (tv) 2 else 1, l = 12, t = 8, r = 12, b = 8)); return@forEachIndexed }
            val sel = i == popIndex && tv
            val main = if (it.off) C.TD else if (it.danger) (if (sel) C.ERROR_ON_PAPER else C.ERROR) else if (sel) C.TI else C.T1
            val second = if (it.off) C.TD else if (it.danger) main else if (sel) C.TI2 else C.T2
            box.addView(u.row {
                if (sel) background = u.rect(C.PAPER, 12)
                u.pad(this, if (tv) 24 else 12, 0, if (tv) 24 else 12, 0)
                val s = if (tv) 36 else 24
                addView(if (it.icon != 0) u.icon(it.icon, s, second) else View(context), u.lin(s, s, r = if (tv) 20 else 16))
                addView(u.text(it.title, if (tv) 30 else 16, main), u.lin(0, WRAP, 1f))
                if (it.sub.isNotEmpty()) addView(u.text(it.sub, if (tv) 26 else 13, if (sel) C.TI2 else C.T3), u.lin(l = 16))
                if (it.toggle != null) addView(u.switch(it.toggle, sel, if (tv) 72 else 44), u.lin(l = 16))
                if (it.key.isNotEmpty() && tv) addView(u.text(it.key, 22, second), u.lin(l = 32))
                contentDescription = it.title
                setOnClickListener { _ -> if (!it.off) { popIndex = i; popChoose() } }
            }, u.lin(MATCH, if (tv) 72 else 48))
        }
    }

    fun closePop() {
        if (!popOpen) return
        popLayer.visibility = View.GONE
        onLayerClosed()
    }

    private fun popChoose() {
        val it = popItems.getOrNull(popIndex) ?: return
        if (it.sep || it.off) return
        if (it.keep) { it.action(); return }
        popLayer.visibility = View.GONE
        it.action()
        if (!popOpen && !dialogOpen) onLayerClosed()
    }

    val popTitles: List<String> get() = if (popOpen) popItems.filter { !it.sep }.map { it.title } else emptyList()

    /** Меню или диалог закрылись: экран возвращает фокус на элемент, который их открыл. */
    protected open fun onLayerClosed() {}

    // ---------- подсказки кнопок ----------
    /** Подсказка «кнопка — что она делает». k: ok, hold, back, l, r, u, d, lr, ud или текст кнопки («0–9», «CH±»). */
    fun hint(k: String, label: String, onVideo: Boolean = false): LinearLayout {
        val u = ui
        val capBg = if (onVideo) C.KEY_ON_VIDEO else C.S3H
        fun arrow(res: Int) = u.icon(res, 26, C.T1)
        fun cap(build: LinearLayout.() -> Unit, wide: Int = 0) = u.row(Gravity.CENTER) { background = u.rect(capBg, 22); minimumWidth = u.p(44); u.pad(this, wide, 0, wide, 0); build() }
        fun label(s: String) = u.text(s, 19, C.T1, 700).apply { letterSpacing = 0.02f }
        val arrows = mapOf("l" to R.drawable.ic_chevron_left, "r" to R.drawable.ic_chevron_right, "u" to R.drawable.ic_chevron_up, "d" to R.drawable.ic_chevron_down)
        return u.row {
            when {
                k == "hold" -> {
                    addView(u.text("удерж.", 22, C.T3), u.lin(r = 10))
                    addView(u.box { background = u.rect(0, 27, C.T3, 2); addView(cap({ addView(label("OK")) }), u.fr(44, 44, Gravity.CENTER)) }, u.lin(54, 54, r = 14))
                }
                k == "ok" -> addView(cap({ addView(label("OK")) }), u.lin(44, 44, r = 14))
                k == "back" -> addView(cap({ addView(label("Назад")) }, 16), u.lin(WRAP, 44, r = 14))
                arrows[k] != null -> addView(cap({ addView(arrow(arrows.getValue(k)), u.lin(26, 26)) }), u.lin(44, 44, r = 14))
                k == "lr" -> addView(cap({ addView(arrow(R.drawable.ic_chevron_left), u.lin(26, 26)); addView(arrow(R.drawable.ic_chevron_right), u.lin(26, 26)) }, 10), u.lin(WRAP, 44, r = 14))
                k == "ud" -> addView(cap({ addView(arrow(R.drawable.ic_chevron_up), u.lin(26, 26)); addView(arrow(R.drawable.ic_chevron_down), u.lin(26, 26)) }, 10), u.lin(WRAP, 44, r = 14))
                else -> addView(cap({ addView(label(k)) }, 16), u.lin(WRAP, 44, r = 14))
            }
            addView(u.text(label, 24, C.T2))
            contentDescription = label
        }
    }

    /** Строка подсказок: пары «кнопка — подпись». Пустая подпись пропускается (действие сейчас недоступно). */
    fun hints(vararg items: Pair<String, String>, onVideo: Boolean = false): LinearLayout = ui.row {
        var first = true
        for ((k, label) in items) {
            if (label.isEmpty()) continue
            addView(hint(k, label, onVideo), ui.lin(WRAP, 54, l = if (first) 0 else 52))
            first = false
        }
    }

    fun hintTexts(v: View?): List<String> = (v as? LinearLayout)?.let { row -> (0 until row.childCount).map { row.getChildAt(it).contentDescription?.toString().orEmpty() } } ?: emptyList()

    // ---------- кнопки пульта ----------
    private var okDown = false
    private var okHeld = false
    private val holdTask = Runnable { okHeld = true; if (!onHoldOk()) onOk() }

    private fun map(code: Int): Key = when (code) {
        KeyEvent.KEYCODE_DPAD_UP -> Key.UP; KeyEvent.KEYCODE_DPAD_DOWN -> Key.DOWN; KeyEvent.KEYCODE_DPAD_LEFT -> Key.LEFT; KeyEvent.KEYCODE_DPAD_RIGHT -> Key.RIGHT
        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER, KeyEvent.KEYCODE_BUTTON_A -> Key.OK
        KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ESCAPE, KeyEvent.KEYCODE_BUTTON_B -> Key.BACK
        KeyEvent.KEYCODE_CHANNEL_UP, KeyEvent.KEYCODE_PAGE_UP -> Key.CH_UP; KeyEvent.KEYCODE_CHANNEL_DOWN, KeyEvent.KEYCODE_PAGE_DOWN -> Key.CH_DOWN
        KeyEvent.KEYCODE_PROG_RED -> Key.RED; KeyEvent.KEYCODE_PROG_GREEN -> Key.GREEN; KeyEvent.KEYCODE_PROG_YELLOW -> Key.YELLOW; KeyEvent.KEYCODE_PROG_BLUE -> Key.BLUE
        KeyEvent.KEYCODE_GUIDE -> Key.GUIDE; KeyEvent.KEYCODE_INFO -> Key.INFO; KeyEvent.KEYCODE_SEARCH -> Key.SEARCH; KeyEvent.KEYCODE_LAST_CHANNEL -> Key.LAST
        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> Key.PLAY_PAUSE; KeyEvent.KEYCODE_MEDIA_PLAY -> Key.PLAY; KeyEvent.KEYCODE_MEDIA_PAUSE -> Key.PAUSE
        KeyEvent.KEYCODE_MEDIA_REWIND -> Key.REW; KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> Key.FF
        KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_SETTINGS -> Key.MENU
        // CH LIST: у пультов Android TV своей кнопки «Список каналов» нет; ближайшая — «Содержание»
        KeyEvent.KEYCODE_TV_CONTENTS_MENU -> Key.LIST
        KeyEvent.KEYCODE_VOLUME_UP -> Key.VOL_UP; KeyEvent.KEYCODE_VOLUME_DOWN -> Key.VOL_DOWN; KeyEvent.KEYCODE_VOLUME_MUTE, KeyEvent.KEYCODE_MUTE -> Key.MUTE
        KeyEvent.KEYCODE_DEL, KeyEvent.KEYCODE_FORWARD_DEL -> Key.DEL
        in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9, in KeyEvent.KEYCODE_NUMPAD_0..KeyEvent.KEYCODE_NUMPAD_9 -> Key.DIGIT
        else -> Key.OTHER
    }

    protected fun digitOf(code: Int): Int = when (code) {
        in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9 -> code - KeyEvent.KEYCODE_0
        in KeyEvent.KEYCODE_NUMPAD_0..KeyEvent.KEYCODE_NUMPAD_9 -> code - KeyEvent.KEYCODE_NUMPAD_0
        else -> -1
    }

    /** Идёт ввод в текстовое поле: кнопки достаются полю и системной клавиатуре. */
    protected open fun typing(): Boolean = currentFocus is android.widget.EditText

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val key = map(event.keyCode)
        if (typing() && key != Key.BACK && !typingKey(key, event)) return super.dispatchKeyEvent(event)
        if (key == Key.OTHER) return super.dispatchKeyEvent(event)
        if (key == Key.VOL_UP || key == Key.VOL_DOWN || key == Key.MUTE) {
            return if (event.action == KeyEvent.ACTION_DOWN && onVolumeKey(key)) true else if (event.action == KeyEvent.ACTION_UP && volumeHandled) true else super.dispatchKeyEvent(event)
        }
        if (event.action == KeyEvent.ACTION_UP) {
            if (key == Key.OK && okDown) {
                okDown = false
                handler.removeCallbacks(holdTask)
                if (!okHeld) onOk()
            }
            return true
        }
        if (event.action != KeyEvent.ACTION_DOWN) return true
        if (key == Key.BACK) { if (event.repeatCount == 0) back(); return true }
        // Диалог и меню запирают фокус на себе
        if (dialogOpen) { if (!dialogBusy) dialogKey(key); return true }
        if (popOpen) { popKey(key); return true }
        if (key == Key.OK) {
            if (event.repeatCount > 0) return true
            if (toastAct()) return true
            // Удержание OK (650 мс) — меню действий; короткое нажатие срабатывает на отпускании
            if (holdable()) { okDown = true; okHeld = false; handler.postDelayed(holdTask, 650) } else onOk()
            return true
        }
        onKey(key, event)
        return true
    }

    private fun dialogKey(key: Key) {
        when (key) {
            Key.LEFT, Key.UP -> dialogIndex = (dialogIndex - 1).coerceAtLeast(0)
            Key.RIGHT, Key.DOWN -> dialogIndex = (dialogIndex + 1).coerceAtMost(dialogButtons.size - 1)
            Key.OK -> { dialogButtons.getOrNull(dialogIndex)?.action?.invoke(); return }
            else -> return
        }
        renderDialog()
    }

    private fun popKey(key: Key) {
        val step = when (key) { Key.UP -> -1; Key.DOWN -> 1; Key.OK -> { popChoose(); return }; else -> return }
        var i = popIndex + step
        while (i in popItems.indices) { if (!popItems[i].sep && !popItems[i].off) { popIndex = i; break }; i += step }
        renderPop()
    }

    private fun back() {
        when {
            dialogOpen -> if (!dialogBusy) { val b = dialogBack; if (b != null) b() else closeDialog() }
            popOpen -> closePop()
            else -> onBack()
        }
    }

    /** Нажатия для автотестов и подсказок: то же, что кнопка пульта. */
    fun press(code: Int, hold: Boolean = false) {
        dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
        if (hold) holdTask.run()
        dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
    }

    private var volumeHandled = false
    /** Кнопки громкости: true — экран обработал сам и показал свою шкалу. */
    protected open fun onVolumeKey(key: Key): Boolean { volumeHandled = false; return false }
    protected fun volumeTaken() { volumeHandled = true }
    /** Кнопки во время ввода текста, которые экран берёт себе (▲▼ между полями). */
    protected open fun typingKey(key: Key, event: KeyEvent): Boolean = false
    /** Есть ли у элемента в фокусе действие по удержанию OK. */
    protected open fun holdable(): Boolean = false
    protected open fun onHoldOk(): Boolean = false
    protected abstract fun onOk()
    protected abstract fun onBack()
    protected abstract fun onKey(key: Key, event: KeyEvent)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyOrientation()
    }

    protected fun label(v: TextView?): String = v?.text?.toString().orEmpty()
}

/** Индикатор загрузки: дуга, которая бежит по кругу (оборот 0,9 с). */
class Spinner(ctx: android.content.Context, private val stroke: Float) : AnimatedView(ctx) {
    private val track = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { style = android.graphics.Paint.Style.STROKE; strokeWidth = stroke; color = 0x1FF2F0EC }
    private val arc = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { style = android.graphics.Paint.Style.STROKE; strokeWidth = stroke; strokeCap = android.graphics.Paint.Cap.ROUND; color = C.T2 }
    private val oval = android.graphics.RectF()
    override fun onDraw(canvas: android.graphics.Canvas) {
        val pad = stroke / 2 + 1
        oval.set(pad, pad, width - pad, height - pad)
        canvas.drawOval(oval, track)
        canvas.drawArc(oval, (time / 0.9f * 360f) % 360f, 90f, false, arc)
    }
}
