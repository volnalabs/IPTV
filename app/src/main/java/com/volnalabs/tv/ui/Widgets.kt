package com.volnalabs.tv.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.os.SystemClock
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.min
import kotlin.math.sin

/**
 * Список для пульта: выбранная строка сразу встаёт ближе к центру, без плавной докрутки.
 * Так фокус никогда не уезжает за край, а при удержании кнопки список не «догоняет» пульт.
 */
class CenterLayoutManager(context: Context) : LinearLayoutManager(context) {
    override fun requestChildRectangleOnScreen(
        parent: RecyclerView, child: View, rect: Rect, immediate: Boolean, focusedChildVisible: Boolean,
    ): Boolean {
        val center = (parent.height - parent.paddingTop - parent.paddingBottom) / 2 + parent.paddingTop
        val dy = (child.top + child.height / 2) - center
        if (dy != 0) parent.scrollBy(0, dy)
        return dy != 0
    }
}

/** Буквы вместо логотипа: первые буквы слов названия («Россия К» → «РК»), не больше трёх. */
fun initials(name: String): String {
    val words = name.split(Regex("[^0-9A-Za-zА-Яа-яЁё]+")).filter { it.isNotEmpty() }
    var out = ""
    for (w in words) {
        if (out.length >= 3) break
        out += if (w.all { it.isDigit() }) w.take(3 - out.length) else w.take(1)
    }
    if (out.length == 1 && words.isNotEmpty() && words[0].length > 1 && !words[0][0].isDigit()) out = words[0].take(2)
    return out.ifEmpty { "•" }.uppercase()
}

/** Заглушка логотипа: буквы названия на нейтральной плитке. Цветных плашек нет. */
class LetterDrawable(name: String, private val density: Float, private val tile: Int = 0xFF1F1F22.toInt()) : Drawable() {
    private val letters = initials(name)
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = tile }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFF2F0EC.toInt()
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    private val rect = RectF()

    /** Шрифт интерфейса (Onest) — задаёт тот, кто создаёт заглушку. */
    fun typeface(face: Typeface?): LetterDrawable { if (face != null) text.typeface = face; return this }

    override fun draw(canvas: Canvas) {
        val b = bounds
        rect.set(b)
        val radius = min(b.width(), b.height()) * 0.14f
        canvas.drawRoundRect(rect, radius, radius, fill)
        text.textSize = min(b.height() * 0.42f, b.width() * 0.9f / letters.length.coerceAtLeast(2) * 1.4f)
        val y = rect.centerY() - (text.descent() + text.ascent()) / 2
        canvas.drawText(letters, rect.centerX(), y, text)
    }

    override fun getIntrinsicWidth() = (56 * density).toInt()
    override fun getIntrinsicHeight() = (42 * density).toInt()
    override fun setAlpha(alpha: Int) { fill.alpha = alpha; text.alpha = alpha }
    override fun setColorFilter(colorFilter: ColorFilter?) { fill.colorFilter = colorFilter }
    @Deprecated("Deprecated in Java")
    override fun getOpacity() = PixelFormat.TRANSLUCENT
}

/** Базовый класс для бесконечных анимаций: крутится, только пока вид на экране. */
abstract class AnimatedView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    protected val dp = resources.displayMetrics.density
    protected val startedAt = SystemClock.uptimeMillis()
    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 1000
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener { invalidate() }
    }

    /** Секунды с момента создания — от них считаются все фазы. */
    protected val time: Float get() = (SystemClock.uptimeMillis() - startedAt) / 1000f

    private fun sync() {
        val run = isAttachedToWindow && isShown
        if (run && !animator.isStarted) animator.start()
        if (!run && animator.isStarted) animator.cancel()
    }

    override fun onAttachedToWindow() { super.onAttachedToWindow(); sync() }
    override fun onDetachedFromWindow() { animator.cancel(); super.onDetachedFromWindow() }
    override fun onVisibilityChanged(changedView: View, visibility: Int) { super.onVisibilityChanged(changedView, visibility); sync() }
}

/** Индикатор буферизации: тонкая дуга, которая плавно бежит по кругу. */
class LoadingView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : AnimatedView(context, attrs) {
    private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 4 * dp; color = 0x1FF2F0EC
    }
    private val arc = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 4 * dp; strokeCap = Paint.Cap.ROUND; color = 0xFFB8B5AF.toInt()
    }
    private val oval = RectF()

    override fun onDraw(canvas: Canvas) {
        val pad = 4 * dp
        oval.set(pad, pad, width - pad, height - pad)
        canvas.drawOval(oval, track)
        val t = time
        val sweep = 70f + 50f * sin(t * 2.2f)
        canvas.drawArc(oval, (t * 300f) % 360f, sweep, false, arc)
    }
}

/**
 * Заставка при запуске: значок появляется с лёгким увеличением, под ним название
 * и три точки, которые по очереди «дышат», пока загружаются каналы.
 */
