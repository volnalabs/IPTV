package com.volnalabs.tv.data

import android.content.Context
import android.content.SharedPreferences

class Prefs(context: Context) {
    private val sp: SharedPreferences = context.getSharedPreferences("iptv", Context.MODE_PRIVATE)

    var playlistUrl: String
        get() = sp.getString("playlist_url", "") ?: ""
        set(v) = sp.edit().putString("playlist_url", v.trim()).apply()

    /** Своя ссылка на телепрограмму. Пусто — берём из заголовка плейлиста. */
    var epgUrl: String
        get() = sp.getString("epg_url", "") ?: ""
        set(v) = sp.edit().putString("epg_url", v.trim()).apply()

    var lastChannelKey: String?
        get() = sp.getString("last_channel", null)
        set(v) = sp.edit().putString("last_channel", v).apply()

    var lastGroup: String?
        get() = sp.getString("last_group", null)
        set(v) = sp.edit().putString("last_group", v).apply()

    var playlistUpdated: Long
        get() = sp.getLong("playlist_updated", 0)
        set(v) = sp.edit().putLong("playlist_updated", v).apply()

    /** Автообновление приложения с GitHub. По умолчанию включено. */
    var autoUpdate: Boolean
        get() = sp.getBoolean("auto_update", true)
        set(v) = sp.edit().putBoolean("auto_update", v).apply()

    /** Громкость при прошлом просмотре (-1 — ещё не сохраняли). */
    var volume: Int
        get() = sp.getInt("volume", -1)
        set(v) = sp.edit().putInt("volume", v).apply()

    var rememberVolume: Boolean
        get() = sp.getBoolean("remember_volume", true)
        set(v) = sp.edit().putBoolean("remember_volume", v).apply()

    /** Увеличивать картинку, чтобы убрать чёрные полосы. */
    var fillScreen: Boolean
        get() = sp.getBoolean("fill_screen", false)
        set(v) = sp.edit().putBoolean("fill_screen", v).apply()

    var lastUpdateCheck: Long
        get() = sp.getLong("last_update_check", 0)
        set(v) = sp.edit().putLong("last_update_check", v).apply()

    var favorites: Set<String>
        get() = sp.getStringSet("favorites", emptySet())?.toSet() ?: emptySet()
        set(v) = sp.edit().putStringSet("favorites", v).apply()

    /** Список плейлистов. Старые ключи playlist_url и epg_url держатся равными активному — на случай отката версии. */
    var playlists: List<PlaylistInfo>
        get() = runCatching {
            val a = org.json.JSONArray(sp.getString("playlists", "[]"))
            List(a.length()) { i ->
                val o = a.getJSONObject(i)
                val err = o.optJSONObject("lastError")
                PlaylistInfo(o.getString("id"), o.optString("name"), o.optString("url"), o.optString("epgUrl"), o.optLong("updated"), o.optInt("count"),
                    err?.optLong("time") ?: 0, err?.optString("message").orEmpty())
            }
        }.getOrDefault(emptyList())
        set(v) {
            val a = org.json.JSONArray()
            for (p in v) a.put(org.json.JSONObject().put("id", p.id).put("name", p.name).put("url", p.url).put("epgUrl", p.epgUrl).put("updated", p.updated).put("count", p.count)
                .put("lastError", if (p.failed) org.json.JSONObject().put("time", p.errorTime).put("message", p.errorMessage) else org.json.JSONObject.NULL))
            sp.edit().putString("playlists", a.toString()).apply()
        }

    var activePlaylist: String
        get() = sp.getString("active_playlist", "") ?: ""
        set(v) = sp.edit().putString("active_playlist", v).apply()

    /** Недавние каналы: до 20, не старше 7 дней. Пары «адрес потока — время». */
    var recent: List<Pair<String, Long>>
        get() = runCatching {
            val a = org.json.JSONArray(sp.getString("recent", "[]"))
            val limit = System.currentTimeMillis() - 7 * 86_400_000L
            (0 until a.length()).map { a.getJSONObject(it) }.map { it.getString("url") to it.getLong("t") }.filter { it.second > limit }
        }.getOrDefault(emptyList())
        set(v) {
            val a = org.json.JSONArray()
            for ((url, t) in v.take(20)) a.put(org.json.JSONObject().put("url", url).put("t", t))
            sp.edit().putString("recent", a.toString()).apply()
        }

    fun addRecent(key: String) { recent = listOf(key to System.currentTimeMillis()) + recent.filter { it.first != key } }

    /** Метка «новое» у функции показывается до первого использования. */
    fun seen(feature: String): Boolean = sp.getBoolean("seen_$feature", false)
    fun markSeen(feature: String) { if (!seen(feature)) sp.edit().putBoolean("seen_$feature", true).apply() }

    fun toggleFavorite(key: String): Boolean {
        val set = favorites.toMutableSet()
        val added = if (key in set) { set.remove(key); false } else { set.add(key); true }
        favorites = set
        return added
    }
}

/** Плейлист из списка плейлистов. Ошибка последней загрузки хранится, чтобы показать её в настройках. */
data class PlaylistInfo(
    val id: String, val name: String, val url: String, val epgUrl: String = "",
    val updated: Long = 0, val count: Int = 0, val errorTime: Long = 0, val errorMessage: String = "",
) {
    val failed: Boolean get() = errorMessage.isNotEmpty()
}
