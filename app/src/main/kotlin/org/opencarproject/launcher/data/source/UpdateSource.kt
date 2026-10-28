package org.opencarproject.launcher.data.source

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.opencarproject.launcher.core.pure.AppVersion
import java.net.HttpURLConnection
import java.net.URL

/** A published release: its version and the signed APK attached to it. */
data class AppRelease(val version: String, val apkUrl: String)

/**
 * Updates from the project's latest GitHub release. The APK is streamed straight into an install
 * session; Android then asks the user to confirm (and, the first time, to allow this source).
 * Updating in place keeps the app's data, so settings and layout stay as they were.
 */
object UpdateSource {

    private const val LATEST = "https://api.github.com/repos/maldos23/open-car-project/releases/latest"
    const val ACTION_STATUS = "org.opencarproject.launcher.UPDATE_STATUS"

    fun currentVersion(context: Context): String =
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()

    /** The latest release; null when offline or GitHub doesn't answer. */
    suspend fun latest(): AppRelease? = withContext(Dispatchers.IO) {
        runCatching {
            val json = JSONObject(open(LATEST).inputStream.bufferedReader().use { it.readText() })
            val assets = json.getJSONArray("assets")
            val apk = (0 until assets.length()).map { assets.getJSONObject(it) }
                .first { it.getString("name").endsWith(".apk") }
            AppRelease(json.getString("tag_name").removePrefix("v"), apk.getString("browser_download_url"))
        }.getOrNull()
    }

    /** The latest release if it is newer than the installed version. */
    suspend fun available(context: Context): AppRelease? =
        latest()?.takeIf { AppVersion.isNewer(it.version, currentVersion(context)) }

    /**
     * Downloads [release] into an install session and commits it; the outcome (confirmation
     * screen or error) arrives at [UpdateReceiver]. Returns false if the download failed.
     */
    suspend fun install(context: Context, release: AppRelease): Boolean = withContext(Dispatchers.IO) {
        val app = context.applicationContext
        val installer = app.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
            .apply { setAppPackageName(app.packageName) }
        val id = installer.createSession(params)
        runCatching {
            installer.openSession(id).use { session ->
                val connection = open(release.apkUrl)
                connection.inputStream.use { input ->
                    session.openWrite("update.apk", 0, connection.contentLengthLong).use { out ->
                        input.copyTo(out)
                        session.fsync(out)
                    }
                }
                val status = PendingIntent.getBroadcast(
                    app, 0,
                    Intent(ACTION_STATUS).setPackage(app.packageName),
                    // Mutable: the installer adds the status extras.
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
                )
                session.commit(status.intentSender)
            }
        }.onFailure { runCatching { installer.abandonSession(id) } }.isSuccess
    }

    private fun open(url: String) = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = 10_000
        readTimeout = 30_000
        setRequestProperty("Accept", "application/vnd.github+json")
    }
}
