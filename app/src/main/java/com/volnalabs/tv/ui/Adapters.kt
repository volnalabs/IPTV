package com.volnalabs.tv.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.volnalabs.tv.R
import com.volnalabs.tv.data.Channel
import com.volnalabs.tv.data.EpgData
import com.volnalabs.tv.data.Programme
import java.text.SimpleDateFormat
import java.util.Locale

/** Пункт левой колонки. */
data class GroupItem(
    val id: String,
    val title: String,
    val channels: List<Channel>,
    /** Значок слева (0 — без значка). */
    val icon: Int = 0,
    /** Пункт-действие («Поиск», «Настройки»), а не группа каналов. */
    val isAction: Boolean = false,
)

class GroupAdapter(
    private val onFocus: (Int) -> Unit,
    private val onClick: (Int) -> Unit,
    /** Строка для колонки на телевизоре или «таблетка» для сенсорного экрана. */
    private val layout: Int = R.layout.item_group,
) : RecyclerView.Adapter<GroupAdapter.VH>() {

    var items: List<GroupItem> = emptyList()
        private set
    var activated = -1
        private set

    fun submit(list: List<GroupItem>, active: Int) {
        items = list; activated = active
        notifyDataSetChanged()
    }

    fun setActivated(pos: Int) {
        val old = activated
        activated = pos
        if (old in items.indices) notifyItemChanged(old, PAYLOAD)
        if (pos in items.indices) notifyItemChanged(pos, PAYLOAD)
    }

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val icon: ImageView = v.findViewById(R.id.icon)
        val name: TextView = v.findViewById(R.id.name)
        val count: TextView = v.findViewById(R.id.count)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(LayoutInflater.from(parent.context).inflate(layout, parent, false))

    override fun getItemCount() = items.size

    override fun onBindViewHolder(h: VH, position: Int, payloads: MutableList<Any>) {
        if (payloads.isNotEmpty()) {
            h.itemView.isActivated = position == activated
            return
        }
        onBindViewHolder(h, position)
    }

    override fun onBindViewHolder(h: VH, position: Int) {
        val item = items[position]
        h.name.text = item.title
        h.count.text = if (item.isAction) "" else item.channels.size.toString()
        // У групп из плейлиста своего значка нет: в колонке ставим точку, в «таблетке» обходимся без него
        if (item.icon != 0) { h.icon.setImageResource(item.icon); h.icon.visibility = View.VISIBLE }
        else if (layout == R.layout.item_group) { h.icon.setImageResource(R.drawable.ic_dot); h.icon.visibility = View.VISIBLE }
        else h.icon.visibility = View.GONE
        h.itemView.isActivated = position == activated
        h.itemView.setOnFocusChangeListener { _, has -> if (has) onFocus(h.bindingAdapterPosition) }
        h.itemView.setOnClickListener { onClick(h.bindingAdapterPosition) }
    }

    companion object { const val PAYLOAD = "state" }
}

class ChannelAdapter(
    private val onFocus: (Channel, Int) -> Unit,
    private val onClick: (Channel) -> Unit,
    private val onLongClick: (Channel) -> Unit,
) : RecyclerView.Adapter<ChannelAdapter.VH>() {

    /** Сенсорный экран: звёздочка в строке — кнопка «в избранное», её видно всегда. */
    var touch = false

    var items: List<Channel> = emptyList()
        private set
    var playingKey: String? = null
    var favorites: Set<String> = emptySet()
    var epg: EpgData = EpgData.EMPTY

    fun submit(list: List<Channel>) {
        items = list
        notifyDataSetChanged()
    }

    fun refreshVisible() = notifyItemRangeChanged(0, items.size, PAYLOAD)

    fun indexOf(key: String?): Int = items.indexOfFirst { it.key == key }

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val number: TextView = v.findViewById(R.id.number)
        val logo: ImageView = v.findViewById(R.id.logo)
        val name: TextView = v.findViewById(R.id.name)
        val now: TextView = v.findViewById(R.id.now)
        val progress: ProgressBar = v.findViewById(R.id.progress)
        val marks: ImageView = v.findViewById(R.id.marks)
        val playing: ImageView = v.findViewById(R.id.playing)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_channel, parent, false))

    override fun getItemCount() = items.size

    override fun onBindViewHolder(h: VH, position: Int, payloads: MutableList<Any>) {
        if (payloads.isNotEmpty()) bindDynamic(h, items[position]) else onBindViewHolder(h, position)
    }

    override fun onBindViewHolder(h: VH, position: Int) {
        val ch = items[position]
        h.number.text = ch.number.toString()
        h.name.text = ch.name
        h.logo.loadLogo(ch)
        bindDynamic(h, ch)
        h.itemView.setOnFocusChangeListener { _, has ->
            if (has) h.bindingAdapterPosition.takeIf { it >= 0 }?.let { onFocus(items[it], it) }
        }
        h.itemView.setOnClickListener { h.bindingAdapterPosition.takeIf { it >= 0 }?.let { onClick(items[it]) } }
        h.itemView.setOnLongClickListener {
            h.bindingAdapterPosition.takeIf { it >= 0 }?.let { onLongClick(items[it]) }
            true
        }
        if (touch) h.marks.setOnClickListener { h.bindingAdapterPosition.takeIf { it >= 0 }?.let { onLongClick(items[it]) } }
    }

    private fun bindDynamic(h: VH, ch: Channel) {
        h.itemView.isActivated = ch.key == playingKey
        val now = System.currentTimeMillis()
        val p = epg.forChannel(ch)?.now(now)
        if (p != null) {
            h.now.visibility = View.VISIBLE
            h.progress.visibility = View.VISIBLE
            h.now.text = p.title
            h.progress.progress = ((now - p.start) * 1000 / (p.stop - p.start).coerceAtLeast(1)).toInt()
        } else {
            h.now.visibility = View.GONE
            h.progress.visibility = View.GONE
        }
        val fav = ch.key in favorites
        h.playing.visibility = if (ch.key == playingKey) View.VISIBLE else View.GONE
        h.marks.visibility = if (fav || touch) View.VISIBLE else View.GONE
        h.marks.setImageResource(if (fav) R.drawable.ic_star_filled else R.drawable.ic_star)
        h.marks.imageTintList = ContextCompat.getColorStateList(h.marks.context, if (fav) R.color.star_tint else R.color.text_dim)
    }

    companion object { const val PAYLOAD = "dyn" }
}

