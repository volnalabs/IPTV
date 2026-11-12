package com.volnalabs.tv.ui

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import coil.load
import com.volnalabs.tv.R
import com.volnalabs.tv.data.Channel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/** Токены дизайн-системы «Тихий эфир» (SPEC, раздел 2). Тёмная тема единственная. */
object C {
    const val BG = 0xFF0D0D0E.toInt()
    const val BG2 = 0xFF131314.toInt()
    const val S1 = 0xFF19191B.toInt()
    const val S2 = 0xFF222225.toInt()
    const val S3 = 0xFF2C2C30.toInt()
    const val S3H = 0xFF36363B.toInt()
    const val SCRIM = 0xCC0A0A0B.toInt()
    const val SCRIM_GUIDE = 0xF00D0D0E.toInt()
    const val SCRIM_MINI = 0xE60A0A0B.toInt()
    const val T1 = 0xFFF2F0EC.toInt()
    const val T2 = 0xFFB8B5AF.toInt()
    const val T3 = 0xFF9A978F.toInt()
    const val TD = 0xFF5F5D59.toInt()
    const val TI = 0xFF141415.toInt()
    const val TI2 = 0xFF56534D.toInt()
    const val LINE = 0xFF2E2E32.toInt()
    const val LINE_CTL = 0xFF77767C.toInt()
    const val PAPER = 0xFFEFEAE0.toInt()
    const val ACCENT = 0xFFE8956A.toInt()
    const val ACCENT_RING = 0x8CE8956A.toInt()
    const val ACCENT_ON_PAPER = 0xFFA0461C.toInt()
    const val TRACK = 0x29F2F0EC
    const val TRACK_ON_PAPER = 0x29141415
    const val BUFFER = 0x52F2F0EC
    const val OSD_TRACK = 0x47F2F0EC
    const val OSD_TILE = 0x24F2F0EC
    const val CTL_BTN = 0x1AF2F0EC
    const val CTL_SEP = 0x29F2F0EC
    const val KEY_ON_VIDEO = 0x29FFFFFF
    const val LOGO_TILE = 0xFF1F1F22.toInt()
    const val ERROR = 0xFFF06977.toInt()
    const val ERROR_ON_PAPER = 0xFFB4232F.toInt()
    const val ERROR_SUBTLE = 0x24F06977
    const val WARNING = 0xFFE3BD62.toInt()
    const val SUCCESS = 0xFF74C79A.toInt()
    const val NEW_TEXT = 0xFF1C160A.toInt()
}

const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

/**
 * Помощник для сборки экранов из обычных View. Размеры задаются в единицах макета:
 * на телевизоре — px холста 1920×1080 (scale = ширина экрана ÷ 1920), на телефоне — dp (scale = плотность).
 */
class Ui(val ctx: Context, val scale: Float) {
    fun p(v: Int): Int = (v * scale).roundToInt()
    fun p(v: Float): Int = (v * scale).roundToInt()

    private val fonts = HashMap<Int, Typeface>()
    fun font(weight: Int): Typeface = fonts.getOrPut(weight) {
        val id = when { weight >= 700 -> R.font.onest_bold; weight >= 600 -> R.font.onest_semibold; weight >= 500 -> R.font.onest_medium; else -> R.font.onest_regular }
        runCatching { ResourcesCompat.getFont(ctx, id) }.getOrNull() ?: if (weight >= 600) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
    }

    /** Текст: кегль и высота строки в единицах макета, одна строка с многоточием (lines > 1 — несколько). */
    fun text(s: CharSequence, size: Int, color: Int, weight: Int = 400, lines: Int = 1, lineHeight: Int = 0): TextView = TextView(ctx).apply {
        text = s
        setTextSize(TypedValue.COMPLEX_UNIT_PX, size * scale)
        setTextColor(color)
        typeface = font(weight)
        fontFeatureSettings = "tnum"
        includeFontPadding = false
        maxLines = lines
        ellipsize = TextUtils.TruncateAt.END
        if (lines == 1) isSingleLine = true
        if (lineHeight > 0) setLineSpacing(lineHeight * scale - paint.getFontMetrics(null), 1f)
    }

