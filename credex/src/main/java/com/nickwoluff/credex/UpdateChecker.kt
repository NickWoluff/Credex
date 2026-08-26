package com.nickwoluff.credex

import android.content.Context
import android.text.Html
import android.util.Xml
import androidx.core.content.edit
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader
import java.net.HttpURLConnection
import java.net.URL

internal data class AvailableUpdate(
    val version: String,
    val releaseUrl: String,
    val releaseNotes: String = "",
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
    private const val RELEASES_FEED_URL =
        "https://github.com/NickWoluff/Credex/releases.atom"

    private data class ReleaseFeedEntry(
        val title: String,
        val releaseUrl: String,
        val releaseNotes: String,
    )

    fun check(): Result<AvailableUpdate?> = runCatching {
        val connection = URL(RELEASES_FEED_URL).openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 8_000
        connection.readTimeout = 8_000
        connection.setRequestProperty("Accept", "application/atom+xml, application/xml;q=0.9, text/xml;q=0.8")
        connection.setRequestProperty("User-Agent", "Credex-Update-Check")
        try {
            if (connection.responseCode !in 200..299) {
                error("GitHub Release feed 返回 HTTP ${connection.responseCode}")
            }
            val release = parseLatestRelease(
                connection.inputStream.bufferedReader().use { it.readText() },
            ) ?: return@runCatching null
            val remoteVersionText = extractVersion(release.title) ?: return@runCatching null
            val remoteVersion = normalizeVersion(remoteVersionText) ?: return@runCatching null
            val currentVersion = normalizeVersion(BuildConfig.VERSION_NAME) ?: return@runCatching null
            if (compareVersions(remoteVersion, currentVersion) <= 0) {
                null
            } else {
                AvailableUpdate(
                    version = remoteVersionText,
                    releaseUrl = release.releaseUrl,
                    releaseNotes = release.releaseNotes,
                )
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun parseLatestRelease(xml: String): ReleaseFeedEntry? {
        val parser = Xml.newPullParser()
        parser.setInput(StringReader(xml))
        var inFirstEntry = false
        var title = ""
        var releaseUrl = ""
        var releaseNotes = ""

        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "entry" -> {
                        if (inFirstEntry) {
                            return ReleaseFeedEntry(title, releaseUrl, releaseNotes)
                        }
                        inFirstEntry = true
                    }
                    "title" -> if (inFirstEntry) title = parser.nextText().trim()
                    "content" -> if (inFirstEntry) {
                        releaseNotes = htmlToReleaseNotes(parser.nextText())
                    }
                    "link" -> if (inFirstEntry && releaseUrl.isBlank()) {
                        releaseUrl = parser.getAttributeValue(null, "href").orEmpty().trim()
                    }
                }
                XmlPullParser.END_TAG -> if (parser.name == "entry" && inFirstEntry) {
                    return ReleaseFeedEntry(title, releaseUrl, releaseNotes)
                }
            }
        }
        return null
    }

    private fun extractVersion(title: String): String? =
        Regex("(?:^|\\s)v?(\\d+(?:\\.\\d+){0,2})(?:\\s|$)")
            .find(title)
            ?.groupValues
            ?.getOrNull(1)

    private fun htmlToReleaseNotes(value: String): String =
        Html.fromHtml(value, Html.FROM_HTML_MODE_LEGACY)
            .toString()
            .replace('\u00a0', ' ')
            .lines()
            .joinToString("\n") { it.trimEnd() }
            .trim()

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
