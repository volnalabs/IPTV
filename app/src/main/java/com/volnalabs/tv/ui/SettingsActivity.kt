package com.volnalabs.tv.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.volnalabs.tv.BuildConfig
import com.volnalabs.tv.R
import com.volnalabs.tv.data.LoadState
import com.volnalabs.tv.data.Repo
import com.volnalabs.tv.data.Updater
import com.volnalabs.tv.remote.RemoteInputServer
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class SettingsActivity : Activity() {

    private val scope = MainScope()
    private lateinit var playlistUrl: EditText
    private lateinit var epgUrl: EditText
    private lateinit var playlistStatus: TextView
    private lateinit var epgStatus: TextView
    private lateinit var updateStatus: TextView
    private lateinit var autoUpdate: ToggleView
    private lateinit var qr: ImageView
    private lateinit var remoteUrl: TextView

    /** Пользователь сам нажал «Проверить» — результат показываем сразу, не дожидаясь главного экрана. */
    private var manualCheck = false

    private val server = RemoteInputServer(
        currentPlaylist = { Repo.prefs.playlistUrl },
        currentEpg = { Repo.prefs.epgUrl },
        onSubmit = { playlist, epg ->
            runOnUiThread {
                playlistUrl.setText(playlist)
                epgUrl.setText(epg)
                save()
            }
        },
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        playlistUrl = findViewById(R.id.playlistUrl)
        epgUrl = findViewById(R.id.epgUrl)
        playlistStatus = findViewById(R.id.playlistStatus)
        epgStatus = findViewById(R.id.epgStatus)
        updateStatus = findViewById(R.id.updateStatus)
        autoUpdate = findViewById(R.id.autoUpdateToggle)
        qr = findViewById(R.id.qr)
        remoteUrl = findViewById(R.id.remoteUrl)

        playlistUrl.setText(Repo.prefs.playlistUrl)
        epgUrl.setText(Repo.prefs.epgUrl)

        findViewById<Button>(R.id.save).setOnClickListener { save() }
        findViewById<Button>(R.id.refresh).setOnClickListener {
            if (Repo.prefs.playlistUrl.isEmpty()) save() else Repo.refreshAsync(forceEpg = true)
        }
        epgUrl.setOnEditorActionListener { _, action, _ ->
            if (action == EditorInfo.IME_ACTION_DONE) findViewById<View>(R.id.save).requestFocus()
            false
        }

        autoUpdate.setChecked(Repo.prefs.autoUpdate, animate = false)
        findViewById<View>(R.id.autoUpdateRow).setOnClickListener {
            val on = !autoUpdate.isChecked
            Repo.prefs.autoUpdate = on
            autoUpdate.setChecked(on, animate = true)
            if (on) Updater.autoCheck(this)
        }
        bindToggle(R.id.volumeRow, R.id.volumeToggle, Repo.prefs.rememberVolume) { Repo.prefs.rememberVolume = it }
        bindToggle(R.id.fillRow, R.id.fillToggle, Repo.prefs.fillScreen) { Repo.prefs.fillScreen = it }
        findViewById<View>(R.id.checkUpdate).setOnClickListener {
            val ready = Updater.state.value as? Updater.State.Ready
            if (ready != null) startActivity(Intent(this, UpdateActivity::class.java))
            else { manualCheck = true; Updater.check(this) }
        }

        scope.launch { Repo.playlistState.collect { show(playlistStatus, it, idle = "Плейлист пока не добавлен") } }
        scope.launch { Repo.epgState.collect { show(epgStatus, it, idle = "") } }
        scope.launch { Updater.state.collect { showUpdate(it) } }

        // Без плейлиста сразу ставим фокус на поле ввода, иначе — на «Сохранить».
        if (Repo.prefs.playlistUrl.isEmpty()) playlistUrl.requestFocus()
        else findViewById<View>(R.id.save).let { save -> save.post { save.requestFocusFromTouch() } }
    }

    override fun onResume() {
        super.onResume()
        val ip = RemoteInputServer.localIp()
        if (ip != null && server.start()) {
            val url = "http://$ip:${server.port}"
            remoteUrl.text = url.removePrefix("http://")
            qr.setImageBitmap(RemoteInputServer.qr(url, 400))
        } else {
            remoteUrl.text = "Нет подключения к сети"
            qr.setImageDrawable(null)
        }
    }

    override fun onPause() {
        server.stop()
        super.onPause()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun bindToggle(rowId: Int, toggleId: Int, initial: Boolean, onChange: (Boolean) -> Unit) {
        val toggle = findViewById<ToggleView>(toggleId)
        toggle.setChecked(initial, animate = false)
        findViewById<View>(rowId).setOnClickListener {
            val on = !toggle.isChecked
            toggle.setChecked(on, animate = true)
            onChange(on)
        }
    }

    private fun save() {
        val url = playlistUrl.text.toString().trim()
        if (!url.startsWith("http://", true) && !url.startsWith("https://", true)) {
            playlistStatus.text = "Ссылка должна начинаться с http:// или https://"
            playlistStatus.setTextColor(ContextCompat.getColor(this, R.color.live))
            playlistUrl.requestFocus()
            return
        }
        Repo.setSources(url, epgUrl.text.toString())
        finish()
    }

    private fun show(view: TextView, state: LoadState, idle: String) {
        val (text, color) = when (state) {
            LoadState.Idle -> idle to R.color.text3
            is LoadState.Loading -> state.message to R.color.text2
            is LoadState.Ready -> state.message to R.color.archive
            is LoadState.Error -> state.message to R.color.live
        }
        view.text = text
        view.visibility = if (text.isEmpty()) View.GONE else View.VISIBLE
        view.setTextColor(ContextCompat.getColor(this, color))
    }

    private fun showUpdate(state: Updater.State) {
        val version = "Версия ${BuildConfig.VERSION_NAME}"
        val (text, color) = when (state) {
            Updater.State.Idle -> version to R.color.text2
            Updater.State.Checking -> "Проверяю…" to R.color.text2
            is Updater.State.UpToDate -> "$version · новее нет" to R.color.archive
            is Updater.State.Downloading -> "Скачиваю ${state.version}… ${state.percent}%" to R.color.text2
            is Updater.State.Ready -> "Готова ${state.version} — нажмите ОК" to R.color.archive
            is Updater.State.Error -> "Не получилось проверить" to R.color.live
        }
        updateStatus.text = text
        updateStatus.setTextColor(ContextCompat.getColor(this, color))
        if (state is Updater.State.Ready && manualCheck) {
            manualCheck = false
            startActivity(Intent(this, UpdateActivity::class.java))
        }
    }
}
