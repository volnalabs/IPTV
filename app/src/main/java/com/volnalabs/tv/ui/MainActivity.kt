package com.volnalabs.tv.ui

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.media.AudioManager
import android.os.Bundle
import android.os.SystemClock
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.volnalabs.tv.R
import com.volnalabs.tv.data.Catchup
import com.volnalabs.tv.data.Channel
import com.volnalabs.tv.data.EpgData
import com.volnalabs.tv.data.Http
import com.volnalabs.tv.data.LoadState
import com.volnalabs.tv.data.Playlist
import com.volnalabs.tv.data.Plugins
import com.volnalabs.tv.data.Programme
import com.volnalabs.tv.data.Repo
import com.volnalabs.tv.data.Updater
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** Пункт меню гида: раздел со списком каналов или действие (поиск, настройки). */
class MenuItem(val id: String, val title: String, val icon: Int, val list: List<Channel> = emptyList(), val action: Boolean = false, val isNew: String = "")

/** Передача в подробностях гида. arch — есть запись. */
class Prog(val start: Long, val stop: Long, val title: String, val desc: String, val arch: Boolean)

/** Результат поиска: канал, передача (эфир, будущая или запись), группа или плейлист. */
class Found(val type: String, val ch: Channel? = null, val start: Long = 0, val stop: Long = 0, val title: String = "", val rec: Boolean = false, val id: String = "")
class FoundSection(val name: String, val note: String, val from: Int, val count: Int, val isNew: String = "", val status: String = "")

/**
 * Главный экран: плеер — корень, всё остальное — слои поверх него (гид, мини-гид, сетка, поиск).
 * Спецификация: docs/design/handoff/SPEC.md. Зоны фокуса те же, что в web-версии (web/app.js):
 * play, ctl, grid, head, groups, channels, epg, date, search, results.
 */
@OptIn(UnstableApi::class)
class MainActivity : BaseActivity() {

    internal val scope = MainScope()
    internal lateinit var player: ExoPlayer
    internal lateinit var playerView: PlayerView
    internal lateinit var splash: SplashView
    internal var touchUi: TouchUi? = null
    /** Слои телевизора; на телефоне и планшете не создаются. */
    internal val t by lazy { TvLayers(this) }

    // ---------- состояние ----------
    internal var playlist: Playlist = Playlist.EMPTY
    internal var epg: EpgData = EpgData.EMPTY
    internal var epgLoading = false
    internal var epgStatus = ""
    /** Канал, который играет, и канал, выбранный ▲▼ (поток включится через 400 мс). */
    var cur: Channel? = null; internal set
    var sel: Channel? = null; internal set
    var arch: Programme? = null; internal set
    internal var prev: Channel? = null
    /** Список, по которому листаем ▲▼: раздел, из которого включён канал. */
    internal var playList: List<Channel> = emptyList()
    var zone = "play"; internal set
    internal var menu: List<MenuItem> = emptyList()
    var menuId = "all"; internal set
    internal var list: List<Channel> = emptyList()
    var gi = 0; internal set
    var ci = 0; internal set
    var pi = 0; internal set
    internal var epgCh: Channel? = null
    internal var prog: List<Prog> = emptyList()
    internal var day = 0L
    internal var days: List<Long> = emptyList()
    internal var paused = false
    /** Состояние плеера: null, loading, buffer, error, denied, noimage. */
    var state: String? = null; internal set
    internal var stateCode = 0
    internal var muted = false
    private var retries = 0
    private var forcedHls = false
    private var started = false
    private var trouble = false
    private var lastWarn = 0L
    private val buffers = ArrayList<Long>()
    private var lastBack = 0L
    private var visible = false
    internal var splashDone = false
    private val createdAt = SystemClock.uptimeMillis()
    private var playSeq = 0
    internal val audio by lazy { getSystemService(Context.AUDIO_SERVICE) as AudioManager }

    override val toastOnTop: Boolean get() = tv && (zone == "play" || zone == "ctl")

    // ---------- жизненный цикл ----------
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupRoot(0xFF000000.toInt())
        playerView = PlayerView(this).apply { useController = false; setShutterBackgroundColor(0xFF000000.toInt()); isFocusable = false }
        root.addView(playerView, FrameLayout.LayoutParams(MATCH, MATCH))
        setupPlayer()
        if (tv) t.build() else touchUi = TouchUi(this).also { it.build() }
        splash = SplashView(this)
        root.addView(splash.apply { setBackgroundColor(C.BG) }, FrameLayout.LayoutParams(MATCH, MATCH))
        setupOverlays()
        Plugins.toastSink = { toast(it) }

