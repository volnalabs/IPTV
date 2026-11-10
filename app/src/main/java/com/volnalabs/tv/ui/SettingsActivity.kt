package com.volnalabs.tv.ui

import android.app.Activity
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
    private lateinit var qr: ImageView
    private lateinit var remoteUrl: TextView

    private val server = RemoteInputServer(
        currentPlaylist = { Repo.prefs.playlistUrl },
        currentEpg = { Repo.prefs.epgUrl },
        onSubmit = { playlist, epg ->
            runOnUiThread {
                playlistUrl.setText(playlist)
                epgUrl.setText(epg)
                save(closeAfter = true)
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
        qr = findViewById(R.id.qr)
        remoteUrl = findViewById(R.id.remoteUrl)

        playlistUrl.setText(Repo.prefs.playlistUrl)
        epgUrl.setText(Repo.prefs.epgUrl)
        findViewById<TextView>(R.id.version).text = "Volna TV ${BuildConfig.VERSION_NAME}"

        findViewById<Button>(R.id.save).setOnClickListener { save(closeAfter = true) }
        findViewById<Button>(R.id.refresh).setOnClickListener {
            if (Repo.prefs.playlistUrl.isEmpty()) save(closeAfter = false)
            else Repo.refreshAsync(forceEpg = true)
        }
        findViewById<Button>(R.id.back).setOnClickListener { finish() }
        epgUrl.setOnEditorActionListener { _, action, _ ->
            if (action == EditorInfo.IME_ACTION_DONE) { findViewById<View>(R.id.save).requestFocus(); false } else false
        }

        scope.launch { Repo.playlistState.collect { show(playlistStatus, "Плейлист", it) } }
        scope.launch { Repo.epgState.collect { show(epgStatus, "Телепрограмма", it) } }

        // Без плейлиста сразу ставим фокус на поле ввода, иначе — на кнопку «К каналам».
        if (Repo.prefs.playlistUrl.isEmpty()) playlistUrl.requestFocus()
        else findViewById<View>(R.id.back).requestFocus()
    }

    override fun onResume() {
        super.onResume()
        val ip = RemoteInputServer.localIp()
        if (ip != null && server.start()) {
            val url = "http://$ip:${server.port}"
            remoteUrl.text = url
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

    private fun save(closeAfter: Boolean) {
        val url = playlistUrl.text.toString().trim()
        if (!url.startsWith("http://", true) && !url.startsWith("https://", true)) {
            playlistStatus.text = "Ссылка должна начинаться с http:// или https://"
            playlistStatus.setTextColor(ContextCompat.getColor(this, R.color.live))
            playlistUrl.requestFocus()
            return
        }
        Repo.setSources(url, epgUrl.text.toString())
        if (closeAfter) finish()
    }

    private fun show(view: TextView, label: String, state: LoadState) {
        val (text, color) = when (state) {
            LoadState.Idle -> "$label: не задан" to R.color.text3
            is LoadState.Loading -> "$label: ${state.message}" to R.color.text2
            is LoadState.Ready -> "$label: ${state.message}" to R.color.archive
            is LoadState.Error -> "$label: ${state.message}" to R.color.live
        }
        view.text = text
        view.setTextColor(ContextCompat.getColor(this, color))
    }
}
