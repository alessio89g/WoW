package net.alessio.wow

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

internal object UpdateInstaller {
    private const val APK_MIME = "application/vnd.android.package-archive"
    private fun preferences(context: Context) =
        context.getSharedPreferences("release_updates", Context.MODE_PRIVATE)

    fun pending(context: Context): Long = preferences(context).getLong("download", -1)

    fun remember(context: Context, id: Long) {
        preferences(context).edit().putLong("download", id).apply()
    }

    private fun forget(context: Context, id: Long) {
        if (pending(context) == id) preferences(context).edit().remove("download").apply()
    }

    suspend fun completed(context: Context, id: Long): File? = withContext(Dispatchers.IO) {
        if (id < 0 || pending(context) != id) return@withContext null
        val manager = context.getSystemService(DownloadManager::class.java)
        val status = manager.query(DownloadManager.Query().setFilterById(id))?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            else null
        }
        val file = File(context.cacheDir, "updates/release-$id.apk")
        if (status == null || status == DownloadManager.STATUS_FAILED) {
            forget(context, id)
            file.delete()
            throw UiFailure(R.string.update_download_failed)
        }
        if (status != DownloadManager.STATUS_SUCCESSFUL) return@withContext null

        try {
            if (!file.isFile) {
                val uri = manager.getUriForDownloadedFile(id) ?: throw UiFailure(R.string.update_download_failed)
                file.parentFile?.mkdirs()
                context.contentResolver.openInputStream(uri)?.use { input ->
                    file.outputStream().use { output ->
                        val buffer = ByteArray(8192)
                        var total = 0L
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            total += count
                            if (total > ReleaseUpdates.MAX_APK_BYTES) throw UiFailure(R.string.update_invalid_apk)
                            output.write(buffer, 0, count)
                        }
                    }
                } ?: throw UiFailure(R.string.update_download_failed)
            }
            val (archive, installed) = packages(context, file)
            if (archive.packageName != installed.packageName || !sameSigner(archive, installed)) {
                throw UiFailure(R.string.update_invalid_apk)
            }
            if (PackageInfoCompat.getLongVersionCode(archive) <= PackageInfoCompat.getLongVersionCode(installed)) {
                forget(context, id)
                file.delete()
                return@withContext null
            }
            file
        } catch (failure: Exception) {
            forget(context, id)
            file.delete()
            throw failure
        }
    }

    @Suppress("DEPRECATION")
    private fun packages(context: Context, apk: File): Pair<PackageInfo, PackageInfo> {
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES
        else PackageManager.GET_SIGNATURES
        val pm = context.packageManager
        val archive = pm.getPackageArchiveInfo(apk.path, flags) ?: throw UiFailure(R.string.update_invalid_apk)
        return archive to pm.getPackageInfo(context.packageName, flags)
    }

    @Suppress("DEPRECATION")
    private fun sameSigner(archive: PackageInfo, installed: PackageInfo): Boolean {
        fun hashes(info: PackageInfo): Set<List<Byte>> {
            val signatures = if (Build.VERSION.SDK_INT >= 28) info.signingInfo?.apkContentsSigners
            else info.signatures
            return signatures.orEmpty().map {
                MessageDigest.getInstance("SHA-256").digest(it.toByteArray()).toList()
            }.toSet()
        }
        val expected = hashes(installed)
        return expected.isNotEmpty() && hashes(archive) == expected
    }

    suspend fun install(context: Context, file: File): Boolean {
        withContext(Dispatchers.IO) {
            val (archive, installed) = packages(context, file)
            if (archive.packageName != installed.packageName || !sameSigner(archive, installed) ||
                PackageInfoCompat.getLongVersionCode(archive) <= PackageInfoCompat.getLongVersionCode(installed)
            ) throw UiFailure(R.string.update_invalid_apk)
        }

        if (!context.packageManager.canRequestPackageInstalls()) {
            context.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")))
            return false
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", file)
        context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, APK_MIME)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
        return true
    }
}
