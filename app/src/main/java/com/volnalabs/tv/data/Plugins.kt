package com.volnalabs.tv.data

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.widget.Toast
import com.volnalabs.tv.BuildConfig
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

/**
 * Плагины — файлы .js, которые меняют список каналов и адреса потоков (описание: docs/PLUGINS.md).
 * Они выполняются в скрытой веб-странице: так один и тот же плагин работает и здесь,
 * и в версиях для компьютеров, Samsung и LG. Пока ни одного плагина нет, страница не создаётся.
 */
object Plugins {
    private const val TAG = "IptvPlugins"

    data class Item(
        val url: String, val enabled: Boolean, val code: String,
        val id: String, val name: String, val version: String, val description: String,
        val author: String = "", val hasPlaylist: Boolean = false, val hasStream: Boolean = false,
        /** Последняя ошибка: когда, текст и в каком вызове (file, start, playlist, stream). Пусто — плагин работает. */
        val errorTime: Long = 0, val errorMessage: String = "", val errorHook: String = "",
    ) {
        val failed: Boolean get() = errorMessage.isNotEmpty()
        val title: String get() = name.ifEmpty { id.ifEmpty { url.substringAfterLast('/') } }
    }

    /** Куда показывать api.toast() плагинов: главный экран подставляет свой тост. */
    @Volatile var toastSink: ((String) -> Unit)? = null

    private lateinit var app: Context
    private lateinit var sp: SharedPreferences
    private val main = Handler(Looper.getMainLooper())

    private val _items = MutableStateFlow<List<Item>>(emptyList())
    val items: StateFlow<List<Item>> = _items

    private var web: WebView? = null
    private var loaded: CompletableDeferred<JSONArray>? = null
    @Volatile private var input = ""

    /** Есть ли включённые плагины. */
    val active: Boolean get() = _items.value.any { it.enabled }

    fun init(context: Context) {
        app = context.applicationContext
        sp = app.getSharedPreferences("iptv_plugins", Context.MODE_PRIVATE)
        _items.value = runCatching {
            val a = JSONArray(sp.getString("list", "[]"))
            List(a.length()) { i ->
                val o = a.getJSONObject(i)
                Item(o.getString("url"), o.optBoolean("enabled", true), o.optString("code"), o.optString("id"),
                    o.optString("name"), o.optString("version"), o.optString("description"), o.optString("author"),
                    o.optBoolean("hasPlaylist"), o.optBoolean("hasStream"), o.optLong("errorTime"), o.optString("errorMessage"), o.optString("errorHook"))
            }
        }.getOrDefault(emptyList())
    }

    private fun store(list: List<Item>) {
        _items.value = list
        val a = JSONArray()
        for (p in list) a.put(JSONObject().put("url", p.url).put("enabled", p.enabled).put("code", p.code)
            .put("id", p.id).put("name", p.name).put("version", p.version).put("description", p.description).put("author", p.author)
            .put("hasPlaylist", p.hasPlaylist).put("hasStream", p.hasStream).put("errorTime", p.errorTime).put("errorMessage", p.errorMessage).put("errorHook", p.errorHook))
        sp.edit().putString("list", a.toString()).apply()
    }

    /** Ссылку на страницу файла GitHub превращаем в ссылку на сам файл. */
    fun rawUrl(url: String): String {
        val m = Regex("^https?://github\\.com/([^/]+)/([^/]+)/blob/(.+)$", RegexOption.IGNORE_CASE).find(url.trim())
        return if (m != null) "https://raw.githubusercontent.com/${m.groupValues[1]}/${m.groupValues[2]}/${m.groupValues[3]}" else url.trim()
    }

    /** Добавляет плагин по ссылке. Возвращает текст ошибки или null, если всё получилось. */
    suspend fun add(link: String): String? {
        val url = rawUrl(link)
        if (!url.startsWith("http://", true) && !url.startsWith("https://", true)) return "Ссылка должна начинаться с http:// или https://"
        if (_items.value.any { it.url == url }) return "Этот плагин уже добавлен"
        val code = try {
            withContext(Dispatchers.IO) { Http.readText(url) }
        } catch (e: Exception) {
            return "Не удалось загрузить плагин: " + Repo.reason(e).trimEnd('.').replaceFirstChar { it.lowercase() }
        }
        if (!Regex("IPTV\\s*\\.\\s*plugin").containsMatchIn(code)) return "В этом файле нет плагина: не найден вызов IPTV.plugin({ id: … })"
        val before = _items.value
        val index = before.count { it.enabled }
        store(before + Item(url, true, code, "", url.substringAfterLast('/'), "", ""))
        val metas = reload()
        val meta = metas?.let { a -> (0 until a.length()).map { a.getJSONObject(it) }.firstOrNull { it.optInt("source") == index } }
        if (meta == null) {
            store(before)
            reload()
            return if (metas == null) "На этом устройстве плагины не работают: нет системного компонента WebView"
            else "Плагин не запустился: проверьте, что в файле есть IPTV.plugin({ id: … })"
        }
        store(before + Item(url, true, code, meta.optString("id"), meta.optString("name"), meta.optString("version"), meta.optString("description"),
            meta.optString("author"), meta.optBoolean("hasPlaylist"), meta.optBoolean("hasStream")))
        Repo.reapplyPlugins()
        return null
    }

