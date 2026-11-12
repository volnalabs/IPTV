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
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.net.SocketTimeoutException
import java.net.URI
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

sealed class LoadState {
    object Idle : LoadState()
    data class Loading(val message: String) : LoadState()
    data class Ready(val message: String) : LoadState()
    data class Error(val message: String) : LoadState()
}

/**
 * Данные приложения: список плейлистов, каналы активного плейлиста и телепрограмма.
 * Плейлистов может быть несколько; каналы берутся из активного. Новая ссылка заменяет список только
 * после того, как по ней загрузился и разобрался плейлист, — при ошибке работает прежний список.
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
    private val _playlists = MutableStateFlow<List<PlaylistInfo>>(emptyList())
    /** Все плейлисты в порядке добавления. */
    val playlists: StateFlow<List<PlaylistInfo>> = _playlists

    val activeId: String get() = prefs.activePlaylist
    val active: PlaylistInfo? get() = _playlists.value.firstOrNull { it.id == prefs.activePlaylist }

    private val playlistFile get() = File(dir, "playlist.m3u")
    private val epgFile get() = File(dir, "epg.bin.gz")

    fun init(context: Context) {
        prefs = Prefs(context)
        dir = context.filesDir
        source = Playlist.EMPTY
        _playlist.value = Playlist.EMPTY
        _epg.value = EpgData.EMPTY
        _playlistState.value = LoadState.Idle
        _epgState.value = LoadState.Idle
        migrate()
        Plugins.init(context)
        scope.launch {
            loadCachedPlaylist()
            loadCachedEpg()
            refreshPlaylist(force = false)
            Plugins.refreshCode()
        }
    }

    /** Первая версия с несколькими плейлистами: прежняя ссылка становится плейлистом «Мой плейлист». */
    private fun migrate() {
        var list = prefs.playlists
        if (list.isEmpty() && prefs.playlistUrl.isNotEmpty()) {
            list = listOf(PlaylistInfo("p1", "Мой плейлист", prefs.playlistUrl, prefs.epgUrl, prefs.playlistUpdated))
            prefs.playlists = list
            prefs.activePlaylist = "p1"
        }
        if (list.isNotEmpty() && list.none { it.id == prefs.activePlaylist }) prefs.activePlaylist = list[0].id
        _playlists.value = list
        syncActive()
    }

    /** Старые ключи держим равными активному плейлисту: откат на прежнюю версию ничего не сломает. */
    private fun syncActive() {
        val a = active ?: return
        if (prefs.playlistUrl != a.url) prefs.playlistUrl = a.url
        if (prefs.epgUrl != a.epgUrl) prefs.epgUrl = a.epgUrl
    }

    private fun store(list: List<PlaylistInfo>) {
        prefs.playlists = list
        _playlists.value = list
        syncActive()
    }

    private fun update(id: String, change: (PlaylistInfo) -> PlaylistInfo) = store(_playlists.value.map { if (it.id == id) change(it) else it })

    private var source = Playlist.EMPTY

    /** После изменения плагинов пропускаем уже загруженный плейлист через них заново. */
    fun reapplyPlugins() {
        scope.launch {
            val s = source
            if (s.channels.isEmpty()) return@launch
            val p = Plugins.playlist(s)
            _playlist.value = p
            _playlistState.value = LoadState.Ready("Каналов: ${p.channels.size}")
        }
    }

    private suspend fun loadCachedPlaylist() {
        val f = playlistFile
        if (!f.exists() || active == null) return
        runCatching { M3uParser.parse(f.readText()).also { source = it }.let { Plugins.playlist(it) } }
            .onSuccess {
                _playlist.value = it
                _playlistState.value = LoadState.Ready("Каналов: ${it.channels.size}")
            }
            .onFailure { Log.w(TAG, "cached playlist", it) }
    }

    /** Текст причины для формы плейлиста и тостов (SPEC 5.3). */
    fun reason(e: Throwable): String = when {
        e is StatusException && e.code == 404 -> "Сервер ответил 404 — по этой ссылке плейлиста нет."
        e is StatusException && (e.code == 403 || e.code == 401) -> "Сервер не пускает по этой ссылке (ошибка ${e.code}). Проверьте ссылку у провайдера."
        e is StatusException -> "Сервер ответил ${e.code}."
        e is SocketTimeoutException -> "Сервер не отвечает."
        e is NoChannelsException -> "Ссылка открылась, но в файле нет ни одного канала."
        else -> "Нет связи с сервером."
    }

    class NoChannelsException : Exception()

    /** Загружает и разбирает плейлист по ссылке, ничего не сохраняя. */
    private fun fetch(url: String): Pair<String, Playlist> {
        val text = Http.readText(url)
        val parsed = M3uParser.parse(text)
        if (parsed.channels.isEmpty()) throw NoChannelsException()
        return text to parsed
    }

    private suspend fun apply(text: String, raw: Playlist) {
        source = raw
        playlistFile.writeText(text)
        prefs.playlistUpdated = System.currentTimeMillis()
        val p = Plugins.playlist(raw)
        _playlist.value = p
        _playlistState.value = LoadState.Ready("Каналов: ${p.channels.size}")
    }

    fun hostOf(url: String): String = runCatching { URI(url.trim()).host }.getOrNull().orEmpty()

    /**
     * Сохраняет плейлист (новый, если [id] пуст). Ссылка сначала загружается и разбирается; запись меняется
     * только при успехе. Возвращает текст ошибки или null. Первый плейлист сразу становится активным.
     */
    suspend fun savePlaylist(id: String?, name: String, url: String, epgUrl: String): String? = withContext(Dispatchers.IO) {
        val link = url.trim()
        if (!link.startsWith("http://", true) && !link.startsWith("https://", true)) return@withContext "Ссылка должна начинаться с http:// или https://"
        val old = _playlists.value.firstOrNull { it.id == id }
        val title = name.trim().take(40).ifEmpty { hostOf(link).ifEmpty { "Плейлист" } }
        var loaded: Pair<String, Playlist>? = null
        if (old == null || old.url != link) {
            loaded = try { fetch(link) } catch (e: Exception) { Log.w(TAG, "check", e); return@withContext reason(e) }
        }
        val newId = old?.id ?: generateSequence(1) { it + 1 }.map { "p$it" }.first { n -> _playlists.value.none { it.id == n } }
        val info = (old ?: PlaylistInfo(newId, title, link)).copy(name = title, url = link, epgUrl = epgUrl.trim()).let {
            if (loaded != null) it.copy(updated = System.currentTimeMillis(), count = loaded.second.channels.size, errorTime = 0, errorMessage = "") else it
        }
        val becomesActive = active == null
        if (becomesActive) prefs.activePlaylist = newId
        store(if (old == null) _playlists.value + info else _playlists.value.map { if (it.id == newId) info else it })
        if (info.id == prefs.activePlaylist) {
            val epgChanged = old != null && old.epgUrl != info.epgUrl
            if (loaded != null) playlistMutex.withLock {
                _epg.value = EpgData.EMPTY
                epgFile.delete()
                apply(loaded.first, loaded.second)
                refreshEpg(force = true)
            } else if (epgChanged) { epgFile.delete(); refreshEpg(force = true) }
        }
        null
    }

    /** Делает плейлист активным: список грузится по его ссылке и подменяется только после успеха. */
    suspend fun activate(id: String): String? = withContext(Dispatchers.IO) {
        val p = _playlists.value.firstOrNull { it.id == id } ?: return@withContext "Плейлист не найден"
        if (id == prefs.activePlaylist) return@withContext null
        val loaded = try { fetch(p.url) } catch (e: Exception) {
            val why = reason(e)
            update(id) { it.copy(errorTime = System.currentTimeMillis(), errorMessage = why.trimEnd('.')) }
            return@withContext why
        }
        playlistMutex.withLock {
            prefs.activePlaylist = id
            prefs.lastGroup = null
            update(id) { it.copy(updated = System.currentTimeMillis(), count = loaded.second.channels.size, errorTime = 0, errorMessage = "") }
            _epg.value = EpgData.EMPTY
            epgFile.delete()
            apply(loaded.first, loaded.second)
            refreshEpg(force = true)
        }
        null
    }

    /** Перепроверяет неактивный плейлист: обновляет число каналов или записывает ошибку. Возвращает текст ошибки или null. */
    suspend fun recheck(id: String): String? = withContext(Dispatchers.IO) {
        val p = _playlists.value.firstOrNull { it.id == id } ?: return@withContext null
        try {
            val n = fetch(p.url).second.channels.size
            update(id) { it.copy(updated = System.currentTimeMillis(), count = n, errorTime = 0, errorMessage = "") }
            null
        } catch (e: Exception) {
            val why = reason(e)
            update(id) { it.copy(errorTime = System.currentTimeMillis(), errorMessage = why.trimEnd('.')) }
            why
        }
    }

    /** Удаляет плейлист. Активный удалить нельзя. */
    fun deletePlaylist(id: String) {
        if (id == prefs.activePlaylist) return
        store(_playlists.value.filter { it.id != id })
    }

    /** «Обновить каналы»: активный плейлист и, по желанию, телепрограмма. */
    fun refreshAsync(forceEpg: Boolean = false) {
        scope.launch {
            refreshPlaylist(force = true)
            if (forceEpg) refreshEpg(force = true)
        }
    }

    suspend fun refreshPlaylist(force: Boolean) = playlistMutex.withLock {
        val a = active
        if (a == null) {
            _playlistState.value = LoadState.Idle
            return@withLock
        }
        val fresh = System.currentTimeMillis() - prefs.playlistUpdated < PLAYLIST_TTL
        if (!force && fresh && _playlist.value.channels.isNotEmpty()) {
            refreshEpg(force = false)
            return@withLock
        }
        _playlistState.value = LoadState.Loading("Обновляем список…")
        try {
            val (text, raw) = fetch(a.url)
            apply(text, raw)
            update(a.id) { it.copy(updated = System.currentTimeMillis(), count = raw.channels.size, errorTime = 0, errorMessage = "") }
        } catch (e: Exception) {
            Log.w(TAG, "playlist", e)
            val why = reason(e)
            update(a.id) { it.copy(errorTime = System.currentTimeMillis(), errorMessage = why.trimEnd('.')) }
            // Ошибка не прячет работающие данные: сохранённый список остаётся
            _playlistState.value = LoadState.Error(why)
        }
        refreshEpg(force = false)
    }

    private fun loadCachedEpg() {
        val f = epgFile
        if (!f.exists()) return
        runCatching {
            DataInputStream(BufferedInputStream(GZIPInputStream(f.inputStream()), 256 * 1024)).use { EpgData.read(it) }
        }.onSuccess {
            if (it.byId.isNotEmpty()) {
                _epg.value = it
                _epgState.value = LoadState.Ready("")
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
        if (epgJob?.isActive == true) { if (!force) return; epgJob?.cancel() }
        epgJob = scope.launch {
            epgMutex.withLock {
                val urls = epgSourceUrls()
                if (urls.isEmpty()) {
                    _epgState.value = LoadState.Error("В плейлисте нет ссылки на телепрограмму. Её можно указать в настройках плейлиста.")
                    return@withLock
                }
                var lastError: Exception? = null
                for (url in urls) {
                    try {
                        _epgState.value = LoadState.Loading("Загружаю программу…")
                        val data = Http.open(url, readTimeoutMs = 120_000).use { input ->
                            EpgParser.parse(input, playlist) { n ->
                                _epgState.value = LoadState.Loading("Загружаю программу… $n")
                            }
                        }
                        if (data.byId.isEmpty()) throw IllegalStateException("в файле нет передач для каналов плейлиста")
                        DataOutputStream(BufferedOutputStream(GZIPOutputStream(epgFile.outputStream()), 256 * 1024)).use { data.write(it) }
                        _epg.value = data
                        _epgState.value = LoadState.Ready("")
                        return@withLock
                    } catch (e: Exception) {
                        Log.w(TAG, "epg $url", e)
                        lastError = e
                    }
                }
                _epgState.value = LoadState.Error("Не удалось загрузить телепрограмму: ${lastError?.message ?: "ошибка"}.")
            }
        }
    }
}
