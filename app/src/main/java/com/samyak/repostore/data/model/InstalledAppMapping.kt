package com.samyak.repostore.data.model

import androidx.room.Entity
import androidx.room.Index

/**
 * Maps a GitHub repo (owner/repo) to the actual Android package name.
 * Package name is extracted from the APK during download/install.
 * This provides 100% accurate installed-app detection.
 */
@Entity(
    tableName = "installed_app_mappings",
    primaryKeys = ["ownerName", "repoName"],
    indices = [Index(value = ["packageName"])]
)
data class InstalledAppMapping(
    val ownerName: String,
    val repoName: String,
    val packageName: String,
    val updatedAt: Long = System.currentTimeMillis()
)
