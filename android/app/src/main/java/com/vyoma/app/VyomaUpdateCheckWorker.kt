package com.vyoma.app

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

private const val UPDATE_URL =
    "https://github.com/murmusuvash2-wq/vyoma/releases/download/latest-test/latest-test.json"
private const val CHANNEL_ID = "vyoma_app_updates"
private const val PREFS = "vyoma_update_state"
private const val LAST_NOTIFIED_CODE = "last_notified_version_code"

internal object UpdateCheckScheduler {
    fun schedule(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val periodic = PeriodicWorkRequestBuilder<VyomaUpdateCheckWorker>(12, TimeUnit.HOURS)
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "vyoma_periodic_update_check",
            ExistingPeriodicWorkPolicy.KEEP,
            periodic
        )

        val immediate = OneTimeWorkRequestBuilder<VyomaUpdateCheckWorker>()
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            "vyoma_immediate_update_check",
            ExistingWorkPolicy.KEEP,
            immediate
        )
    }
}

internal class VyomaUpdateCheckWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            val connection = (URL("$UPDATE_URL?check=${System.currentTimeMillis()}").openConnection() as HttpURLConnection).apply {
                connectTimeout = 12000
                readTimeout = 12000
                instanceFollowRedirects = true
                setRequestProperty("Cache-Control", "no-cache")
                setRequestProperty("Accept", "application/json")
            }
            val metadata = try {
                if (connection.responseCode !in 200..299) return Result.retry()
                JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            } finally {
                connection.disconnect()
            }

            val latestCode = metadata.getLong("versionCode")
            val latestName = metadata.getString("versionName")
            val apkUrl = metadata.getString("apkUrl")
            if (!apkUrl.startsWith("https://github.com/murmusuvash2-wq/vyoma/releases/download/latest-test/")) {
                return Result.failure()
            }

            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                applicationContext.packageManager.getPackageInfo(applicationContext.packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                applicationContext.packageManager.getPackageInfo(applicationContext.packageName, 0)
            }
            val installedCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                packageInfo.versionCode.toLong()
            }
            if (latestCode <= installedCode) return Result.success()

            val prefs = applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            if (prefs.getLong(LAST_NOTIFIED_CODE, -1L) >= latestCode) return Result.success()

            createChannel()
            val notificationsAllowed = Build.VERSION.SDK_INT < 33 ||
                ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            if (notificationsAllowed) {
                val launchIntent = applicationContext.packageManager.getLaunchIntentForPackage(applicationContext.packageName)
                    ?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                val pendingIntent = launchIntent?.let {
                    androidx.core.app.PendingIntentCompat.getActivity(
                        applicationContext, 7041, it,
                        androidx.appsearch.app.AppSearchManager.ACTION_APP_SEARCH, // placeholder removed below
                        0
                    )
                }
                val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
                    .setSmallIcon(applicationContext.applicationInfo.icon)
                    .setContentTitle("Vyoma update available")
                    .setContentText("Vyoma $latestName is ready. Open the app to update.")
                    .setStyle(NotificationCompat.BigTextStyle().bigText("Vyoma $latestName (build $latestCode) is available. Open Vyoma → Settings → Check updates to download and install it."))
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .setAutoCancel(true)
                    .apply { if (pendingIntent != null) setContentIntent(pendingIntent) }
                    .build()
                NotificationManagerCompat.from(applicationContext).notify(7041, notification)
                prefs.edit().putLong(LAST_NOTIFIED_CODE, latestCode).apply()
            }
            Result.success()
        } catch (_: java.io.IOException) {
            Result.retry()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Vyoma app updates", NotificationManager.IMPORTANCE_DEFAULT)
            )
        }
    }
}