        scope.launch { Repo.playlist.collect { onPlaylist(it) } }
        scope.launch { Repo.epg.collect { onEpg(it) } }
        scope.launch { Repo.epgState.collect { onEpgState(it) } }
        scope.launch { Repo.playlistState.collect { onPlaylistState(it) } }
        scope.launch { Repo.playlists.collect { if (tv && inGuide()) t.renderGuide(); touchUi?.onPlaylists() } }
        scope.launch { Updater.state.collect { if (it is Updater.State.Ready && visible) offerUpdate(it) } }

        if (Repo.active == null) { hideSplash(immediate = true); openOnboarding() }
        // Заставка не должна висеть вечно, даже если канал так и не запустился.
        handler.postDelayed({ hideSplash() }, 9000)
        handler.post(ticker)
        Updater.autoCheck(this)
    }

    override fun onStart() {
        super.onStart()
        visible = true
        applyPlaybackPrefs()
        if (cur != null && !player.isPlaying) cur?.let { start(it) }
        (Updater.state.value as? Updater.State.Ready)?.let { offerUpdate(it) }
        if (tv) t.renderZone()
    }

    override fun onStop() {
        visible = false
        if (!audio.isVolumeFixed) Repo.prefs.volume = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
        player.stop()
        super.onStop()
    }

    override fun onDestroy() {
        Plugins.toastSink = null
        scope.cancel()
        player.release()
        super.onDestroy()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        touchUi?.layout()
    }

    /** Возвращаем громкость и формат картинки такими, какими они были при прошлом просмотре. */
    internal fun applyPlaybackPrefs() {
        val prefs = Repo.prefs
        playerView.resizeMode = if (prefs.fillScreen) AspectRatioFrameLayout.RESIZE_MODE_ZOOM else AspectRatioFrameLayout.RESIZE_MODE_FIT
        val saved = prefs.volume
        if (prefs.rememberVolume && saved >= 0 && !audio.isVolumeFixed) runCatching {
            audio.setStreamVolume(AudioManager.STREAM_MUSIC, saved.coerceAtMost(audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)), 0)
        }
    }

    private var offered: String? = null
    private fun offerUpdate(ready: Updater.State.Ready) {
        if (offered == ready.version || Updater.postponed == ready.version || !splashDone) return
        if (tv && zone != "play") return // не перебиваем выбор канала
        offered = ready.version
        startActivity(Intent(this, UpdateActivity::class.java))
    }

    internal fun openSettings(view: String = "") {
        startActivity(Intent(this, SettingsActivity::class.java).putExtra("view", view))
    }

    private fun openOnboarding() = startActivity(Intent(this, SettingsActivity::class.java).putExtra("view", "onb"))

    // ---------- заставка ----------
    internal fun hideSplash(immediate: Boolean = false) {
        if (splashDone) return
        val shown = SystemClock.uptimeMillis() - createdAt
        if (!immediate && shown < 1500) { handler.postDelayed({ hideSplash() }, 1500 - shown); return }
        splashDone = true
        if (immediate) { splash.visibility = View.GONE; return }
        splash.animate().alpha(0f).setDuration(240).withEndAction { splash.visibility = View.GONE }.start()
        if (cur != null) showInfo()
        (Updater.state.value as? Updater.State.Ready)?.let { offerUpdate(it) }
    }

    private val ticker = object : Runnable {
        override fun run() {
            if (tv) t.tick() else touchUi?.tick()
            handler.postDelayed(this, 1000)
        }
    }

    // ---------- плеер ----------
    private fun setupPlayer() {
        val loadControl = DefaultLoadControl.Builder().setBufferDurationsMs(15_000, 50_000, 1_500, 3_000).build()
        player = ExoPlayer.Builder(this).setLoadControl(loadControl).build()
        playerView.player = player
        player.addListener(object : Player.Listener {
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) = keepScreenOn()
            override fun onPlaybackStateChanged(playbackState: Int) {
                keepScreenOn()
                when (playbackState) {
                    Player.STATE_BUFFERING -> if (started) buffering()
                    Player.STATE_READY -> playing()
                    Player.STATE_ENDED -> ended()
                    else -> Unit
                }
            }
            override fun onPlayerError(error: PlaybackException) = handleError(error)
        })
    }

    /**
     * Пока канал идёт или подгружается, экран не гаснет и заставка Android TV не включается.
     * На паузе, после ошибки и в фоне флаг снимается — дальше решает система.
     */
    private fun keepScreenOn() {
        val on = player.playWhenReady && (player.playbackState == Player.STATE_READY || player.playbackState == Player.STATE_BUFFERING)
        if (on) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private val noImageCheck = Runnable {
        // Звук идёт, кадров нет дольше 5 с: в потоке нет видеодорожки
        val hasVideo = player.currentTracks.groups.any { it.type == androidx.media3.common.C.TRACK_TYPE_VIDEO }
        if (started && state == null && player.isPlaying && !hasVideo) showState("noimage")
    }

    private fun playing() {
        retries = 0
        started = true
        showState(null)
        hideSplash()
        if (trouble) { trouble = false; toast("Связь восстановлена", "ok") }
        handler.removeCallbacks(noImageCheck)
        handler.postDelayed(noImageCheck, 5000)
    }

    private fun buffering() {
        val now = System.currentTimeMillis()
        showState("buffer")
        buffers += now
        buffers.removeAll { now - it > 60_000 }
        if (buffers.size >= 3 && now - lastWarn > 5 * 60_000) {
            lastWarn = now; buffers.clear(); trouble = true
            toast("Поток часто останавливается. Проверьте интернет или выберите другой канал", "warn", ms = 6000)
        }
    }

    /** Запись доиграла: следующая передача в записи, если она уже прошла; иначе — эфир. */
    private fun ended() {
        val ch = cur ?: return
        val a = arch ?: return
        val e = epg.forChannel(ch)
        val now = System.currentTimeMillis()
        val i = e?.starts?.indexOf(a.start) ?: -1
        if (e != null && i >= 0 && i + 1 < e.size && e.stops[i + 1] <= now) play(ch, e[i + 1])
        else { play(ch, null); toast("Прямой эфир") }
    }

    /** Единая точка включения канала: запоминает последний и предыдущий канал, «Недавние», показывает инфо. */
    internal fun play(ch: Channel, programme: Programme?) {
        handler.removeCallbacks(zapTask)
        sel = null
        if (cur != null && cur?.key != ch.key) prev = cur
        val isNew = cur?.key != ch.key || arch != programme
        cur = ch
        arch = programme
        paused = false
        if (isNew) { retries = 0; forcedHls = false; buffers.clear() }
        if (programme == null) Repo.prefs.lastChannelKey = ch.key
        Repo.prefs.addRecent(ch.key)
        buildMenu()
        start(ch)
        if (tv) { hideZap(); t.renderArchTag(); showInfo() } else touchUi?.onPlay()
    }

    private fun start(ch: Channel) {
        val programme = arch
        val url = if (programme != null) Catchup.url(ch, programme.start, programme.stop) ?: ch.url else ch.url
        started = false
        handler.removeCallbacks(noImageCheck)
        handler.removeCallbacks(retryTask)
        showState("loading")
        val seq = ++playSeq
        if (!Plugins.active) { startPlayback(ch, url); return }
        // Плагины могут подменить адрес потока; если они молчат, включаем исходный
        scope.launch {
            val final = Plugins.stream(ch, url, programme != null)
            if (seq == playSeq) startPlayback(ch, final)
        }
    }

    private fun startPlayback(ch: Channel, url: String) {
        val dataSource = DefaultHttpDataSource.Factory()
            .setUserAgent(ch.userAgent ?: Http.USER_AGENT)
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(12_000)
            .setReadTimeoutMs(15_000)
        ch.referrer?.let { dataSource.setDefaultRequestProperties(mapOf("Referer" to it)) }
        val item = MediaItem.Builder().setUri(url).apply { if (forcedHls || looksLikeHls(url)) setMimeType(MimeTypes.APPLICATION_M3U8) }.build()
        player.setMediaSource(DefaultMediaSourceFactory(dataSource).createMediaSource(item))
        player.prepare()
        player.playWhenReady = true
        player.volume = if (muted) 0f else 1f
    }

    private fun looksLikeHls(url: String): Boolean {
        val path = url.substringBefore('?').lowercase()
        return path.endsWith(".m3u8") || path.endsWith(".m3u") || path.contains("/hls")
    }

    private val retryTask = Runnable { cur?.let { start(it) } }

    private fun handleError(error: PlaybackException) {
        val ch = cur ?: return
        if (error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) { player.seekToDefaultPosition(); player.prepare(); return }
        // Ссылка без расширения, а внутри HLS — пробуем ещё раз как HLS.
        if (!forcedHls && (error.errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED ||
                error.errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED)) {
            forcedHls = true
            start(ch)
            return
        }
        trouble = true
        // Сервер ответил 403 или 404 — канал недоступен, автоповторов нет
        val code = generateSequence(error as Throwable?) { it.cause }.filterIsInstance<HttpDataSource.InvalidResponseCodeException>().firstOrNull()?.responseCode ?: 0
        if (code == 403 || code == 404) { stateCode = code; hideSplash(); showState("denied"); return }
        if (retries < 2) {
            retries++
            handler.postDelayed(retryTask, 2000L * retries)
        } else {
            hideSplash()
            showState("error")
        }
    }

    internal fun retryPlay() { cur?.let { retries = 0; start(it) } }
    internal fun goLive() { cur?.let { play(it, null); toast("Прямой эфир") } }
    internal fun playPrev() { val p = prev ?: return; val ch = playlist.channels.firstOrNull { it.key == p.key } ?: p; if (tv) t.closeCtl(); play(ch, null) }

    internal fun position(): Long = player.currentPosition.coerceAtLeast(0) / 1000
    internal fun archLength(): Long = arch?.let { (minOf(it.stop, System.currentTimeMillis()) - it.start) / 1000 } ?: 0
    internal fun seek(sec: Int) {
        if (arch == null) return
        runCatching { player.seekTo((player.currentPosition + sec * 1000L).coerceAtLeast(0)) }
    }
    internal fun togglePause() {
        if (arch == null) return
        paused = !paused
        player.playWhenReady = !paused
        if (tv) { t.renderArchTag(); if (zone == "ctl") { t.renderCtl(); t.armCtl() }; showInfo() } else touchUi?.onPlay()
    }

    internal fun showState(name: String?) {
        state = name
        if (tv) t.renderState() else touchUi?.onState()
    }

    // ---------- быстрое переключение: поток включается через 400 мс после последнего нажатия ----------
    private val zapTask = Runnable { commitZap() }
    internal fun zap(dir: Int) {
        val l = playList.ifEmpty { playlist.channels }
        if (l.isEmpty()) return
        val i = l.indexOfFirst { it.key == (sel ?: cur)?.key }
        sel = l[if (i < 0) 0 else Math.floorMod(i + dir, l.size)]
        showZap()
        showInfo()
        handler.removeCallbacks(zapTask)
        handler.postDelayed(zapTask, 400)
    }
    private fun commitZap() {
        val ch = sel ?: return
        sel = null
        hideZap()
        if (ch.key == cur?.key && arch == null && state == null) showInfo() else play(ch, null)
    }

    // ---------- данные ----------
    private fun onPlaylist(p: Playlist) {
        val firstLoad = playlist.channels.isEmpty()
        playlist = p
        buildMenu()
        if (p.channels.isEmpty()) {
            cur = null
            player.stop()
            if (tv) t.renderZone() else touchUi?.onData()
            return
        }
        // Если играющий канал остался в новом плейлисте — просто обновляем ссылку на объект.
        val same = cur?.let { c -> p.channels.firstOrNull { it.key == c.key } }
        if (same != null) {
            cur = same
            playList = listOf(Repo.prefs.lastGroup ?: "all") ?: p.channels
        } else {
            val ch = p.channels.firstOrNull { it.key == Repo.prefs.lastChannelKey } ?: p.channels.first()
            val g = Repo.prefs.lastGroup ?: "all"
            playList = listOf(g)?.takeIf { l -> l.any { it.key == ch.key } } ?: p.channels
            prev = null
            arch = null
            play(ch, null)
        }
        if (tv) { refocus(); if (!firstLoad && zone == "play") showInfo() } else touchUi?.onData()
    }

    /** Список обновился под фокусом: ищем тот же канал по адресу, иначе тот же номер строки (SPEC 8.3 п. 7). */
    private fun refocus() {
        if (!inGuide()) { t.renderZone(); return }
        val key = list.getOrNull(ci)?.key
        if (listOf(menuId) == null) menuId = "all"
        list = listOf(menuId) ?: playlist.channels
        gi = menuIndex(menuId)
        val at = list.indexOfFirst { it.key == key }
        ci = if (at >= 0) at else ci.coerceIn(0, (list.size - 1).coerceAtLeast(0))
        t.progKey = ""
        t.renderGuide()
    }

    private fun onEpg(data: EpgData) {
        epg = data
        t.progKey = ""
        if (tv) { t.renderZone(); if (t.osdShown()) t.renderOsd(); if (zone == "search" || zone == "results") { runSearch(); t.renderSearch() } } else touchUi?.onEpg()
    }

    private fun onEpgState(s: LoadState) {
        epgLoading = s is LoadState.Loading
        epgStatus = when (s) { is LoadState.Loading -> s.message; is LoadState.Error -> s.message; else -> "" }
        if (tv) { if (inGuide() && prog.isEmpty()) t.renderDetails() } else touchUi?.onEpg()
    }

    private var lastState: LoadState = LoadState.Idle
    private fun onPlaylistState(s: LoadState) {
        // Ошибка не прячет работающие данные: сохранённый список остаётся, причина — в тосте
        if (s is LoadState.Error && lastState !is LoadState.Error && splashDone) toast("Не удалось обновить список: " + s.message.replaceFirstChar { it.lowercase() }, "warn")
        if (s is LoadState.Error) hideSplash()
        lastState = s
    }

    // ---------- меню и списки ----------
    internal fun isFav(ch: Channel) = ch.key in Repo.prefs.favorites
    internal fun favList(): List<Channel> { val f = Repo.prefs.favorites; return playlist.channels.filter { it.key in f } }
    internal fun recentList(): List<Channel> {
        val byKey = playlist.channels.associateBy { it.key }
        return Repo.prefs.recent.mapNotNull { byKey[it.first] }
    }
    internal fun listOf(id: String): List<Channel>? = when (id) {
        "all" -> playlist.channels
        "fav" -> favList()
        "recent" -> recentList()
        else -> menu.firstOrNull { it.id == id && !it.action }?.list
    }

    /** Меню гида: Поиск, Избранное, Недавние, Все каналы, группы плейлиста в порядке файла. «Настройки» — шестерёнка в шапке. */
    internal fun buildMenu() {
        val groups = LinkedHashMap<String, ArrayList<Channel>>()
        for (c in playlist.channels) groups.getOrPut(c.group) { ArrayList() }.add(c)
        menu = listOf(
            MenuItem("search", "Поиск", R.drawable.ic_search, action = true),
            MenuItem("fav", "Избранное", R.drawable.ic_star, favList()),
            MenuItem("recent", "Недавние", R.drawable.ic_history, recentList(), isNew = "recent"),
            MenuItem("all", "Все каналы", R.drawable.ic_tv, playlist.channels),
        ) + groups.map { MenuItem("g:" + it.key, it.key, R.drawable.ic_folder, it.value) }
        if (listOf(menuId) == null) menuId = "all"
    }
    internal fun menuIndex(id: String) = menu.indexOfFirst { it.id == id }.coerceAtLeast(0)
    internal fun menuTitle(id: String) = menu.getOrNull(menuIndex(id))?.title ?: "Все каналы"
    internal fun inGuide() = zone == "groups" || zone == "channels" || zone == "epg" || zone == "date" || zone == "head"

    internal fun selectMenu(id: String) {
        menuId = id
        gi = menuIndex(id)
        list = listOf(id) ?: emptyList()
        ci = list.indexOfFirst { it.key == cur?.key }.coerceAtLeast(0)
        if (id == "recent") Repo.prefs.markSeen("recent")
    }

    /** Дни, за которые у канала есть программа, и передачи выбранного дня. false — программы нет. */
    internal fun loadProg(ch: Channel?, wantDay: Long? = null): Boolean {
        val e = ch?.let { epg.forChannel(it) }
        val now = System.currentTimeMillis()
        epgCh = ch
        prog = emptyList(); pi = 0; days = emptyList()
        if (ch == null || e == null || e.size == 0) return false
        days = e.starts.map { dayStart(it) }.distinct().sorted()
        val today = dayStart(now)
        day = if (wantDay != null && wantDay in days) wantDay else if (today in days) today else days.last()
        val from = if (ch.hasArchive) now - ch.catchupDays * DAY else Long.MAX_VALUE
        val out = ArrayList<Prog>()
        for (i in 0 until e.size) {
            if (dayStart(e.starts[i]) != day) continue
            if (e.starts[i] <= now && e.stops[i] > now) pi = out.size
            if (arch != null && cur?.key == ch.key && arch?.start == e.starts[i]) pi = out.size
            out += Prog(e.starts[i], e.stops[i], e.titles[i].ifEmpty { "Без названия" }, e.descs[i], ch.hasArchive && e.starts[i] >= from && e.stops[i] <= now)
        }
        prog = out
        return out.isNotEmpty()
    }

    /** Включает канал из любого списка и запоминает раздел, из которого он включён. */
    internal fun tuneTo(ch: Channel, from: List<Channel>?, group: String) {
        playList = from?.takeIf { it.isNotEmpty() } ?: playlist.channels
        Repo.prefs.lastGroup = group
        val same = cur?.key == ch.key && arch == null && state != "error" && state != "denied"
        if (tv) t.closeLayers()
        if (same) showInfo() else play(ch, null)
    }

    internal fun chooseProgramme(p: Prog, ch: Channel) {
        val now = System.currentTimeMillis()
        when {
            p.start <= now && p.stop > now -> tuneTo(ch, list.takeIf { l -> l.any { it.key == ch.key } }, if (list.any { it.key == ch.key }) menuId else "all")
            p.stop <= now && p.arch -> { if (tv) t.closeLayers(); play(ch, Programme(p.start, p.stop, p.title)) }
            p.stop <= now -> toast("У этой передачи нет записи")
            else -> toast("Передача начнётся в " + clock(p.start))
        }
    }

    internal fun toggleFav(ch: Channel) {
        val added = Repo.prefs.toggleFavorite(ch.key)
        toast("«${ch.name}» " + if (added) "добавлен в избранное" else "убран из избранного", "fav")
        buildMenu()
        if (!tv) { touchUi?.onData(); return }
        if (inGuide()) {
            if (menuId == "fav") { list = favList(); ci = ci.coerceIn(0, (list.size - 1).coerceAtLeast(0)); if (list.isEmpty() && zone == "channels") zone = "groups" }
            else list = listOf(menuId) ?: list
            gi = menuIndex(menuId)
            t.renderGuide()
        }
        if (zone == "ctl") t.renderCtl()
        if (zone == "results") t.renderSearch()
        if (zone == "grid") t.renderGrid()
    }

    // ---------- поиск: каналы, передачи, записи, группы, плейлисты (SPEC 3.5) ----------
    internal var query = ""
    internal var found: List<Found> = emptyList()
    internal var foundSections: List<FoundSection> = emptyList()
    internal var fi = 0

    private fun norm(s: String) = s.lowercase().replace('ё', 'е')

    internal fun runSearch() {
        val q = norm(query.trim())
        val items = ArrayList<Found>()
        val sections = ArrayList<FoundSection>()
        if (q.isEmpty()) { found = items; foundSections = sections; fi = 0; return }
        val num = q.all { it.isDigit() }
        val now = System.currentTimeMillis()
        fun add(name: String, note: String, l: List<Found>, isNew: String = "") { sections += FoundSection(name, note, items.size, minOf(5, l.size), isNew); items += l.take(5) }
        val chs = ArrayList<Found>()
        if (num) for (c in playlist.channels) if (chs.size < 5 && c.number.toString().startsWith(q)) chs += Found("ch", c)
        for (c in playlist.channels) if (chs.size < 5 && !(num && c.number.toString().startsWith(q)) && norm(c.name).contains(q)) chs += Found("ch", c)
        add("Каналы", "", chs)
        if (epg.byId.isNotEmpty()) {
            if (!num) {
                val progs = ArrayList<Found>()
                val recs = ArrayList<Found>()
                val done = HashSet<Any>()
                for (c in playlist.channels) {
                    if (progs.size >= 60 && recs.size >= 60) break
                    val e = epg.forChannel(c) ?: continue
                    if (!done.add(e)) continue
                    val first = e.firstEndingAfter(now)
                    var k = first
                    while (k < e.size && progs.size < 60) { if (norm(e.titles[k]).contains(q)) progs += Found("prog", c, e.starts[k], e.stops[k], e.titles[k]); k++ }
                    if (c.hasArchive) {
                        val from = now - c.catchupDays * DAY
                        k = first - 1
                        while (k >= 0 && e.starts[k] >= from && recs.size < 60) { if (norm(e.titles[k]).contains(q)) recs += Found("prog", c, e.starts[k], e.stops[k], e.titles[k], rec = true); k-- }
                    }
                }
                add("Передачи", "сейчас и позже", progs.sortedBy { it.start }, "progsearch")
                add("Записи", "", recs.sortedByDescending { it.start }, "progsearch")
            }
        } else if (epgLoading) sections += FoundSection("Передачи", "", items.size, 0, status = "Телепрограмма загружается — передачи появятся через минуту")
        add("Группы", "", menu.filter { it.id.startsWith("g:") && norm(it.title).contains(q) }.map { Found("group", title = it.title, id = it.id) })
        val pls = Repo.playlists.value
        if (pls.size > 1) add("Плейлисты", "", pls.filter { norm(it.name).contains(q) }.map { Found("pl", title = it.name, id = it.id) }, "playlists")
        found = items; foundSections = sections
        fi = fi.coerceIn(0, (items.size - 1).coerceAtLeast(0))
    }

    internal fun resultChoose(f: Found) {
        val now = System.currentTimeMillis()
        when (f.type) {
            "ch" -> tuneTo(f.ch!!, playlist.channels, "all")
            "prog" -> when {
                f.start <= now && f.stop > now -> tuneTo(f.ch!!, playlist.channels, "all")
                f.rec -> { if (tv) t.closeLayers(); play(f.ch!!, Programme(f.start, f.stop, f.title.ifEmpty { "Без названия" })) }
                else -> if (tv) { t.closeLayers(); t.openGuide(); t.showProgramme(f.ch!!, f.start) } else touchUi?.showProgramme(f.ch!!, f.start)
            }
            "group" -> if (tv) { t.closeLayers(); t.openGuide(); selectMenu(f.id); zone = if (list.isNotEmpty()) "channels" else "groups"; t.renderGuide() } else touchUi?.showGroup(f.id)
            "pl" -> { if (tv) t.closeLayers(); activatePlaylist(f.id) { if (tv && it) t.openGuide() } }
        }
    }

    /** Смена активного плейлиста: каналы подменяются только после успешной загрузки. */
    internal fun activatePlaylist(id: String, done: (Boolean) -> Unit = {}) {
        val p = Repo.playlists.value.firstOrNull { it.id == id } ?: return
        if (id == Repo.activeId) { done(true); return }
        toast("Загружаем «${p.name}»…", "wait")
        scope.launch {
            val error = Repo.activate(id)
            if (error == null) { menuId = "all"; toast("Плейлист «${p.name}» · " + chCount(Repo.playlist.value.channels.size), "ok") }
            else toast("«${p.name}» не загрузился: " + error.trimEnd('.').replaceFirstChar { it.lowercase() }, "warn")
            done(error == null)
        }
    }

    // ---------- громкость (SPEC 7.1): уровень — системный поток музыки, «Без звука» — громкость плеера ----------
    override fun onVolumeKey(key: Key): Boolean {
        if (!tv) return false
        if (key == Key.MUTE) { toggleMute(); volumeTaken(); return true }
        if (audio.isVolumeFixed) return false // громкостью управляет телевизор или ресивер — показывает свою шкалу
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val v = (audio.getStreamVolume(AudioManager.STREAM_MUSIC) + if (key == Key.VOL_UP) 1 else -1).coerceIn(0, max)
        runCatching { audio.setStreamVolume(AudioManager.STREAM_MUSIC, v, 0) }
        if (muted && key == Key.VOL_UP) { muted = false; player.volume = 1f }
        t.showVol(v * 100 / max.coerceAtLeast(1))
        volumeTaken()
        return true
    }

    internal fun toggleMute() {
        muted = !muted
        player.volume = if (muted) 0f else 1f
        Repo.prefs.markSeen("volume")
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        if (tv) { t.showVol(audio.getStreamVolume(AudioManager.STREAM_MUSIC) * 100 / max); if (zone == "ctl") t.renderCtl() }
    }

    // ---------- кнопки пульта ----------
    override fun holdable(): Boolean = tv && !t.numOpen() && (zone == "channels" || zone == "results" || zone == "grid" && t.gridHead < 0 || zone == "play")

    /** Канал под фокусом в текущем слое (для зелёной, жёлтой и меню действий). */
    internal fun focusedChannel(): Channel? = when (zone) {
        "channels" -> list.getOrNull(ci)
        "epg", "date" -> epgCh
        "grid" -> t.gridList.getOrNull(t.gridRow)
        "results" -> found.getOrNull(fi)?.ch
        else -> cur
    }

    override fun onOk() {
        if (!tv) return
        if (!splashDone) return
        if (t.numOpen()) { t.numCommit(); return }
        when (zone) {
            "channels" -> list.getOrNull(ci)?.let { tuneTo(it, list, menuId) }
            "results" -> found.getOrNull(fi)?.let { resultChoose(it) }
            "grid" -> if (t.gridHead >= 0) t.keyGrid(Key.OK) else t.gridChoose()
            "play" -> playOk()
            "ctl" -> t.ctlOk()
            "search" -> t.kbPress()
            else -> t.keyGuide(Key.OK)
        }
    }

    private fun playOk() {
        when {
            playlist.channels.isEmpty() -> if (Repo.active == null) openOnboarding() else t.openGuide()
            state == "error" || state == "noimage" -> retryPlay()
            state == "denied" -> t.openGuide()
            arch != null -> { togglePause(); t.openCtl("bar") }
            // В эфире OK открывает гид целиком: разделы, каналы и что идёт
            else -> t.openGuide()
        }
    }

    /** Удержание OK (650 мс): канал сразу в избранное или из него. */
    override fun onHoldOk(): Boolean {
        val ch = focusedChannel() ?: return false
        if (zone != "play" && zone != "channels" && zone != "results" && zone != "grid") return false
        toggleFav(ch)
        return true
    }

    override fun onBack() {
        if (!tv) { if (touchUi?.onBack() != true) finish(); return }
        if (t.numOpen()) { t.numClose(); return }
        when (zone) {
            "ctl" -> t.closeCtl()
            "grid" -> if (t.gridHead >= 0) { t.gridHead = -1; t.renderGrid() } else t.closeGrid()
            "results" -> { zone = "search"; t.renderSearch() }
            "search" -> t.closeSearch()
            "date" -> { zone = "epg"; t.renderGuide() }
            "head" -> { zone = "groups"; gi = 0; t.renderGuide() }
            "groups", "channels", "epg" -> { t.closeGuide(); showInfo() }
            else -> when {
                arch != null -> goLive()
                t.osdShown() && state != "error" && state != "denied" && System.currentTimeMillis() - lastBack >= 2000 -> { t.hideInfo(); lastBack = 0 }
                System.currentTimeMillis() - lastBack < 2000 -> finish()
                else -> { lastBack = System.currentTimeMillis(); toast("Нажмите «Назад» ещё раз, чтобы выйти") }
            }
        }
    }

    /** Буквы с настоящей клавиатуры (пульт с клавиатурой, USB) в поиске сразу попадают в строку. */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (tv && zone == "search" && !dialogOpen && !popOpen && event.action == KeyEvent.ACTION_DOWN && !event.isCtrlPressed && !event.isAltPressed) {
            val c = event.unicodeChar
            if (c != 0 && (Character.isLetter(c) || c == ' '.code) && digitOf(event.keyCode) < 0) { t.typeText(String(Character.toChars(c))); return true }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onKey(key: Key, event: KeyEvent) {
        if (!tv) return
        if (!splashDone) return
        if (t.numOpen() && t.numKey(key, event)) return
        if (zone == "search") { t.keySearch(key, event); return }
        if (zone == "results") { t.keyResults(key); return }
        if (key == Key.DIGIT) { t.typeDigit(digitOf(event.keyCode)); return }
        val ch = focusedChannel()
        when (key) {
            Key.RED, Key.SEARCH -> { t.openSearch(); return }
            Key.GREEN -> { ch?.let { toggleFav(it) }; return }
            Key.YELLOW, Key.GUIDE -> { ch?.let { t.showProgramme(it) }; return }
            Key.BLUE, Key.MENU -> { openSettings(); return }
            Key.LIST -> { if (inGuide()) { t.closeGuide(); showInfo() } else t.openGuide(); return }
            else -> Unit
        }
        when {
            inGuide() -> t.keyGuide(key)
            zone == "grid" -> t.keyGrid(key)
            zone == "ctl" && t.keyCtl(key) -> Unit
            else -> when (key) {
                Key.LEFT -> if (arch != null) t.archKey(-1) else t.openGuide()
                Key.RIGHT -> if (arch != null) t.archKey(1) else cur?.let { t.showProgramme(it) }
                Key.UP, Key.CH_UP -> zap(1)
                Key.DOWN, Key.CH_DOWN -> zap(-1)
                Key.INFO -> if (t.osdShown()) t.hideInfo() else showInfo()
                Key.LAST, Key.DEL -> playPrev()
                Key.PLAY_PAUSE, Key.PLAY, Key.PAUSE -> if (arch != null) { togglePause(); t.openCtl("bar") }
                Key.REW -> if (arch != null) t.archKey(-1)
                Key.FF -> if (arch != null) t.archKey(1)
                else -> Unit
            }
        }
    }

    override fun onLayerClosed() { if (tv) t.renderZone() }

    internal fun showInfo() { if (tv) t.showInfo() else touchUi?.showInfo() }
    internal fun showZap() { if (tv) t.showZap() }
    internal fun hideZap() { if (tv) t.hideZap() }
    fun digit(code: Int) = digitOf(code)
}