    suspend fun setEnabled(index: Int, enabled: Boolean) {
        store(_items.value.mapIndexed { i, p -> if (i == index) p.copy(enabled = enabled, errorTime = 0, errorMessage = "", errorHook = "") else p })
        applyMeta(reload())
        Repo.reapplyPlugins()
    }

    /** Удаляет плагин и возвращает его запись: по ней [restore] ставит плагин на прежнее место («Вернуть»). */
    suspend fun remove(index: Int): Item? {
        val item = _items.value.getOrNull(index) ?: return null
        store(_items.value.filterIndexed { i, _ -> i != index })
        applyMeta(reload())
        Repo.reapplyPlugins()
        return item
    }

    suspend fun restore(index: Int, item: Item) {
        val list = _items.value.toMutableList()
        list.add(index.coerceIn(0, list.size), item)
        store(list)
        applyMeta(reload())
        Repo.reapplyPlugins()
    }

    /** «Обновить код» одного плагина. Возвращает текст для тоста. */
    suspend fun refreshOne(index: Int): String {
        val p = _items.value.getOrNull(index) ?: return ""
        val code = try { withContext(Dispatchers.IO) { Http.readText(p.url) } } catch (e: Exception) { return Repo.reason(e) }
        if (code == p.code) return "Новой версии нет"
        if (!Regex("IPTV\\s*\\.\\s*plugin").containsMatchIn(code)) return "В этом файле нет плагина: не найден вызов IPTV.plugin({ id: … })"
        store(_items.value.mapIndexed { i, it -> if (i == index) it.copy(code = code, errorTime = 0, errorMessage = "", errorHook = "") else it })
        applyMeta(reload())
        Repo.reapplyPlugins()
        return "Код плагина обновлён"
    }

    /** Записывает сведения, которые плагины сообщили о себе при запуске: автора и что они умеют. */
    private fun applyMeta(metas: JSONArray?) {
        metas ?: return
        val enabled = _items.value.withIndex().filter { it.value.enabled }.map { it.index }
        var list = _items.value
        for (k in 0 until metas.length()) {
            val m = metas.optJSONObject(k) ?: continue
            val at = enabled.getOrNull(m.optInt("source", -1)) ?: continue
            list = list.toMutableList().also { it[at] = it[at].copy(author = m.optString("author"), hasPlaylist = m.optBoolean("hasPlaylist"), hasStream = m.optBoolean("hasStream")) }
        }
        if (list != _items.value) store(list)
    }

    /** Ошибка плагина: source — номер среди включённых. Показывается только в карточке плагина. */
    private fun fail(source: Int, hook: String, text: String) {
        val at = _items.value.withIndex().filter { it.value.enabled }.map { it.index }.getOrNull(source) ?: return
        store(_items.value.mapIndexed { i, p -> if (i == at) p.copy(errorTime = System.currentTimeMillis(), errorMessage = text.take(300), errorHook = hook) else p })
    }

    /** Плагины не ответили вовремя: считаем это ошибкой первого плагина с таким вызовом. */
    private fun timedOut(hook: String, seconds: String) {
        val enabled = _items.value.filter { it.enabled }
        val source = enabled.indexOfFirst { if (hook == "stream") it.hasStream else it.hasPlaylist }
        if (source >= 0) main.post { fail(source, hook, "Плагин не ответил за $seconds с") }
    }

    /** Свежие версии плагинов подтягиваем в фоне — они заработают со следующего запуска. */
    suspend fun refreshCode() {
        var list = _items.value
        for ((i, p) in list.withIndex()) {
            if (!p.enabled) continue
            val code = runCatching { Http.readText(p.url) }.getOrNull() ?: continue
            if (code != p.code && Regex("IPTV\\s*\\.\\s*plugin").containsMatchIn(code)) {
                list = list.toMutableList().also { it[i] = p.copy(code = code) }
            }
        }
        if (list != _items.value) store(list)
    }

    // ---------- скрытая страница ----------

    private class Bridge {
        @JavascriptInterface fun count(): Int = _items.value.count { it.enabled }
        @JavascriptInterface fun code(i: Int): String = _items.value.filter { it.enabled }.getOrNull(i)?.code.orEmpty()
        @JavascriptInterface fun input(): String = input
        @JavascriptInterface fun log(text: String) { Log.i(TAG, text) }
        @JavascriptInterface fun toast(text: String) { main.post { toastSink?.invoke(text) ?: Toast.makeText(app, text, Toast.LENGTH_LONG).show() } }
        @JavascriptInterface fun error(source: Int, hook: String, text: String) { main.post { fail(source, hook, text) } }
        @JavascriptInterface fun ready(list: String) { loaded?.complete(runCatching { JSONArray(list) }.getOrDefault(JSONArray())) }
    }

