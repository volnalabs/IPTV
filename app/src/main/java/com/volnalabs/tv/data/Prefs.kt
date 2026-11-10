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

    var lastUpdateCheck: Long
        get() = sp.getLong("last_update_check", 0)
        set(v) = sp.edit().putLong("last_update_check", v).apply()

    var favorites: Set<String>
        get() = sp.getStringSet("favorites", emptySet())?.toSet() ?: emptySet()
        set(v) = sp.edit().putStringSet("favorites", v).apply()

    fun toggleFavorite(key: String): Boolean {
        val set = favorites.toMutableSet()
        val added = if (key in set) { set.remove(key); false } else { set.add(key); true }
        favorites = set
        return added
    }
}
