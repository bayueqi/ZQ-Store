package com.samyak.repostore.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.samyak.repostore.data.model.GitHubRelease
import com.samyak.repostore.data.model.GitHubRepo
import com.samyak.repostore.data.repository.GitHubRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class DetailViewModel(private val repository: GitHubRepository) : ViewModel() {

    private val _uiState = MutableStateFlow<DetailUiState>(DetailUiState.Loading)
    val uiState: StateFlow<DetailUiState> = _uiState.asStateFlow()

    private val _readme = MutableStateFlow<String?>(null)
    val readme: StateFlow<String?> = _readme.asStateFlow()

    private val _screenshots = MutableStateFlow<List<String>>(emptyList())
    val screenshots: StateFlow<List<String>> = _screenshots.asStateFlow()

    fun loadAppDetails(owner: String, repoName: String) {
        viewModelScope.launch {
            _uiState.value = DetailUiState.Loading

            val repoResult = repository.getRepoDetails(owner, repoName)

            repoResult.fold(
                onSuccess = { repo ->
                    // Show repo data immediately
                    _uiState.value = DetailUiState.Success(repo, null)

                    // Load release in background
                    loadRelease(owner, repoName, repo)

                    // Load screenshots in background
                    loadScreenshots(owner, repoName, repo.defaultBranch)

                    // Load readme in background
                    loadReadme(owner, repoName)
                },
                onFailure = { error ->
                    _uiState.value = DetailUiState.Error(error.message ?: "Failed to load app details")
                }
            )
        }
    }

    private fun loadRelease(owner: String, repoName: String, repo: GitHubRepo) {
        viewModelScope.launch {
            val releaseResult = repository.getLatestRelease(owner, repoName)
            releaseResult.onSuccess { release ->
                _uiState.value = DetailUiState.Success(repo, release)
            }
        }
    }

    private fun loadScreenshots(owner: String, repoName: String, defaultBranch: String?) {
        viewModelScope.launch {
            val result = repository.getScreenshots(owner, repoName, defaultBranch)
            result.onSuccess { images ->
                _screenshots.value = images
            }
        }
    }

    private fun loadReadme(owner: String, repoName: String) {
        viewModelScope.launch {
            val result = repository.getReadme(owner, repoName)
            result.onSuccess { content ->
                _readme.value = content
            }
        }
    }

    fun retry(owner: String, repoName: String) {
        loadAppDetails(owner, repoName)
    }
}

sealed class DetailUiState {
    data object Loading : DetailUiState()
    data class Success(val repo: GitHubRepo, val release: GitHubRelease?) : DetailUiState()
    data class Error(val message: String) : DetailUiState()
}

class DetailViewModelFactory(private val repository: GitHubRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(DetailViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return DetailViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
