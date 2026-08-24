package com.nickwoluff.credex

import android.content.Context
import androidx.core.content.edit
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

internal data class AvailableUpdate(
    val version: String,
    val releaseUrl: String,
)

internal object UpdatePreferences {
    private const val PREFS = "update_preferences"
    private const val AUTO_CHECK = "auto_check"

    fun autoCheck(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(AUTO_CHECK, true)

    fun setAutoCheck(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            putBoolean(AUTO_CHECK, enabled)
        }
    }
}

internal object UpdateChecker {
    private const val LATEST_RELEASE_URL =
        "https://api.github.com/repos/NickWoluff/Credex/releases/latest"

    fun check(): Result<AvailableUpdate?> = runCatching {
        val connection = URL(LATEST_RELEASE_URL).openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 8_000
        connection.readTimeout = 8_000
        connection.setRequestProperty("Accept", "application/vnd.github+json")
        connection.setRequestProperty("User-Agent", "Credex-Update-Check")
        try {
            if (connection.responseCode !in 200..299) {
                error("GitHub 返回 HTTP ${connection.responseCode}")
            }
            val release = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            if (release.optBoolean("draft") || release.optBoolean("prerelease")) return@runCatching null
            val remoteVersionText = release.optString("tag_name").trim().removePrefix("v")
            val remoteVersion = normalizeVersion(remoteVersionText) ?: return@runCatching null
            val currentVersion = normalizeVersion(BuildConfig.VERSION_NAME) ?: return@runCatching null
            if (compareVersions(remoteVersion, currentVersion) <= 0) {
                null
            } else {
                AvailableUpdate(
                    version = remoteVersionText,
                    releaseUrl = release.optString("html_url"),
                )
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun normalizeVersion(value: String): List<Int>? =
        value.trim().removePrefix("v").split('.').takeIf { it.size in 1..3 }
            ?.map { it.toIntOrNull() ?: return null }

    private fun compareVersions(left: List<Int>, right: List<Int>): Int {
        for (index in 0 until maxOf(left.size, right.size)) {
            val comparison = (left.getOrNull(index) ?: 0).compareTo(right.getOrNull(index) ?: 0)
            if (comparison != 0) return comparison
        }
        return 0
    }
}
