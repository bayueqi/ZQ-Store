package com.samyak.repostore.util

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.samyak.repostore.data.prefs.DownloadPreferences
import com.samyak.repostore.RepoStoreApp
import com.samyak.repostore.data.model.InstalledAppMapping
import kotlinx.coroutines.*
import java.io.File

/**
 * Professional App Installer - Inspired by GitHub Store project
 * Handles APK download and installation with proper state management
 */
class AppInstaller private constructor(private val context: Context) {

    companion object {
        private const val TAG = "AppInstaller"
        private const val PROGRESS_INTERVAL = 300L

        @Volatile
        private var instance: AppInstaller? = null

        fun getInstance(context: Context): AppInstaller {
            return instance ?: synchronized(this) {
                instance ?: AppInstaller(context.applicationContext).also { instance = it }
            }
        }
    }

    private val downloadManager: DownloadManager by lazy {
        context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    }
    
    private val multiPartDownloader: MultiPartDownloader by lazy {
        MultiPartDownloader(context)
    }
    
    private val downloadScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val mainHandler = Handler(Looper.getMainLooper())
    
    private var currentDownloadId: Long = -1
    private var currentRepoName: String? = null
    private var currentOwnerName: String? = null
    private var downloadReceiver: BroadcastReceiver? = null
    private var progressRunnable: Runnable? = null
    private var isDownloading = false
    private var stateCallback: ((InstallState) -> Unit)? = null

    /**
     * Installation states
     */
    sealed class InstallState {
        object Idle : InstallState()
        data class Downloading(
            val progress: Int,
            val downloaded: String,
            val total: String
        ) : InstallState()
        object Installing : InstallState()
        object Success : InstallState()
        data class Error(val message: String) : InstallState()
    }

    /**
     * Start download and install APK
     */
    fun download(
        url: String,
        fileName: String,
        title: String,
        repoName: String? = null,
        ownerName: String? = null,
        onStateChanged: (InstallState) -> Unit
    ) {
        // Prevent multiple downloads
        if (isDownloading) {
            mainHandler.post {
                onStateChanged(InstallState.Error("Download already in progress"))
            }
            return
        }

        // Store callback and context
        stateCallback = onStateChanged
        currentRepoName = repoName
        currentOwnerName = ownerName
        isDownloading = true

        // Clean old file
        deleteFile(fileName)

        // Check if multi-part download is enabled
        val useMultiPart = DownloadPreferences.isMultiPartEnabled(context) || 
                          DownloadPreferences.isMirrorProxyEnabled(context)
        
        if (useMultiPart) {
            downloadWithMultiPart(url, fileName, onStateChanged)
        } else {
            downloadWithDownloadManager(url, fileName, title, onStateChanged)
        }
    }
    
