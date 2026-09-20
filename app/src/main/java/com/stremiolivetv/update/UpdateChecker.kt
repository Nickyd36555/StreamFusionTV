package com.stremiolivetv.update

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import com.stremiolivetv.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Checks this app's own configured GitHub repository and offers its APK. */
class UpdateChecker(private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    fun checkOnLaunch() {
        val repo = BuildConfig.GITHUB_REPOSITORY
        if (!repo.matches(Regex("[^/]+/[^/]+"))) return
        scope.launch {
            runCatching { withContext(Dispatchers.IO) { latestRelease(repo) } }
                .onSuccess { release ->
                    if (release.versionCode > BuildConfig.VERSION_CODE) offer(release)
                }
        }
    }

    private fun latestRelease(repo: String): Release {
        val connection = URL("https://api.github.com/repos/$repo/releases/latest").openConnection() as HttpURLConnection
        connection.setRequestProperty("Accept", "application/vnd.github+json")
        connection.setRequestProperty("User-Agent", "StreamFusionTV/${BuildConfig.VERSION_NAME}")
        connection.connectTimeout = 10_000
        connection.readTimeout = 15_000
        val body = connection.inputStream.bufferedReader().use { it.readText() }
        val json = JSONObject(body)
        val tag = json.optString("tag_name")
        val versionCode = Regex("(\\d+)$").find(tag)?.groupValues?.get(1)?.toIntOrNull()
            ?: error("Release tag must end with the Android version code")
        val assets = json.getJSONArray("assets")
        for (i in 0 until assets.length()) {
            val asset = assets.getJSONObject(i)
            if (asset.getString("name").startsWith("StreamFusionTV-") && asset.getString("name").endsWith(".apk", true))
                return Release(versionCode, asset.getString("browser_download_url"))
        }
        error("No Stream Fusion TV release found")
    }

    private fun offer(release: Release) {
        AlertDialog.Builder(context)
            .setTitle("Update available")
            .setMessage("A newer version is ready to install.")
            .setNegativeButton("Later", null)
            .setPositiveButton("Update") { _, _ -> scope.launch { downloadAndInstall(release.url) } }
            .show()
    }

    private suspend fun downloadAndInstall(downloadUrl: String) {
        val apk = withContext(Dispatchers.IO) {
            val target = File(context.cacheDir, "updates/StreamFusionTV-update.apk")
            target.parentFile?.mkdirs()
            URL(downloadUrl).openStream().use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
            target
        }
        if (android.os.Build.VERSION.SDK_INT >= 26 && !context.packageManager.canRequestPackageInstalls()) {
            context.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")))
            return
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", apk)
        context.startActivity(Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }

    private data class Release(val versionCode: Int, val url: String)
}
