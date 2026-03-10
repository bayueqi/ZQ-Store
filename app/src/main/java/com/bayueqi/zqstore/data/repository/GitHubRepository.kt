package com.bayueqi.zqstore.data.repository

import com.bayueqi.zqstore.data.api.RetrofitClient
import com.bayueqi.zqstore.data.db.RepoDao
import com.bayueqi.zqstore.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.time.Instant
import java.time.temporal.ChronoUnit

class GitHubRepository(private val repoDao: RepoDao) {

    private val api = RetrofitClient.api

    // In-memory cache
    private val releaseCache = mutableMapOf<String, GitHubRelease?>()
    private val screenshotCache = mutableMapOf<String, List<String>>()
    private val developerReposCache = mutableMapOf<String, Pair<Long, List<AppItem>>>()
    private val installableReposCache = mutableMapOf<String, Boolean>() // Cache for repos with installable assets
    private val searchCache = mutableMapOf<String, Pair<Long, List<AppItem>>>() // Cache for search results
    
    private var lastFetchTime = 0L
    private val cacheValidityMs = 30 * 60 * 1000L // 30 minutes
    private val developerCacheValidityMs = 30 * 60 * 1000L // 30 minutes
    private val searchCacheValidityMs = 15 * 60 * 1000L // 15 minutes

    private val screenshotFolders = listOf(
        "screenshots", "screenshot", "images", "image", "assets",
        "art", "media", "pics", "pictures", "img"
    )

    private val imageExtensions = listOf(".png", ".jpg", ".jpeg", ".gif", ".webp")
    
    // Installable asset extensions for different platforms
    private val installableExtensions = listOf(
        // Android
        ".apk", ".aab",
        // Windows
        ".exe", ".msi", ".msix", ".appx",
        // macOS
        ".dmg", ".pkg", ".app",
        // Linux
        ".deb", ".rpm", ".snap", ".flatpak", ".AppImage",
        // iOS
        ".ipa"
    )

    /**
     * Check if a release has installable assets for specified platforms
     */
    private fun hasInstallableAsset(release: GitHubRelease?, platforms: List<com.bayueqi.zqstore.data.model.Platform> = emptyList()): Boolean {
        if (release == null) return false
        
        return release.assets.any { asset ->
            val assetName = asset.name.lowercase()
            
            if (platforms.isEmpty()) {
                // No platform filter, check all installable extensions
                installableExtensions.any { ext ->
                    assetName.endsWith(ext)
                }
            } else {
                // Check if asset matches any selected platform's extensions
                platforms.any { platform ->
                    platform.extensions.any { ext ->
                        assetName.endsWith(ext)
                    }
                }
            }
        }
    }

    /**
     * Check if repo has installable assets in latest release
     */
    suspend fun repoHasInstallableAssets(owner: String, repoName: String, platforms: List<com.bayueqi.zqstore.data.model.Platform> = emptyList()): Boolean {
        val cacheKey = if (platforms.isEmpty()) {
            "$owner/$repoName"
        } else {
            "$owner/$repoName/${platforms.joinToString(",") { it.name }}"
        }
        
        // Check cache first
        installableReposCache[cacheKey]?.let { return it }
        
        return try {
            val release = api.getLatestRelease(owner, repoName)
            val hasInstallable = hasInstallableAsset(release, platforms)
            installableReposCache[cacheKey] = hasInstallable
            if (hasInstallable) {
                releaseCache[cacheKey] = release
            }
            hasInstallable
        } catch (e: Exception) {
            installableReposCache[cacheKey] = false
            false
        }
    }

