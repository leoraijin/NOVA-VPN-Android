package com.nova.vpn.android.data

import org.json.JSONObject
import java.net.URI
import java.net.HttpURLConnection
import java.net.URL

data class AndroidRelease(
    val version: String,
    val pageUrl: String,
    val downloadUrl: String?,
    val notes: String = ""
)

/** Reads only public GitHub Releases metadata; no profiles or device identifiers are sent. */
object AndroidReleaseChecker {
    const val OWNER = "leoraijin"
    const val REPOSITORY = "NOVA-VPN-Android"
    const val RELEASES_PAGE = "https://github.com/$OWNER/$REPOSITORY/releases/latest"
    private const val API_URL = "https://api.github.com/repos/$OWNER/$REPOSITORY/releases/latest"

    fun fetchLatest(): AndroidRelease? {
        val connection = (URL(API_URL).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 7_000
            readTimeout = 7_000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            setRequestProperty("User-Agent", "NOVA-VPN-Android")
        }
        return try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return null
            val json = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            parseLatestRelease(json)
        } finally {
            connection.disconnect()
        }
    }

    internal fun parseLatestRelease(json: String): AndroidRelease? = runCatching {
        val release = JSONObject(json)
        val tag = release.optString("tag_name").trim()
        if (!isValidVersion(tag)) return null
        val pageUrl = release.optString("html_url").takeIf { value ->
            runCatching { URI(value).let { it.scheme == "https" && it.host == "github.com" } }.getOrDefault(false)
        }
            ?: RELEASES_PAGE
        val assets = release.optJSONArray("assets")
        var arm64Url: String? = null
        var universalUrl: String? = null
        if (assets != null) {
            for (index in 0 until assets.length()) {
                val asset = assets.optJSONObject(index) ?: continue
                val name = asset.optString("name")
                val url = asset.optString("browser_download_url")
                    .takeIf { value ->
                        runCatching {
                            URI(value).let {
                                it.scheme == "https" && it.host == "github.com" &&
                                    it.path.startsWith("/$OWNER/$REPOSITORY/releases/download/")
                            }
                        }.getOrDefault(false)
                    }
                    ?: continue
                if (name.endsWith(".apk", ignoreCase = true) && name.contains("arm64", ignoreCase = true)) arm64Url = url
                if (name.endsWith(".apk", ignoreCase = true) && name.contains("universal", ignoreCase = true)) universalUrl = url
            }
        }
        AndroidRelease(
            tag.removePrefix("v").removePrefix("V"),
            pageUrl,
            arm64Url ?: universalUrl,
            release.optString("body").trim().take(MAX_NOTES_LENGTH)
        )
    }.getOrNull()

    fun isNewerVersion(latest: String, current: String): Boolean {
        val latestParts = versionParts(latest) ?: return false
        val currentParts = versionParts(current) ?: return false
        for (index in 0 until maxOf(latestParts.size, currentParts.size)) {
            val latestPart = latestParts.getOrElse(index) { 0 }
            val currentPart = currentParts.getOrElse(index) { 0 }
            if (latestPart != currentPart) return latestPart > currentPart
        }
        return false
    }

    private fun isValidVersion(version: String): Boolean = versionParts(version) != null

    private const val MAX_NOTES_LENGTH = 1_200

    private fun versionParts(version: String): List<Int>? {
        val normalized = version.trim().removePrefix("v").removePrefix("V").substringBefore('-').substringBefore('+')
        if (normalized.isBlank() || normalized.split('.').any { part -> part.isBlank() || !part.all(Char::isDigit) }) return null
        return normalized.split('.').map { it.toIntOrNull() ?: return null }
    }
}