    fun icon(res: Int, size: Int, color: Int): ImageView = ImageView(ctx).apply {
        setImageResource(res)
        setColorFilter(color)
        layoutParams = ViewGroup.LayoutParams(p(size), p(size))
    }

    fun rect(color: Int, radius: Int = 0, stroke: Int = 0, strokeWidth: Int = 0): GradientDrawable = GradientDrawable().apply {
        setColor(color)
        cornerRadius = radius * scale
        if (strokeWidth > 0) setStroke(p(strokeWidth).coerceAtLeast(1), stroke)
    }

    fun row(gravity: Int = Gravity.CENTER_VERTICAL, build: LinearLayout.() -> Unit = {}): LinearLayout = LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL
        this.gravity = gravity
        build()
    }

    fun col(gravity: Int = Gravity.START, build: LinearLayout.() -> Unit = {}): LinearLayout = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        this.gravity = gravity
        build()
    }

    fun box(build: FrameLayout.() -> Unit = {}): FrameLayout = FrameLayout(ctx).apply(build)

    /** Параметры для LinearLayout: размеры и отступы в единицах макета; MATCH/WRAP передаются как есть. */
    fun lin(w: Int = WRAP, h: Int = WRAP, weight: Float = 0f, l: Int = 0, t: Int = 0, r: Int = 0, b: Int = 0, gravity: Int = -1) =
        LinearLayout.LayoutParams(size(w), size(h), weight).apply {
            setMargins(p(l), p(t), p(r), p(b))
            if (gravity != -1) this.gravity = gravity
        }

    fun fr(w: Int = WRAP, h: Int = WRAP, gravity: Int = Gravity.TOP or Gravity.START, l: Int = 0, t: Int = 0, r: Int = 0, b: Int = 0) =
        FrameLayout.LayoutParams(size(w), size(h), gravity).apply { setMargins(p(l), p(t), p(r), p(b)) }

    private fun size(v: Int) = if (v == MATCH || v == WRAP) v else p(v)

    fun pad(v: View, l: Int = 0, t: Int = 0, r: Int = 0, b: Int = 0) = v.setPadding(p(l), p(t), p(r), p(b))

    /** Логотип канала: буквы на нейтральной плитке, картинка поверх после загрузки. Цветных плашек нет. */
    fun logo(ch: Channel, w: Int, h: Int, radius: Int, tile: Int = C.LOGO_TILE, inset: Int = 0): ImageView = ImageView(ctx).apply {
        val letters = LetterDrawable(ch.name, 1f, tile).typeface(font(600)).radius(radius * scale)
        scaleType = ImageView.ScaleType.FIT_CENTER
        layoutParams = ViewGroup.LayoutParams(p(w), p(h))
        if (ch.logo.isNullOrEmpty()) setImageDrawable(letters)
        else {
            background = rect(tile, radius)
            if (inset > 0) setPadding(p(inset), p(inset), p(inset), p(inset))
            load(ch.logo) { placeholder(letters); error(letters); crossfade(false) }
        }
    }

    /** Полоса прогресса: дорожка и заполнение на долю [part]. */
    fun bar(part: Float, track: Int, fill: Int, height: Int): View = object : View(ctx) {
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        override fun onDraw(canvas: android.graphics.Canvas) {
            val r = this.height / 2f
            paint.color = track
            canvas.drawRoundRect(0f, 0f, width.toFloat(), this.height.toFloat(), r, r, paint)
            paint.color = fill
            val w = width * part.coerceIn(0f, 1f)
            if (w > 0) canvas.drawRoundRect(0f, 0f, w, this.height.toFloat(), r, r, paint)
        }
    }.apply { minimumHeight = p(height) }

    /** Метка «новое». */
    fun newTag(label: String = "новое", size: Int = 20): TextView = text(label, size, C.NEW_TEXT, 600).apply {
        background = rect(C.WARNING, (size * 0.3f).roundToInt())
        setPadding(p(size / 2), p(size / 10), p(size / 2), p(size / 10))
    }

    /** Переключатель 72×40: выключен — рамка, включён — «бумага». onPaper — внутри строки в фокусе. */
    fun switch(on: Boolean, onPaper: Boolean, w: Int = 72): View = box {
        val h = w * 40 / 72
        val knob = w * 24 / 72
        val border = if (onPaper) (if (on) C.TI else C.TI2) else if (on) C.PAPER else C.LINE_CTL
        background = rect(if (on) (if (onPaper) C.TI else C.PAPER) else 0, h / 2, border, 3)
        layoutParams = ViewGroup.LayoutParams(p(w), p(h))
        addView(View(ctx).apply { background = rect(if (on) (if (onPaper) C.PAPER else C.TI) else if (onPaper) C.TI2 else C.T2, knob / 2) },
            fr(knob, knob, Gravity.CENTER_VERTICAL, l = if (on) w - knob - 8 else 8))
    }
}

