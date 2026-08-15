package com.nudroidlabs.nuscan.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.nudroidlabs.nuscan.BuildConfig
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

data class UpdateInfo(
    val versionCode: Long,
    val versionName: String,
    val apkUrl: String,
    val sha256: String,
    val changelog: String,
    val mandatory: Boolean = false
)

sealed interface UpdateCheckResult {
    data class Available(val info: UpdateInfo) : UpdateCheckResult
    data object UpToDate : UpdateCheckResult
    data class Error(val message: String) : UpdateCheckResult
}

sealed interface InstallPreparation {
    data object Started : InstallPreparation
    data class NeedsPermission(val intent: Intent) : InstallPreparation
    data class Error(val message: String) : InstallPreparation
}

class UpdateManager(private val context: Context) {

    suspend fun checkForUpdate(): UpdateCheckResult = withContext(Dispatchers.IO) {
        runCatching {
            val connection = openConnection(BuildConfig.UPDATE_METADATA_URL)
            val json = connection.inputStream.bufferedReader().use { it.readText() }
            connection.disconnect()
            val root = JSONObject(json)
            val info = UpdateInfo(
                versionCode = root.getLong("versionCode"),
                versionName = root.getString("versionName"),
                apkUrl = root.optString("apkUrl").trim(),
                sha256 = root.optString("sha256").trim().lowercase(),
                changelog = root.optString("changelog", "Bug fixes and improvements."),
                mandatory = root.optBoolean("mandatory", false)
            )
            if (info.versionCode > BuildConfig.VERSION_CODE && info.apkUrl.startsWith("https://")) {
                UpdateCheckResult.Available(info)
            } else {
                UpdateCheckResult.UpToDate
            }
        }.getOrElse { error ->
            UpdateCheckResult.Error(error.message ?: "Unable to check for updates")
        }
    }

    suspend fun download(
        info: UpdateInfo,
        onProgress: (Int) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val updateDir = File(context.cacheDir, "updates").apply { mkdirs() }
            updateDir.listFiles()?.forEach { it.delete() }
            val output = File(updateDir, "NuScan-${info.versionName}.apk")
            val connection = openConnection(info.apkUrl)
            val total = connection.contentLengthLong
            connection.inputStream.use { input ->
                FileOutputStream(output).use { out ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var read: Int
                    var downloaded = 0L
                    var lastProgress = -1
                    while (input.read(buffer).also { read = it } >= 0) {
                        if (read == 0) continue
                        out.write(buffer, 0, read)
                        downloaded += read
                        if (total > 0) {
                            val progress = ((downloaded * 100L) / total).toInt().coerceIn(0, 100)
                            if (progress != lastProgress) {
                                lastProgress = progress
                                onProgress(progress)
                            }
                        }
                    }
                    out.fd.sync()
                }
            }
            connection.disconnect()

            if (output.length() <= 0L) error("Downloaded APK is empty")
            if (info.sha256.isNotBlank()) {
                val actual = sha256(output)
                if (!actual.equals(info.sha256, ignoreCase = true)) {
                    output.delete()
                    error("Update verification failed: SHA-256 does not match")
                }
            }
            verifyApk(output, info)
            onProgress(100)
            output
        }
    }

    fun prepareInstall(apk: File): InstallPreparation {
        if (!apk.exists()) return InstallPreparation.Error("Downloaded update was not found")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
            val settingsIntent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            return InstallPreparation.NeedsPermission(settingsIntent)
        }

        return runCatching {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", apk)
            val intent = Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, APK_MIME)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            InstallPreparation.Started
        }.getOrElse { InstallPreparation.Error(it.message ?: "Unable to open Android installer") }
    }

    private fun verifyApk(file: File, info: UpdateInfo) {
        val archive = archivePackageInfo(file)
            ?: error("Android could not read the downloaded APK")
        if (archive.packageName != BuildConfig.APPLICATION_ID) {
            error("Update package does not match NuScan")
        }
        val archiveVersion = archive.longVersionCodeCompat()
        if (archiveVersion != info.versionCode || archiveVersion <= BuildConfig.VERSION_CODE) {
            error("Downloaded APK version is not a valid update")
        }

        val installed = installedPackageInfo()
        val currentSigners = signerDigests(installed)
        val updateSigners = signerDigests(archive)
        if (currentSigners.isEmpty() || updateSigners.isEmpty() || currentSigners.intersect(updateSigners).isEmpty()) {
            error("Update signing certificate does not match the installed NuScan")
        }
    }

    @Suppress("DEPRECATION")
    private fun archivePackageInfo(file: File): PackageInfo? {
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            PackageManager.GET_SIGNATURES
        }
        return context.packageManager.getPackageArchiveInfo(file.absolutePath, flags)
    }

    @Suppress("DEPRECATION")
    private fun installedPackageInfo(): PackageInfo {
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            PackageManager.GET_SIGNATURES
        }
        return context.packageManager.getPackageInfo(context.packageName, flags)
    }

    @Suppress("DEPRECATION")
    private fun signerDigests(info: PackageInfo): Set<String> {
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val signingInfo = info.signingInfo ?: return emptySet()
            if (signingInfo.hasMultipleSigners()) {
                signingInfo.apkContentsSigners.toList()
            } else {
                signingInfo.signingCertificateHistory.toList()
            }
        } else {
            info.signatures?.toList().orEmpty()
        }
        return signatures.map { signature ->
            val digest = MessageDigest.getInstance("SHA-256").digest(signature.toByteArray())
            digest.joinToString("") { "%02x".format(it) }
        }.toSet()
    }

    @Suppress("DEPRECATION")
    private fun PackageInfo.longVersionCodeCompat(): Long =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) longVersionCode else versionCode.toLong()

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var read: Int
            while (input.read(buffer).also { read = it } >= 0) {
                if (read > 0) digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun openConnection(rawUrl: String): HttpURLConnection {
        val connection = URL(rawUrl).openConnection() as HttpURLConnection
        connection.instanceFollowRedirects = true
        connection.connectTimeout = 15_000
        connection.readTimeout = 45_000
        connection.setRequestProperty("User-Agent", "NuScan/${BuildConfig.VERSION_NAME}")
        connection.setRequestProperty("Accept", "application/json, application/octet-stream, */*")
        connection.connect()
        if (connection.responseCode !in 200..299) {
            val code = connection.responseCode
            connection.disconnect()
            error("Server returned HTTP $code")
        }
        return connection
    }

    companion object {
        private const val APK_MIME = "application/vnd.android.package-archive"
    }
}
