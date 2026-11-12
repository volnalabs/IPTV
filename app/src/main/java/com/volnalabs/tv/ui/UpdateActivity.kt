package com.volnalabs.tv.ui

import android.os.Bundle
import android.view.KeyEvent
import com.volnalabs.tv.BuildConfig
import com.volnalabs.tv.data.Updater

/** Диалог «Вышла версия…» (SPEC 3.9, D3): новая версия уже скачана, остаётся подтвердить установку. */
class UpdateActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val ready = Updater.state.value as? Updater.State.Ready
        if (ready == null) { finish(); return }
        setupRoot(0)
        setupOverlays()
        show(ready)
    }

    private fun show(ready: Updater.State.Ready) {
        openDialog("Вышла версия ${ready.version}",
            "Сейчас установлена версия ${BuildConfig.VERSION_NAME}. Обновление установится само, это займёт несколько секунд.",
            listOf(DialogButton("Обновить") { install(ready) }, DialogButton("Позже") { later(ready) }), onBack = { later(ready) })
    }

    private fun install(ready: Updater.State.Ready) {
        if (Updater.install(this, ready.file)) { finish(); return }
        openDialog("Не удалось обновиться", "Не получилось открыть установщик. Разрешите приложению IPTV установку из неизвестных источников в настройках Android.",
            listOf(DialogButton("Повторить") { install(ready) }, DialogButton("Отмена") { later(ready) }), onBack = { later(ready) })
    }

    private fun later(ready: Updater.State.Ready) {
        Updater.postponed = ready.version
        finish()
    }

    override fun onOk() {}
    override fun onBack() = finish()
    override fun onKey(key: Key, event: KeyEvent) {}
}
