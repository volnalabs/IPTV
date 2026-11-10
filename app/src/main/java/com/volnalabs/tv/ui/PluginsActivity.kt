package com.volnalabs.tv.ui

import android.app.Activity
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.volnalabs.tv.R
import com.volnalabs.tv.data.Plugins
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** Экран «Плагины»: добавить по ссылке, включить, выключить, удалить. */
class PluginsActivity : Activity() {

    private val scope = MainScope()
    private lateinit var url: EditText
    private lateinit var status: TextView
    private lateinit var list: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyOrientation()
        setContentView(R.layout.activity_plugins)
        url = findViewById(R.id.pluginUrl)
        status = findViewById(R.id.pluginStatus)
        list = findViewById(R.id.pluginList)

        if (!isTv()) {
            // На телефоне поля уже, а в портретной ориентации кнопка уходит под поле
            val dp = resources.displayMetrics.density
            val pad = (16 * dp).toInt()
            findViewById<View>(R.id.pluginsRoot).setPadding(pad, pad, pad, pad)
            if (isPortrait()) {
                val row = findViewById<LinearLayout>(R.id.addRow)
                row.orientation = LinearLayout.VERTICAL
                url.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (50 * dp).toInt())
                findViewById<View>(R.id.pluginAdd).layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, (46 * dp).toInt()).apply { topMargin = (10 * dp).toInt() }
            }
        }

        findViewById<Button>(R.id.pluginAdd).setOnClickListener { add() }
        url.setOnEditorActionListener { _, action, _ -> if (action == EditorInfo.IME_ACTION_DONE) add(); false }
        scope.launch { Plugins.items.collect { render(it) } }
        say(if (isTv()) "Ссылку удобнее прислать с телефона: Настройки → «С телефона»." else "", error = false)
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun say(text: String, error: Boolean) {
        status.text = text
        status.visibility = if (text.isEmpty()) View.GONE else View.VISIBLE
        status.setTextColor(ContextCompat.getColor(this, if (error) R.color.live else R.color.text2))
    }

    private fun add() {
        val link = url.text.toString().trim()
        if (link.isEmpty()) { say("Вставьте ссылку на файл плагина", error = true); return }
        say("Загружаю плагин…", error = false)
        scope.launch {
            val error = Plugins.add(link)
            if (error != null) say(error, error = true)
            else { url.setText(""); say("Плагин добавлен и уже работает.", error = false) }
        }
    }

    private fun render(items: List<Plugins.Item>) {
        list.removeAllViews()
        list.visibility = if (items.isEmpty()) View.GONE else View.VISIBLE
        items.forEachIndexed { index, p ->
            val row = LayoutInflater.from(this).inflate(R.layout.item_plugin, list, false)
            row.findViewById<TextView>(R.id.name).text = p.name.ifEmpty { p.id.ifEmpty { p.url } } + if (p.version.isNotEmpty()) "  ·  ${p.version}" else ""
            row.findViewById<TextView>(R.id.desc).text = p.description.ifEmpty { p.url }
            row.findViewById<ToggleView>(R.id.toggle).setChecked(p.enabled, animate = false)
            row.alpha = if (p.enabled) 1f else 0.6f
            row.setOnClickListener { scope.launch { Plugins.setEnabled(index, !p.enabled) } }
            row.setOnLongClickListener {
                scope.launch { Plugins.remove(index); say("Плагин «${p.name.ifEmpty { p.url }}» удалён.", error = false) }
                true
            }
            list.addView(row)
        }
        if (items.isNotEmpty() && status.text.isEmpty()) say("Нажатие — включить или выключить. Долгое нажатие — удалить.", error = false)
    }
}
