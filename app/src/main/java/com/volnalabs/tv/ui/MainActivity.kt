package com.volnalabs.tv.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.annotation.OptIn
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
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
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(UnstableApi::class)
class MainActivity : Activity() {

    private val scope = MainScope()
    private val handler = Handler(Looper.getMainLooper())

    private lateinit var player: ExoPlayer
    private lateinit var playerView: PlayerView
    private lateinit var loading: View
    private lateinit var message: TextView
    private lateinit var toast: TextView
    private lateinit var numberInput: TextView
    private lateinit var splash: SplashView

    private lateinit var infoBar: View
    private lateinit var infoLogo: ImageView
    private lateinit var infoNumber: TextView
    private lateinit var infoName: TextView
    private lateinit var infoBadge: TextView
    private lateinit var infoClock: TextView
    private lateinit var infoNow: TextView
    private lateinit var infoProgress: ProgressBar
    private lateinit var infoNext: TextView
    private lateinit var infoStart: TextView
    private lateinit var infoEnd: TextView
    private lateinit var infoTimeline: View

    private lateinit var panel: View
    private lateinit var groupsColumn: View
    private lateinit var epgColumn: View
    private lateinit var groupsView: RecyclerView
    private lateinit var channelsView: RecyclerView
    private lateinit var programmesView: RecyclerView
    private lateinit var channelsTitle: TextView
    private lateinit var channelsHint: TextView
    private lateinit var searchBox: View
    private lateinit var searchField: EditText
    private lateinit var epgTitle: TextView
    private lateinit var epgHint: TextView
    private lateinit var epgEmpty: TextView

    private lateinit var groupAdapter: GroupAdapter
    private lateinit var channelAdapter: ChannelAdapter
    private lateinit var programmeAdapter: ProgrammeAdapter

    private var playlist: Playlist = Playlist.EMPTY
    private var epg: EpgData = EpgData.EMPTY

    /** Канал, который сейчас играет. */
    private var current: Channel? = null
    /** Список, по которому листаем вверх/вниз (группа, из которой выбран канал). */
    private var playingList: List<Channel> = emptyList()
    private var playingGroupId: String? = null
    /** Передача из архива, если смотрим архив. */
    private var archive: Programme? = null
    /** Канал, чья программа показана в правой колонке панели. */
    private var epgChannel: Channel? = null
    private var lastChannel: Channel? = null

    private var searchMode = false
    private var retries = 0
    private var forcedHls = false
    private var lastBackPress = 0L
    private var digits = ""
    private var started = false
    private var splashDone = false
    private val createdAt = SystemClock.uptimeMillis()

    private val clock = SimpleDateFormat("HH:mm", Locale("ru"))
    private val dayFormat = SimpleDateFormat("EEEE, d MMMM", Locale("ru"))

    // ------------------------------------------------------------ жизненный цикл

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyOrientation()
        setContentView(R.layout.activity_main)
        bindViews()
        setupPlayer()
        setupLists()
        setupSearch()

        scope.launch { Repo.playlist.collect { onPlaylist(it) } }
        scope.launch { Repo.epg.collect { onEpg(it) } }
        scope.launch { Repo.playlistState.collect { onPlaylistState(it) } }
        scope.launch { Updater.state.collect { onUpdateState(it) } }