    /** Пересоздаёт страницу с включёнными плагинами. Возвращает их описания или null, если WebView недоступен. */
    @SuppressLint("SetJavaScriptEnabled")
    private suspend fun reload(): JSONArray? = withContext(Dispatchers.Main) {
        web?.destroy()
        web = null
        if (!active) return@withContext JSONArray()
        val done = CompletableDeferred<JSONArray>()
        loaded = done
        try {
            val runtime = app.assets.open("plugin-runtime.js").bufferedReader().use { it.readText() }
            val view = WebView(app)
            view.settings.javaScriptEnabled = true
            view.settings.domStorageEnabled = true
            view.addJavascriptInterface(Bridge(), "Android")
            val boot = """
                __iptvPlugins.setHost({version:'${BuildConfig.VERSION_NAME}',platform:'android',
                  toast:function(t){Android.toast(String(t))},log:function(t){Android.log(String(t))},
                  error:function(id,hook,text,source){Android.error(source,String(hook),String(text))}});
                for(var i=0;i<Android.count();i++){__iptvPlugins.setSource(i);
                  try{(new Function(Android.code(i)))()}catch(e){Android.log('Плагин '+i+': '+e);Android.error(i,'file',String(e))}}
                __iptvPlugins.setSource(-1);__iptvPlugins.start();
                Android.ready(JSON.stringify(__iptvPlugins.list()));
            """.trimIndent()
            view.loadDataWithBaseURL("https://plugins.iptv.local/", "<script>$runtime</script><script>$boot</script>", "text/html", "utf-8", null)
            web = view
        } catch (e: Throwable) {
            Log.w(TAG, "webview", e)
            return@withContext null
        }
        withTimeoutOrNull(5000) { done.await() } ?: JSONArray()
    }

    private suspend fun ensure(): WebView? {
        if (!active) return null
        if (web == null) reload()
        return web
    }

    /** Выполняет выражение на странице плагинов; результат — значение JSON или null (в том числе по истечении времени). */
    private suspend fun eval(expr: String, data: String, timeoutMs: Long): Any? = withContext(Dispatchers.Main) {
        val view = ensure() ?: return@withContext null
        val done = CompletableDeferred<String?>()
        input = data
        view.evaluateJavascript("(function(){try{return $expr}catch(e){Android.log(String(e));return null}})()") { done.complete(it) }
        val raw = withTimeoutOrNull(timeoutMs) { done.await() } ?: return@withContext null
        runCatching { JSONTokener(raw).nextValue() }.getOrNull()
    }

    private fun toJson(c: Channel) = JSONObject()
        .put("number", c.number).put("name", c.name).put("url", c.url).put("group", c.group)
        .put("logo", c.logo.orEmpty()).put("tvgId", c.tvgId.orEmpty()).put("tvgName", c.tvgName.orEmpty())
        .put("catchup", c.catchup.orEmpty()).put("catchupDays", c.catchupDays).put("catchupSource", c.catchupSource.orEmpty())
        .put("userAgent", c.userAgent.orEmpty()).put("referrer", c.referrer.orEmpty())

    /** Пропускает список каналов через плагины. При любой ошибке возвращает исходный список. */
    suspend fun playlist(p: Playlist): Playlist {
        if (!active || p.channels.isEmpty()) return p
        val data = withContext(Dispatchers.Default) { JSONArray().apply { p.channels.forEach { put(toJson(it)) } }.toString() }
        val started = System.currentTimeMillis()
        val result = eval("__iptvPlugins.hasPlaylist()?JSON.stringify(__iptvPlugins.playlist(JSON.parse(Android.input()))):null", data, 8000) as? String
        if (result == null) {
            if (System.currentTimeMillis() - started >= 8000) timedOut("playlist", "8")
            return p
        }
        return withContext(Dispatchers.Default) {
            runCatching {
                val a = JSONArray(result)
                val list = ArrayList<Channel>(a.length())
                for (i in 0 until a.length()) {
                    val o = a.optJSONObject(i) ?: continue
                    val url = o.optString("url"); val name = o.optString("name")
                    if (url.isEmpty() || name.isEmpty()) continue
                    fun s(k: String) = o.optString(k).takeIf { it.isNotEmpty() && it != "null" }
                    val days = o.optInt("catchupDays", 0)
                    list += Channel(list.size + 1, name, url, s("group") ?: "Без группы", s("logo"), s("tvgId"), s("tvgName"),
                        s("catchup").takeIf { days > 0 }, days, s("catchupSource"), s("userAgent"), s("referrer"))
                }
                if (list.isEmpty()) p else Playlist(list, p.epgUrls)
            }.getOrDefault(p)
        }
    }

    /** Спрашивает у плагинов адрес потока. Ответ приходит в главном потоке; если плагины молчат — исходный адрес. */
    suspend fun stream(ch: Channel, url: String, archive: Boolean): String {
        if (!active) return url
        val data = toJson(ch).put("url", url).put("archive", archive).toString()
        val started = System.currentTimeMillis()
        val result = eval("__iptvPlugins.hasStream()?__iptvPlugins.stream(JSON.parse(Android.input())):null", data, 1500) as? String
        if (result == null && System.currentTimeMillis() - started >= 1500) timedOut("stream", "1,5")
        return result?.takeIf { it.isNotEmpty() } ?: url
    }
}
