package com.samyak.repostore.util

/**
 * Utility for comparing version strings from GitHub releases with installed app versions.
 * Handles various version formats like "v1.2.3", "1.2.3", "2.0.0-beta", "1.0.0-rc1", etc.
 */
object VersionComparator {

    /**
     * Check if the latest version is newer than the current installed version.
     * 
     * @param installedVersion The version string of the installed app (e.g., "1.2.3")
     * @param latestVersion The version string from GitHub release tag (e.g., "v1.2.4")
     * @return true if latestVersion is newer than installedVersion
     */
    fun isNewerVersion(installedVersion: String, latestVersion: String): Boolean {
        val normalizedInstalled = normalizeVersion(installedVersion)
        val normalizedLatest = normalizeVersion(latestVersion)
        
        if (normalizedInstalled.isEmpty() || normalizedLatest.isEmpty()) {
            return false
        }
        
        val installedParts = normalizedInstalled.split(".")
        val latestParts = normalizedLatest.split(".")
        
        val maxLength = maxOf(installedParts.size, latestParts.size)
        
        for (i in 0 until maxLength) {
            val installedPart = installedParts.getOrNull(i)?.toIntOrNull() ?: 0
            val latestPart = latestParts.getOrNull(i)?.toIntOrNull() ?: 0
            
            when {
                latestPart > installedPart -> return true
                latestPart < installedPart -> return false
            }
        }
        
        return false // Versions are equal
    }
    
    /**
     * Normalize a version string by removing common prefixes and suffixes.
     * Examples:
     * - "v1.2.3" -> "1.2.3"
     * - "V1.2.3" -> "1.2.3"
     * - "1.2.3-beta" -> "1.2.3"
     * - "1.2.3-rc1" -> "1.2.3"
     * - "release-1.2.3" -> "1.2.3"
     */
    fun normalizeVersion(version: String): String {
        var normalized = version.trim()
        
        // Remove common prefixes
        val prefixes = listOf("v", "V", "version", "Version", "release-", "Release-", "ver", "Ver")
        for (prefix in prefixes) {
            if (normalized.startsWith(prefix)) {
                normalized = normalized.removePrefix(prefix)
                break
            }
        }
        
        // Remove suffixes after hyphen (e.g., "-beta", "-rc1", "-alpha")
        val hyphenIndex = normalized.indexOf('-')
        if (hyphenIndex > 0) {
            normalized = normalized.substring(0, hyphenIndex)
        }
        
        // Remove suffixes after underscore (e.g., "_beta", "_rc1")
        val underscoreIndex = normalized.indexOf('_')
        if (underscoreIndex > 0) {
            normalized = normalized.substring(0, underscoreIndex)
        }
        
        // Keep only digits and dots
        normalized = normalized.replace(Regex("[^0-9.]"), "")
        
        // Remove leading/trailing dots
        normalized = normalized.trim('.')
        
        return normalized
    }
}