    /**
     * Download using multi-part downloader with coroutines
     */
    private fun downloadWithMultiPart(
        url: String,
        fileName: String,
        onStateChanged: (InstallState) -> Unit
    ) {
        Log.d(TAG, "Using multi-part downloader for: $url")
        
        downloadScope.launch {
            try {
                multiPartDownloader.download(url, fileName) { state ->
                    mainHandler.post {
                        when (state) {
                            is MultiPartDownloader.DownloadState.Idle -> {
                                onStateChanged(InstallState.Idle)
                            }
                            is MultiPartDownloader.DownloadState.Downloading -> {
                                val downloadedStr = formatSize(state.downloadedBytes)
                                val totalStr = if (state.totalBytes > 0) formatSize(state.totalBytes) else "..."
                                onStateChanged(InstallState.Downloading(
                                    state.progress,
                                    downloadedStr,
                                    totalStr
                                ))
                            }
                            is MultiPartDownloader.DownloadState.Completed -> {
                                onStateChanged(InstallState.Installing)
                                
                                // Extract and save package mapping if we have repo info
                                if (currentRepoName != null && currentOwnerName != null) {
                                    val apkFile = state.file
                                    if (apkFile.exists()) {
                                        extractAndSavePackageName(apkFile, currentRepoName!!, currentOwnerName!!)
                                    }
                                }
                                
                                val installStarted = installApk(state.file)
                                if (installStarted) {
                                    onStateChanged(InstallState.Success)
                                } else {
                                    onStateChanged(InstallState.Error("Failed to start installation"))
                                }
                                cleanup()
                            }
                            is MultiPartDownloader.DownloadState.Error -> {
                                onStateChanged(InstallState.Error(state.message))
                                cleanup()
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Multi-part download error", e)
                mainHandler.post {
                    onStateChanged(InstallState.Error(e.message ?: "Download failed"))
                    cleanup()
                }
            }
        }
        
        // Send initial downloading state
        mainHandler.post {
            onStateChanged(InstallState.Downloading(0, "0 B", "..."))
        }
    }
    
    /**
     * Download using system DownloadManager (fallback)
     */
    private fun downloadWithDownloadManager(
        url: String,
        fileName: String,
        title: String,
        onStateChanged: (InstallState) -> Unit
    ) {
        try {
            // Apply mirror proxy if enabled
            val downloadUrl = DownloadPreferences.transformUrl(context, url)
            Log.d(TAG, "Using DownloadManager for: $downloadUrl")
            
            // Create request
            val request = DownloadManager.Request(Uri.parse(downloadUrl)).apply {
                setTitle(title)
                setDescription("Downloading APK...")
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
                setAllowedOverMetered(true)
                setAllowedOverRoaming(true)
                setMimeType("application/vnd.android.package-archive")
            }

            // Enqueue download
            currentDownloadId = downloadManager.enqueue(request)
            Log.d(TAG, "Download started: $currentDownloadId, URL: $downloadUrl")

            // Send initial downloading state
            mainHandler.post {
                onStateChanged(InstallState.Downloading(0, "0 B", "..."))
            }

            // Register receiver
            registerReceiver(fileName, onStateChanged)

            // Start progress tracking
            startProgressTracking(onStateChanged)

        } catch (e: Exception) {
            Log.e(TAG, "Download error", e)
            isDownloading = false
            mainHandler.post {
                onStateChanged(InstallState.Error(e.message ?: "Download failed"))
            }
        }
    }

    private fun registerReceiver(fileName: String, onStateChanged: (InstallState) -> Unit) {
        unregisterReceiver()

        downloadReceiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                val id = intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1) ?: -1
                Log.d(TAG, "Download complete received: id=$id, currentId=$currentDownloadId")
                if (id == currentDownloadId) {
                    stopProgressTracking()
                    checkDownloadStatus(fileName, onStateChanged)
                }
            }
        }

        // For Android 13+ (TIRAMISU), use RECEIVER_EXPORTED for system broadcasts
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(
                downloadReceiver,
                IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
                Context.RECEIVER_EXPORTED
            )
        } else {
            ContextCompat.registerReceiver(
                context,
                downloadReceiver,
                IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
                ContextCompat.RECEIVER_EXPORTED
            )
        }
    }