class SplashView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : AnimatedView(context, attrs) {
    private val mark = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG)
    private val play = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF141415.toInt() }
    private val title = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textSize = 30 * resources.displayMetrics.scaledDensity
        letterSpacing = 0.32f
    }
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFB8B5AF.toInt() }
    private val rect = RectF()
    private val triangle = Path()

    /** Подпись под точками (например, «Загружаю каналы…»). */
    var caption: String = ""
        set(value) { field = value; invalidate() }
    private val captionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF9A978F.toInt()
        textAlign = Paint.Align.CENTER
        textSize = 14 * resources.displayMetrics.scaledDensity
    }

    override fun onDraw(canvas: Canvas) {
        val t = time
        val cx = width / 2f
        val cy = height / 2f - 36 * dp

        // Появление: 0 → 1 за 0,6 с с мягким торможением
        val intro = (t / 0.6f).coerceIn(0f, 1f).let { 1 - (1 - it) * (1 - it) * (1 - it) }
        val size = 104 * dp * (0.86f + 0.14f * intro)
        val alpha = (255 * intro).toInt()

        // Мягкое свечение, которое медленно «дышит»
        val breathe = 0.5f + 0.5f * sin(t * 1.6f)
        glow.shader = android.graphics.RadialGradient(
            cx, cy, size * (1.25f + 0.15f * breathe),
            intArrayOf(Color.argb((46 * intro * (0.6f + 0.4f * breathe)).toInt(), 239, 234, 224), Color.TRANSPARENT),
            null, Shader.TileMode.CLAMP,
        )
        canvas.drawCircle(cx, cy, size * 1.4f, glow)

        // Значок: скруглённый квадрат с градиентом и треугольником «play»
        rect.set(cx - size / 2, cy - size / 2, cx + size / 2, cy + size / 2)
        mark.shader = LinearGradient(rect.left, rect.top, rect.right, rect.bottom, 0xFFEFEAE0.toInt(), 0xFFDDD7CB.toInt(), Shader.TileMode.CLAMP)
        mark.alpha = alpha
        canvas.drawRoundRect(rect, size * 0.28f, size * 0.28f, mark)
        val r = size * 0.2f
        triangle.reset()
        triangle.moveTo(cx - r * 0.72f, cy - r)
        triangle.lineTo(cx + r * 1.02f, cy)
        triangle.lineTo(cx - r * 0.72f, cy + r)
        triangle.close()
        play.alpha = alpha
        play.pathEffect = android.graphics.CornerPathEffect(size * 0.045f)
        canvas.drawPath(triangle, play)

        // Название
        val textIntro = ((t - 0.25f) / 0.5f).coerceIn(0f, 1f)
        title.alpha = (255 * textIntro).toInt()
        val ty = rect.bottom + 54 * dp + (1 - textIntro) * 10 * dp
        // letterSpacing добавляет отступ после последней буквы — сдвигаем, чтобы стояло ровно по центру
        canvas.drawText("IPTV", cx + title.textSize * 0.16f, ty, title)

        // Три точки
        val dotsY = ty + 40 * dp
        val gap = 18 * dp
        for (i in 0..2) {
            val phase = 0.5f + 0.5f * sin(t * 5f - i * 0.9f)
            dot.alpha = ((70 + 185 * phase) * textIntro).toInt()
            canvas.drawCircle(cx + (i - 1) * gap, dotsY, (3.2f + 1.6f * phase) * dp, dot)
        }

        if (caption.isNotEmpty()) {
            captionPaint.alpha = (255 * textIntro).toInt()
            canvas.drawText(caption, cx, dotsY + 44 * dp, captionPaint)
        }
    }
}

/** Переключатель-ползунок. Сам не фокусируется — им управляет строка настроек, в которой он стоит. */
class ToggleView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    private val dp = resources.displayMetrics.density
    private val track = Paint(Paint.ANTI_ALIAS_FLAG)
    private val thumb = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val rect = RectF()
    private var position = 0f
    private var animator: ValueAnimator? = null

    var isChecked: Boolean = false
        private set

    fun setChecked(checked: Boolean, animate: Boolean) {
        isChecked = checked
        val target = if (checked) 1f else 0f
        animator?.cancel()
        if (!animate) { position = target; invalidate(); return }
        animator = ValueAnimator.ofFloat(position, target).apply {
            duration = 160
            addUpdateListener { position = it.animatedValue as Float; invalidate() }
            start()
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension((48 * dp).toInt(), (28 * dp).toInt())
    }

    override fun onDraw(canvas: Canvas) {
        rect.set(0f, 0f, width.toFloat(), height.toFloat())
        track.color = blend(0xFF77767C.toInt(), 0xFFB8B5AF.toInt(), position)
        canvas.drawRoundRect(rect, height / 2f, height / 2f, track)
        val r = height / 2f - 3 * dp
        val cx = height / 2f + (width - height) * position
        canvas.drawCircle(cx, height / 2f, r, thumb)
    }

    private fun blend(a: Int, b: Int, t: Float): Int = Color.rgb(
        (Color.red(a) + (Color.red(b) - Color.red(a)) * t).toInt(),
        (Color.green(a) + (Color.green(b) - Color.green(a)) * t).toInt(),
        (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t).toInt(),
    )
}
