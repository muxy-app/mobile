package com.muxy.app.data

import android.content.Context

class DemoModeStore(context: Context) {
    private val prefs = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    fun load(): Boolean = prefs.getBoolean(KEY, false)

    fun save(enabled: Boolean) {
        prefs.edit().putBoolean(KEY, enabled).apply()
    }

    companion object {
        private const val FILE_NAME = "muxy_demo"
        private const val KEY = "demoMode"
    }
}