        if (Repo.prefs.playlistUrl.isEmpty()) {
            hideSplash(immediate = true)
            openSettings()
        }
        // Заставка не должна висеть вечно, даже если канал так и не запустился.
        handler.postDelayed({ hideSplash() }, 9000)
        handler.post(ticker)
        Updater.autoCheck(this)
    }

    override fun onStart() {
        super.onStart()
        started = true
        applyPlaybackPrefs()
        if (current != null && !player.isPlaying) current?.let { play(it, archive) }
        (Updater.state.value as? Updater.State.Ready)?.let { offerUpdate(it) }
    }

    override fun onStop() {
        started = false
        if (!audio.isVolumeFixed) Repo.prefs.volume = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
        player.stop()
        super.onStop()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        scope.cancel()
        player.release()
        super.onDestroy()
    }

    private val audio by lazy { getSystemService(Context.AUDIO_SERVICE) as AudioManager }

    /** Возвращаем громкость и формат картинки такими, какими они были при прошлом просмотре. */
    private fun applyPlaybackPrefs() {
        val prefs = Repo.prefs
        playerView.resizeMode = if (prefs.fillScreen) AspectRatioFrameLayout.RESIZE_MODE_ZOOM else AspectRatioFrameLayout.RESIZE_MODE_FIT
        val saved = prefs.volume
        if (prefs.rememberVolume && saved >= 0 && !audio.isVolumeFixed) runCatching {
            audio.setStreamVolume(AudioManager.STREAM_MUSIC, saved.coerceAtMost(audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)), 0)
        }
    }

    /** Телефон или планшет: управление касаниями вместо пульта. */
    private val touch by lazy { !isTv() }
    /** Телефон держат вертикально: видео сверху, список каналов под ним. */
    private var portrait = false
    private val channelsColumn: View by lazy { findViewById(R.id.channelsColumn) }

    private fun setupTouch() {
        // Касание экрана открывает список каналов, касание мимо карточек — закрывает.
        findViewById<View>(R.id.root).setOnClickListener { onScreenTap() }
        panel.setOnClickListener { hidePanel() }
        if (!touch) return

        findViewById<View>(R.id.touchBar).visibility = View.VISIBLE
        val dp = resources.displayMetrics.density
        channelsTitle.setPadding(channelsTitle.paddingLeft, (4 * dp).toInt(), channelsTitle.paddingRight, 0)
        findViewById<View>(R.id.touchMenu).setOnClickListener {
            val show = groupsColumn.visibility != View.VISIBLE
            epgColumn.visibility = View.GONE
            groupsColumn.visibility = if (show) View.VISIBLE else View.GONE
            if (portrait) channelsColumn.visibility = if (show) View.GONE else View.VISIBLE
            if (show) (groupsView.layoutManager as LinearLayoutManager).scrollToPositionWithOffset(groupAdapter.activated.coerceAtLeast(0), 0)
        }
        findViewById<View>(R.id.touchEpg).setOnClickListener {
            val ch = current ?: return@setOnClickListener
            val show = epgColumn.visibility != View.VISIBLE
            groupsColumn.visibility = View.GONE
            epgColumn.visibility = if (show) View.VISIBLE else View.GONE
            if (portrait) channelsColumn.visibility = if (show) View.GONE else View.VISIBLE
            if (show) showProgrammes(ch, focus = false)
        }
        findViewById<View>(R.id.touchLive).setOnClickListener { current?.let { play(it, null) }; showToast("Прямой эфир") }
        // В вертикальной раскладке карточки занимают весь экран, поэтому заголовок возвращает к каналам
        findViewById<View>(R.id.groupsTitle).setOnClickListener { if (portrait) showChannelsColumn() }
        epgTitle.setOnClickListener { if (portrait) showChannelsColumn() }
        applyLayout()
    }

    private fun showChannelsColumn() {
        groupsColumn.visibility = View.GONE
        epgColumn.visibility = View.GONE
        channelsColumn.visibility = View.VISIBLE
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (touch) applyLayout()
    }

    /** Раскладка под телефон: вертикально — видео и список под ним, горизонтально — видео на весь экран. */
    private fun applyLayout() {
        val dp = resources.displayMetrics.density
        val cfg = resources.configuration
        portrait = cfg.orientation == Configuration.ORIENTATION_PORTRAIT
        val videoHeight = (cfg.screenWidthDp * 9 / 16 * dp).toInt()
        val match = ViewGroup.LayoutParams.MATCH_PARENT

        playerView.layoutParams = FrameLayout.LayoutParams(match, if (portrait) videoHeight else match, Gravity.TOP)
        (loading.layoutParams as FrameLayout.LayoutParams).apply {
            gravity = if (portrait) Gravity.TOP or Gravity.CENTER_HORIZONTAL else Gravity.CENTER
            topMargin = if (portrait) videoHeight / 2 - (28 * dp).toInt() else 0
        }
        (message.layoutParams as FrameLayout.LayoutParams).apply {
            gravity = if (portrait) Gravity.TOP or Gravity.CENTER_HORIZONTAL else Gravity.CENTER
            topMargin = if (portrait) videoHeight / 4 else 0
        }
        (panel.layoutParams as FrameLayout.LayoutParams).topMargin = if (portrait) videoHeight else 0
        findViewById<TextView>(R.id.groupsTitle).text = if (portrait) "‹  Меню" else "Меню"

        if (portrait) {
            val pad = (8 * dp).toInt()
            panel.setBackgroundColor(ContextCompat.getColor(this, R.color.bg))
            panel.setPadding(pad, pad, pad, pad)
            for (column in listOf(groupsColumn, channelsColumn, epgColumn)) column.layoutParams = LinearLayout.LayoutParams(match, match)
            infoBar.visibility = View.GONE
            showChannelsColumn()
            if (playlist.channels.isNotEmpty()) showPanel()
        } else {
            val pad = (20 * dp).toInt()
            val gap = (12 * dp).toInt()
            panel.setBackgroundResource(R.drawable.panel_scrim)
            panel.setPadding(pad, pad, pad, pad)
            // На узком экране делим ширину между карточками, чтобы программа помещалась рядом с каналами.
            val free = cfg.screenWidthDp - 40 - 12
            val channelsWidth = minOf(440, free / 2)
            channelsColumn.layoutParams = LinearLayout.LayoutParams((channelsWidth * dp).toInt(), match)
            epgColumn.layoutParams = LinearLayout.LayoutParams((minOf(424, free - channelsWidth) * dp).toInt(), match).apply { marginStart = gap }
            groupsColumn.layoutParams = LinearLayout.LayoutParams((minOf(264, free - channelsWidth) * dp).toInt(), match).apply { marginEnd = gap }
            showChannelsColumn()
            panel.visibility = View.GONE
        }
        updateArchiveChip()
        panel.requestLayout()
    }

    private fun updateArchiveChip() {
        if (touch) findViewById<View>(R.id.touchLive).visibility = if (archive != null) View.VISIBLE else View.GONE
    }

    private fun onScreenTap() {
        val ch = current
        when {
            splash.visibility == View.VISIBLE -> Unit
            playlist.channels.isEmpty() -> openSettings()
            message.visibility == View.VISIBLE && ch != null -> { retries = 0; play(ch, archive) }
            portrait -> if (archive != null) { togglePause(); showToast(if (player.playWhenReady) "Запись идёт" else "Пауза") }
            else -> showPanel()
        }
    }

    private fun bindViews() {
        playerView = findViewById(R.id.player)
        loading = findViewById(R.id.loading)
        message = findViewById(R.id.message)
        toast = findViewById(R.id.toast)
        numberInput = findViewById(R.id.numberInput)
        splash = findViewById(R.id.splash)
        infoBar = findViewById(R.id.infoBar)
        infoLogo = findViewById(R.id.infoLogo)
        infoNumber = findViewById(R.id.infoNumber)
        infoName = findViewById(R.id.infoName)
        infoBadge = findViewById(R.id.infoBadge)
        infoClock = findViewById(R.id.infoClock)
        infoNow = findViewById(R.id.infoNow)
        infoProgress = findViewById(R.id.infoProgress)
        infoNext = findViewById(R.id.infoNext)
        infoStart = findViewById(R.id.infoStart)
        infoEnd = findViewById(R.id.infoEnd)
        infoTimeline = findViewById(R.id.infoTimeline)
        panel = findViewById(R.id.panel)
        groupsColumn = findViewById(R.id.groupsColumn)
        epgColumn = findViewById(R.id.epgColumn)
        groupsView = findViewById(R.id.groups)
        channelsView = findViewById(R.id.channels)
        programmesView = findViewById(R.id.programmes)
        channelsTitle = findViewById(R.id.channelsTitle)
        channelsHint = findViewById(R.id.channelsHint)
        searchBox = findViewById(R.id.searchBox)
        searchField = findViewById(R.id.searchField)
        epgTitle = findViewById(R.id.epgTitle)
        epgHint = findViewById(R.id.epgHint)
        epgEmpty = findViewById(R.id.epgEmpty)
        // Карточки со скруглёнными углами: содержимое списков не вылезает за края.
        for (id in intArrayOf(R.id.groupsColumn, R.id.channelsColumn, R.id.epgColumn)) findViewById<View>(id).apply {
            clipToOutline = true
            isClickable = true // касание внутри карточки не закрывает панель
        }
        setupTouch()
        // Колонки групп и программы видны, только пока в них фокус — панель остаётся лёгкой.
        panel.viewTreeObserver.addOnGlobalFocusChangeListener { _, newFocus -> updateColumns(newFocus) }
    }

    // ------------------------------------------------------------ заставка

    private fun hideSplash(immediate: Boolean = false) {
        if (splashDone) return
        // Даём анимации появиться целиком, чтобы заставка не мелькала.
        val shown = SystemClock.uptimeMillis() - createdAt
        if (!immediate && shown < 1500) {
            handler.postDelayed({ hideSplash() }, 1500 - shown)
            return
        }
        splashDone = true
        if (immediate) { splash.visibility = View.GONE; return }
        splash.animate().alpha(0f).setDuration(350).withEndAction { splash.visibility = View.GONE }.start()
        // Плашка канала могла погаснуть, пока шла заставка, — показываем её заново.
        if (current != null) showInfo()
    }

    // ------------------------------------------------------------ плеер

    private fun setupPlayer() {
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(15_000, 50_000, 1_500, 3_000)
            .build()
        player = ExoPlayer.Builder(this).setLoadControl(loadControl).build()
        playerView.player = player
        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                loading.visibility = if (state == Player.STATE_BUFFERING) View.VISIBLE else View.GONE
                if (state == Player.STATE_READY) {
                    retries = 0
                    message.visibility = View.GONE
                    hideSplash()
                }
                if (state == Player.STATE_ENDED && archive != null) {
                    showToast("Передача закончилась — возвращаю прямой эфир")
                    current?.let { play(it, null) }
                }
            }

            override fun onPlayerError(error: PlaybackException) = handleError(error)
        })
    }

    private fun play(ch: Channel, programme: Programme?) {
        val url = if (programme != null) Catchup.url(ch, programme.start, programme.stop) ?: ch.url else ch.url
        val isNew = current?.key != ch.key || archive != programme
        current = ch
        archive = programme
        if (isNew) { retries = 0; forcedHls = false }
        if (programme == null) Repo.prefs.lastChannelKey = ch.key
        updateArchiveChip()
        channelAdapter.playingKey = ch.key
        channelAdapter.refreshVisible()
        programmeAdapter.playingStart = programme?.start ?: -1
        showInfo()

        val seq = ++playSeq
        if (!Plugins.active) { startPlayback(ch, url); return }
        // Плагины могут подменить адрес потока; если они молчат, включаем исходный
        scope.launch {
            val final = Plugins.stream(ch, url, programme != null)
            if (seq == playSeq) startPlayback(ch, final)
        }
    }

    private var playSeq = 0

    private fun startPlayback(ch: Channel, url: String) {
        val dataSource = DefaultHttpDataSource.Factory()
            .setUserAgent(ch.userAgent ?: Http.USER_AGENT)
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(12_000)
            .setReadTimeoutMs(15_000)
        ch.referrer?.let { dataSource.setDefaultRequestProperties(mapOf("Referer" to it)) }

        val item = MediaItem.Builder().setUri(url).apply {
            if (forcedHls || looksLikeHls(url)) setMimeType(MimeTypes.APPLICATION_M3U8)
        }.build()

        message.visibility = View.GONE
        player.setMediaSource(DefaultMediaSourceFactory(dataSource).createMediaSource(item))
        player.prepare()
        player.playWhenReady = true
    }

    private fun looksLikeHls(url: String): Boolean {
        val path = url.substringBefore('?').lowercase()
        return path.endsWith(".m3u8") || path.endsWith(".m3u") || path.contains("/hls")
    }

    private fun handleError(error: PlaybackException) {
        val ch = current ?: return
        if (error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
            player.seekToDefaultPosition()
            player.prepare()
            return
        }
        // Ссылка без расширения, а внутри HLS — пробуем ещё раз как HLS.
        if (!forcedHls && (error.errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED ||
                error.errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED)
        ) {
            forcedHls = true
            play(ch, archive)
            return
        }
        if (retries < 3) {
            retries++
            if (splashDone) showMessage("Канал не отвечает, пробую ещё раз… ($retries из 3)")
            handler.postDelayed({ if (current?.key == ch.key) play(ch, archive) }, 2000L * retries)
        } else {
            val reason = when (error.errorCode) {
                PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> "сервер вернул ошибку"
                PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
                PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> "нет связи с сервером"
                PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
                PlaybackException.ERROR_CODE_DECODER_INIT_FAILED -> "этот формат не поддерживается телевизором"
                else -> "ошибка воспроизведения"
            }
            hideSplash()
            showMessage("Не удалось включить «${ch.name}»: $reason.\nНажмите ОК, чтобы попробовать снова, или ▲▼ — другой канал.")
        }
    }

    // ------------------------------------------------------------ данные

    private fun onPlaylist(p: Playlist) {
        val panelOpen = panel.visibility == View.VISIBLE
        playlist = p
        if (searchMode) exitSearch()
        rebuildGroups(keepFocus = false)
        if (p.channels.isEmpty()) {
            current = null
            player.stop()
            if (panelOpen) panel.visibility = View.GONE
            if (Repo.prefs.playlistUrl.isEmpty()) {
                hideSplash(immediate = true)
                showMessage("Добро пожаловать!\nНажмите ОК и добавьте ссылку на плейлист.")
            }
            return
        }
        // Если играющий канал остался в новом плейлисте — просто обновляем ссылку на объект.
        val cur = current
        val same = cur?.let { c -> p.channels.firstOrNull { it.key == c.key } }
        if (same != null) {
            current = same
            playingList = groupChannels(playingGroupId) ?: p.channels
        } else {
            val last = Repo.prefs.lastChannelKey
            val ch = p.channels.firstOrNull { it.key == last } ?: p.channels.first()
            playingGroupId = Repo.prefs.lastGroup?.takeIf { groupChannels(it)?.any { c -> c.key == ch.key } == true } ?: ALL
            playingList = groupChannels(playingGroupId) ?: p.channels
            play(ch, null)
        }
        if (portrait) { if (!panelOpen) showPanel(); return }
        // Список пересобран — возвращаем фокус на текущий канал, иначе он «убежит».
        if (panelOpen) focusPosition(channelsView, channelAdapter.indexOf(current?.key).coerceAtLeast(0))
    }

    private fun onEpg(data: EpgData) {
        epg = data
        channelAdapter.epg = data
        channelAdapter.refreshVisible()
        val inProgrammes = containingList(window.decorView.findFocus()) === programmesView
        if (epgColumn.visibility == View.VISIBLE) epgChannel?.let { showProgrammes(it, focus = inProgrammes) }
        if (infoBar.visibility == View.VISIBLE) updateInfo()
    }

    private fun onPlaylistState(state: LoadState) {
        when (state) {
            is LoadState.Loading -> if (playlist.channels.isEmpty()) {
                splash.caption = state.message
                if (splashDone) showMessage(state.message)
            }
            is LoadState.Error -> {
                hideSplash()
                if (playlist.channels.isEmpty()) showMessage("${state.message}\n\nНажмите ОК, чтобы открыть настройки.")
                else showToast(state.message)
            }
            else -> splash.caption = ""
        }
    }

    // ------------------------------------------------------------ обновление приложения

    private fun onUpdateState(state: Updater.State) {
        if (state is Updater.State.Ready) offerUpdate(state)
    }

    private fun offerUpdate(state: Updater.State.Ready) {
        if (!started || !Repo.prefs.autoUpdate) return
        if (Updater.postponed == state.version) return
        Updater.postponed = state.version // спрашиваем один раз за запуск
        startActivity(Intent(this, UpdateActivity::class.java))
    }

    // ------------------------------------------------------------ панель каналов

    private fun setupLists() {
        groupAdapter = GroupAdapter(
            onFocus = { pos -> handler.removeCallbacks(groupFocusRunnable); pendingGroup = pos; handler.postDelayed(groupFocusRunnable, 120) },
            onClick = { pos -> onGroupClick(pos) },
        )
        channelAdapter = ChannelAdapter(
            onFocus = { _, _ -> },
            onClick = { ch -> onChannelClick(ch) },
            onLongClick = { ch -> toggleFavorite(ch) },
        )
        programmeAdapter = ProgrammeAdapter { row -> onProgrammeClick(row) }

        for ((rv, adapter) in listOf(groupsView to groupAdapter, channelsView to channelAdapter, programmesView to programmeAdapter)) {
            rv.layoutManager = CenterLayoutManager(this)
            rv.adapter = adapter
            rv.itemAnimator = null
            rv.setItemViewCacheSize(20)
        }
    }

    private var pendingGroup = -1
    private val groupFocusRunnable = Runnable { selectGroup(pendingGroup, focusChannels = false) }

    private fun groupChannels(id: String?): List<Channel>? = when (id) {
        null -> null
        ALL -> playlist.channels
        FAV -> Repo.prefs.favorites.let { fav -> playlist.channels.filter { it.key in fav } }
        else -> playlist.channels.filter { it.group == id }.takeIf { it.isNotEmpty() }
    }

    private fun rebuildGroups(keepFocus: Boolean) {
        val fav = Repo.prefs.favorites
        val items = ArrayList<GroupItem>()
        if (playlist.channels.isNotEmpty()) {
            items += GroupItem(SEARCH, "Поиск", emptyList(), icon = R.drawable.ic_search, isAction = true)
            items += GroupItem(SETTINGS, "Настройки", emptyList(), icon = R.drawable.ic_settings, isAction = true)
            items += GroupItem(PLUGINS, "Плагины", emptyList(), icon = R.drawable.ic_plug, isAction = true)
            val favChannels = playlist.channels.filter { it.key in fav }
            if (favChannels.isNotEmpty()) items += GroupItem(FAV, "Избранное", favChannels, icon = R.drawable.ic_star)
            items += GroupItem(ALL, "Все каналы", playlist.channels, icon = R.drawable.ic_tv)
            val byGroup = playlist.channels.groupBy { it.group }
            for (g in playlist.groups) items += GroupItem(g, g, byGroup[g].orEmpty())
        }
        if (items.isEmpty()) items += GroupItem(SETTINGS, "Настройки", emptyList(), icon = R.drawable.ic_settings, isAction = true)

        val activeId = groupAdapter.items.getOrNull(groupAdapter.activated)?.id?.takeIf { it != SEARCH } ?: playingGroupId ?: ALL
        val active = items.indexOfFirst { it.id == activeId }.takeIf { it >= 0 } ?: items.indexOfFirst { it.id == ALL }.coerceAtLeast(0)
        groupAdapter.submit(items, active)
        channelAdapter.favorites = fav
        if (!keepFocus) {
            selectGroup(active, focusChannels = false)
        } else if (!searchMode) {
            val list = items.getOrNull(active)?.channels.orEmpty()
            if (list != channelAdapter.items) channelAdapter.submit(list) else channelAdapter.refreshVisible()
        } else {
            channelAdapter.refreshVisible()
        }
    }

    private fun selectGroup(pos: Int, focusChannels: Boolean) {
        val item = groupAdapter.items.getOrNull(pos) ?: return
        if (item.isAction) return
        if (searchMode) exitSearch()
        groupAdapter.setActivated(pos)
        channelsTitle.text = item.title
        updateChannelsHint()
        if (channelAdapter.items !== item.channels) {
            channelAdapter.submit(item.channels)
            // Новый список открываем на играющем канале, а если его тут нет — с начала.
            val idx = channelAdapter.indexOf(current?.key).coerceAtLeast(0)
            (channelsView.layoutManager as LinearLayoutManager).scrollToPositionWithOffset(idx, if (idx == 0) 0 else channelsView.height / 3)
        }
        if (focusChannels) focusPosition(channelsView, channelAdapter.indexOf(current?.key).coerceAtLeast(0))
    }

    private fun updateChannelsHint() {
        if (searchMode) return
        val count = plural(groupAdapter.items.getOrNull(groupAdapter.activated)?.channels?.size ?: 0, "канал", "канала", "каналов")
        channelsHint.text = if (touch || groupsColumn.visibility == View.VISIBLE) count else "$count  ·  ◀ группы и поиск  ·  ▶ программа"
    }

    private fun onGroupClick(pos: Int) {
        val item = groupAdapter.items.getOrNull(pos) ?: return
        handler.removeCallbacks(groupFocusRunnable)
        when (item.id) {
            SETTINGS -> { hidePanel(); openSettings() }
            PLUGINS -> { hidePanel(); startActivity(Intent(this, PluginsActivity::class.java)) }
            SEARCH -> enterSearch()
            else -> { selectGroup(pos, focusChannels = !portrait); if (portrait) showChannelsColumn() }
        }
    }

    private fun onChannelClick(ch: Channel) {
        if (current?.key == ch.key && archive == null) {
            hidePanel()
            if (player.playbackState == Player.STATE_IDLE || message.visibility == View.VISIBLE) play(ch, null)
            return
        }
        setPlayingContext()
        hidePanel()
        switchTo(ch)
    }

    /** Запоминаем, из какого списка включён канал, — по нему потом листаем ▲▼. */
    private fun setPlayingContext() {
        val group = groupAdapter.items.getOrNull(groupAdapter.activated)
        if (searchMode || group == null || group.isAction) {
            playingGroupId = ALL
            playingList = playlist.channels
        } else {
            playingGroupId = group.id
            playingList = group.channels
        }
        Repo.prefs.lastGroup = playingGroupId
    }

    private fun toggleFavorite(ch: Channel) {
        val added = Repo.prefs.toggleFavorite(ch.key)
        showToast(if (added) "«${ch.name}» добавлен в избранное" else "«${ch.name}» убран из избранного")
        rebuildGroups(keepFocus = true)
        val idx = channelAdapter.indexOf(ch.key)
        if (idx >= 0) focusPosition(channelsView, idx)
        else focusPosition(channelsView, 0)
    }

    // ------------------------------------------------------------ поиск

    private fun setupSearch() {
        searchField.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) { if (searchMode) applySearch(s?.toString().orEmpty()) }
        })
        searchField.setOnEditorActionListener { _, action, _ ->
            if (action == EditorInfo.IME_ACTION_SEARCH || action == EditorInfo.IME_ACTION_DONE) {
                hideKeyboard()
                if (channelAdapter.items.isNotEmpty()) focusPosition(channelsView, 0)
                true
            } else false
        }
    }

    private fun enterSearch() {
        searchMode = true
        groupAdapter.setActivated(groupAdapter.items.indexOfFirst { it.id == SEARCH })
        channelsTitle.visibility = View.GONE
        searchBox.visibility = View.VISIBLE
        searchField.setText("")
        if (portrait) showChannelsColumn()
        applySearch("")
        searchField.requestFocusFromTouch()
        (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showSoftInput(searchField, 0)
    }

    private fun exitSearch() {
        if (!searchMode) return
        searchMode = false
        hideKeyboard()
        searchBox.visibility = View.GONE
        channelsTitle.visibility = View.VISIBLE
    }

    private fun hideKeyboard() {
        (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(searchField.windowToken, 0)
    }

    private fun applySearch(text: String) {
        val query = normalize(text)
        val result = if (query.isEmpty()) playlist.channels else playlist.channels.filter { normalize(it.name).contains(query) }
        channelAdapter.submit(result)
        (channelsView.layoutManager as LinearLayoutManager).scrollToPositionWithOffset(0, 0)
        channelsHint.text = when {
            query.isEmpty() -> "Начните вводить название — список сузится сам"
            result.isEmpty() -> "Ничего не нашлось. Попробуйте короче."
            else -> "Нашлось: ${plural(result.size, "канал", "канала", "каналов")}  ·  ▼ к списку"
        }
    }

    private fun normalize(s: String) = s.trim().lowercase().replace('ё', 'е')

    // ------------------------------------------------------------ телепрограмма

    private fun showProgrammes(ch: Channel, focus: Boolean) {
        epgChannel = ch
        epgTitle.text = if (portrait) "‹  ${ch.name}" else ch.name
        epgHint.text = if (ch.hasArchive) "Прошедшие передачи можно смотреть в записи" else "Телепрограмма"
        val e = epg.forChannel(ch)
        val now = System.currentTimeMillis()
        if (e == null || e.size == 0) {
            programmeAdapter.submit(emptyList())
            epgEmpty.visibility = View.VISIBLE
            epgEmpty.text = when (Repo.epgState.value) {
                is LoadState.Loading -> "Телепрограмма загружается, подождите немного…"
                else -> "Для этого канала телепрограммы нет"
            }
            return
        }
        epgEmpty.visibility = View.GONE
        val day = 24 * 60 * 60 * 1000L
        val archiveFrom = now - ch.catchupDays * day
        val from = if (ch.hasArchive) archiveFrom else startOfDay(now)
        val rows = ArrayList<ProgRow>()
        var lastDay = ""
        var nowIndex = 0
        for (p in e.between(from, now + day)) {
            val label = dayLabel(p.start, now)
            if (label != lastDay) { rows += ProgRow.Day(label); lastDay = label }
            val isNow = p.isNow(now)
            if (isNow) nowIndex = rows.size
            rows += ProgRow.Item(p, isNow, p.isPast(now), ch.hasArchive && p.start >= archiveFrom)
        }
        val playingArchive = if (current?.key == ch.key) archive else null
        programmeAdapter.playingStart = playingArchive?.start ?: -1
        programmeAdapter.submit(rows)
        val target = playingArchive?.let { a -> rows.indexOfFirst { it is ProgRow.Item && it.p.start == a.start }.takeIf { it >= 0 } } ?: nowIndex
        if (focus) focusPosition(programmesView, target)
        else (programmesView.layoutManager as LinearLayoutManager).scrollToPositionWithOffset(target, programmesView.height / 3)
    }

    private fun onProgrammeClick(row: ProgRow.Item) {
        val ch = epgChannel ?: return
        when {
            row.isNow -> onChannelClick(ch)
            row.isPast && row.archive -> {
                if (current?.key != ch.key) { setPlayingContext(); lastChannel = current }
                hidePanel()
                play(ch, row.p)
            }
            row.isPast -> showToast("Эта передача уже прошла, записи нет")
            else -> showToast("Передача начнётся в ${clock.format(Date(row.p.start))}")
        }
    }

    // ------------------------------------------------------------ показ и фокус панели

    private fun updateColumns(focused: View?) {
        if (panel.visibility != View.VISIBLE || focused == null || portrait) return
        when {
            containingList(focused) === groupsView -> { groupsColumn.visibility = View.VISIBLE; epgColumn.visibility = View.GONE }
            containingList(focused) === programmesView -> { epgColumn.visibility = View.VISIBLE; groupsColumn.visibility = View.GONE }
            containingList(focused) === channelsView || focused === searchField -> { groupsColumn.visibility = View.GONE; epgColumn.visibility = View.GONE }
        }
        updateChannelsHint()
    }

    private fun showPanel(focusEpg: Boolean = false) {
        if (playlist.channels.isEmpty()) { openSettings(); return }
        infoBar.visibility = View.GONE
        toast.visibility = View.GONE
        panel.visibility = View.VISIBLE
        if (portrait) {
            // Список всегда на экране; фокус пульта не нужен — просто показываем играющий канал
            val at = groupAdapter.items.indexOfFirst { it.id == (playingGroupId ?: ALL) }.takeIf { it >= 0 }
                ?: groupAdapter.items.indexOfFirst { it.id == ALL }
            selectGroup(at, focusChannels = false)
            channelAdapter.refreshVisible()
            return
        }
        groupsColumn.visibility = View.GONE
        epgColumn.visibility = if (focusEpg) View.VISIBLE else View.GONE
        // Открываем на группе текущего канала
        val gpos = groupAdapter.items.indexOfFirst { it.id == (playingGroupId ?: ALL) }.takeIf { it >= 0 }
            ?: groupAdapter.items.indexOfFirst { it.id == ALL }
        selectGroup(gpos, focusChannels = false)
        channelAdapter.refreshVisible()
        val ch = current
        if (focusEpg && ch != null) showProgrammes(ch, focus = true)
        else focusPosition(channelsView, channelAdapter.indexOf(current?.key).coerceAtLeast(0))
    }

    private fun hidePanel() {
        if (searchMode) exitSearch()
        if (portrait) { showChannelsColumn(); return }
        panel.visibility = View.GONE
    }

    private fun focusPosition(rv: RecyclerView, pos: Int, attempt: Int = 0) {
        val count = rv.adapter?.itemCount ?: 0
        if (count == 0) return
        var p = pos.coerceIn(0, count - 1)
        // В программе заголовки дней не фокусируются — шагаем к ближайшей передаче.
        if (rv === programmesView) {
            while (p < count - 1 && programmeAdapter.rows[p] is ProgRow.Day) p++
        }
        val vh = rv.findViewHolderForAdapterPosition(p)
        if (vh != null && vh.itemView.isShown) {
            // FromTouch: выходим из «сенсорного режима» (бывает после аэромыши), иначе фокус не встанет.
            vh.itemView.requestFocusFromTouch()
            return
        }
        if (attempt >= 4) { vh?.itemView?.requestFocusFromTouch(); return }
        if (vh == null) (rv.layoutManager as LinearLayoutManager).scrollToPositionWithOffset(p, rv.height / 3)
        // Список ещё не разложен (панель только что показали) — ставим фокус сразу после раскладки.
        rv.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                rv.viewTreeObserver.removeOnGlobalLayoutListener(this)
                focusPosition(rv, p, attempt + 1)
            }
        })
        rv.requestLayout()
    }

    private fun containingList(v: View?): RecyclerView? {
        var cur: View? = v
        while (cur != null) {
            val parent = cur.parent
            if (parent is RecyclerView) return parent
            cur = parent as? View
        }
        return null
    }

    // ------------------------------------------------------------ информация о канале

    private val hideInfo = Runnable { infoBar.visibility = View.GONE }

    private fun showInfo(durationMs: Long = 5000) {
        if (panel.visibility == View.VISIBLE) return
        updateInfo()
        infoBar.visibility = View.VISIBLE
        handler.removeCallbacks(hideInfo)
        if (durationMs > 0) handler.postDelayed(hideInfo, durationMs)
    }

    private fun updateInfo() {
        val ch = current ?: return
        val now = System.currentTimeMillis()
        infoNumber.text = ch.number.toString()
        infoName.text = ch.name
        infoClock.text = clock.format(Date(now))
        infoLogo.loadLogo(ch)

        val arch = archive
        if (arch != null) {
            infoBadge.visibility = View.VISIBLE
            infoBadge.text = if (player.playWhenReady) "ЗАПИСЬ" else "ПАУЗА"
            infoNow.text = arch.title.ifEmpty { ch.name }
            val dur = (arch.stop - arch.start).coerceAtLeast(1)
            val pos = player.currentPosition.coerceAtLeast(0)
            infoTimeline.visibility = View.VISIBLE
            infoStart.text = formatDuration(pos)
            infoEnd.text = formatDuration(dur)
            infoProgress.progress = (pos * 1000 / dur).toInt().coerceIn(0, 1000)
            infoNext.visibility = View.VISIBLE
            infoNext.text = "${dayLabel(arch.start, now)}, ${clock.format(Date(arch.start))}  ·  ◀ ▶ перемотка  ·  ОК пауза  ·  Назад — в эфир"
            return
        }
        infoBadge.visibility = View.GONE
        val e = epg.forChannel(ch)
        val p = e?.now(now)
        if (p != null) {
            infoNow.text = p.title
            infoTimeline.visibility = View.VISIBLE
            infoStart.text = clock.format(Date(p.start))
            infoEnd.text = clock.format(Date(p.stop))
            infoProgress.progress = ((now - p.start) * 1000 / (p.stop - p.start).coerceAtLeast(1)).toInt()
        } else {
            infoNow.text = ch.group
            infoTimeline.visibility = View.GONE
        }
        val next = e?.next(now)
        infoNext.text = next?.let { "Далее в ${clock.format(Date(it.start))}  ·  ${it.title}" } ?: ""
        infoNext.visibility = if (next != null) View.VISIBLE else View.GONE
    }

    private val ticker = object : Runnable {
        override fun run() {
            if (infoBar.visibility == View.VISIBLE) updateInfo()
            handler.postDelayed(this, if (archive != null) 1000 else 30_000)
        }
    }

    // ------------------------------------------------------------ пульт

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (splash.visibility == View.VISIBLE && event.keyCode != KeyEvent.KEYCODE_BACK) return true
        if (event.action == KeyEvent.ACTION_DOWN && panel.visibility == View.VISIBLE) {
            if (handlePanelKey(event)) return true
        }
        return super.dispatchKeyEvent(event)
    }

    /** Переходы между колонками панели и упор в края списков. */
    private fun handlePanelKey(event: KeyEvent): Boolean {
        val focused = window.decorView.findFocus()
        if (focused === searchField) return handleSearchKey(event)
        val list = containingList(focused)
        val pos = focused?.let { list?.getChildAdapterPosition(it) } ?: RecyclerView.NO_POSITION
        when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                when (list) {
                    channelsView -> openGroups()
                    programmesView -> focusPosition(channelsView, channelAdapter.indexOf(epgChannel?.key).coerceAtLeast(0))
                    groupsView -> Unit
                    else -> focusPosition(channelsView, 0)
                }
                return true
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                when (list) {
                    groupsView -> {
                        handler.removeCallbacks(groupFocusRunnable)
                        val item = groupAdapter.items.getOrNull(pos)
                        if (item?.id == SEARCH) { enterSearch(); return true }
                        if (item?.isAction == true) return true
                        selectGroup(pos, focusChannels = true)
                    }
                    channelsView -> channelAdapter.items.getOrNull(pos)?.let { ch ->
                        epgColumn.visibility = View.VISIBLE
                        showProgrammes(ch, focus = true)
                        // Нет программы — фокус остаётся на канале, колонка просто показывает пояснение.
                        if (programmeAdapter.rows.isEmpty()) handler.postDelayed({
                            if (containingList(window.decorView.findFocus()) === channelsView) epgColumn.visibility = View.GONE
                        }, 2500)
                    }
                }
                return true
            }
            KeyEvent.KEYCODE_DPAD_UP -> if (list != null && pos == 0) {
                if (list === channelsView && searchMode) searchField.requestFocusFromTouch()
                return true
            }
            KeyEvent.KEYCODE_DPAD_DOWN ->
                if (list != null && pos == (list.adapter?.itemCount ?: 0) - 1) return true
            KeyEvent.KEYCODE_MENU -> {
                if (list === channelsView) channelAdapter.items.getOrNull(pos)?.let { toggleFavorite(it) }
                else { hidePanel(); openSettings() }
                return true
            }
            KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ESCAPE -> { hidePanel(); return true }
            KeyEvent.KEYCODE_PAGE_DOWN, KeyEvent.KEYCODE_CHANNEL_DOWN, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> { page(list, pos, +1); return true }
            KeyEvent.KEYCODE_PAGE_UP, KeyEvent.KEYCODE_CHANNEL_UP, KeyEvent.KEYCODE_MEDIA_REWIND -> { page(list, pos, -1); return true }
        }
        return false
    }

    private fun handleSearchKey(event: KeyEvent): Boolean {
        when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                if (channelAdapter.items.isNotEmpty()) { hideKeyboard(); focusPosition(channelsView, 0) }
                return true
            }
            KeyEvent.KEYCODE_DPAD_UP -> return true
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                if (searchField.selectionStart > 0) return false // двигаем курсор
                hideKeyboard()
                openGroups()
                return true
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> return searchField.selectionEnd >= searchField.length()
            KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ESCAPE -> {
                if (searchField.length() > 0) searchField.setText("")
                else {
                    val all = groupAdapter.items.indexOfFirst { it.id == ALL }
                    selectGroup(all, focusChannels = true)
                }
                return true
            }
        }
        return false
    }

    private fun openGroups() {
        groupsColumn.visibility = View.VISIBLE
        focusPosition(groupsView, groupAdapter.activated.coerceAtLeast(0))
    }

    private fun page(list: RecyclerView?, pos: Int, dir: Int) {
        val rv = list ?: return
        val count = rv.adapter?.itemCount ?: return
        focusPosition(rv, (pos + dir * 8).coerceIn(0, count - 1))
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (panel.visibility == View.VISIBLE) return super.onKeyDown(keyCode, event)

        // Цифры — номер канала
        val digit = when (keyCode) {
            in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9 -> keyCode - KeyEvent.KEYCODE_0
            in KeyEvent.KEYCODE_NUMPAD_0..KeyEvent.KEYCODE_NUMPAD_9 -> keyCode - KeyEvent.KEYCODE_NUMPAD_0
            else -> -1
        }
        if (digit >= 0) { typeDigit(digit); return true }

        val inArchive = archive != null
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                val ch = current
                when {
                    playlist.channels.isEmpty() -> openSettings()
                    message.visibility == View.VISIBLE && ch != null -> { retries = 0; play(ch, archive) }
                    inArchive -> togglePause()
                    else -> showPanel()
                }
                return true
            }
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_CHANNEL_UP -> { step(+1); return true }
            KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_CHANNEL_DOWN -> { step(-1); return true }
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_MEDIA_REWIND -> {
                if (inArchive) seek(-30_000) else showPanel()
                return true
            }
            KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                if (inArchive) seek(+30_000) else showPanel(focusEpg = true)
                return true
            }
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_MEDIA_PLAY, KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                if (inArchive) togglePause()
                return true
            }
            KeyEvent.KEYCODE_GUIDE -> { showPanel(focusEpg = true); return true }
            KeyEvent.KEYCODE_INFO -> {
                if (infoBar.visibility == View.VISIBLE) infoBar.visibility = View.GONE else showInfo(8000)
                return true
            }
            KeyEvent.KEYCODE_SEARCH -> {
                if (playlist.channels.isEmpty()) return true
                showPanel()
                enterSearch()
                return true
            }
            KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_SETTINGS -> { openSettings(); return true }
            KeyEvent.KEYCODE_LAST_CHANNEL -> { lastChannel?.let { switchTo(it) }; return true }
        }
        return super.onKeyDown(keyCode, event)
    }

    @Deprecated("Обрабатываем «Назад» вручную ради двойного нажатия для выхода")
    override fun onBackPressed() {
        when {
            portrait && (channelsColumn.visibility != View.VISIBLE || searchMode) -> hidePanel()
            panel.visibility == View.VISIBLE && !portrait -> hidePanel()
            archive != null -> { current?.let { play(it, null) }; showToast("Прямой эфир") }
            infoBar.visibility == View.VISIBLE -> infoBar.visibility = View.GONE
            System.currentTimeMillis() - lastBackPress < 2000 -> {
                @Suppress("DEPRECATION")
                super.onBackPressed()
            }
            else -> {
                lastBackPress = System.currentTimeMillis()
                showToast("Нажмите «Назад» ещё раз, чтобы выйти")
            }
        }
    }

    private fun switchTo(ch: Channel) {
        if (current?.key != ch.key) lastChannel = current
        play(ch, null)
    }

    /** +1 — следующий канал, −1 — предыдущий (по кругу внутри текущей группы). */
    private fun step(dir: Int) {
        val list = playingList.ifEmpty { playlist.channels }
        if (list.isEmpty()) return
        val idx = list.indexOfFirst { it.key == current?.key }
        switchTo(list[if (idx < 0) 0 else Math.floorMod(idx + dir, list.size)])
    }

    private fun typeDigit(d: Int) {
        if (digits.length >= 4) digits = ""
        digits += d
        numberInput.text = digits
        numberInput.visibility = View.VISIBLE
        handler.removeCallbacks(applyDigits)
        handler.postDelayed(applyDigits, 1500)
    }

    private val applyDigits = Runnable {
        val n = digits.toIntOrNull()
        digits = ""
        numberInput.visibility = View.GONE
        val ch = n?.let { num -> playlist.channels.firstOrNull { it.number == num } }
        if (ch == null) { showToast("Канала с номером $n нет"); return@Runnable }
        playingGroupId = ALL
        playingList = playlist.channels
        switchTo(ch)
    }

    private fun togglePause() {
        player.playWhenReady = !player.playWhenReady
        showInfo(if (player.playWhenReady) 3000 else 0)
    }

    private fun seek(deltaMs: Long) {
        val arch = archive ?: return
        val max = (minOf(arch.stop, System.currentTimeMillis()) - arch.start).coerceAtLeast(0)
        player.seekTo((player.currentPosition + deltaMs).coerceIn(0, maxOf(0, max - 5000)))
        showInfo(4000)
    }

    // ------------------------------------------------------------ мелочи

    private fun openSettings() {
        startActivity(Intent(this, SettingsActivity::class.java))
    }

    private fun showMessage(text: String) {
        message.text = text
        message.visibility = View.VISIBLE
    }

    private val hideToast = Runnable { toast.visibility = View.GONE }

    private fun showToast(text: String) {
        toast.text = text
        toast.visibility = View.VISIBLE
        handler.removeCallbacks(hideToast)
        handler.postDelayed(hideToast, 3000)
    }

    private fun plural(n: Int, one: String, few: String, many: String): String {
        val m10 = n % 10
        val m100 = n % 100
        val w = when {
            m10 == 1 && m100 != 11 -> one
            m10 in 2..4 && m100 !in 12..14 -> few
            else -> many
        }
        return "$n $w"
    }

    private fun startOfDay(t: Long): Long = Calendar.getInstance().apply {
        timeInMillis = t
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun dayLabel(t: Long, now: Long): String {
        val diff = Math.round((startOfDay(t) - startOfDay(now)) / (24 * 60 * 60 * 1000.0))
        return when (diff) {
            0L -> "Сегодня"
            -1L -> "Вчера"
            1L -> "Завтра"
            else -> dayFormat.format(Date(t)).replaceFirstChar { it.uppercase() }
        }
    }

    private fun formatDuration(ms: Long): String {
        val s = ms / 1000
        return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s % 3600 / 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
    }

    companion object {
        private const val ALL = "\u0000all"
        private const val FAV = "\u0000fav"
        private const val SEARCH = "\u0000search"
        private const val SETTINGS = "\u0000settings"
        private const val PLUGINS = "\u0000plugins"
    }
}
