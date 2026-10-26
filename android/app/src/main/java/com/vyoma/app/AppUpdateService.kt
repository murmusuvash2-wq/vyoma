package com.vyoma.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

data class AppUpdateInfo(
    val isUpdateAvailable: Boolean,
    val currentVersionName: String,
    val latestVersionName: String,
    val releaseNotes: String,
    val apkDownloadUrl: String?,
    val apkFileName: String?,
    val publishedAt: String?
)

object AppUpdateService {

    private const val DEFAULT_OWNER = "murmusuvash2-wq"
    private const val DEFAULT_REPO = "clever-ramanujan"

    fun getCurrentVersionName(context: Context): String {
        return try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            pInfo.versionName ?: "0.1.0"
        } catch (_: Exception) {
            "0.1.0"
        }
    }

    suspend fun checkForUpdates(
        context: Context,
        owner: String? = null,
        repo: String? = null,
        token: String? = null
    ): Result<AppUpdateInfo> = withContext(Dispatchers.IO) {
        val targetOwner = owner?.ifBlank { null } ?: DEFAULT_OWNER
        val targetRepo = repo?.ifBlank { null } ?: DEFAULT_REPO
        val currentVersion = getCurrentVersionName(context)

        try {
            val url = URL("https://api.github.com/repos/$targetOwner/$targetRepo/releases/latest")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "Vyoma-InApp-Updater")
                if (!token.isNullOrBlank()) {
                    setRequestProperty("Authorization", "Bearer $token")
                }
                connectTimeout = 15000
                readTimeout = 15000
            }

            if (conn.responseCode !in 200..299) {
                val error = conn.errorStream?.bufferedReader()?.use(BufferedReader::readText) ?: "HTTP ${conn.responseCode}"
                return@withContext Result.failure(Exception("GitHub Release Check Failed: $error"))
            }

            val body = conn.inputStream.bufferedReader().use(BufferedReader::readText)
            val json = JSONObject(body)

            val rawTagName = json.optString("tag_name", "").trim()
            val latestCleanVersion = rawTagName.removePrefix("v").trim()
            val releaseNotes = json.optString("body", "Bug fixes and improvements.")
            val publishedAt = json.optString("published_at", "")

            var apkUrl: String? = null
            var apkFileName: String? = null

            val assets = json.optJSONArray("assets")
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    val name = asset.optString("name", "")
                    if (name.endsWith(".apk", ignoreCase = true)) {
                        apkUrl = asset.optString("browser_download_url")
                        apkFileName = name
                        break
                    }
                }
            }

            val isNewer = isVersionNewer(latestCleanVersion, currentVersion)

            Result.success(
                AppUpdateInfo(
                    isUpdateAvailable = isNewer && !apkUrl.isNullOrBlank(),
                    currentVersionName = currentVersion,
                    latestVersionName = latestCleanVersion.ifBlank { currentVersion },
                    releaseNotes = releaseNotes,
                    apkDownloadUrl = apkUrl,
                    apkFileName = apkFileName ?: "Vyoma-Update.apk",
                    publishedAt = publishedAt
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun isVersionNewer(latest: String, current: String): Boolean {
        if (latest.isBlank()) return false
        if (latest == current) return false

        val latestParts = latest.split(".", "-", "+").mapNotNull { it.toIntOrNull() }
        val currentParts = current.split(".", "-", "+").mapNotNull { it.toIntOrNull() }

        val maxLen = maxOf(latestParts.size, currentParts.size)
        for (i in 0 until maxLen) {
            val l = latestParts.getOrElse(i) { 0 }
            val c = currentParts.getOrElse(i) { 0 }
            if (l > c) return true
            if (l < c) return false
        }
        return false
    }

    suspend fun downloadApk(
        context: Context,
        downloadUrl: String,
        fileName: String,
        onProgress: (Int) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val destinationDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.cacheDir
            if (!destinationDir.exists()) destinationDir.mkdirs()
            val destinationFile = File(destinationDir, fileName)

            if (destinationFile.exists()) destinationFile.delete()

            val url = URL(downloadUrl)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "Vyoma-InApp-Updater")
                connectTimeout = 30000
                readTimeout = 30000
            }

            // Handle HTTP 302 redirects if needed
            var redirectConn = conn
            if (conn.responseCode in 300..399) {
                val newUrl = conn.getHeaderField("Location")
                redirectConn = (URL(newUrl).openConnection() as HttpURLConnection).apply {
                    setRequestProperty("User-Agent", "Vyoma-InApp-Updater")
                    connectTimeout = 30000
                    readTimeout = 30000
                }
            }

            val totalSize = redirectConn.contentLength
            val input = redirectConn.inputStream
            val output = FileOutputStream(destinationFile)

            val buffer = ByteArray(8 * 1024)
            var bytesRead: Int
            var totalBytesRead = 0L

            while (input.read(buffer).also { bytesRead = it } != -1) {
                output.write(buffer, 0, bytesRead)
                totalBytesRead += bytesRead
                if (totalSize > 0) {
                    val progress = ((totalBytesRead * 100) / totalSize).toInt()
                    onProgress(progress)
                }
            }

            output.flush()
            output.close()
            input.close()

            Result.success(destinationFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun promptInstallApk(context: Context, apkFile: File): Result<Boolean> {
        try {
            if (!apkFile.exists() || apkFile.length() == 0L) {
                return Result.failure(Exception("Downloaded APK file is empty or missing."))
            }

            // Android 8.0+ permission check
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!context.packageManager.canRequestPackageInstalls()) {
                    val settingsIntent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                        data = Uri.parse("package:${context.packageName}")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(settingsIntent)
                    return Result.failure(Exception("Please allow 'Install unknown apps' permission, then tap Update again."))
                }
            }

            val apkUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            context.startActivity(installIntent)
            return Result.success(true)
        } catch (e: Exception) {
            return Result.failure(e)
        }
    }
}
