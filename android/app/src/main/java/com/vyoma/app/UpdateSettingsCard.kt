package com.vyoma.app

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
    var message by remember { mutableStateOf("Checking for updates…") }
    var latestBuild by remember { mutableStateOf<LatestVyomaBuild?>(null) }
    var downloadedApk by remember { mutableStateOf<File?>(null) }
    var busy by remember { mutableStateOf(false) }

    // Check and download in the background whenever the update card enters Settings.
    // Android still requires the user to confirm the actual APK installation.
    LaunchedEffect(Unit) {
        busy = true
        try {
            val result = withContext(Dispatchers.IO) { fetchLatestBuild() }
            val installedCode = PackageInfoCompat.getLongVersionCode(
                context.packageManager.getPackageInfo(context.packageName, 0)
            )
            latestBuild = result
            if (result.versionCode > installedCode) {
                message = "Vyoma ${result.versionName} is available. Downloading and verifying…"
                downloadedApk = withContext(Dispatchers.IO) { downloadAndVerify(result) }
                message = "Update ${result.versionName} downloaded and verified. Tap Install update to continue."
            } else {
                message = "You're up to date. Installed build: $installedCode; latest build: ${result.versionCode}."
            }
        } catch (error: Exception) {
            val detail = error.message?.take(100)?.ifBlank { null } ?: error.javaClass.simpleName
            message = "Automatic update check failed: $detail. Tap Check updates to retry."
        } finally {
            busy = false
        }
    }

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
            if (busy) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CircularProgressIndicator()
                    Text("Please wait…", color = Muted)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    enabled = !busy,
                    onClick = {
                        busy = true
                        latestBuild = null
                        message = "Checking latest test build…"
                        Toast.makeText(context, "Vyoma: checking for updates", Toast.LENGTH_SHORT).show()
                        scope.launch {
                            try {
                                val result = withContext(Dispatchers.IO) { fetchLatestBuild() }
                                val installedCode = PackageInfoCompat.getLongVersionCode(
                                    context.packageManager.getPackageInfo(context.packageName, 0)
                                )
                                latestBuild = result
                                downloadedApk = null
                                if (result.versionCode > installedCode) {
                                    message = "Update available: Vyoma ${result.versionName} (build ${result.versionCode}). Downloading and verifying…"
                                    val apk = withContext(Dispatchers.IO) { downloadAndVerify(result) }
                                    downloadedApk = apk
                                    message = "Update ${result.versionName} downloaded and verified. Tap Install update to continue."
                                } else {
                                    message = "You're up to date. Installed build: $installedCode; latest build: ${result.versionCode}."
                                }
                            } catch (error: Exception) {
                                val detail = error.message?.take(100)?.ifBlank { null } ?: error.javaClass.simpleName
                                message = "Update check failed: $detail. Check internet and try again."
                            } finally {
                                busy = false
                            }
                        }
                    }
                ) { Text("Check updates") }

                Button(
                    enabled = !busy && latestBuild != null &&
                        (latestBuild?.versionCode ?: 0L) > PackageInfoCompat.getLongVersionCode(
                            context.packageManager.getPackageInfo(context.packageName, 0)
                        ),
                    onClick = {
                        val build = latestBuild ?: return@Button
                        busy = true
                        message = "Downloading and verifying the APK…"
                        scope.launch {
                            try {
                                val apk = downloadedApk ?: withContext(Dispatchers.IO) { downloadAndVerify(build) }
                                downloadedApk = apk
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                                    !context.packageManager.canRequestPackageInstalls()
                                ) {
                                    message = "APK verified. Allow Vyoma to install unknown apps, return here, then tap Install update."
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
                                    message = "APK verified. Confirm installation in Android's installer."
                                }
                            } catch (error: Exception) {
                                val detail = error.message?.take(100)?.ifBlank { null } ?: error.javaClass.simpleName
                                message = "Download/install handoff failed: $detail. No APK was installed by Vyoma."
                            } finally {
                                busy = false
                            }
                        }
                    }
                ) { Text(if (downloadedApk == null) "Download update" else "Install update") }
            }
            Text(
                "Test channel only. APK integrity is checked before opening Android's installer. Android requires confirmation to install.",
                color = Muted
            )
        }
    }
}

private fun fetchLatestBuild(): LatestVyomaBuild {
    val connection = (URL("$UPDATE_METADATA_URL?check=${System.currentTimeMillis()}").openConnection() as HttpURLConnection).apply {
        connectTimeout = 15000
        readTimeout = 15000
        requestMethod = "GET"
        instanceFollowRedirects = true
        setRequestProperty("Accept", "application/json")
        setRequestProperty("Cache-Control", "no-cache")
        setRequestProperty("User-Agent", "Vyoma-Android-Updater")
    }
    try {
        val status = connection.responseCode
        if (status !in 200..299) error("Release server returned HTTP $status")
        val json = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
        val apkUrl = json.getString("apkUrl")
        require(apkUrl.startsWith("https://github.com/murmusuvash2-wq/vyoma/releases/download/latest-test/")) {
            "Unexpected APK download address"
        }
        val sha = (json.optString("sha256Apk").takeIf { it.isNotBlank() } ?: json.optString("sha256")).lowercase()
        require(sha.matches(Regex("[a-f0-9]{64}"))) { "Invalid APK checksum metadata" }
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
        readTimeout = 45000
        instanceFollowRedirects = true
        setRequestProperty("User-Agent", "Vyoma-Android-Updater")
    }
    val target = File.createTempFile("vyoma-update-", ".apk")
    try {
        val status = connection.responseCode
        if (status !in 200..299) error("APK server returned HTTP $status")
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
            error("APK checksum does not match")
        }
        return target
    } finally {
        connection.disconnect()
    }
}
