package com.volnalabs.tv.data

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.InputStream
import java.io.InputStreamReader

/** Телепрограмма одного канала в компактном виде (массивы вместо объектов — экономим память ТВ). */
class EpgChannel(val starts: LongArray, val stops: LongArray, val titles: Array<String>, val descs: Array<String> = Array(starts.size) { "" }) {
    val size get() = starts.size

    operator fun get(i: Int) = Programme(starts[i], stops[i], titles[i], descs[i])

    /** Номер первой передачи, которая заканчивается после [time] (или size). */
    fun firstEndingAfter(time: Long): Int {
        var lo = 0
        var hi = size - 1
        var found = size
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            if (stops[mid] > time) { found = mid; hi = mid - 1 } else lo = mid + 1
        }
        return found
    }

    /** Индекс передачи, идущей в момент [time], или -1. */
    fun indexAt(time: Long): Int {
        var lo = 0
        var hi = size - 1
        var found = -1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            if (starts[mid] <= time) { found = mid; lo = mid + 1 } else hi = mid - 1
        }
        return if (found >= 0 && stops[found] > time) found else -1
    }

    fun now(time: Long): Programme? = indexAt(time).takeIf { it >= 0 }?.let { get(it) }

    fun next(time: Long): Programme? {
        for (i in 0 until size) if (starts[i] > time) return get(i)
        return null
    }

    fun between(from: Long, to: Long): List<Programme> {
        val out = ArrayList<Programme>()
        for (i in 0 until size) if (stops[i] > from && starts[i] < to) out += get(i)
        return out
    }
}

class EpgData(
    val byId: Map<String, EpgChannel>,
    val idByName: Map<String, String>,
    val updated: Long,
) {
    val programmeCount: Int get() = byId.values.sumOf { it.size }

    fun forChannel(ch: Channel): EpgChannel? {
        ch.tvgId?.let { id -> byId[id]?.let { return it } }
        for (n in listOfNotNull(ch.tvgName, ch.name)) {
            val id = idByName[normalize(n)] ?: continue
            byId[id]?.let { return it }
        }
        return null
    }

    fun write(out: DataOutputStream) {
        out.writeInt(VERSION)
        out.writeLong(updated)
        out.writeInt(byId.size)
        for ((id, ch) in byId) {
            out.writeUTF(id)
            out.writeInt(ch.size)
            for (i in 0 until ch.size) {
                out.writeLong(ch.starts[i])
                out.writeInt(((ch.stops[i] - ch.starts[i]) / 1000).toInt())
                out.writeUTF(ch.titles[i].take(300))
                out.writeUTF(ch.descs[i].take(400))
            }
        }
        out.writeInt(idByName.size)
        for ((name, id) in idByName) { out.writeUTF(name); out.writeUTF(id) }
    }

    companion object {
        private const val VERSION = 3
        val EMPTY = EpgData(emptyMap(), emptyMap(), 0)

        fun read(input: DataInputStream): EpgData {
            if (input.readInt() != VERSION) return EMPTY
            val updated = input.readLong()
            val count = input.readInt()
            val byId = HashMap<String, EpgChannel>(count * 2)
            repeat(count) {
                val id = input.readUTF()
                val n = input.readInt()
                val starts = LongArray(n)
                val stops = LongArray(n)
                val titles = arrayOfNulls<String>(n)
                val descs = arrayOfNulls<String>(n)
                for (i in 0 until n) {
                    starts[i] = input.readLong()
                    stops[i] = starts[i] + input.readInt() * 1000L
                    titles[i] = input.readUTF()
                    descs[i] = input.readUTF()
                }
                @Suppress("UNCHECKED_CAST")
                byId[id] = EpgChannel(starts, stops, titles as Array<String>, descs as Array<String>)
            }
            val names = input.readInt()
            val idByName = HashMap<String, String>(names * 2)
            repeat(names) { idByName[input.readUTF()] = input.readUTF() }
            return EpgData(byId, idByName, updated)
        }

        fun normalize(name: String): String =
            name.lowercase().filter { it.isLetterOrDigit() }
    }
}

/**
 * Потоковый разбор XMLTV. Файлы бывают по 150 МБ, поэтому:
 *  - читаем по одному тегу, не строя дерево;
 *  - берём только каналы из плейлиста;
 *  - храним только окно времени вокруг «сейчас» (для архива и на пару дней вперёд).
 */
object EpgParser {

