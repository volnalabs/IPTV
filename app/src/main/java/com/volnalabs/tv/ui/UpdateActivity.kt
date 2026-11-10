package com.volnalabs.tv.ui

import android.app.Activity
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import com.volnalabs.tv.BuildConfig
import com.volnalabs.tv.R
import com.volnalabs.tv.data.Updater

/** Окно «Доступно обновление»: новая версия уже скачана, остаётся подтвердить установку. */
class UpdateActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val ready = Updater.state.value as? Updater.State.Ready
        if (ready == null) { finish(); return }
        setContentView(R.layout.activity_update)

        findViewById<TextView>(R.id.updateTitle).text = "Вышла версия ${ready.version}"
        findViewById<TextView>(R.id.updateText).text =
            "Она уже скачана. Установка займёт несколько секунд, каналы и настройки сохранятся.\n" +
                "Сейчас установлена версия ${BuildConfig.VERSION_NAME}."

        findViewById<Button>(R.id.install).apply {
            setOnClickListener {
                if (Updater.install(this@UpdateActivity, ready.file)) finish()
                else findViewById<TextView>(R.id.updateText).text =
                    "Не получилось открыть установщик. Разрешите приложению IPTV установку из неизвестных источников в настройках телевизора и попробуйте ещё раз."
            }
            post { requestFocusFromTouch() }
        }
        findViewById<View>(R.id.later).setOnClickListener {
            Updater.postponed = ready.version
            finish()
        }
    }
}
