package com.volnalabs.tv.data

/**
 * Разбор M3U/M3U8-плейлистов IPTV.
 *
 * Поддерживает атрибуты #EXTINF (tvg-id, tvg-name, tvg-logo, group-title, catchup,
 * catchup-days, catchup-source, timeshift), строки #EXTGRP и #EXTVLCOPT
 * (http-user-agent, http-referrer), а также настройки архива из заголовка #EXTM3U.
 */
object M3uParser {

    private val ATTR = Regex("""([A-Za-z0-9_-]+)\s*=\s*"([^"]*)"""")

    fun parse(text: String): Playlist {
        val lines = text.replace("﻿", "").lineSequence()
        val channels = ArrayList<Channel>()
        val epgUrls = ArrayList<String>()

        var headerCatchup: String? = null
        var headerCatchupDays = 0
        var headerCatchupSource: String? = null

        var info: Map<String, String>? = null
        var name: String? = null
        var extGroup: String? = null
        var userAgent: String? = null
        var referrer: String? = null

        fun reset() {
            info = null; name = null; extGroup = null; userAgent = null; referrer = null
        }

        for (raw in lines) {
            val line = raw.trim()
            if (line.isEmpty()) continue

            when {
                line.startsWith("#EXTM3U", ignoreCase = true) -> {
                    val attrs = attributes(line)
                    (attrs["url-tvg"] ?: attrs["x-tvg-url"] ?: attrs["tvg-url"])
                        ?.split(',')
                        ?.map { it.trim() }
                        ?.filter { it.isNotEmpty() }
                        ?.let { epgUrls.addAll(it) }
                    headerCatchup = attrs["catchup"] ?: headerCatchup
                    headerCatchupDays = (attrs["catchup-days"] ?: attrs["timeshift"])?.toIntOrNull() ?: headerCatchupDays
                    headerCatchupSource = attrs["catchup-source"] ?: headerCatchupSource
                }

                line.startsWith("#EXTINF", ignoreCase = true) -> {
                    reset()
                    info = attributes(line)
                    name = titleOf(line)
                }

                line.startsWith("#EXTGRP:", ignoreCase = true) -> {
                    extGroup = line.substringAfter(':').trim().ifEmpty { null }
                }

                line.startsWith("#EXTVLCOPT:", ignoreCase = true) -> {
                    val opt = line.substringAfter(':')
                    val key = opt.substringBefore('=').trim().lowercase()
                    val value = opt.substringAfter('=', "").trim()
                    when (key) {
                        "http-user-agent" -> userAgent = value
                        "http-referrer", "http-referer" -> referrer = value
                    }
                }

                line.startsWith("#") -> Unit // прочие директивы пропускаем

                else -> {
                    val attrs = info ?: emptyMap()
                    val url = line
                    val group = attrs["group-title"]?.takeIf { it.isNotBlank() }
                        ?: extGroup
                        ?: "Без группы"

                    val catchupType = (attrs["catchup"] ?: attrs["catchup-type"] ?: headerCatchup)
                        ?.lowercase()?.takeIf { it.isNotBlank() }
                    val catchupDays = (attrs["catchup-days"] ?: attrs["timeshift"] ?: attrs["tvg-rec"])
                        ?.toIntOrNull() ?: headerCatchupDays

                    channels += Channel(
                        number = channels.size + 1,
                        name = name?.takeIf { it.isNotBlank() } ?: attrs["tvg-name"] ?: "Канал ${channels.size + 1}",
                        url = url,
                        group = group,
                        logo = attrs["tvg-logo"]?.takeIf { it.isNotBlank() },
                        tvgId = attrs["tvg-id"]?.takeIf { it.isNotBlank() },
                        tvgName = attrs["tvg-name"]?.takeIf { it.isNotBlank() },
                        catchup = if (catchupDays > 0 || catchupType != null) catchupType ?: "shift" else null,
                        catchupDays = if (catchupType != null && catchupDays == 0) 1 else catchupDays,
                        catchupSource = attrs["catchup-source"] ?: headerCatchupSource,
                        userAgent = userAgent,
                        referrer = referrer,
                    )
                    reset()
                }
            }
        }
        return Playlist(channels, epgUrls)
    }

    private fun attributes(line: String): Map<String, String> {
        val map = HashMap<String, String>()
        for (m in ATTR.findAll(line)) map[m.groupValues[1].lowercase()] = m.groupValues[2].trim()
        return map
    }

    /** Название канала — текст после первой запятой, которая стоит вне кавычек. */
    private fun titleOf(line: String): String {
        var inQuotes = false
        for (i in line.indices) {
            when (line[i]) {
                '"' -> inQuotes = !inQuotes
                ',' -> if (!inQuotes) return line.substring(i + 1).trim()
            }
        }
        return ""
    }
}
