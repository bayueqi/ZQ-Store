package com.samyak.repostore.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.samyak.repostore.data.model.GitHubRepo
import kotlinx.coroutines.flow.Flow

@Dao
interface RepoDao {

    @Query("SELECT * FROM repositories ORDER BY stars DESC")
    fun getAllRepos(): Flow<List<GitHubRepo>>

    @Query("SELECT * FROM repositories WHERE id = :id")
    suspend fun getRepoById(id: Long): GitHubRepo?

    @Query("SELECT * FROM repositories WHERE fullName = :fullName LIMIT 1")
    suspend fun getRepoByFullName(fullName: String): GitHubRepo?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRepos(repos: List<GitHubRepo>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRepo(repo: GitHubRepo)

    @Query("DELETE FROM repositories")
    suspend fun clearAll()

    @Query("SELECT * FROM repositories WHERE name LIKE '%' || :query || '%' OR description LIKE '%' || :query || '%' ORDER BY stars DESC")
    fun searchRepos(query: String): Flow<List<GitHubRepo>>
}