    fun parse(
        input: InputStream,
        playlist: Playlist,
        now: Long = System.currentTimeMillis(),
        progress: ((Int) -> Unit)? = null,
    ): EpgData {
        val wantedIds = playlist.channels.mapNotNullTo(HashSet()) { it.tvgId }
        val wantedNames = HashSet<String>()
        playlist.channels.forEach { ch ->
            ch.tvgName?.let { wantedNames += EpgData.normalize(it) }
            wantedNames += EpgData.normalize(ch.name)
        }
        val maxDays = (playlist.channels.maxOfOrNull { it.catchupDays } ?: 0).coerceIn(1, 14)
        val from = now - (maxDays + 1) * DAY
        val to = now + 3 * DAY

        // Описания храним только для «сегодня ± 1 день» и не длиннее 400 символов — иначе растёт память
        val descFrom = now - now % DAY - 2 * DAY
        val descTo = now - now % DAY + 3 * DAY
        val keptIds = HashSet<String>()
        val idByName = HashMap<String, String>()
        val builders = HashMap<String, Builder>()

        val parser = XmlPullParserFactory.newInstance().newPullParser()
        runCatching { parser.setFeature("http://xmlpull.org/v1/doc/features.html#relaxed", true) }
        parser.setInput(InputStreamReader(input, Charsets.UTF_8))

        var count = 0
        try {
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG) {
                    when (parser.name) {
                        "channel" -> {
                            val id = parser.getAttributeValue(null, "id")
                            val names = readChannelNames(parser)
                            if (id != null) {
                                val normalized = names.map { EpgData.normalize(it) }
                                val matchedNames = normalized.filter { it in wantedNames }
                                if (id in wantedIds || matchedNames.isNotEmpty()) {
                                    keptIds += id
                                    for (n in matchedNames) idByName.putIfAbsent(n, id)
                                }
                            }
                        }

                        "programme" -> {
                            val id = parser.getAttributeValue(null, "channel")
                            if (id == null || (id !in keptIds && id !in wantedIds)) {
                                skip(parser)
                            } else {
                                val start = parseTime(parser.getAttributeValue(null, "start"))
                                val stop = parseTime(parser.getAttributeValue(null, "stop"))
                                val (title, desc) = readTitle(parser)
                                if (start > 0 && stop > start && stop > from && start < to) {
                                    builders.getOrPut(id) { Builder() }.add(start, stop, title, if (stop > descFrom && start < descTo) desc.take(400) else "")
                                }
                            }
                            if (++count % 5000 == 0) progress?.invoke(count)
                        }
                    }
                }
                event = parser.next()
            }
        } catch (e: Exception) {
            // Битый хвост файла: оставляем то, что успели прочитать.
            if (builders.isEmpty()) throw e
        }

        val byId = HashMap<String, EpgChannel>(builders.size * 2)
        for ((id, b) in builders) byId[id] = b.build()
        return EpgData(byId, idByName, now)
    }

    private fun readChannelNames(p: XmlPullParser): List<String> {
        val names = ArrayList<String>()
        val depth = p.depth
        var e = p.next()
        while (!(e == XmlPullParser.END_TAG && p.depth == depth) && e != XmlPullParser.END_DOCUMENT) {
            if (e == XmlPullParser.START_TAG && p.name == "display-name") {
                names += p.nextText().trim()
            }
            e = p.next()
        }
        return names
    }

    private fun readTitle(p: XmlPullParser): Pair<String, String> {
        var title: String? = null
        var desc: String? = null
        val depth = p.depth
        var e = p.next()
        while (!(e == XmlPullParser.END_TAG && p.depth == depth) && e != XmlPullParser.END_DOCUMENT) {
            if (e == XmlPullParser.START_TAG && p.name == "title" && title == null) {
                title = p.nextText().trim()
            } else if (e == XmlPullParser.START_TAG && p.name == "desc" && desc == null) {
                desc = p.nextText().trim()
            }
            e = p.next()
        }
        return (title ?: "") to (desc ?: "")
    }

    private fun skip(p: XmlPullParser) {
        var depth = 1
        while (depth > 0) {
            when (p.next()) {
                XmlPullParser.START_TAG -> depth++
                XmlPullParser.END_TAG -> depth--
                XmlPullParser.END_DOCUMENT -> return
            }
        }
    }

    /** «20261107142500 +0300» → миллисекунды UTC. Быстрее, чем SimpleDateFormat на 200 тыс. строк. */
    fun parseTime(s: String?): Long {
        if (s == null || s.length < 14) return 0
        return try {
            val y = s.substring(0, 4).toInt()
            val mo = s.substring(4, 6).toInt()
            val d = s.substring(6, 8).toInt()
            val h = s.substring(8, 10).toInt()
            val mi = s.substring(10, 12).toInt()
            val se = s.substring(12, 14).toInt()
            var offsetMin = 0
            val tz = s.substring(14).trim()
            if (tz.length >= 5 && (tz[0] == '+' || tz[0] == '-')) {
                val sign = if (tz[0] == '-') -1 else 1
                offsetMin = sign * (tz.substring(1, 3).toInt() * 60 + tz.substring(3, 5).toInt())
            }
            val days = daysFromCivil(y, mo, d)
            (days * 86400L + h * 3600L + mi * 60L + se - offsetMin * 60L) * 1000L
        } catch (e: NumberFormatException) {
            0
        }
    }

    /** Число дней от 1970-01-01 (алгоритм Говарда Хиннанта). */
    private fun daysFromCivil(year: Int, month: Int, day: Int): Long {
        val y = if (month <= 2) year - 1 else year
        val era = (if (y >= 0) y else y - 399) / 400
        val yoe = y - era * 400
        val mp = (month + 9) % 12
        val doy = (153 * mp + 2) / 5 + day - 1
        val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
        return era * 146097L + doe - 719468L
    }

    private const val DAY = 24 * 60 * 60 * 1000L

    private class Builder {
        val starts = ArrayList<Long>()
        val stops = ArrayList<Long>()
        val titles = ArrayList<String>()
        val descs = ArrayList<String>()

        fun add(start: Long, stop: Long, title: String, desc: String) {
            starts += start; stops += stop; titles += title; descs += desc
        }

        fun build(): EpgChannel {
            val order = starts.indices.sortedBy { starts[it] }
            // убираем дубли по времени начала
            val keep = ArrayList<Int>(order.size)
            var last = Long.MIN_VALUE
            for (i in order) if (starts[i] != last) { keep += i; last = starts[i] }
            return EpgChannel(
                LongArray(keep.size) { starts[keep[it]] },
                LongArray(keep.size) { stops[keep[it]] },
                Array(keep.size) { titles[keep[it]] },
                Array(keep.size) { descs[keep[it]] },
            )
        }
    }
}
