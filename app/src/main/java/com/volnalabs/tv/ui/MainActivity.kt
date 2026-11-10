package com.volnalabs.tv.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.view.ViewTreeObserver
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
import androidx.media3.ui.PlayerView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.volnalabs.tv.R
import com.volnalabs.tv.data.Catchup
import com.volnalabs.tv.data.Channel
import com.volnalabs.tv.data.EpgData
import com.volnalabs.tv.data.Http
import com.volnalabs.tv.data.LoadState
import com.volnalabs.tv.data.Playlist
import com.volnalabs.tv.data.Programme
import com.volnalabs.tv.data.Repo
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
    private lateinit var loading: ProgressBar
    private lateinit var message: TextView
    private lateinit var toast: TextView
    private lateinit var numberInput: TextView

    private lateinit var infoBar: View
    private lateinit var infoLogo: ImageView
    private lateinit var infoNumber: TextView
    private lateinit var infoName: TextView
    private lateinit var infoBadge: TextView
    private lateinit var infoClock: TextView
    private lateinit var infoNow: TextView
    private lateinit var infoProgress: ProgressBar
    private lateinit var infoNext: TextView
    private lateinit var infoHint: TextView

    private lateinit var panel: View
    private lateinit var groupsView: RecyclerView
    private lateinit var channelsView: RecyclerView
    private lateinit var programmesView: RecyclerView
    private lateinit var channelsTitle: TextView
    private lateinit var epgTitle: TextView
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

    private var retries = 0
    private var forcedHls = false
    private var lastBackPress = 0L
    private var digits = ""

    private val clock = SimpleDateFormat("HH:mm", Locale("ru"))

    // ------------------------------------------------------------ жизненный цикл

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        bindViews()
        setupPlayer()
        setupLists()

        scope.launch { Repo.playlist.collect { onPlaylist(it) } }
        scope.launch { Repo.epg.collect { onEpg(it) } }
        scope.launch { Repo.playlistState.collect { onPlaylistState(it) } }

        if (Repo.prefs.playlistUrl.isEmpty()) openSettings()
        handler.post(ticker)
    }

    override fun onStart() {
        super.onStart()
        if (current != null && !player.isPlaying) {
            current?.let { play(it, archive) }
        }
    }

    override fun onStop() {
        super.onStop()
        player.stop()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        scope.cancel()
        player.release()
        super.onDestroy()
    }

    private fun bindViews() {
        playerView = findViewById(R.id.player)
        loading = findViewById(R.id.loading)
        message = findViewById(R.id.message)
        toast = findViewById(R.id.toast)
        numberInput = findViewById(R.id.numberInput)
        infoBar = findViewById(R.id.infoBar)
        infoLogo = findViewById(R.id.infoLogo)
        infoNumber = findViewById(R.id.infoNumber)
        infoName = findViewById(R.id.infoName)
        infoBadge = findViewById(R.id.infoBadge)
        infoClock = findViewById(R.id.infoClock)
        infoNow = findViewById(R.id.infoNow)
        infoProgress = findViewById(R.id.infoProgress)
        infoNext = findViewById(R.id.infoNext)
        infoHint = findViewById(R.id.infoHint)
        panel = findViewById(R.id.panel)
        groupsView = findViewById(R.id.groups)
        channelsView = findViewById(R.id.channels)
        programmesView = findViewById(R.id.programmes)
        channelsTitle = findViewById(R.id.channelsTitle)
        epgTitle = findViewById(R.id.epgTitle)
        epgEmpty = findViewById(R.id.epgEmpty)
    }

    // ------------------------------------------------------------ плеер

    private fun setupPlayer() {
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(15_000, 50_000, 1_500, 3_000)
            .build()
        player = ExoPlayer.Builder(this)
            .setLoadControl(loadControl)
            .build()
        playerView.player = player
        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                loading.visibility = if (state == Player.STATE_BUFFERING) View.VISIBLE else View.GONE
                if (state == Player.STATE_READY) {
                    retries = 0
                    message.visibility = View.GONE
                }
                if (state == Player.STATE_ENDED && archive != null) {
                    showToast("Архив закончился — возвращаюсь в эфир")
                    current?.let { play(it, null) }
                }
            }

            override fun onPlayerError(error: PlaybackException) = handleError(error)
        })
    }

    private fun play(ch: Channel, programme: Programme?) {
        val url = if (programme != null) Catchup.url(ch, programme.start, programme.stop) ?: ch.url else ch.url
        val isNewChannel = current?.key != ch.key || archive != programme
        current = ch
        archive = programme
        if (isNewChannel) { retries = 0; forcedHls = false }
        if (programme == null) Repo.prefs.lastChannelKey = ch.key

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

        channelAdapter.playingKey = ch.key
        channelAdapter.refreshVisible()
        programmeAdapter.playingStart = programme?.start ?: -1
        if (epgChannel?.key == ch.key) programmeAdapter.notifyDataSetChanged()
        showInfo()
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
            val delay = 2000L * retries
            showMessage("Канал не отвечает, повторяю… ($retries из 3)")
            handler.postDelayed({ if (current?.key == ch.key) play(ch, archive) }, delay)
        } else {
            val reason = when (error.errorCode) {
                PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> "сервер вернул ошибку"
                PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
                PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> "нет связи с сервером"
                PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
                PlaybackException.ERROR_CODE_DECODER_INIT_FAILED -> "формат не поддерживается этим ТВ"
                else -> error.errorCodeName
            }
            showMessage("Не удалось включить «${ch.name}»: $reason.\nНажмите ОК, чтобы попробовать снова.")
        }
    }

    // ------------------------------------------------------------ данные

    private fun onPlaylist(p: Playlist) {
        playlist = p
        rebuildGroups(keepFocus = false)
        if (p.channels.isEmpty()) {
            current = null
            player.stop()
            if (Repo.prefs.playlistUrl.isEmpty()) {
                showMessage("Добавьте ссылку на плейлист в настройках.\nНажмите ОК.")
            }
            return
        }
        // Если играющий канал остался в новом плейлисте — обновляем ссылку на объект.
        val cur = current
        if (cur != null) {
            val same = p.channels.firstOrNull { it.key == cur.key }
            if (same != null) {
                current = same
                playingList = groupChannels(playingGroupId) ?: p.channels
                return
            }
        }
        val last = Repo.prefs.lastChannelKey
        val ch = p.channels.firstOrNull { it.key == last } ?: p.channels.first()
        playingGroupId = Repo.prefs.lastGroup?.takeIf { groupChannels(it)?.any { c -> c.key == ch.key } == true } ?: ALL
        playingList = groupChannels(playingGroupId) ?: p.channels
        play(ch, null)
    }

    private fun onEpg(data: EpgData) {
        epg = data
        channelAdapter.epg = data
        channelAdapter.refreshVisible()
        epgChannel?.let { showProgrammes(it, focus = false) }
        if (infoBar.visibility == View.VISIBLE) updateInfo()
    }

    private fun onPlaylistState(state: LoadState) {
        when (state) {
            is LoadState.Loading -> if (playlist.channels.isEmpty()) showMessage(state.message)
            is LoadState.Error -> if (playlist.channels.isEmpty()) {
                showMessage("${state.message}\n\nНажмите ОК, чтобы открыть настройки.")
            } else showToast(state.message)
            else -> Unit
        }
    }

    // ------------------------------------------------------------ панель каналов

    private fun setupLists() {
        groupAdapter = GroupAdapter(
            onFocus = { pos -> handler.removeCallbacks(groupFocusRunnable); pendingGroup = pos; handler.postDelayed(groupFocusRunnable, 120) },
            onClick = { pos -> onGroupClick(pos) },
        )
        channelAdapter = ChannelAdapter(
            onFocus = { ch, _ -> pendingEpgChannel = ch; handler.removeCallbacks(epgFocusRunnable); handler.postDelayed(epgFocusRunnable, 150) },
            onClick = { ch -> onChannelClick(ch) },
            onLongClick = { ch -> toggleFavorite(ch) },
        )
        programmeAdapter = ProgrammeAdapter { row -> onProgrammeClick(row) }

        for ((rv, adapter) in listOf(groupsView to groupAdapter, channelsView to channelAdapter, programmesView to programmeAdapter)) {
            rv.layoutManager = LinearLayoutManager(this)
            rv.adapter = adapter
            rv.itemAnimator = null
            rv.setItemViewCacheSize(20)
        }
    }

    private var pendingGroup = -1
    private val groupFocusRunnable = Runnable { selectGroup(pendingGroup, focusChannels = false) }
    private var pendingEpgChannel: Channel? = null
    private val epgFocusRunnable = Runnable { pendingEpgChannel?.let { showProgrammes(it, focus = false) } }

    private fun groupChannels(id: String?): List<Channel>? = when (id) {
        null -> null
        ALL -> playlist.channels
        FAV -> Repo.prefs.favorites.let { fav -> playlist.channels.filter { it.key in fav } }
        else -> playlist.channels.filter { it.group == id }.takeIf { it.isNotEmpty() }
    }

    private fun rebuildGroups(keepFocus: Boolean) {
        val fav = Repo.prefs.favorites
        val items = ArrayList<GroupItem>()
        val favChannels = playlist.channels.filter { it.key in fav }
        if (favChannels.isNotEmpty()) items += GroupItem(FAV, "★ Избранное", favChannels)
        if (playlist.channels.isNotEmpty()) items += GroupItem(ALL, "Все каналы", playlist.channels)
        val byGroup = playlist.channels.groupBy { it.group }
        for (g in playlist.groups) items += GroupItem(g, g, byGroup[g].orEmpty())
        items += GroupItem(SETTINGS, "⚙ Настройки", emptyList(), isSettings = true)

        val activeId = groupAdapter.items.getOrNull(groupAdapter.activated)?.id ?: playingGroupId ?: ALL
        val active = items.indexOfFirst { it.id == activeId }.takeIf { it >= 0 } ?: items.indexOfFirst { it.id == ALL }.coerceAtLeast(0)
        groupAdapter.submit(items, active)
        channelAdapter.favorites = fav
        if (!keepFocus) selectGroup(active, focusChannels = false)
        else {
            val list = items.getOrNull(active)?.channels.orEmpty()
            if (list != channelAdapter.items) channelAdapter.submit(list)
            else channelAdapter.refreshVisible()
        }
    }

    private fun selectGroup(pos: Int, focusChannels: Boolean) {
        val item = groupAdapter.items.getOrNull(pos) ?: return
        if (item.isSettings) return
        groupAdapter.setActivated(pos)
        channelsTitle.text = "${item.title} · ${item.channels.size}"
        if (channelAdapter.items !== item.channels) channelAdapter.submit(item.channels)
        if (focusChannels) {
            val idx = channelAdapter.indexOf(current?.key).coerceAtLeast(0)
            focusPosition(channelsView, idx)
        }
    }

    private fun onGroupClick(pos: Int) {
        val item = groupAdapter.items.getOrNull(pos) ?: return
        if (item.isSettings) { hidePanel(); openSettings(); return }
        handler.removeCallbacks(groupFocusRunnable)
        selectGroup(pos, focusChannels = true)
    }

    private fun onChannelClick(ch: Channel) {
        val group = groupAdapter.items.getOrNull(groupAdapter.activated)
        if (current?.key == ch.key && archive == null) {
            hidePanel()
            if (player.playbackState == Player.STATE_IDLE || message.visibility == View.VISIBLE) play(ch, null)
            return
        }
        playingGroupId = group?.id ?: ALL
        playingList = group?.channels ?: playlist.channels
        Repo.prefs.lastGroup = playingGroupId
        hidePanel()
        play(ch, null)
    }

    private fun toggleFavorite(ch: Channel) {
        val added = Repo.prefs.toggleFavorite(ch.key)
        showToast(if (added) "«${ch.name}» добавлен в избранное" else "«${ch.name}» убран из избранного")
        val focusedKey = ch.key
        rebuildGroups(keepFocus = true)
        val idx = channelAdapter.indexOf(focusedKey)
        if (idx >= 0) focusPosition(channelsView, idx)
    }

    private fun showProgrammes(ch: Channel, focus: Boolean) {
        epgChannel = ch
        epgTitle.text = ch.name
        val e = epg.forChannel(ch)
        val now = System.currentTimeMillis()
        if (e == null || e.size == 0) {
            programmeAdapter.submit(emptyList())
            epgEmpty.visibility = View.VISIBLE
            epgEmpty.text = when (Repo.epgState.value) {
                is LoadState.Loading -> "Телепрограмма загружается…"
                else -> "Нет телепрограммы для этого канала"
            }
            return
        }
        epgEmpty.visibility = View.GONE
        val day = 24 * 60 * 60 * 1000L
        val from = if (ch.hasArchive) now - ch.catchupDays * day else startOfDay(now)
        val list = e.between(from, now + day)
        val rows = ArrayList<ProgRow>()
        var lastDay = ""
        var nowIndex = 0
        val archiveFrom = now - ch.catchupDays * day
        for (p in list) {
            val label = dayLabel(p.start, now)
            if (label != lastDay) { rows += ProgRow.Day(label); lastDay = label }
            val isNow = p.isNow(now)
            if (isNow) nowIndex = rows.size
            rows += ProgRow.Item(p, isNow, p.isPast(now), ch.hasArchive && p.start >= archiveFrom)
        }
        programmeAdapter.playingStart = if (current?.key == ch.key) archive?.start ?: -1 else -1
        programmeAdapter.submit(rows)
        val target = if (current?.key == ch.key && archive != null) {
            rows.indexOfFirst { it is ProgRow.Item && it.p.start == archive?.start }.takeIf { it >= 0 } ?: nowIndex
        } else nowIndex
        if (focus) focusPosition(programmesView, target)
        else (programmesView.layoutManager as LinearLayoutManager).scrollToPositionWithOffset(target, 120)
    }

    private fun onProgrammeClick(row: ProgRow.Item) {
        val ch = epgChannel ?: return
        when {
            row.isNow -> { onChannelClick(ch) }
            row.isPast && row.archive -> {
                if (current?.key != ch.key) {
                    val group = groupAdapter.items.getOrNull(groupAdapter.activated)
                    playingGroupId = group?.id ?: ALL
                    playingList = group?.channels ?: playlist.channels
                }
                hidePanel()
                play(ch, row.p)
            }
            row.isPast -> showToast("Для этой передачи архива нет")
            else -> showToast("Передача начнётся в ${clock.format(Date(row.p.start))}")
        }
    }

    private fun showPanel(focusEpg: Boolean = false) {
        if (playlist.channels.isEmpty()) { openSettings(); return }
        infoBar.visibility = View.GONE
        panel.visibility = View.VISIBLE
        // Открываем на группе текущего канала
        val gpos = groupAdapter.items.indexOfFirst { it.id == (playingGroupId ?: ALL) }.takeIf { it >= 0 } ?: groupAdapter.activated
        selectGroup(gpos, focusChannels = false)
        channelAdapter.refreshVisible()
        val idx = channelAdapter.indexOf(current?.key).coerceAtLeast(0)
        val ch = current
        if (ch != null) showProgrammes(ch, focus = focusEpg)
        if (!focusEpg) focusPosition(channelsView, idx)
    }

    private fun hidePanel() {
        panel.visibility = View.GONE
        playerView.requestFocus()
    }

    private fun focusPosition(rv: RecyclerView, pos: Int, attempt: Int = 0) {
        val count = rv.adapter?.itemCount ?: 0
        if (count == 0) { rv.requestFocus(); return }
        var p = pos.coerceIn(0, count - 1)
        // В программе заголовки дней не фокусируются — шагаем к ближайшей передаче.
        if (rv === programmesView) {
            while (p < count - 1 && programmeAdapter.rows[p] is ProgRow.Day) p++
        }
        val lm = rv.layoutManager as LinearLayoutManager
        val vh = rv.findViewHolderForAdapterPosition(p)
        if (vh != null && vh.itemView.isShown) {
            vh.itemView.requestFocus()
            return
        }
        if (attempt >= 3) { rv.requestFocus(); return }
        lm.scrollToPositionWithOffset(p, rv.height / 3)
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
        if (ch.logo != null) infoLogo.load(ch.logo) { crossfade(false); allowHardware(false) } else infoLogo.setImageDrawable(null)

        val arch = archive
        val e = epg.forChannel(ch)
        if (arch != null) {
            infoBadge.text = "АРХИВ"
            infoBadge.setTextColor(ContextCompat.getColor(this, R.color.archive))
            infoNow.text = "${clock.format(Date(arch.start))}–${clock.format(Date(arch.stop))}  ${arch.title}"
            val dur = (arch.stop - arch.start).coerceAtLeast(1)
            val pos = player.currentPosition.coerceAtLeast(0)
            infoProgress.visibility = View.VISIBLE
            infoProgress.progress = (pos * 1000 / dur).toInt().coerceIn(0, 1000)
            infoNext.text = "${formatDuration(pos)} / ${formatDuration(dur)}" + if (!player.playWhenReady) "   ❚❚ пауза" else ""
            infoHint.text = "◀ ▶ перемотка · ОК пауза · Назад — в эфир"
        } else {
            infoBadge.text = "● ЭФИР"
            infoBadge.setTextColor(ContextCompat.getColor(this, R.color.live))
            val p = e?.now(now)
            if (p != null) {
                infoNow.text = "${clock.format(Date(p.start))}–${clock.format(Date(p.stop))}  ${p.title}"
                infoProgress.visibility = View.VISIBLE
                infoProgress.progress = ((now - p.start) * 1000 / (p.stop - p.start).coerceAtLeast(1)).toInt()
            } else {
                infoNow.text = ch.group
                infoProgress.visibility = View.GONE
            }
            val next = e?.next(now)
            infoNext.text = next?.let { "Далее ${clock.format(Date(it.start))}  ${it.title}" } ?: ""
            infoHint.text = if (ch.hasArchive) "ОК — каналы · ▲▼ переключение · ⟲ архив ${ch.catchupDays} дн." else "ОК — каналы · ▲▼ переключение"
        }
    }

    private val ticker = object : Runnable {
        override fun run() {
            if (infoBar.visibility == View.VISIBLE) updateInfo()
            handler.postDelayed(this, if (archive != null) 1000 else 30_000)
        }
    }

    // ------------------------------------------------------------ пульт

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN && panel.visibility == View.VISIBLE) {
            if (handlePanelKey(event)) return true
        }
        return super.dispatchKeyEvent(event)
    }

    /** Переходы между колонками панели и упор в края списков. */
    private fun handlePanelKey(event: KeyEvent): Boolean {
        val focused = window.decorView.findFocus()
        val list = containingList(focused)
        val pos = focused?.let { list?.getChildAdapterPosition(it) } ?: RecyclerView.NO_POSITION
        when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                when (list) {
                    channelsView -> focusPosition(groupsView, groupAdapter.activated)
                    programmesView -> {
                        val idx = channelAdapter.indexOf(epgChannel?.key).takeIf { it >= 0 } ?: 0
                        focusPosition(channelsView, idx)
                    }
                    groupsView -> Unit
                    else -> focusPosition(channelsView, 0)
                }
                return true
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                when (list) {
                    groupsView -> {
                        handler.removeCallbacks(groupFocusRunnable)
                        selectGroup(pos, focusChannels = false)
                        val idx = channelAdapter.indexOf(current?.key).coerceAtLeast(0)
                        focusPosition(channelsView, idx)
                    }
                    channelsView -> {
                        val ch = channelAdapter.items.getOrNull(pos)
                        if (ch != null) {
                            handler.removeCallbacks(epgFocusRunnable)
                            showProgrammes(ch, focus = true)
                        }
                    }
                }
                return true
            }
            KeyEvent.KEYCODE_DPAD_UP -> if (list != null && pos == 0) return true
            KeyEvent.KEYCODE_DPAD_DOWN ->
                if (list != null && pos == (list.adapter?.itemCount ?: 0) - 1) return true
            KeyEvent.KEYCODE_MENU -> {
                if (list === channelsView) channelAdapter.items.getOrNull(pos)?.let { toggleFavorite(it) }
                else { hidePanel(); openSettings() }
                return true
            }
            KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ESCAPE -> { hidePanel(); return true }
            KeyEvent.KEYCODE_PAGE_DOWN, KeyEvent.KEYCODE_CHANNEL_DOWN -> { page(list, pos, +1); return true }
            KeyEvent.KEYCODE_PAGE_UP, KeyEvent.KEYCODE_CHANNEL_UP -> { page(list, pos, -1); return true }
        }
        return false
    }

    private fun page(list: RecyclerView?, pos: Int, dir: Int) {
        val rv = list ?: return
        val count = rv.adapter?.itemCount ?: return
        focusPosition(rv, (pos + dir * 10).coerceIn(0, count - 1))
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
                if (playlist.channels.isEmpty()) { openSettings(); return true }
                if (message.visibility == View.VISIBLE && current != null) { retries = 0; play(current!!, archive); return true }
                if (inArchive) togglePause() else showPanel()
                return true
            }
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_CHANNEL_UP -> { step(+1); return true }
            KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_CHANNEL_DOWN -> { step(-1); return true }
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_MEDIA_REWIND -> {
                if (inArchive) seek(-30_000) else showPanel()
                return true
            }
            KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                if (inArchive) seek(+30_000) else showInfo()
                return true
            }
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_MEDIA_PLAY, KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                if (inArchive) togglePause()
                return true
            }
            KeyEvent.KEYCODE_GUIDE, KeyEvent.KEYCODE_TV_INPUT -> { showPanel(focusEpg = true); return true }
            KeyEvent.KEYCODE_INFO -> {
                if (infoBar.visibility == View.VISIBLE) infoBar.visibility = View.GONE else showInfo(8000)
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
            panel.visibility == View.VISIBLE -> hidePanel()
            archive != null -> { current?.let { play(it, null) }; showToast("Прямой эфир") }
            infoBar.visibility == View.VISIBLE -> infoBar.visibility = View.GONE
            System.currentTimeMillis() - lastBackPress < 2000 -> {
                @Suppress("DEPRECATION")
                super.onBackPressed()
            }
            else -> {
                lastBackPress = System.currentTimeMillis()
                showToast("Нажмите «Назад» ещё раз для выхода")
            }
        }
    }

    private var lastChannel: Channel? = null

    private fun switchTo(ch: Channel) {
        if (current?.key != ch.key) lastChannel = current
        play(ch, null)
    }

    /** +1 — следующий канал (вверх по номеру), −1 — предыдущий. */
    private fun step(dir: Int) {
        val list = playingList.ifEmpty { playlist.channels }
        if (list.isEmpty()) return
        val idx = list.indexOfFirst { it.key == current?.key }
        val next = if (idx < 0) 0 else Math.floorMod(idx + dir, list.size)
        switchTo(list[next])
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
        val target = (player.currentPosition + deltaMs).coerceIn(0, maxOf(0, max - 5000))
        player.seekTo(target)
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

    private fun startOfDay(t: Long): Long = Calendar.getInstance().apply {
        timeInMillis = t
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private val dayFormat = SimpleDateFormat("EEEE, d MMMM", Locale("ru"))

    private fun dayLabel(t: Long, now: Long): String {
        val diff = ((startOfDay(t) - startOfDay(now)) / (24 * 60 * 60 * 1000.0)).let { Math.round(it) }
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
        private const val SETTINGS = "\u0000settings"
    }
}