sealed class ProgRow {
    data class Day(val label: String) : ProgRow()
    data class Item(val p: Programme, val isNow: Boolean, val isPast: Boolean, val archive: Boolean) : ProgRow()
}

class ProgrammeAdapter(
    private val onClick: (ProgRow.Item) -> Unit,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    var rows: List<ProgRow> = emptyList()
        private set
    var playingStart: Long = -1

    fun submit(list: List<ProgRow>) {
        rows = list
        notifyDataSetChanged()
    }

    private val time = SimpleDateFormat("HH:mm", Locale("ru"))

    override fun getItemViewType(position: Int) = if (rows[position] is ProgRow.Day) 0 else 1

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inf = LayoutInflater.from(parent.context)
        return if (viewType == 0) object : RecyclerView.ViewHolder(inf.inflate(R.layout.item_day, parent, false)) {}
        else object : RecyclerView.ViewHolder(inf.inflate(R.layout.item_programme, parent, false)) {}
    }

    override fun getItemCount() = rows.size

    override fun onBindViewHolder(h: RecyclerView.ViewHolder, position: Int) {
        when (val row = rows[position]) {
            is ProgRow.Day -> (h.itemView as TextView).text = row.label
            is ProgRow.Item -> {
                val v = h.itemView
                val ctx = v.context
                v.findViewById<TextView>(R.id.time).text = time.format(row.p.start)
                val title = v.findViewById<TextView>(R.id.title)
                title.text = row.p.title
                title.setTextColor(
                    ContextCompat.getColorStateList(ctx, if (row.isPast && !row.archive) R.color.text_dim else R.color.text_main)
                )
                val badge = v.findViewById<TextView>(R.id.badge)
                val badgeIcon = v.findViewById<ImageView>(R.id.badgeIcon)
                val progress = v.findViewById<ProgressBar>(R.id.progress)
                val now = System.currentTimeMillis()
                fun icon(res: Int, color: Int) {
                    if (res == 0) { badgeIcon.visibility = View.GONE; return }
                    badgeIcon.setImageResource(res)
                    badgeIcon.imageTintList = ContextCompat.getColorStateList(ctx, color)
                    badgeIcon.visibility = View.VISIBLE
                }
                when {
                    row.p.start == playingStart -> icon(R.drawable.ic_play, R.color.accent)
                    row.isPast && row.archive -> icon(R.drawable.ic_history, R.color.archive_icon)
                    else -> icon(0, 0)
                }
                // Передача в эфире: метка и полоска «сколько прошло»
                badge.visibility = if (row.isNow) View.VISIBLE else View.GONE
                progress.visibility = if (row.isNow) View.VISIBLE else View.GONE
                if (row.isNow) progress.progress = ((now - row.p.start) * 1000 / (row.p.stop - row.p.start).coerceAtLeast(1)).toInt()
                v.isActivated = row.isNow || row.p.start == playingStart
                v.setOnClickListener { onClick(row) }
            }
        }
    }
}

/** Логотип канала из плейлиста; пока грузится или если его нет — буква на цветной плашке. */
fun ImageView.loadLogo(ch: Channel) {
    val letter = LetterDrawable(ch.name, resources.displayMetrics.density)
    if (ch.logo.isNullOrBlank()) {
        coil.util.CoilUtils.dispose(this)
        setImageDrawable(letter)
    } else {
        load(ch.logo) {
            crossfade(false)
            allowHardware(false)
            placeholder(letter)
            error(letter)
        }
    }
}
