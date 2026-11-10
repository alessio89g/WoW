package net.alessio.wow

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

internal data class ReleaseUpdate(val version: String, val url: String, val name: String, val changelog: String)

internal object ReleaseUpdates {
    private const val REPOSITORY = "alessio89g/WoW"
    const val REPOSITORY_URL = "https://github.com/$REPOSITORY"
    private const val MAX_RESPONSE = 512 * 1024
    internal const val MAX_APK_BYTES = 64L * 1024 * 1024
    private val versionPattern = Regex("v?(\\d+)\\.(\\d+)\\.(\\d+)")

    private fun versionParts(value: String): List<Long>? =
        versionPattern.matchEntire(value)?.groupValues?.drop(1)?.map { it.toLongOrNull() ?: return null }

    fun parse(json: String, current: String): ReleaseUpdate? {
        val release = JSONObject(json)
        if (release.opt("draft") != false || release.opt("prerelease") != false) return null
        val tag = release.optString("tag_name")
        val candidate = versionParts(tag) ?: return null
        val installed = versionParts(current) ?: return null
        val firstDifference = candidate.zip(installed).firstOrNull { (a, b) -> a != b } ?: return null
        if (firstDifference.first < firstDifference.second) return null

        val assets = release.optJSONArray("assets") ?: return null
        val version = tag.removePrefix("v")
        for (name in listOf("WoW-$version.apk", "WoW-$version-debug.apk")) {
            val url = "$REPOSITORY_URL/releases/download/$tag/$name"
            for (i in 0 until assets.length()) {
                val asset = assets.optJSONObject(i) ?: continue
                if (asset.optString("name") == name && asset.optString("browser_download_url") == url &&
                    asset.optLong("size") in 1..MAX_APK_BYTES
                ) return ReleaseUpdate(tag, url, name, (release.opt("body") as? String).orEmpty().trim())
            }
        }
        return null
    }

    suspend fun check(context: Context): ReleaseUpdate? = withContext(Dispatchers.IO) {
        val version = context.packageManager.getPackageInfo(context.packageName, 0).versionName
            ?: return@withContext null
        val connection = URL("https://api.github.com/repos/$REPOSITORY/releases/latest")
            .openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 8000
            connection.readTimeout = 8000
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.setRequestProperty("User-Agent", "WoW/$version")
            when (connection.responseCode) {
                200 -> Unit
                404 -> throw UiFailure(R.string.update_feed_unavailable)
                else -> throw UiFailure(R.string.update_check_failed)
            }
            val bytes = connection.inputStream.use { input ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    if (output.size() + count > MAX_RESPONSE) throw UiFailure(R.string.update_check_failed)
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            }
            parse(bytes.toString(Charsets.UTF_8), version)
        } finally {
            connection.disconnect()
        }
    }

    fun download(context: Context, update: ReleaseUpdate): Long {
        val request = DownloadManager.Request(Uri.parse(update.url))
            .setTitle(update.name)
            .setMimeType("application/vnd.android.package-archive")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(
                context,
                Environment.DIRECTORY_DOWNLOADS,
                update.name.removeSuffix(".apk") + "-${System.currentTimeMillis()}.apk"
            )
        return context.getSystemService(DownloadManager::class.java).enqueue(request)
    }
}
