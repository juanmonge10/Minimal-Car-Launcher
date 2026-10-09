package com.minimal.carlauncher.service

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

data class UpdateInfo(
    val tagName: String,
    val title: String,
    val changelog: String,
    val apkDownloadUrl: String,
    val apkSize: Long,
    val isUpdateAvailable: Boolean
)

class UpdateManager(private val context: Context) {

    companion object {
        private const val GITHUB_REPO = "juanmonge10/Minimal-Car-Launcher"
    }

    private val releasesUrl = "https://api.github.com/repos/$GITHUB_REPO/releases/latest"

    fun getCurrentVersionName(): String {
        return try {
            val pInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(
                    context.packageName,
                    android.content.pm.PackageManager.PackageInfoFlags.of(0L)
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0)
            }
            pInfo.versionName ?: "1.0.0"
        } catch (e: Exception) {
            "1.0.0"
        }
    }

    fun isNewerVersion(remoteTag: String, currentVersion: String = getCurrentVersionName()): Boolean {
        val cleanRemote = remoteTag.trim().removePrefix("v").removePrefix("V")
        val cleanCurrent = currentVersion.trim().removePrefix("v").removePrefix("V")

        val remoteParts = cleanRemote.split(".").map { it.toIntOrNull() ?: 0 }
        val currentParts = cleanCurrent.split(".").map { it.toIntOrNull() ?: 0 }

        val maxLen = maxOf(remoteParts.size, currentParts.size)
        for (i in 0 until maxLen) {
            val r = remoteParts.getOrElse(i) { 0 }
            val c = currentParts.getOrElse(i) { 0 }
            if (r > c) return true
            if (r < c) return false
        }
        return false
    }

    suspend fun checkLatestRelease(): UpdateInfo? = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            val url = URL(releasesUrl)
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Accept", "application/vnd.github.v3+json")
                setRequestProperty("User-Agent", "MinimalCarLauncher-Android")
                connectTimeout = 10000
                readTimeout = 10000
            }

            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                // If "latest" release returns 404 (e.g. no release marked latest yet), fallback to list of releases
                return@withContext fetchFromReleasesList()
            }

            val responseText = connection.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(responseText)
            parseReleaseJson(json)
        } catch (e: Exception) {
            e.printStackTrace()
            fetchFromReleasesList()
        } finally {
            connection?.disconnect()
        }
    }

    private fun fetchFromReleasesList(): UpdateInfo? {
        var connection: HttpURLConnection? = null
        return try {
            val url = URL("https://api.github.com/repos/$GITHUB_REPO/releases")
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Accept", "application/vnd.github.v3+json")
                setRequestProperty("User-Agent", "MinimalCarLauncher-Android")
                connectTimeout = 10000
                readTimeout = 10000
            }

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val responseText = connection.inputStream.bufferedReader().use { it.readText() }
                val array = JSONArray(responseText)
                if (array.length() > 0) {
                    parseReleaseJson(array.getJSONObject(0))
                } else null
            } else null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        } finally {
            connection?.disconnect()
        }
    }

    private fun parseReleaseJson(json: JSONObject): UpdateInfo? {
        val tagName = json.optString("tag_name", "")
        val title = json.optString("name", tagName)
        val changelog = json.optString("body", "No changelog provided.")

        val assets = json.optJSONArray("assets") ?: return null
        for (i in 0 until assets.length()) {
            val asset = assets.getJSONObject(i)
            val name = asset.optString("name", "")
            val downloadUrl = asset.optString("browser_download_url", "")
            val size = asset.optLong("size", 0L)
            if (name.endsWith(".apk", ignoreCase = true) && downloadUrl.isNotBlank()) {
                val isUpdateAvailable = isNewerVersion(tagName)
                return UpdateInfo(
                    tagName = tagName,
                    title = title,
                    changelog = changelog,
                    apkDownloadUrl = downloadUrl,
                    apkSize = size,
                    isUpdateAvailable = isUpdateAvailable
                )
            }
        }
        return null
    }

    suspend fun downloadApk(
        downloadUrl: String,
        onProgress: (Int) -> Unit
    ): File? = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            val targetFile = File(context.cacheDir, "update.apk")
            if (targetFile.exists()) {
                targetFile.delete()
            }

            var currentUrl = downloadUrl
            // Follow redirects (GitHub release downloads redirect to AWS S3)
            var redirectCount = 0
            while (redirectCount < 5) {
                val url = URL(currentUrl)
                connection = (url.openConnection() as HttpURLConnection).apply {
                    instanceFollowRedirects = false
                    connectTimeout = 15000
                    readTimeout = 15000
                    setRequestProperty("User-Agent", "MinimalCarLauncher-Android")
                }

                val status = connection.responseCode
                if (status == HttpURLConnection.HTTP_MOVED_TEMP ||
                    status == HttpURLConnection.HTTP_MOVED_PERM ||
                    status == 307 || status == 308) {
                    currentUrl = connection.getHeaderField("Location")
                    connection.disconnect()
                    redirectCount++
                } else {
                    break
                }
            }

            val activeConnection = connection ?: return@withContext null
            val fileLength = activeConnection.contentLength
            val input = BufferedInputStream(activeConnection.inputStream)
            val output = FileOutputStream(targetFile)

            val data = ByteArray(8192)
            var total: Long = 0
            var count: Int

            while (input.read(data).also { count = it } != -1) {
                total += count
                output.write(data, 0, count)
                if (fileLength > 0) {
                    val progress = ((total * 100) / fileLength).toInt()
                    withContext(Dispatchers.Main) {
                        onProgress(progress)
                    }
                }
            }

            output.flush()
            output.close()
            input.close()
            targetFile
        } catch (e: Exception) {
            e.printStackTrace()
            null
        } finally {
            connection?.disconnect()
        }
    }

    fun promptInstall(apkFile: File) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!context.packageManager.canRequestPackageInstalls()) {
                    val permissionIntent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                        data = Uri.parse("package:${context.packageName}")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(permissionIntent)
                }
            }

            val apkUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(installIntent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
