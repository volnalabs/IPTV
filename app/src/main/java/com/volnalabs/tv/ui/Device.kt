package com.volnalabs.tv.ui

import android.app.Activity
import android.app.UiModeManager
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration

/** Телевизор или приставка: управление пультом. Всё остальное — телефон или планшет с касаниями. */
fun Context.isTv(): Boolean {
    val mode = (getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager)?.currentModeType
    val configMode = resources.configuration.uiMode and Configuration.UI_MODE_TYPE_MASK
    return mode == Configuration.UI_MODE_TYPE_TELEVISION || configMode == Configuration.UI_MODE_TYPE_TELEVISION ||
        !packageManager.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN)
}

/** На телевизоре экран всегда лежит на боку; телефон можно держать как удобно. */
fun Activity.applyOrientation() {
    // Прозрачным окнам Android 8.0 запрещает менять ориентацию — там просто оставляем как есть
    runCatching { requestedOrientation = if (isTv()) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_USER }
}

fun Context.isPortrait(): Boolean = resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT
