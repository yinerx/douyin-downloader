package com.yiner.videodownloader

import android.content.Context

internal class UpdatePrefs(context: Context) {
    private val prefs = context.getSharedPreferences("updates", Context.MODE_PRIVATE)

    fun checkOnLaunch(): Boolean = prefs.getBoolean(KEY_CHECK, true)

    fun setCheckOnLaunch(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_CHECK, enabled).apply()
    }

    fun updateUrl(): String {
        val saved = prefs.getString(KEY_URL, null)
        return if (saved.isNullOrBlank()) BuildConfig.UPDATE_URL else saved
    }

    fun setUpdateUrl(url: String) {
        prefs.edit().putString(KEY_URL, url.trim()).apply()
    }

    fun dismissedCode(): Int = prefs.getInt(KEY_DISMISSED, 0)

    fun setDismissed(versionCode: Int) {
        prefs.edit().putInt(KEY_DISMISSED, versionCode).apply()
    }

    private companion object {
        const val KEY_CHECK = "checkOnLaunch"
        const val KEY_URL = "url"
        const val KEY_DISMISSED = "dismissedCode"
    }
}