    private fun checkDownloadStatus(fileName: String, onStateChanged: (InstallState) -> Unit) {
        val query = DownloadManager.Query().setFilterById(currentDownloadId)
        var cursor: Cursor? = null

        try {
            cursor = downloadManager.query(query)
            if (cursor != null && cursor.moveToFirst()) {
                val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))

                when (status) {
                    DownloadManager.STATUS_SUCCESSFUL -> {
                        Log.d(TAG, "Download successful")
                        mainHandler.post {
                            onStateChanged(InstallState.Installing)
                        }

                        // Extract and save package mapping if we have repo info
                        if (currentRepoName != null && currentOwnerName != null) {
                            val apkFile = getDownloadFile(fileName)
                            if (apkFile != null && apkFile.exists()) {
                                extractAndSavePackageName(apkFile, currentRepoName!!, currentOwnerName!!)
                            }
                        }
                        
                        // Get the downloaded file URI from DownloadManager
                        val downloadUri = downloadManager.getUriForDownloadedFile(currentDownloadId)
                        
                        if (downloadUri != null) {
                            Log.d(TAG, "Download URI: $downloadUri")
                            val installStarted = installApkFromUri(downloadUri)
                            mainHandler.post {
                                if (installStarted) {
                                    onStateChanged(InstallState.Success)
                                } else {
                                    onStateChanged(InstallState.Error("Failed to start installation"))
                                }
                                cleanup()
                            }
                        } else {
                            // Fallback to file path
                            val file = getDownloadFile(fileName)
                            if (file != null && file.exists()) {
                                val installStarted = installApk(file)
                                mainHandler.post {
                                    if (installStarted) {
                                        onStateChanged(InstallState.Success)
                                    } else {
                                        onStateChanged(InstallState.Error("Failed to start installation"))
                                    }
                                    cleanup()
                                }
                            } else {
                                Log.e(TAG, "Downloaded file not found: $fileName")
                                mainHandler.post {
                                    onStateChanged(InstallState.Error("Downloaded file not found"))
                                    cleanup()
                                }
                            }
                        }
                    }
                    DownloadManager.STATUS_FAILED -> {
                        val reason = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
                        val errorMsg = getErrorMessage(reason)
                        Log.e(TAG, "Download failed: $errorMsg")
                        mainHandler.post {
                            onStateChanged(InstallState.Error(errorMsg))
                            cleanup()
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Status check error", e)
            mainHandler.post {
                onStateChanged(InstallState.Error("Status check failed: ${e.message}"))
                cleanup()
            }
        } finally {
            cursor?.close()
        }
    }

    private fun startProgressTracking(onStateChanged: (InstallState) -> Unit) {
        stopProgressTracking()

        progressRunnable = object : Runnable {
            override fun run() {
                if (!isDownloading || currentDownloadId == -1L) return

                val query = DownloadManager.Query().setFilterById(currentDownloadId)
                var cursor: Cursor? = null

                try {
                    cursor = downloadManager.query(query)
                    if (cursor != null && cursor.moveToFirst()) {
                        val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))

                        if (status == DownloadManager.STATUS_RUNNING || status == DownloadManager.STATUS_PENDING) {
                            val bytesDownloaded = cursor.getLong(
                                cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                            )
                            val bytesTotal = cursor.getLong(
                                cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                            )

                            val progress = if (bytesTotal > 0) {
                                ((bytesDownloaded * 100) / bytesTotal).toInt().coerceIn(0, 100)
                            } else {
                                0
                            }

                            val downloadedStr = formatSize(bytesDownloaded)
                            val totalStr = if (bytesTotal > 0) formatSize(bytesTotal) else "..."

                            mainHandler.post {
                                onStateChanged(InstallState.Downloading(progress, downloadedStr, totalStr))
                            }

                            // Continue tracking
                            mainHandler.postDelayed(this, PROGRESS_INTERVAL)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Progress error", e)
                } finally {
                    cursor?.close()
                }
            }
        }

        mainHandler.postDelayed(progressRunnable!!, PROGRESS_INTERVAL)
    }

    private fun stopProgressTracking() {
        progressRunnable?.let { mainHandler.removeCallbacks(it) }
        progressRunnable = null
    }

    /**
     * Install APK using URI from DownloadManager (preferred method)
     */
    private fun installApkFromUri(uri: Uri): Boolean {
        return try {
            Log.d(TAG, "Installing APK from URI: $uri")
            
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            Log.d(TAG, "Install intent started successfully from URI")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Install from URI error: ${e.message}", e)
            false
        }
    }

    /**
     * Install APK using File with FileProvider (fallback method)
     */
    private fun installApk(file: File): Boolean {
        return try {
            val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.provider",
                    file
                )
            } else {
                Uri.fromFile(file)
            }
            
            Log.d(TAG, "Installing APK from: ${file.absolutePath}, URI: $uri, File size: ${file.length()}")
            
            // Verify file is valid before installing
            if (!file.exists() || file.length() == 0L) {
                Log.e(TAG, "APK file is invalid or empty")
                return false
            }
            
            val intent = Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
                data = uri
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
                putExtra(Intent.EXTRA_RETURN_RESULT, true)
            }
            context.startActivity(intent)
            Log.d(TAG, "Install intent started successfully")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Install error: ${e.message}", e)
            // Fallback to ACTION_VIEW
            try {
                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.provider",
                    file
                )
                val fallbackIntent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "application/vnd.android.package-archive")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(fallbackIntent)
                true
            } catch (e2: Exception) {
                Log.e(TAG, "Fallback install error: ${e2.message}", e2)
                false
            }
        }
    }

    /**
     * Cancel current download
     */
    fun cancel() {
        if (currentDownloadId != -1L) {
            try {
                downloadManager.remove(currentDownloadId)
            } catch (e: Exception) {
                Log.e(TAG, "Cancel error", e)
            }
        }
        val callback = stateCallback
        cleanup()
        mainHandler.post {
            callback?.invoke(InstallState.Idle)
        }
    }

    private fun cleanup() {
        stopProgressTracking()
        unregisterReceiver()
        currentDownloadId = -1
        currentRepoName = null
        currentOwnerName = null
        isDownloading = false
        stateCallback = null
    }

    private fun unregisterReceiver() {
        try {
            downloadReceiver?.let { context.unregisterReceiver(it) }
        } catch (e: Exception) {
            // Ignore
        }
        downloadReceiver = null
    }

    private fun getDownloadFile(fileName: String): File? {
        val file = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            fileName
        )
        return if (file.exists()) file else null
    }

    private fun deleteFile(fileName: String) {
        try {
            val file = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                fileName
            )
            if (file.exists()) file.delete()
        } catch (e: Exception) {
            Log.e(TAG, "Delete error", e)
        }
    }

    private fun formatSize(bytes: Long): String {
        return when {
            bytes >= 1_073_741_824 -> String.format("%.1f GB", bytes / 1_073_741_824.0)
            bytes >= 1_048_576 -> String.format("%.1f MB", bytes / 1_048_576.0)
            bytes >= 1024 -> String.format("%.1f KB", bytes / 1024.0)
            else -> "$bytes B"
        }
    }

    private fun getErrorMessage(reason: Int): String {
        return when (reason) {
            DownloadManager.ERROR_CANNOT_RESUME -> "Cannot resume"
            DownloadManager.ERROR_DEVICE_NOT_FOUND -> "Storage not found"
            DownloadManager.ERROR_FILE_ALREADY_EXISTS -> "File exists"
            DownloadManager.ERROR_FILE_ERROR -> "File error"
            DownloadManager.ERROR_HTTP_DATA_ERROR -> "Network error"
            DownloadManager.ERROR_INSUFFICIENT_SPACE -> "No space"
            DownloadManager.ERROR_TOO_MANY_REDIRECTS -> "Too many redirects"
            DownloadManager.ERROR_UNHANDLED_HTTP_CODE -> "Server error"
            else -> "Download failed"
        }
    }

    // ==================== App Detection ====================

    /**
     * Check if package is installed
     */
    fun isInstalled(packageName: String): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(packageName, 0)
            }
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    /**
     * Get the installed version of a package
     * @param packageName The package name to check
     * @return The version name string, or null if not installed
     */
    fun getInstalledVersion(packageName: String): String? {
        return try {
            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(packageName, 0)
            }
            packageInfo.versionName
        } catch (e: PackageManager.NameNotFoundException) {
            null
        }
    }

    /**
     * Launch app by package name
     */
    fun launch(packageName: String): Boolean {
        return try {
            val intent = context.packageManager.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Launch error", e)
            false
        }
    }

    /**
     * Uninstall app by package name
     */
    fun uninstall(packageName: String) {
        try {
            Log.d(TAG, "Requesting uninstall for: $packageName")
            val intent = Intent(Intent.ACTION_DELETE).apply {
                data = Uri.parse("package:$packageName")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Uninstall error", e)
            try {
                android.widget.Toast.makeText(context, "Uninstall failed: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
            } catch (ignored: Exception) {}
        }
    }

    /**
     * Find installed package by repo/owner name.
     * 
     * Priority:
     * 1. Database Mapping (100% accurate)
     * 2. Token-Based Similarity Scan (Fuzzy Matcher)
     */
    fun findPackage(repoName: String, ownerName: String): String? {
        Log.d(TAG, "findPackage: Looking for repo='$repoName', owner='$ownerName'")

        // 1. Check Database for known mapping (100% accurate)
        try {
            val dao = (context.applicationContext as RepoStoreApp).installedAppMappingDao
            val mappedPackage = dao.getPackageNameSync(ownerName, repoName)
            if (mappedPackage != null && isInstalled(mappedPackage)) {
                Log.d(TAG, "findPackage: DB MATCH — Found mapped package '$mappedPackage'")
                return mappedPackage
            }
        } catch (e: Exception) {
            Log.e(TAG, "findPackage: Failed to check DB mapping", e)
        }

        // 2. Fallback: Token-Based Similarity Scan
        // Scans all apps and scores them based on token overlap with owner/repo
        return findBestMatchingPackage(repoName, ownerName)
    }

    /**
     * Finds the best matching package using token similarity.
     * Returns package name if a strong match is found, null otherwise.
     */
    private fun findBestMatchingPackage(repoName: String, ownerName: String): String? {
        val pm = context.packageManager
        val apps = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.getInstalledApplications(0)
        }

        val requiredRepoTokens = tokenize(repoName)
        val ownerTokens = tokenize(ownerName)
        
        // Combined query tokens for scoring
        // We generally weigh repo tokens higher than owner tokens
        
        var bestMatch: String? = null
        // Minimum score threshold to consider a match valid
        // e.g. at least one significant token match
        var bestScore = 0.0

        for (app in apps) {
            val pkg = app.packageName
            val score = calculateScore(pkg, requiredRepoTokens, ownerTokens)
            
            if (score > bestScore) {
                bestScore = score
                bestMatch = pkg
            }
        }

        // Heuristic: Score must be reasonable
        // e.g., if we matched "calculator" but nothing else, score might be low if owner mismatched
        Log.d(TAG, "Best heuristic match for $ownerName/$repoName: $bestMatch (score=$bestScore)")
        
        // Threshold: adjust as needed. 
        // If score > 0.5, it means significant overlap.
        // For "FossifyOrg/Calculator" vs "org.fossify.calculator":
        // Tokens: [fossify, org], [calculator]
        // Pkg Tokens: [org, fossify, calculator]
        // Match: fossify(1), org(1), calculator(1). Full match.
        
        if (bestScore > 0.65) { 
            return bestMatch
        }
        return null
    }

    /**
     * Calculate match score between package name and query tokens.
     */
    private fun calculateScore(packageName: String, repoTokens: Set<String>, ownerTokens: Set<String>): Double {
        // Tokenize package name (split by dots, underscores)
        val pkgTokens = tokenize(packageName)
        
        // 1. Repo Name Match (Critical)
        // At least one repo token MUST match significantly
        val repoMatches = repoTokens.count { rToken -> 
            pkgTokens.any { pToken -> isTokenMatch(rToken, pToken) }
        }
        
        if (repoMatches == 0) return 0.0 // Repo name part missing? Likely irrelevant app.

        // 2. Owner Name Match (Confirmation)
        val ownerMatches = ownerTokens.count { oToken -> 
            pkgTokens.any { pToken -> isTokenMatch(oToken, pToken) }
        }

        // 3. Calculation
        // Repo match contributes 60%, Owner match contributes 40%
        val repoScore = repoMatches.toDouble() / repoTokens.size.coerceAtLeast(1)
        val ownerScore = ownerMatches.toDouble() / ownerTokens.size.coerceAtLeast(1)
        
        // If owner is missing completely, we penalize correctly
        // But some owners don't put their name in package (e.g. "RetroMusicPlayer" -> "code.name.retro")
        // So we can't require owner match 100%, but it boosts confidence.
        
        // However, to distinguish "Google Calculator" from "Fossify Calculator":
        // "Calculator" matches both.
        // "Fossify" matches "org.fossify" but NOT "com.google".
        // So owner score is the discriminator.
        
        return (repoScore * 0.6) + (ownerScore * 0.4)
    }

    private fun isTokenMatch(token1: String, token2: String): Boolean {
        // Exact match
        if (token1 == token2) return true
        // Substring check for concatenated tokens (e.g. "simplemobiletools" vs "simple")
        if (token1.length > 3 && token2.contains(token1)) return true
        if (token2.length > 3 && token1.contains(token2)) return true
        return false
    }

    /**
     * Split string into normalized alphanumeric tokens.
     * e.g. "FossifyOrg" -> ["fossify", "org"]
     *      "Simple-Gallery" -> ["simple", "gallery"]
     */
    internal fun tokenize(text: String): Set<String> {
        val tokens = mutableSetOf<String>()
        val lower = text.lowercase()
        
        // 1. Raw normalized string (e.g. "simplemobiletools")
        val raw = lower.replace(Regex("[^a-z0-9]"), "")
        if (raw.isNotEmpty()) tokens.add(raw)
        
        // 2. Split by delimiters
        lower.split(Regex("[^a-z0-9]")).forEach { 
            if (it.isNotEmpty()) tokens.add(it) 
        }
        
        // 3. CamelCase split (e.g. "RetroMusic" -> "retro", "music")
        text.replace(Regex("([a-z])([A-Z])"), "$1 $2")
            .lowercase()
            .split(" ")
            .forEach {
                val clean = it.replace(Regex("[^a-z0-9]"), "")
                if (clean.isNotEmpty()) tokens.add(clean) 
            }
            
        return tokens
    }

    private fun extractAndSavePackageName(apkFile: File, repoName: String, ownerName: String) {
        val pm = context.packageManager
        val archiveInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getPackageArchiveInfo(apkFile.absolutePath, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageArchiveInfo(apkFile.absolutePath, 0)
        }

        archiveInfo?.packageName?.let { packageName ->
            Log.d(TAG, "Extracted package name from APK: $packageName")
            downloadScope.launch {
                try {
                    val dao = (context.applicationContext as RepoStoreApp).installedAppMappingDao
                    val mapping = InstalledAppMapping(
                        ownerName = ownerName,
                        repoName = repoName,
                        packageName = packageName
                    )
                    dao.saveMapping(mapping)
                    Log.d(TAG, "Saved mapping: $ownerName/$repoName -> $packageName")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to save package mapping", e)
                }
            }
        } ?: run {
            Log.e(TAG, "Could not extract package name from APK: ${apkFile.absolutePath}")
        }
    }
}
