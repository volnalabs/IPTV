package com.volnalabs.tv.data

/** Канал из M3U-плейлиста. */
data class Channel(
    /** Порядковый номер в плейлисте, начиная с 1 (номер канала на пульте). */
    val number: Int,
    val name: String,
    val url: String,
    val group: String,
    val logo: String?,
    val tvgId: String?,
    val tvgName: String?,
    /** Тип архива: shift, append, default, flussonic, xc… или null, если архива нет. */
    val catchup: String?,
    val catchupDays: Int,
    val catchupSource: String?,
    val userAgent: String?,
    val referrer: String?,
) {
    val hasArchive: Boolean get() = catchup != null && catchupDays > 0

    /** Ключ для избранного и «последнего канала» — переживает обновление плейлиста. */
    val key: String get() = url
}

data class Playlist(
    val channels: List<Channel>,
    /** Ссылки на телепрограмму из заголовка #EXTM3U (url-tvg / x-tvg-url). */
    val epgUrls: List<String>,
) {
    /** Группы в порядке появления в плейлисте. */
    val groups: List<String> by lazy { channels.map { it.group }.distinct() }

    companion object {
        val EMPTY = Playlist(emptyList(), emptyList())
    }
}

/** Одна передача из телепрограммы. Время — в миллисекундах UTC. */
data class Programme(
    val start: Long,
    val stop: Long,
    val title: String,
    /** Описание из <desc>; хранится только для передач «сегодня ± 1 день». */
    val desc: String = "",
) {
    fun isNow(now: Long) = now in start until stop
    fun isPast(now: Long) = stop <= now
}
