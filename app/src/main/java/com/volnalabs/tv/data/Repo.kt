package com.volnalabs.tv.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

sealed class LoadState {
    object Idle : LoadState()
    data class Loading(val message: String) : LoadState()
    data class Ready(val message: String) : LoadState()
    data class Error(val message: String) : LoadState()
}

/**
 * Единое хранилище плейлиста и телепрограммы.
 * Всё кэшируется на диск, поэтому приложение стартует мгновенно даже без сети,
 * а свежие данные подтягиваются в фоне.
 */
object Repo {
    private const val TAG = "IptvRepo"
    private const val PLAYLIST_TTL = 6 * 60 * 60 * 1000L
    private const val EPG_TTL = 12 * 60 * 60 * 1000L

    lateinit var prefs: Prefs
        private set
    private lateinit var dir: File

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val playlistMutex = Mutex()
    private val epgMutex = Mutex()
    private var epgJob: Job? = null

    private val _playlist = MutableStateFlow(Playlist.EMPTY)
    val playlist: StateFlow<Playlist> = _playlist

    private val _epg = MutableStateFlow(EpgData.EMPTY)
    val epg: StateFlow<EpgData> = _epg

    private val _playlistState = MutableStateFlow<LoadState>(LoadState.Idle)
    val playlistState: StateFlow<LoadState> = _playlistState

    private val _epgState = MutableStateFlow<LoadState>(LoadState.Idle)
    val epgState: StateFlow<LoadState> = _epgState

    private val playlistFile get() = File(dir, "playlist.m3u")
    private val epgFile get() = File(dir, "epg.bin.gz")

    fun init(context: Context) {
        prefs = Prefs(context)
        dir = context.filesDir
        scope.launch {
            loadCachedPlaylist()
            loadCachedEpg()
            refreshPlaylist(force = false)
        }
    }

    // ---------- плейлист ----------

    private fun loadCachedPlaylist() {
        val f = playlistFile
        if (!f.exists() || prefs.playlistUrl.isEmpty()) return
        runCatching { M3uParser.parse(f.readText()) }
            .onSuccess {
                _playlist.value = it
                _playlistState.value = LoadState.Ready("Каналов: ${it.channels.size}")
            }
            .onFailure { Log.w(TAG, "cached playlist", it) }
    }

    fun setSources(playlistUrl: String, epgUrl: String) {
        val playlistChanged = playlistUrl.trim() != prefs.playlistUrl
        val epgChanged = epgUrl.trim() != prefs.epgUrl
        prefs.playlistUrl = playlistUrl
        prefs.epgUrl = epgUrl
        if (playlistChanged) {
            _playlist.value = Playlist.EMPTY
            _epg.value = EpgData.EMPTY
            playlistFile.delete()
            epgFile.delete()
        }
        if (epgChanged && !playlistChanged) epgFile.delete()
        scope.launch {
            refreshPlaylist(force = true)
            if (epgChanged && !playlistChanged) refreshEpg(force = true)
        }
    }

    fun refreshAsync(forceEpg: Boolean = false) {
        scope.launch {
            refreshPlaylist(force = true)
            if (forceEpg) refreshEpg(force = true)
        }
    }

    suspend fun refreshPlaylist(force: Boolean) = playlistMutex.withLock {
        val url = prefs.playlistUrl
        if (url.isEmpty()) {
            _playlistState.value = LoadState.Idle
            return@withLock
        }
        val fresh = System.currentTimeMillis() - prefs.playlistUpdated < PLAYLIST_TTL
        if (!force && fresh && _playlist.value.channels.isNotEmpty()) {
            refreshEpg(force = false)
            return@withLock
        }
        _playlistState.value = LoadState.Loading("Загружаю каналы…")
        try {
            val text = Http.readText(url)
            val parsed = M3uParser.parse(text)
            if (parsed.channels.isEmpty()) {
                throw IllegalStateException("В плейлисте нет каналов. Проверьте ссылку.")
            }
            playlistFile.writeText(text)
            prefs.playlistUpdated = System.currentTimeMillis()
            _playlist.value = parsed
            _playlistState.value = LoadState.Ready("Каналов: ${parsed.channels.size}")
        } catch (e: Exception) {
            Log.w(TAG, "playlist", e)
            val cached = _playlist.value.channels.size
            _playlistState.value = LoadState.Error(
                "Не удалось загрузить плейлист: ${e.message ?: e.javaClass.simpleName}." +
                    if (cached > 0) " Показываю сохранённый список." else " Проверьте ссылку и интернет."
            )
        }
        refreshEpg(force = false)
    }

    // ---------- телепрограмма ----------

    private fun loadCachedEpg() {
        val f = epgFile
        if (!f.exists()) return
        runCatching {
            DataInputStream(BufferedInputStream(GZIPInputStream(f.inputStream()), 256 * 1024)).use { EpgData.read(it) }
        }.onSuccess {
            if (it.byId.isNotEmpty()) {
                _epg.value = it
                _epgState.value = LoadState.Ready(describe(it))
            }
        }.onFailure { Log.w(TAG, "cached epg", it) }
    }

    fun epgSourceUrls(): List<String> {
        val own = prefs.epgUrl
        return if (own.isNotEmpty()) listOf(own) else _playlist.value.epgUrls
    }

    fun refreshEpg(force: Boolean) {
        val playlist = _playlist.value
        if (playlist.channels.isEmpty()) return
        val fresh = System.currentTimeMillis() - _epg.value.updated < EPG_TTL
        if (!force && fresh && _epg.value.byId.isNotEmpty()) return
        if (epgJob?.isActive == true) return

        epgJob = scope.launch {
            epgMutex.withLock {
                val urls = epgSourceUrls()
                if (urls.isEmpty()) {
                    _epgState.value = LoadState.Error("В плейлисте нет ссылки на телепрограмму — её можно указать вручную.")
                    return@withLock
                }
                var lastError: Exception? = null
                for (url in urls) {
                    try {
                        _epgState.value = LoadState.Loading("Загружаю телепрограмму…")
                        val data = Http.open(url, readTimeoutMs = 120_000).use { input ->
                            EpgParser.parse(input, playlist) { n ->
                                _epgState.value = LoadState.Loading("Загружаю программу… $n")
                            }
                        }
                        if (data.byId.isEmpty()) throw IllegalStateException("в файле нет передач для каналов плейлиста")
                        DataOutputStream(BufferedOutputStream(GZIPOutputStream(epgFile.outputStream()), 256 * 1024)).use { data.write(it) }
                        _epg.value = data
                        _epgState.value = LoadState.Ready(describe(data))
                        return@withLock
                    } catch (e: Exception) {
                        Log.w(TAG, "epg $url", e)
                        lastError = e
                    }
                }
                _epgState.value = LoadState.Error(
                    "Не удалось загрузить телепрограмму: ${lastError?.message ?: "ошибка"}." +
                        if (_epg.value.byId.isNotEmpty()) " Показываю сохранённую." else ""
                )
            }
        }
    }

    private fun describe(d: EpgData): String {
        val time = java.text.SimpleDateFormat("dd.MM.yy, HH:mm", java.util.Locale("ru")).format(java.util.Date(d.updated))
        return "Программа: $time"
    }
}
