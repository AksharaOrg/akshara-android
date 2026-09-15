package org.slashboard.ime.update

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.util.Log
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class UpdateInfo(
    val hasUpdate: Boolean,
    val latestVersion: String,
    val downloadUrl: String,
    val releaseNotes: String
)

class UpdateManager(private val context: Context) {

    private val repoPath = "dinushlakmal/xxSlashboardxx"

    suspend fun checkForUpdates(currentVersion: String): UpdateInfo = withContext(Dispatchers.IO) {
        try {
            val url = URL("https://api.github.com/repos/$repoPath/releases/latest")
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Accept", "application/vnd.github.v3+json")
                setRequestProperty("User-Agent", "Slashboard-IME-Android")
                connectTimeout = 10000
                readTimeout = 10000
            }

            if (connection.responseCode == 200) {
                val jsonStr = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(jsonStr)
                
                val tagName = json.getString("tag_name") // e.g. "v1.0.1"
                val releaseNotes = json.optString("body", "Bug fixes and improvements")
                
                // APK asset එක සොයා ගැනීම
                val assets = json.getJSONArray("assets")
                var downloadUrl = ""
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    if (asset.getString("name").endsWith(".apk")) {
                        downloadUrl = asset.getString("browser_download_url")
                        break
                    }
                }

                val remoteVer = tagName.removePrefix("v").removePrefix("V").trim()
                val currentVer = currentVersion.removePrefix("v").removePrefix("V").trim()

                val isNewer = isNewerVersion(remoteVer, currentVer)
                Log.d("UpdateCheck", "Remote Tag: $tagName | Download URL: $downloadUrl")
                Log.d("UpdateCheck", "Current: $currentVer | Remote: $remoteVer | IsNewer: $isNewer")
                return@withContext UpdateInfo(isNewer && downloadUrl.isNotEmpty(), tagName, downloadUrl, releaseNotes)
            }
        } catch (e: Exception) {
            Log.e("UpdateCheck", "Error checking for updates", e)
            e.printStackTrace()
        }
        return@withContext UpdateInfo(false, "", "", "")
    }

    fun isNewerVersion(remote: String, current: String): Boolean {
        val rParts = remote.split(".", "-", "_").mapNotNull { it.filter { ch -> ch.isDigit() }.toIntOrNull() }
        val cParts = current.split(".", "-", "_").mapNotNull { it.filter { ch -> ch.isDigit() }.toIntOrNull() }
        val length = maxOf(rParts.size, cParts.size)

        for (i in 0 until length) {
            val r = rParts.getOrElse(i) { 0 }
            val c = cParts.getOrElse(i) { 0 }
            if (r > c) return true
            if (r < c) return false
        }
        return false
    }

    fun startDownloadAndInstall(apkUrl: String) {
        val fileName = "Slashboard-latest.apk"
        val destinationFile = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), fileName)
        if (destinationFile.exists()) destinationFile.delete()

        val request = DownloadManager.Request(Uri.parse(apkUrl)).apply {
            setTitle("Downloading Slashboard Update")
            setDescription("Fetching latest APK...")
            setDestinationUri(Uri.fromFile(destinationFile))
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        }

        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val downloadId = manager.enqueue(request)

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                val id = intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                if (id == downloadId) {
                    installApk(destinationFile)
                    try {
                        context.unregisterReceiver(this)
                    } catch (_: Exception) {}
                }
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE), Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(receiver, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE))
        }
    }

    private fun installApk(file: File) {
        val apkUri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