fun LetterDrawable.radius(r: Float): LetterDrawable { corner = r; return this }

fun Drawable.asBackground(v: View) { v.background = this }

// ---------- даты и подписи (те же тексты, что в web-версии) ----------

private val RU = Locale("ru")
private val MONTHS = arrayOf("января", "февраля", "марта", "апреля", "мая", "июня", "июля", "августа", "сентября", "октября", "ноября", "декабря")
private val WDAYS = arrayOf("Воскресенье", "Понедельник", "Вторник", "Среда", "Четверг", "Пятница", "Суббота")
private val WD = arrayOf("Вс", "Пн", "Вт", "Ср", "Чт", "Пт", "Сб")
private val clockFormat = SimpleDateFormat("HH:mm", RU)
const val DAY = 86_400_000L
const val MIN = 60_000L

fun clock(t: Long): String = clockFormat.format(Date(t))
fun dayStart(t: Long): Long = Calendar.getInstance().apply { timeInMillis = t; set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
fun dayDiff(t: Long, now: Long): Int = Math.round((dayStart(t) - dayStart(now)).toDouble() / DAY).toInt()
private fun cal(t: Long) = Calendar.getInstance().apply { timeInMillis = t }
fun dateText(t: Long): String = cal(t).let { "${it.get(Calendar.DAY_OF_MONTH)} ${MONTHS[it.get(Calendar.MONTH)]}" }
fun yearText(t: Long): String = cal(t).get(Calendar.YEAR).toString()
fun weekday(t: Long): String = WDAYS[cal(t).get(Calendar.DAY_OF_WEEK) - 1]
private fun wd(t: Long) = WD[cal(t).get(Calendar.DAY_OF_WEEK) - 1]

/** «Вчера, 10 ноября», «Сегодня, 11 ноября», дальше «Пт, 13 ноября». */
fun dayTitle(t: Long, now: Long): String = when (dayDiff(t, now)) { 0 -> "Сегодня"; -1 -> "Вчера"; 1 -> "Завтра"; else -> wd(t) } + ", " + dateText(t)
/** «Вчера», «Сегодня», «Завтра», «Пт 13». */
fun dayTab(t: Long, now: Long): String = when (dayDiff(t, now)) { 0 -> "Сегодня"; -1 -> "Вчера"; 1 -> "Завтра"; else -> wd(t) + " " + cal(t).get(Calendar.DAY_OF_MONTH) }
/** «сегодня, 18:00», «вчера, 21:10», «9 ноября, 12:00». */
fun whenText(t: Long, now: Long): String = when (dayDiff(t, now)) { 0 -> "сегодня"; -1 -> "вчера"; 1 -> "завтра"; else -> dateText(t) } + ", " + clock(t)
fun headDate(now: Long): String = wd(now) + ", " + dateText(now) + " · " + clock(now)
fun duration(sec: Long): String { val s = sec.coerceAtLeast(0); return "%d:%02d:%02d".format(s / 3600, s % 3600 / 60, s % 60) }

fun plural(n: Int, one: String, few: String, many: String): String {
    val a = n % 10
    val b = n % 100
    return "$n " + if (a == 1 && b != 11) one else if (a in 2..4 && (b < 12 || b > 14)) few else many
}
fun chCount(n: Int): String = plural(n, "канал", "канала", "каналов")

/** Длинная ссылка обрезается по середине: начало и имя файла остаются видны. */
fun midCut(s: String, max: Int): String {
    if (s.length <= max) return s
    val tail = (max * 0.4).toInt()
    return s.take(max - tail - 1) + "…" + s.takeLast(tail)
}