    /**
     * Filter repos to only include those with installable assets
     */
    private suspend fun filterReposWithInstallableAssets(repos: List<GitHubRepo>, platforms: List<com.bayueqi.zqstore.data.model.Platform> = emptyList()): List<AppItem> = coroutineScope {
        // Limit concurrency to avoid overwhelming the API
        val maxConcurrency = 5
        val chunks = repos.chunked(maxConcurrency)
        val allResults = mutableListOf<AppItem>()
        
        chunks.forEach { chunk ->
            val results = chunk.map { repo ->
                async {
                    try {
                        // Check cache first
                        val cacheKey = if (platforms.isEmpty()) {
                            "${repo.owner.login}/${repo.name}"
                        } else {
                            "${repo.owner.login}/${repo.name}/${platforms.joinToString(",") { it.name }}"
                        }
                        
                        // Skip if already checked and not installable
                        if (installableReposCache.getOrDefault(cacheKey, false)) {
                            val release = releaseCache[cacheKey]
                            val tag = determineTag(repo, release)
                            AppItem(repo, release, tag)
                        } else if (installableReposCache.containsKey(cacheKey)) {
                            null
                        } else {
                            val hasInstallable = repoHasInstallableAssets(repo.owner.login, repo.name, platforms)
                            if (hasInstallable) {
                                val release = releaseCache[cacheKey]
                                val tag = determineTag(repo, release)
                                AppItem(repo, release, tag)
                            } else {
                                null
                            }
                        }
                    } catch (e: Exception) {
                        null
                    }
                }
            }
            allResults.addAll(results.awaitAll().filterNotNull())
        }
        
        allResults
    }

    /**
     * Search result wrapper with metadata
     */
    data class SearchResult(
        val items: List<AppItem>,
        val totalCount: Int,
        val hasNextPage: Boolean,
        val query: String,
        val filters: SearchFilters
    )

