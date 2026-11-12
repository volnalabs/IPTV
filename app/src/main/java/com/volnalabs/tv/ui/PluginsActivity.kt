package com.volnalabs.tv.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle

/** Плагины теперь — раздел настроек (SPEC 6). Прежний экран открывает этот раздел. */
class PluginsActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startActivity(Intent(this, SettingsActivity::class.java).putExtra("view", "plugins"))
        finish()
    }
}
