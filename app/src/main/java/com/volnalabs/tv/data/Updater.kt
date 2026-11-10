package com.volnalabs.tv.data

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.content.FileProvider
import com.volnalabs.tv.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Обновление приложения из релизов GitHub.
 *
 * Как это работает: страница …/releases/latest перенаправляет на …/releases/tag/vX.Y.Z —
 * из адреса берём номер версии и сравниваем с установленной. Если на GitHub новее,
 * скачиваем iptv.apk из этого релиза и предлагаем установить. Саму установку Android
 * всегда подтверждает у пользователя — молча поставить обновление система не даёт.
 */
object Updater {
    private const val TAG = "IptvUpdater"
    const val APK_NAME = "iptv.apk"
    private const val CHECK_INTERVAL = 6 * 60 * 60 * 1000L

    sealed class State {
        object Idle : State()
        object Checking : State()
        data class UpToDate(val checkedAt: Long) : State()
        data class Downloading(val version: String, val percent: Int) : State()
        data class Ready(val version: String, val file: File) : State()
        data class Error(val message: String) : State()
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state

    /** Версия, которую пользователь отложил кнопкой «Позже» — до следующего запуска не напоминаем. */
    @Volatile var postponed: String? = null

    val repoUrl: String get() = "https://github.com/${BuildConfig.UPDATE_REPO}"

    /** Фоновая проверка при запуске: не чаще раза в несколько часов и только если включено в настройках. */
    fun autoCheck(context: Context) {
        val prefs = Repo.prefs
        if (!prefs.autoUpdate) return
        if (_state.value is State.Ready) return
        if (System.currentTimeMillis() - prefs.lastUpdateCheck < CHECK_INTERVAL) return
        check(context)
    }

    fun check(context: Context) {
        if (job?.isActive == true) return
        val app = context.applicationContext
        job = scope.launch {
            try {
                _state.value = State.Checking
                val latest = latestVersion()
                Repo.prefs.lastUpdateCheck = System.currentTimeMillis()
                if (latest == null || !isNewer(latest, BuildConfig.VERSION_NAME)) {
                    cleanup(app)
                    _state.value = State.UpToDate(System.currentTimeMillis())
                    return@launch
                }
                val file = download(app, latest)
                _state.value = State.Ready(latest, file)
            } catch (e: Exception) {
                Log.w(TAG, "update", e)
                _state.value = State.Error(e.message ?: e.javaClass.simpleName)
            }
        }
    }

    /** Номер последнего релиза («1.0.7») или null, если релизов ещё нет. */
    fun latestVersion(): String? {
        val conn = (URL("$repoUrl/releases/latest").openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 15_000
            instanceFollowRedirects = false
            requestMethod = "GET"
            setRequestProperty("User-Agent", Http.USER_AGENT)
        }
        try {
            val code = conn.responseCode
            if (code == 404) throw IOException("репозиторий с обновлениями не найден")
            if (code !in 300..399) throw IOException("GitHub ответил $code")
            return versionFromLocation(conn.getHeaderField("Location"))
        } finally {
            conn.disconnect()
        }
    }

    /** «…/releases/tag/v1.0.7» → «1.0.7»; адрес без тега (релизов нет) → null. */
    fun versionFromLocation(location: String?): String? {
        val tag = location?.substringAfter("/releases/tag/", "")?.substringBefore('?')?.trim('/') ?: return null
        if (tag.isEmpty()) return null
        return java.net.URLDecoder.decode(tag, "UTF-8").removePrefix("v").takeIf { it.firstOrNull()?.isDigit() == true }
    }

    /** Сравнение версий по числам: 1.0.10 новее 1.0.9. */
    fun isNewer(remote: String, local: String): Boolean {
        val r = parts(remote)
        val l = parts(local)
        for (i in 0 until maxOf(r.size, l.size)) {
            val a = r.getOrElse(i) { 0 }
            val b = l.getOrElse(i) { 0 }
            if (a != b) return a > b
        }
        return false
    }

    private fun parts(v: String) = v.removePrefix("v").split('.', '-', '+').map { it.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }

    private fun dir(context: Context) = File(context.externalCacheDir ?: context.cacheDir, "update").apply { mkdirs() }

    private fun cleanup(context: Context) {
        dir(context).listFiles()?.forEach { it.delete() }
    }

    private fun download(context: Context, version: String): File {
        val target = File(dir(context), "iptv-$version.apk")
        if (target.exists() && valid(context, target)) return target
        cleanup(context)
        _state.value = State.Downloading(version, 0)

        val tmp = File(dir(context), "iptv-$version.part")
        val url = "$repoUrl/releases/download/v$version/$APK_NAME"
        var conn = URL(url).openConnection() as HttpURLConnection
        var hops = 0
        while (true) {
            conn.connectTimeout = 15_000
            conn.readTimeout = 60_000
            conn.instanceFollowRedirects = false
            conn.setRequestProperty("User-Agent", Http.USER_AGENT)
            val code = conn.responseCode
            if (code in 300..399 && hops++ < 6) {
                val next = URL(conn.url, conn.getHeaderField("Location")).toString()
                conn.disconnect()
                conn = URL(next).openConnection() as HttpURLConnection
                continue
            }
            if (code == 404) throw IOException("в релизе $version нет файла $APK_NAME")
            if (code !in 200..299) throw IOException("не удалось скачать обновление ($code)")
            break
        }
        try {
            val total = conn.contentLength.toLong()
            conn.inputStream.use { input ->
                tmp.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    var done = 0L
                    var lastPercent = -1
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        done += n
                        val percent = if (total > 0) (done * 100 / total).toInt() else 0
                        if (percent != lastPercent) { lastPercent = percent; _state.value = State.Downloading(version, percent) }
                    }
                }
            }
        } finally {
            conn.disconnect()
        }
        if (!valid(context, tmp)) {
            tmp.delete()
            throw IOException("скачанный файл повреждён")
        }
        if (!tmp.renameTo(target)) throw IOException("не удалось сохранить обновление")
        return target
    }

    /** Файл — это APK именно нашего приложения. Подпись при установке проверит сама система. */
    private fun valid(context: Context, file: File): Boolean = runCatching {
        @Suppress("DEPRECATION")
        val info = context.packageManager.getPackageArchiveInfo(file.absolutePath, 0)
        info != null && info.packageName == context.packageName
    }.getOrDefault(false)

    /** Открывает системное окно установки. */
    fun install(activity: Activity, file: File): Boolean = try {
        val uri = if (Build.VERSION.SDK_INT >= 24) {
            FileProvider.getUriForFile(activity, "${activity.packageName}.files", file)
        } else {
            @Suppress("SetWorldReadable")
            file.setReadable(true, false)
            Uri.fromFile(file)
        }
        activity.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        true
    } catch (e: Exception) {
        Log.w(TAG, "install", e)
        _state.value = State.Error("не удалось открыть установщик: ${e.message}")
        false
    }
}
