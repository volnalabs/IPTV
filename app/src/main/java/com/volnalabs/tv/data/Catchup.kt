package com.volnalabs.tv.data

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Строит ссылку на архив передачи по правилам плейлиста. */
object Catchup {

    /**
     * @param start начало передачи, мс UTC
     * @param stop конец передачи, мс UTC
     * @param now текущее время, мс UTC
     */
    fun url(channel: Channel, start: Long, stop: Long, now: Long = System.currentTimeMillis()): String? {
        if (!channel.hasArchive) return null
        val source = channel.catchupSource
        val type = channel.catchup ?: "shift"

        return when {
            type == "append" && !source.isNullOrBlank() ->
                channel.url + fill(source, start, stop, now)

            !source.isNullOrBlank() && (type == "default" || type == "flussonic" || type == "fs" || source.contains('{')) ->
                if (source.startsWith("http", ignoreCase = true)) fill(source, start, stop, now)
                else channel.url + fill(source, start, stop, now)

            type == "flussonic" || type == "fs" -> flussonic(channel.url, start, stop, now)

            // shift / archive / timeshift и всё неизвестное: ?utc=начало&lutc=сейчас
            else -> channel.url + sep(channel.url) + "utc=${start / 1000}&lutc=${now / 1000}"
        }
    }

    private fun sep(url: String) = if (url.contains('?')) "&" else "?"

    private fun flussonic(url: String, start: Long, stop: Long, now: Long): String {
        val s = start / 1000
        val d = ((minOf(stop, now) - start) / 1000).coerceAtLeast(60)
        val re = Regex("""/(index|video|mono|tracks-[^/]+)\.m3u8""")
        return when {
            re.containsMatchIn(url) -> url.replace(re) { "/${it.groupValues[1]}-$s-$d.m3u8" }
            url.contains("/mpegts") -> url.replace("/mpegts", "/timeshift_abs-$s.ts")
            else -> url + sep(url) + "utc=$s&lutc=${now / 1000}"
        }
    }

    private fun fill(template: String, start: Long, stop: Long, now: Long): String {
        val s = start / 1000
        val e = stop / 1000
        val n = now / 1000
        val duration = (stop - start) / 1000
        val offset = ((now - start) / 1000).coerceAtLeast(0)

        var out = template
            .replace("\${start}", s.toString())
            .replace("\${end}", e.toString())
            .replace("\${timestamp}", n.toString())
            .replace("\${duration}", duration.toString())
            .replace("\${offset}", offset.toString())
            .replace("{utc}", s.toString())
            .replace("{start}", s.toString())
            .replace("{utcend}", e.toString())
            .replace("{end}", e.toString())
            .replace("{lutc}", n.toString())
            .replace("{now}", n.toString())
            .replace("{timestamp}", n.toString())
            .replace("{duration}", duration.toString())
            .replace("{offset}", offset.toString())

        // {utc:YmdHMS}, {start:Y-m-d} и т.п.
        out = Regex("""\{(utc|start|end|utcend|lutc|now):([^}]+)\}""").replace(out) { m ->
            val t = when (m.groupValues[1]) {
                "end", "utcend" -> stop
                "lutc", "now" -> now
                else -> start
            }
            format(m.groupValues[2], t)
        }
        // Отдельные {Y} {m} {d} {H} {M} {S} — время начала
        val parts = mapOf("{Y}" to "yyyy", "{m}" to "MM", "{d}" to "dd", "{H}" to "HH", "{M}" to "mm", "{S}" to "ss")
        for ((k, p) in parts) if (out.contains(k)) out = out.replace(k, utc(p).format(Date(start)))
        return out
    }

    private fun format(pattern: String, time: Long): String {
        val sb = StringBuilder()
        for (c in pattern) {
            sb.append(
                when (c) {
                    'Y' -> utc("yyyy").format(Date(time))
                    'm' -> utc("MM").format(Date(time))
                    'd' -> utc("dd").format(Date(time))
                    'H' -> utc("HH").format(Date(time))
                    'M' -> utc("mm").format(Date(time))
                    'S' -> utc("ss").format(Date(time))
                    else -> c.toString()
                }
            )
        }
        return sb.toString()
    }

    private fun utc(p: String) = SimpleDateFormat(p, Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
}