    suspend fun searchApps(query: String, page: Int = 1): Result<List<AppItem>> = withContext(Dispatchers.IO) {
        try {
            // Check cache first
            val cacheKey = "search_${query}_$page"
            val currentTime = System.currentTimeMillis()
            searchCache[cacheKey]?.let { (timestamp, apps) ->
                if (currentTime - timestamp < searchCacheValidityMs) {
                    return@withContext Result.success(apps)
                }
            }

            // Search in name, description, and readme
            val searchQuery = "$query in:name,description topic:android"
//            val searchQuery = "$query in:name,description,readme"
            val response = api.searchRepositories(searchQuery, perPage = 40, page = page)

            // Filter to only repos with APK releases
            val appItems = filterReposWithInstallableAssets(response.items)

            if (appItems.isNotEmpty()) {
                repoDao.insertRepos(response.items)
                // Cache the search results
                searchCache[cacheKey] = currentTime to appItems
            }
            
            Result.success(appItems)
        } catch (e: HttpException) {
            handleHttpException(e)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Advanced search with filters for language, stars, sort order, etc.
     * @param query User's search term
     * @param filters Advanced search filters
     * @param page Page number for pagination
     * @return List of matching apps with APK releases
     */
    suspend fun advancedSearchApps(
        query: String, 
        filters: SearchFilters = SearchFilters.DEFAULT,
        page: Int = 1
    ): Result<SearchResult> = withContext(Dispatchers.IO) {
        try {
            // Check cache first
            val cacheKey = "advanced_search_${query}_${filters.hashCode()}_$page"
            val currentTime = System.currentTimeMillis()
            searchCache[cacheKey]?.let { (timestamp, apps) ->
                if (currentTime - timestamp < searchCacheValidityMs) {
                    return@withContext Result.success(SearchResult(
                        items = apps,
                        totalCount = apps.size,
                        hasNextPage = apps.size == 40,
                        query = query,
                        filters = filters
                    ))
                }
            }

            val searchQuery = filters.buildQuery(query)
            
            // For "Best Match", pass null to use GitHub's relevance-based sorting
            // Otherwise, use the specified sort option
            val sortBy = filters.sortBy.apiValue.ifEmpty { null }
            val order = "desc"
            
            val response = api.searchRepositories(
                query = searchQuery,
                sort = sortBy ?: "stars", // Default to stars for Best Match (GitHub's default)
                order = order,
                perPage = 40,
                page = page
            )

            // Always cache the repos for future searches
            if (response.items.isNotEmpty()) {
                repoDao.insertRepos(response.items)
            }

            // Filter to only repos with installable assets
            val appItems = if (filters.platforms.isNotEmpty()) {
                filterReposWithInstallableAssets(response.items, filters.platforms)
            } else {
                filterReposWithInstallableAssets(response.items)
            }
            
            // Apply Play Store-like relevance scoring and sort
            val rankedItems = rankByRelevance(appItems, query)
            
            // Cache the search results
            searchCache[cacheKey] = currentTime to rankedItems
            
            Result.success(SearchResult(
                items = rankedItems,
                totalCount = rankedItems.size,
                hasNextPage = rankedItems.size == 40,
                query = query,
                filters = filters
            ))
        } catch (e: HttpException) {
            handleHttpException(e)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Rank search results by relevance score (Play Store-like algorithm)
     * Scoring factors:
     * - Exact name match: +100 points
     * - Name starts with query: +50 points
     * - Name contains query: +25 points
     * - Description contains query: +10 points
     * - Star count bonus: log10(stars) * 5 points
     * - Android topic bonus: +15 points
     * - Has releases (APK): +20 points
     */
    private fun rankByRelevance(items: List<AppItem>, query: String): List<AppItem> {
        if (query.isBlank()) return items
        
        val queryLower = query.lowercase().trim()
        val queryWords = queryLower.split(" ").filter { it.isNotBlank() }
        
        return items.sortedByDescending { item ->
            var score = 0.0
            val repo = item.repo
            val nameLower = repo.name.lowercase()
            val descLower = repo.description?.lowercase() ?: ""
            
            // Exact name match (highest priority)
            if (nameLower == queryLower) {
                score += 100
            }
            // Name starts with query
            else if (nameLower.startsWith(queryLower)) {
                score += 50
            }
            // Name contains query as whole word
            else if (nameLower.contains(queryLower)) {
                score += 25
            }
            // Name contains all query words
            else if (queryWords.all { word -> nameLower.contains(word) }) {
                score += 20
            }
            
            // Description contains query
            if (descLower.contains(queryLower)) {
                score += 10
            }
            // Description contains all query words
            else if (queryWords.all { word -> descLower.contains(word) }) {
                score += 5
            }
            
            // Star count bonus (logarithmic to prevent mega-repos from dominating)
            if (repo.stars > 0) {
                score += kotlin.math.log10(repo.stars.toDouble()) * 5
            }
            
            // Android topic bonus
            repo.topics?.let { topics ->
                if (topics.any { it.lowercase().contains("android") }) {
                    score += 15
                }
            }
            
            // Has releases (APK) bonus
            if (item.latestRelease != null) {
                score += 20
            }
            
            score
        }
    }

    /**
     * Get search suggestions based on partial query
     * Uses cached repos for quick suggestions
     */
    suspend fun getSearchSuggestions(query: String): List<String> = withContext(Dispatchers.IO) {
        if (query.length < 2) return@withContext emptyList()
        
        try {
            val cachedRepos = repoDao.searchRepos(query).first()
            cachedRepos.take(5).map { it.name }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun getPopularAndroidApps(page: Int = 1): Result<List<AppItem>> = withContext(Dispatchers.IO) {
        try {
            val query = "android app topic:android stars:>100"
            val response = api.searchRepositories(query, perPage = 40, page = page)

            lastFetchTime = System.currentTimeMillis()

            // Filter to only repos with APK releases
            val appItems = filterReposWithInstallableAssets(response.items)

            if (appItems.isNotEmpty() && page == 1) {
                repoDao.clearAll()
                repoDao.insertRepos(response.items)
            }

            Result.success(appItems)
        } catch (e: HttpException) {
            handleHttpException(e)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getAppsByCategory(category: AppCategory, page: Int = 1): Result<List<AppItem>> {
        return withContext(Dispatchers.IO) {
            try {
                // Check cache first
                val cacheKey = "category_${category.name}_$page"
                val currentTime = System.currentTimeMillis()
                searchCache[cacheKey]?.let { (timestamp, apps) ->
                    if (currentTime - timestamp < searchCacheValidityMs) {
                        return@withContext Result.success(apps)
                    }
                }
                
                // Use only the first query for each category, similar to getPopularAndroidApps
                val query = category.queries.first()
                val response = api.searchRepositories(query, perPage = 40, page = page)

                // Filter to only repos with APK releases
                val appItems = filterReposWithInstallableAssets(response.items)

                if (appItems.isNotEmpty() && page == 1) {
                    repoDao.insertRepos(response.items)
                    // Cache the results
                    searchCache[cacheKey] = currentTime to appItems
                }
                
                Result.success(appItems)
            } catch (e: HttpException) {
                handleHttpException(e)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun getRepoDetails(owner: String, repoName: String): Result<GitHubRepo> = withContext(Dispatchers.IO) {
        try {
            // Check cache first
            val cached = repoDao.getRepoByFullName("$owner/$repoName")
            if (cached != null) {
                // Return cached, but also try to update in background
                return@withContext Result.success(cached)
            }

            val repo = api.getRepository(owner, repoName)
            repoDao.insertRepo(repo)
            Result.success(repo)
        } catch (e: HttpException) {
            val cached = repoDao.getRepoByFullName("$owner/$repoName")
            if (cached != null) {
                Result.success(cached)
            } else {
                handleHttpException(e)
            }
        } catch (e: Exception) {
            val cached = repoDao.getRepoByFullName("$owner/$repoName")
            if (cached != null) {
                Result.success(cached)
            } else {
                Result.failure(e)
            }
        }
    }

    suspend fun getReleases(owner: String, repoName: String): Result<List<GitHubRelease>> = withContext(Dispatchers.IO) {
        try {
            val releases = api.getReleases(owner, repoName, perPage = 5)
            Result.success(releases)
        } catch (e: HttpException) {
            handleHttpException(e)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getLatestRelease(owner: String, repoName: String): Result<GitHubRelease> = withContext(Dispatchers.IO) {
        try {
            val cacheKey = "$owner/$repoName"

            releaseCache[cacheKey]?.let {
                return@withContext Result.success(it)
            }

            val release = api.getLatestRelease(owner, repoName)
            releaseCache[cacheKey] = release
            Result.success(release)
        } catch (e: HttpException) {
            if (e.code() == 404) {
                releaseCache["$owner/$repoName"] = null
            }
            handleHttpException(e)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getReadme(owner: String, repoName: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val response = api.getReadme(owner, repoName)
            val decoded = if (response.encoding == "base64") {
                String(android.util.Base64.decode(response.content.replace("\n", ""), android.util.Base64.DEFAULT))
            } else {
                response.content
            }
            Result.success(decoded)
        } catch (e: HttpException) {
            handleHttpException(e)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getDeveloperRepos(username: String, page: Int = 1): Result<List<AppItem>> = withContext(Dispatchers.IO) {
        try {
            // Check cache
            val currentTime = System.currentTimeMillis()
            val cacheKey = "$username-$page"
            developerReposCache[cacheKey]?.let { (timestamp, apps) ->
                if (currentTime - timestamp < developerCacheValidityMs) {
                    return@withContext Result.success(apps)
                }
            }

            val repos = api.getUserRepos(username, sort = "updated", perPage = 20, page = page)

            val appItems = repos.map { repo ->
                val tag = determineTag(repo, null)
                AppItem(repo, null, tag)
            }

            // Cache the result
            developerReposCache[cacheKey] = currentTime to appItems

            repoDao.insertRepos(repos)
            Result.success(appItems)
        } catch (e: HttpException) {
            handleHttpException(e)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Fetch screenshots - simplified to reduce API calls
     */
    suspend fun getScreenshots(owner: String, repoName: String, defaultBranch: String?): Result<List<String>> = withContext(Dispatchers.IO) {
        val cacheKey = "$owner/$repoName"

        screenshotCache[cacheKey]?.let {
            return@withContext Result.success(it)
        }

        try {
            val screenshots = mutableListOf<String>()
            val branch = defaultBranch ?: "main"

            // Only check README for images to minimize API calls
            val readmeImages = getImagesFromReadme(owner, repoName, branch)
            screenshots.addAll(readmeImages)

            // Only if no images found in README, try one folder
            if (screenshots.isEmpty()) {
                try {
                    val rootContents = api.getRootContents(owner, repoName, branch)
                    val screenshotFolder = rootContents.find { content ->
                        content.type == "dir" && screenshotFolders.any { folder ->
                            content.name.equals(folder, ignoreCase = true)
                        }
                    }

                    screenshotFolder?.let { folder ->
                        val images = getImagesFromFolder(owner, repoName, folder.path, branch)
                        screenshots.addAll(images)
                    }
                } catch (e: Exception) {
                    // Ignore - just use README images
                }
            }

            val uniqueScreenshots = screenshots.distinct().take(8)
            screenshotCache[cacheKey] = uniqueScreenshots

            Result.success(uniqueScreenshots)
        } catch (e: Exception) {
            screenshotCache[cacheKey] = emptyList()
            Result.success(emptyList())
        }
    }

    private suspend fun getImagesFromFolder(owner: String, repoName: String, path: String, branch: String): List<String> {
        return try {
            val contents = api.getContents(owner, repoName, path, branch)
            contents.filter { content ->
                content.type == "file" && imageExtensions.any { ext ->
                    content.name.lowercase().endsWith(ext)
                }
            }.mapNotNull { it.downloadUrl }.take(4)
        } catch (e: Exception) {
            emptyList()
        }
    }

    private suspend fun getImagesFromReadme(owner: String, repoName: String, branch: String): List<String> {
        return try {
            val readmeResult = getReadme(owner, repoName)
            val readme = readmeResult.getOrNull() ?: return emptyList()

            val imageRegex = Regex("""!\[.*?\]\((.*?)\)""")
            val htmlImgRegex = Regex("""<img[^>]+src=["']([^"']+)["']""", RegexOption.IGNORE_CASE)

            val markdownImages = imageRegex.findAll(readme).map { it.groupValues[1] }.toList()
            val htmlImages = htmlImgRegex.findAll(readme).map { it.groupValues[1] }.toList()

            (markdownImages + htmlImages)
                .filter { url ->
                    imageExtensions.any { ext -> url.lowercase().contains(ext) }
                }
                .map { url ->
                    if (url.startsWith("http")) {
                        url
                    } else {
                        "https://raw.githubusercontent.com/$owner/$repoName/$branch/${url.trimStart('/')}"
                    }
                }
                .take(5)
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun getCachedRepos(): Flow<List<GitHubRepo>> = repoDao.getAllRepos()

    fun searchCachedRepos(query: String): Flow<List<GitHubRepo>> = repoDao.searchRepos(query)

    private fun <T> handleHttpException(e: HttpException): Result<T> {
        val message = when (e.code()) {
            429 -> "API请求频率超限。请等待几分钟或在设置中添加GitHub令牌。"
            403 -> "API请求频率已达上限。添加GitHub令牌可提高限制（60 到 5000 请求/小时）。"
            404 -> "未找到"
            500, 502, 503 -> "GitHub服务器错误，请稍后重试。"
            else -> "Network error: ${e.message()}"
        }
        return Result.failure(Exception(message))
    }

    fun determineTag(repo: GitHubRepo, release: GitHubRelease?): AppTag? {
        if (repo.archived) return AppTag.ARCHIVED

        val now = Instant.now()
        val createdAt = try {
            Instant.parse(repo.createdAt)
        } catch (e: Exception) {
            null
        }

        if (createdAt != null && ChronoUnit.DAYS.between(createdAt, now) <= 30) {
            return AppTag.NEW
        }

        if (release != null) {
            val publishedAt = try {
                Instant.parse(release.publishedAt)
            } catch (e: Exception) {
                null
            }
            if (publishedAt != null && ChronoUnit.DAYS.between(publishedAt, now) <= 7) {
                return AppTag.UPDATED
            }
        }

        return null
    }

    fun clearCache() {
        releaseCache.clear()
        screenshotCache.clear()
        developerReposCache.clear()
        installableReposCache.clear()
        searchCache.clear()
        lastFetchTime = 0L
    }
}


