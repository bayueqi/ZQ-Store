package com.samyak.repostore.util

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import com.samyak.repostore.RepoStoreApp
import com.samyak.repostore.data.db.InstalledAppMappingDao
import io.mockk.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Tests for AppInstaller's detection logic.
 * Priorities:
 * 1. Database Mapping (100% accurate)
 * 2. Token-Based Fuzzy Scoring (Heuristic)
 */
class AppInstallerDetectionTest {

    private lateinit var context: Context
    private lateinit var repoStoreApp: RepoStoreApp
    private lateinit var packageManager: PackageManager
    private lateinit var installedAppMappingDao: InstalledAppMappingDao
    private lateinit var appInstaller: AppInstaller

    private val installedPackages = mutableSetOf<String>()

    @Before
    fun setUp() {
        context = mockk(relaxed = true)
        repoStoreApp = mockk(relaxed = true)
        packageManager = mockk(relaxed = true)
        installedAppMappingDao = mockk(relaxed = true)

        every { context.applicationContext } returns repoStoreApp
        every { repoStoreApp.installedAppMappingDao } returns installedAppMappingDao
        every { context.packageManager } returns packageManager

        every { installedAppMappingDao.getPackageNameSync(any(), any()) } returns null

        every { packageManager.getPackageInfo(any<String>(), any<Int>()) } answers {
            val pkg = firstArg<String>()
            if (pkg in installedPackages) {
                PackageInfo().apply { packageName = pkg }
            } else {
                throw PackageManager.NameNotFoundException(pkg)
            }
        }

        every { packageManager.getInstalledApplications(any<Int>()) } answers {
            installedPackages.map { pkg ->
                ApplicationInfo().apply { packageName = pkg }
            }
        }

        val constructor = AppInstaller::class.java.getDeclaredConstructor(Context::class.java)
        constructor.isAccessible = true
        appInstaller = constructor.newInstance(context)
    }

    @After
    fun tearDown() {
        installedPackages.clear()
        unmockkAll()
    }

    // ==================== 1. Database Mapping Tests ====================

    @Test
    fun findPackage_usesDatabaseMapping_whenAvailable() {
        every { installedAppMappingDao.getPackageNameSync("FossifyOrg", "Calendar") } returns "org.fossify.calendar"
        installedPackages.add("org.fossify.calendar")

        val result = appInstaller.findPackage("Calendar", "FossifyOrg")
        assertEquals("org.fossify.calendar", result)
    }

    // ==================== 2. Fuzzy Scoring Tests ====================

    @Test
    fun findPackage_fuzzy_matches_FossifyCalculator() {
        // Repo: FossifyOrg/Calculator
        // App: org.fossify.calculator
        // Shared Tokens: fossify, org, calculator. Perfect match.
        installedPackages.add("org.fossify.calculator")
        val result = appInstaller.findPackage("Calculator", "FossifyOrg")
        assertEquals("org.fossify.calculator", result)
    }

    @Test
    fun findPackage_fuzzy_matches_SimpleGallery() {
        // Repo: SimpleMobileTools/Simple-Gallery
        // App: com.simplemobiletools.gallery
        // Shared: simplemobiletools (full match), gallery.
        installedPackages.add("com.simplemobiletools.gallery")
        val result = appInstaller.findPackage("Simple-Gallery", "SimpleMobileTools")
        assertEquals("com.simplemobiletools.gallery", result)
    }

    @Test
    fun findPackage_fuzzy_rejects_GoogleCalculator() {
        // Repo: FossifyOrg/Calculator
        // App: com.google.android.calculator
        // Repo match: calculator (0.6 score)
        // Owner (FossifyOrg) match: 0 (0.0 score)
        // Total: 0.6. Threshold: 0.65.
        // Should REJECT.
        installedPackages.add("com.google.android.calculator")
        val result = appInstaller.findPackage("Calculator", "FossifyOrg")
        assertNull("Should reject Google Calculator due to owner mismatch", result)
    }

    @Test
    fun findPackage_fuzzy_matches_RepoStore() {
        // Repo: Samya/RepoStore
        // App: com.samyak.repostore
        // Repo: repostore (match). Owner: samya vs samyak (substring match).
        installedPackages.add("com.samyak.repostore")
        val result = appInstaller.findPackage("RepoStore", "Samya")
        assertEquals("com.samyak.repostore", result)
    }

    // ==================== Tokenizer Tests ====================

    @Test
    fun tokenize_splitsCamelCase() {
        val tokens = appInstaller.tokenize("RetroMusicPlayer")
        assertTrue(tokens.contains("retro"))
        assertTrue(tokens.contains("music"))
        assertTrue(tokens.contains("player"))
    }
}
