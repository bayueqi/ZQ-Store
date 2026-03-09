package com.samyak.repostore.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.samyak.repostore.data.model.AppCategory
import com.samyak.repostore.data.model.AppItem
import com.samyak.repostore.data.repository.GitHubRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class HomeViewModel(private val repository: GitHubRepository) : ViewModel() {

    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val _selectedCategory = MutableStateFlow(AppCategory.ALL)
    val selectedCategory: StateFlow<AppCategory> = _selectedCategory.asStateFlow()

    private var currentPage = 1
    private val loadedApps = mutableListOf<AppItem>()
    private var loadJob: Job? = null
    private var isLoadingMore = false

    init {
        loadApps()
    }

    fun loadApps(refresh: Boolean = false) {
        if (refresh) {
            currentPage = 1
            loadedApps.clear()
            isLoadingMore = false
        }

        // Cancel any existing load job
        loadJob?.cancel()

        loadJob = viewModelScope.launch {
            _uiState.value = if (loadedApps.isEmpty()) {
                HomeUiState.Loading
            } else {
                HomeUiState.LoadingMore(loadedApps.toList())
            }

            val result = repository.getPopularAndroidApps(currentPage)

            result.fold(
                onSuccess = { apps ->
                    if (refresh || currentPage == 1) {
                        loadedApps.clear()
                    }
                    loadedApps.addAll(apps)
                    _uiState.value = if (loadedApps.isEmpty()) {
                        HomeUiState.Empty
                    } else {
                        HomeUiState.Success(loadedApps.toList())
                    }
                    isLoadingMore = false
                },
                onFailure = { error ->
                    _uiState.value = if (loadedApps.isEmpty()) {
                        HomeUiState.Error(error.message ?: "Failed to load apps")
                    } else {
                        // Keep showing existing data with error toast
                        HomeUiState.Success(loadedApps.toList())
                    }
                    isLoadingMore = false
                }
            )
        }
    }

    fun loadMore() {
        if (isLoadingMore) return
        if (_uiState.value is HomeUiState.Loading) return

        isLoadingMore = true
        currentPage++

        viewModelScope.launch {
            _uiState.value = HomeUiState.LoadingMore(loadedApps.toList())

            val result = if (_selectedCategory.value == AppCategory.ALL) {
                repository.getPopularAndroidApps(currentPage)
            } else {
                repository.getAppsByCategory(_selectedCategory.value, currentPage)
            }

            result.fold(
                onSuccess = { apps ->
                    loadedApps.addAll(apps)
                    _uiState.value = HomeUiState.Success(loadedApps.toList())
                },
                onFailure = {
                    // Just show existing data, don't show error for load more
                    _uiState.value = HomeUiState.Success(loadedApps.toList())
                }
            )
            isLoadingMore = false
        }
    }

    fun selectCategory(category: AppCategory) {
        if (_selectedCategory.value == category) return
        _selectedCategory.value = category
        loadedApps.clear()
        currentPage = 1
        isLoadingMore = false

        loadJob?.cancel()

        loadJob = viewModelScope.launch {
            _uiState.value = HomeUiState.Loading

            val result = if (category == AppCategory.ALL) {
                repository.getPopularAndroidApps(currentPage)
            } else {
                repository.getAppsByCategory(category, currentPage)
            }

            result.fold(
                onSuccess = { apps ->
                    loadedApps.addAll(apps)
                    _uiState.value = if (loadedApps.isEmpty()) {
                        HomeUiState.Empty
                    } else {
                        HomeUiState.Success(loadedApps.toList())
                    }
                },
                onFailure = { error ->
                    _uiState.value = HomeUiState.Error(error.message ?: "Failed to load apps")
                }
            )
        }
    }

    fun refresh() {
        loadApps(refresh = true)
    }

    fun retry() {
        loadApps(refresh = true)
    }
}

sealed class HomeUiState {
    data object Loading : HomeUiState()
    data object Empty : HomeUiState()
    data class LoadingMore(val currentApps: List<AppItem>) : HomeUiState()
    data class Success(val apps: List<AppItem>) : HomeUiState()
    data class Error(val message: String) : HomeUiState()
}

class HomeViewModelFactory(private val repository: GitHubRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(HomeViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return HomeViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
