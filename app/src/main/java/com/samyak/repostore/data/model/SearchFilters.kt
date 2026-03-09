package com.samyak.repostore.data.model

/**
 * Platform options for filtering apps
 */
enum class Platform(val displayName: String, val extensions: List<String>) {
    WINDOWS("Windows", listOf(".exe", ".msi", ".msix", ".appx")),
    MACOS("macOS", listOf(".dmg", ".pkg", ".app")),
    LINUX("Linux", listOf(".deb", ".rpm", ".snap", ".flatpak", ".AppImage")),
    ANDROID("Android", listOf(".apk", ".aab")),
    IOS("iOS", listOf(".ipa"));
    
    companion object {
        fun fromDisplayName(name: String): Platform? {
            return values().find { it.displayName == name }
        }
    }
}

/**
 * Advanced search filters for GitHub repository search
 */
data class SearchFilters(
    val sortBy: SortOption = SortOption.BEST_MATCH,
    val language: String? = null,
    val minStars: Int? = null,
    val platforms: List<Platform> = emptyList(), // Empty by default - show all platforms
    val includeArchived: Boolean = false,
    val updatedWithin: UpdatedWithin? = null,
    val topics: List<String> = emptyList(), // Empty by default - searches ALL repos
    val searchInReadme: Boolean = true // Also search README content
) {
    /**
     * Build the GitHub search query string from filters
     * Optimized for Play Store-like relevance matching
     */
    fun buildQuery(userQuery: String): String {
        val queryParts = mutableListOf<String>()
        
        // User's search term - search in name and description
        if (userQuery.isNotBlank()) {
            queryParts.add("$userQuery in:name,description")
        }
        
        // Add topic filters only if user specified them
        if (topics.isNotEmpty()) {
            topics.forEach { topic ->
                queryParts.add("topic:$topic")
            }
        }
        
        // Language filter
        language?.let { lang ->
            if (lang.isNotBlank()) {
                queryParts.add("language:$lang")
            }
        }
        
        // Minimum stars - helps filter quality repos
        minStars?.let { stars ->
            if (stars > 0) {
                queryParts.add("stars:>=$stars")
            }
        }
        
        // Updated within timeframe
        updatedWithin?.let { timeframe ->
            val dateFilter = timeframe.toQueryString()
            if (dateFilter.isNotBlank()) {
                queryParts.add(dateFilter)
            }
        }
        
        // Exclude archived repos unless explicitly included
        if (!includeArchived) {
            queryParts.add("archived:false")
        }
        
        return queryParts.joinToString(" ")
    }
    
    companion object {
        val DEFAULT = SearchFilters()
        
        val POPULAR_LANGUAGES = listOf(
            "Kotlin", "Java", "C++", "C", "Dart", "Rust", "Go"
        )
        
        val MIN_STAR_OPTIONS = listOf(0, 10, 50, 100, 500, 1000, 5000)
    }
}

/**
 * Sort options for search results
 */
enum class SortOption(val apiValue: String, val displayName: String) {
    BEST_MATCH("", "Best Match"),
    STARS("stars", "Most Stars"),
    FORKS("forks", "Most Forks"),
    UPDATED("updated", "Recently Updated"),
    HELP_WANTED_ISSUES("help-wanted-issues", "Help Wanted");
    
    companion object {
        fun fromApiValue(value: String): SortOption {
            return values().find { it.apiValue == value } ?: BEST_MATCH
        }
    }
}

/**
 * Time-based filter for when repos were last updated
 */
enum class UpdatedWithin(val displayName: String, private val daysAgo: Int?) {
    ANY_TIME("Any time", null),
    LAST_WEEK("Last week", 7),
    LAST_MONTH("Last month", 30),
    LAST_3_MONTHS("Last 3 months", 90),
    LAST_YEAR("Last year", 365);
    
    fun toQueryString(): String {
        if (daysAgo == null) return ""
        
        val calendar = java.util.Calendar.getInstance()
        calendar.add(java.util.Calendar.DAY_OF_YEAR, -daysAgo)
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
        return "pushed:>=${sdf.format(calendar.time)}"
    }
}
