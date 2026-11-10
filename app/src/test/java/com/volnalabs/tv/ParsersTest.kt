package com.volnalabs.tv

import com.volnalabs.tv.data.Catchup
import com.volnalabs.tv.data.EpgParser
import com.volnalabs.tv.data.Http
import com.volnalabs.tv.data.M3uParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ParsersTest {

    private val sample = """
        #EXTM3U url-tvg="http://epg.example/full.xml.gz"
        #EXTINF:0 tvg-id="1" tvg-logo="http://img/1.png" catchup="shift" catchup-days="3" group-title="Федеральные", Первый канал
        #EXTGRP:Федеральные
        http://cdn.example/high/token/1.m3u8
        #EXTINF:-1 tvg-name="Кино, HD" group-title="Кино",Кино, HD
        #EXTVLCOPT:http-user-agent=MyAgent/1.0
        #EXTVLCOPT:http-referrer=http://ref.example/
        http://cdn.example/2.ts
        #EXTINF:-1,Без группы и атрибутов
        #EXTGRP:Разное
        http://cdn.example/3.m3u8?x=1
    """.trimIndent()

    @Test fun parsesPlaylist() {
        val p = M3uParser.parse(sample)
        assertEquals(listOf("http://epg.example/full.xml.gz"), p.epgUrls)
        assertEquals(3, p.channels.size)

        val c1 = p.channels[0]
        assertEquals(1, c1.number)
        assertEquals("Первый канал", c1.name)
        assertEquals("Федеральные", c1.group)
        assertEquals("1", c1.tvgId)
        assertEquals("shift", c1.catchup)
        assertEquals(3, c1.catchupDays)
        assertTrue(c1.hasArchive)

        val c2 = p.channels[1]
        assertEquals("Кино, HD", c2.name)
        assertEquals("MyAgent/1.0", c2.userAgent)
        assertEquals("http://ref.example/", c2.referrer)
        assertNull(c2.catchup)

        val c3 = p.channels[2]
        assertEquals("Разное", c3.group)
        assertEquals("Без группы и атрибутов", c3.name)
        assertEquals(listOf("Федеральные", "Кино", "Разное"), p.groups)
    }

    @Test fun catchupShift() {
        val ch = M3uParser.parse(sample).channels[0]
        val url = Catchup.url(ch, start = 1_000_000_000L, stop = 1_000_360_000L, now = 1_000_900_000L)
        assertEquals("http://cdn.example/high/token/1.m3u8?utc=1000000&lutc=1000900", url)
        val noArchive = M3uParser.parse(sample).channels[1]
        assertNull(Catchup.url(noArchive, 0, 1, 2))
    }

    @Test fun catchupTemplates() {
        val text = """
            #EXTM3U
            #EXTINF:-1 catchup="default" catchup-days="5" catchup-source="http://arch/{utc}/{duration}/{Y}-{m}-{d}.m3u8",A
            http://live/a.m3u8
            #EXTINF:-1 catchup="append" catchup-days="5" catchup-source="?start={utc}&end={utcend}",B
            http://live/b.m3u8
            #EXTINF:-1 catchup="flussonic" catchup-days="5",C
            http://fl.example/ch/index.m3u8?token=1
        """.trimIndent()
        val ch = M3uParser.parse(text).channels
        val start = 1_700_000_000_000L // 2023-11-14 22:13:20 UTC
        val stop = start + 3_600_000
        assertEquals("http://arch/1700000000/3600/2023-11-14.m3u8", Catchup.url(ch[0], start, stop, stop + 1000))
        assertEquals("http://live/b.m3u8?start=1700000000&end=1700003600", Catchup.url(ch[1], start, stop, stop + 1000))
        assertEquals("http://fl.example/ch/index-1700000000-3600.m3u8?token=1", Catchup.url(ch[2], start, stop, stop + 1000))
    }

    @Test fun parsesXmltvTime() {
        // 2026-11-07 14:25:00 +03:00 = 11:25:00 UTC
        assertEquals(1794050700000L, EpgParser.parseTime("20261107142500 +0300"))
        assertEquals(1794061500000L, EpgParser.parseTime("20261107142500"))
        assertEquals(0L, EpgParser.parseTime("bad"))
    }

    @Test fun parsesEpg() {
        val playlist = M3uParser.parse(sample)
        val xml = """
            <?xml version="1.0" encoding="utf-8"?><!DOCTYPE tv SYSTEM "x.dtd">
            <tv>
              <channel id="1"><display-name>Первый канал</display-name></channel>
              <channel id="kino"><display-name>Кино HD</display-name></channel>
              <channel id="999"><display-name>Чужой</display-name></channel>
              <programme start="20261110100000 +0300" stop="20261110110000 +0300" channel="1"><title lang="ru">Новости &amp; погода</title><desc>…</desc></programme>
              <programme start="20261110110000 +0300" stop="20261110120000 +0300" channel="1"><title>Фильм</title></programme>
              <programme start="20261110100000 +0300" stop="20261110120000 +0300" channel="kino"><title>Кино-1</title></programme>
              <programme start="20261110100000 +0300" stop="20261110120000 +0300" channel="999"><title>Не нужен</title></programme>
            </tv>
        """.trimIndent()
        val now = EpgParser.parseTime("20261110103000 +0300")
        val epg = EpgParser.parse(xml.byteInputStream(), playlist, now)
        assertEquals(setOf("1", "kino"), epg.byId.keys)
        val first = epg.forChannel(playlist.channels[0])!!
        assertEquals("Новости & погода", first.now(now)!!.title)
        assertEquals("Фильм", first.next(now)!!.title)
        // Канал без tvg-id находим по названию
        assertEquals("Кино-1", epg.forChannel(playlist.channels[1])!!.now(now)!!.title)
        assertNull(epg.forChannel(playlist.channels[2]))
    }

    /** Прогон на настоящих файлах провайдера, если они лежат рядом (в репозиторий не входят). */
    @Test fun realFilesIfPresent() {
        val m3u = File(System.getenv("VOLNA_TEST_M3U") ?: "/nonexistent")
        val xml = File(System.getenv("VOLNA_TEST_EPG") ?: "/nonexistent")
        if (!m3u.exists() || !xml.exists()) return
        val playlist = M3uParser.parse(m3u.readText())
        val t0 = System.currentTimeMillis()
        val epg = Http.maybeGunzip(xml.inputStream()).use { EpgParser.parse(it, playlist) }
        val ms = System.currentTimeMillis() - t0
        val withEpg = playlist.channels.count { epg.forChannel(it) != null }
        val archive = playlist.channels.count { it.hasArchive }
        println("REAL: channels=${playlist.channels.size} groups=${playlist.groups.size} archive=$archive " +
            "epgUrls=${playlist.epgUrls} epgChannels=${epg.byId.size} withEpg=$withEpg programmes=${epg.programmeCount} parse=${ms}ms")
        val first = playlist.channels.first()
        println("REAL: ${first.name} now=${epg.forChannel(first)?.now(System.currentTimeMillis())} archiveUrl=${Catchup.url(first, System.currentTimeMillis() - 3_600_000, System.currentTimeMillis())?.replace(Regex("/high/[^/]+/"), "/high/***/")}")
        assertTrue(playlist.channels.size > 100)
        assertNotNull(epg.forChannel(first))
        // round-trip кэша
        val bytes = java.io.ByteArrayOutputStream()
        java.io.DataOutputStream(bytes).use { epg.write(it) }
        val back = java.io.DataInputStream(bytes.toByteArray().inputStream()).use { com.volnalabs.tv.data.EpgData.read(it) }
        assertEquals(epg.programmeCount, back.programmeCount)
        println("REAL: cache size=${bytes.size() / 1024} KB")
    }
}
