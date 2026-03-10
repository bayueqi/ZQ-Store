package com.bayueqi.zqstore.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.gson.annotations.SerializedName

data class GitHubSearchResponse(
    @SerializedName("total_count") val totalCount: Int,
    @SerializedName("incomplete_results") val incompleteResults: Boolean,
    val items: List<GitHubRepo>
)

@Entity(tableName = "repositories")
data class GitHubRepo(
    @PrimaryKey val id: Long,
    val name: String,
    @SerializedName("full_name") val fullName: String,
    val description: String?,
    @SerializedName("html_url") val htmlUrl: String,
    @SerializedName("stargazers_count") val stars: Int,
    @SerializedName("forks_count") val forks: Int,
    val language: String?,
    @SerializedName("updated_at") val updatedAt: String,
    @SerializedName("created_at") val createdAt: String,
    val archived: Boolean,
    val owner: Owner,
    val topics: List<String>?,
    @SerializedName("default_branch") val defaultBranch: String?
) {
    data class Owner(
        val login: String,
        @SerializedName("avatar_url") val avatarUrl: String,
        @SerializedName("html_url") val htmlUrl: String
    )
}

data class GitHubRelease(
    val id: Long,
    @SerializedName("tag_name") val tagName: String,
    val name: String?,
    val body: String?,
    @SerializedName("html_url") val htmlUrl: String,
    @SerializedName("published_at") val publishedAt: String,
    val prerelease: Boolean,
    val draft: Boolean,
    val assets: List<ReleaseAsset>
)

data class ReleaseAsset(
    val id: Long,
    val name: String,
    val size: Long,
    @SerializedName("download_count") val downloadCount: Int,
    @SerializedName("browser_download_url") val downloadUrl: String,
    @SerializedName("content_type") val contentType: String
)

data class ReadmeResponse(
    val content: String,
    val encoding: String
)

// GitHub Contents API response
data class GitHubContent(
    val name: String,
    val path: String,
    val type: String, // "file" or "dir"
    @SerializedName("download_url") val downloadUrl: String?,
    @SerializedName("html_url") val htmlUrl: String,
    val size: Long?
)

// UI Models
data class AppItem(
    val repo: GitHubRepo,
    val latestRelease: GitHubRelease?,
    val tag: AppTag?
)

enum class AppTag {
    NEW, UPDATED, ARCHIVED
}

enum class AppCategory(val displayName: String, val queries: List<String>) {
    ALL("推荐", listOf(
        "android app topic:android stars:>100",
        "android application topic:android stars:>50",
        "android open source stars:>100",
        "安卓应用 stars:>50",
        "Android 应用 stars:>50",
        "中文安卓应用 stars:>50",
        "热门安卓应用 stars:>50"
    )),
    GAMES("游戏", listOf(
        "android game stars:>50",
        "android gaming stars:>50",
        "topic:android-game stars:>30",
        "android game engine stars:>50",
        "安卓游戏 stars:>50",
        "Android 游戏 stars:>50",
        "中文游戏 stars:>50",
        "热门游戏 stars:>50",
        "安卓手游 stars:>50"
    )),
    VIDEO("视频", listOf(
        "android video player stars:>50",
        "android video editor stars:>30",
        "topic:android-video stars:>30",
        "android media player stars:>50",
        "安卓视频播放�?stars:>50",
        "Android 视频 stars:>50",
        "安卓番剧 stars:>50",
        "安卓动漫 stars:>50",
        "热门视频播放�?stars:>50",
        "安卓视频播放 stars:>50"
    )),
    MUSIC("音乐", listOf(
        "android music player stars:>50",
        "android audio player stars:>30",
        "topic:android-music stars:>30",
        "android music streaming stars:>50",
        "安卓音乐播放�?stars:>50",
        "Android 音乐 stars:>50",
        "安卓音乐播放 stars:>50",
        "安卓音频播放�?stars:>50",
        "热门音乐 app stars:>50"
    )),
    TOOLS("工具", listOf(
        "android tools stars:>50",
        "android utility stars:>50",
        "android productivity stars:>50",
        "安卓工具 stars:>50",
        "Android 工具 stars:>50",
        "中文工具 stars:>50",
        "热门工具 stars:>50",
        "安卓实用工具 stars:>50"
    ));

    // For backward compatibility
    val query: String get() = queries.first()
}

data class GitHubUser(
    val id: Long,
    val login: String,
    val name: String?,
    @SerializedName("avatar_url") val avatarUrl: String,
    @SerializedName("html_url") val htmlUrl: String,
    val bio: String?,
    val company: String?,
    val location: String?,
    val email: String?,
    val blog: String?,
    @SerializedName("twitter_username") val twitterUsername: String?,
    @SerializedName("public_repos") val publicRepos: Int,
    @SerializedName("public_gists") val publicGists: Int,
    val followers: Int,
    val following: Int,
    @SerializedName("created_at") val createdAt: String
)


