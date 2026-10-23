package com.vyoma.app

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

private const val UPDATE_METADATA_URL =
    "https://github.com/murmusuvash2-wq/vyoma/releases/download/latest-test/latest-test.json"

private data class LatestVyomaBuild(
    val versionName: String,
    val versionCode: Long,
    val apkUrl: String,
    val sha256: String,
    val commit: String
)

@Composable
internal fun UpdateSettingsCard() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf("Check whether a newer Vyoma test build is available.") }
    var latestBuild by remember { mutableStateOf<LatestVyomaBuild?>(null) }
    var downloadedApk by remember { mutableStateOf<File?>(null) }
    var busy by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Vyoma app updates", color = Navy, fontWeight = FontWeight.Bold)
            Text(message, color = Muted)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    enabled = !busy,
                    onClick = {
                        busy = true
                        message = "Checking latest test build…"
                        scope.launch {
                            try {
                                val result = withContext(Dispatchers.IO) { fetchLatestBuild() }
                                val installedCode = context.packageManager
                                    .getPackageInfo(context.packageName, 0).longVersionCode
                                latestBuild = result
                                downloadedApk = null
                                message = if (result.versionCode > installedCode) {
                                    "Update available: Vyoma ${result.versionName} (build ${result.versionCode})."
                                } else {
                                    "You're using the latest published test build (or a newer build)."
                                }
                            } catch (error: Exception) {
                                message = "Could not check updates. Check your internet connection and try again."
                            } finally {
                                busy = false
                            }
                        }
                    }
                ) { Text("Check updates") }

                Button(
                    enabled = !busy && latestBuild != null &&
                        (latestBuild?.versionCode ?: 0L) > PackageInfoCompat.getLongVersionCode(context.packageManager.getPackageInfo(context.packageName, 0)),
                    onClick = {
                        val build = latestBuild ?: return@Button
                        busy = true
                        message = "Downloading and verifying the APK…"
                        scope.launch {
                            try {
                                val apk = downloadedApk ?: withContext(Dispatchers.IO) {
                                    downloadAndVerify(build)
                                }
                                downloadedApk = apk
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                                    !context.packageManager.canRequestPackageInstalls()
                                ) {
                                    message = "APK verified. Allow Vyoma to install updates on the next screen, then return and tap Install update again."
                                    context.startActivity(
                                        Intent(
                                            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                            Uri.parse("package:${context.packageName}")
                                        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    )
                                } else {
                                    val uri = FileProvider.getUriForFile(
                                        context,
                                        "${context.packageName}.fileprovider",
                                        apk
                                    )
                                    val installIntent = Intent(Intent.ACTION_VIEW).apply {
                                        setDataAndType(uri, "application/vnd.android.package-archive")
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    context.startActivity(installIntent)
                                    message = "APK verified. Android will ask you to confirm installation."
                                }
                            } catch (error: Exception) {
                                message = "Download or verification failed. No APK was installed."
                            } finally {
                                busy = false
                            }
                        }
                    }
                ) { Text(if (downloadedApk == null) "Download update" else "Install update") }
            }
            Text(
                "Test channel only. APK integrity is checked before opening Android's installer. Installation always requires Android/user confirmation.",
                color = Muted
            )
        }
    }
}

private fun fetchLatestBuild(): LatestVyomaBuild {
    val connection = (URL(UPDATE_METADATA_URL).openConnection() as HttpURLConnection).apply {
        connectTimeout = 12000
        readTimeout = 12000
        requestMethod = "GET"
        setRequestProperty("Accept", "application/json")
        setRequestProperty("Cache-Control", "no-cache")
    }
    try {
        if (connection.responseCode !in 200..299) error("Update metadata unavailable")
        val json = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
        val apkUrl = json.getString("apkUrl")
        require(apkUrl.startsWith("https://github.com/murmusuvash2-wq/vyoma/releases/download/latest-test/"))
        val sha = json.getString("sha256").lowercase()
        require(sha.matches(Regex("[a-f0-9]{64}")))
        return LatestVyomaBuild(
            versionName = json.getString("versionName"),
            versionCode = json.getLong("versionCode"),
            apkUrl = apkUrl,
            sha256 = sha,
            commit = json.getString("commit")
        )
    } finally {
        connection.disconnect()
    }
}

private fun downloadAndVerify(build: LatestVyomaBuild): File {
    val connection = (URL(build.apkUrl).openConnection() as HttpURLConnection).apply {
        connectTimeout = 15000
        readTimeout = 30000
        instanceFollowRedirects = true
    }
    val target = File.createTempFile("vyoma-update-", ".apk")
    try {
        if (connection.responseCode !in 200..299) error("APK download failed")
        connection.inputStream.use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        }
        val digest = MessageDigest.getInstance("SHA-256")
        val actual = target.inputStream().use { input ->
            val buffer = ByteArray(8192)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        }
        if (!actual.equals(build.sha256, ignoreCase = true)) {
            target.delete()
            error("APK checksum mismatch")
        }
        return target
    } finally {
        connection.disconnect()
    }
}
