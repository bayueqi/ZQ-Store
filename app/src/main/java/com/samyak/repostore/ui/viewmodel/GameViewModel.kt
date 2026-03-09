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

class GameViewModel(private val repository: GitHubRepository) : ViewModel() {

    private val _uiState = MutableStateFlow<GameUiState>(GameUiState.Loading)
    val uiState: StateFlow<GameUiState> = _uiState.asStateFlow()

    private var currentPage = 1
    private val loadedGames = mutableListOf<AppItem>()
    private var loadJob: Job? = null
    private var isLoadingMore = false

    init {
        loadGames()
    }

    fun loadGames(refresh: Boolean = false) {
        if (refresh) {
            currentPage = 1
            loadedGames.clear()
            isLoadingMore = false
        }

        loadJob?.cancel()

        loadJob = viewModelScope.launch {
            _uiState.value = if (loadedGames.isEmpty()) {
                GameUiState.Loading
            } else {
                GameUiState.LoadingMore(loadedGames.toList())
            }

            val result = repository.getAppsByCategory(AppCategory.GAMES, currentPage)

            result.fold(
                onSuccess = { games ->
                    if (refresh || currentPage == 1) {
                        loadedGames.clear()
                    }
                    loadedGames.addAll(games)
                    _uiState.value = if (loadedGames.isEmpty()) {
                        GameUiState.Empty
                    } else {
                        GameUiState.Success(loadedGames.toList())
                    }
                    isLoadingMore = false
                },
                onFailure = { error ->
                    _uiState.value = if (loadedGames.isEmpty()) {
                        GameUiState.Error(error.message ?: "Failed to load games")
                    } else {
                        GameUiState.Success(loadedGames.toList())
                    }
                    isLoadingMore = false
                }
            )
        }
    }

    fun loadMore() {
        if (isLoadingMore) return
        if (_uiState.value is GameUiState.Loading) return

        isLoadingMore = true
        currentPage++

        viewModelScope.launch {
            _uiState.value = GameUiState.LoadingMore(loadedGames.toList())

            val result = repository.getAppsByCategory(AppCategory.GAMES, currentPage)

            result.fold(
                onSuccess = { games ->
                    loadedGames.addAll(games)
                    _uiState.value = GameUiState.Success(loadedGames.toList())
                },
                onFailure = {
                    _uiState.value = GameUiState.Success(loadedGames.toList())
                }
            )
            isLoadingMore = false
        }
    }

    fun refresh() {
        loadGames(refresh = true)
    }

    fun retry() {
        loadGames(refresh = true)
    }
}

sealed class GameUiState {
    data object Loading : GameUiState()
    data object Empty : GameUiState()
    data class LoadingMore(val currentGames: List<AppItem>) : GameUiState()
    data class Success(val games: List<AppItem>) : GameUiState()
    data class Error(val message: String) : GameUiState()
}

class GameViewModelFactory(private val repository: GitHubRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(GameViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return GameViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
