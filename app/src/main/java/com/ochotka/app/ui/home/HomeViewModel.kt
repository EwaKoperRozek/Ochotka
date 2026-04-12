package com.ochotka.app.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.ochotka.app.data.repository.RestaurantRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = RestaurantRepository.getInstance(application)

    private val _uiState = MutableLiveData<HomeUiState>(HomeUiState.Idle)
    val uiState: LiveData<HomeUiState> = _uiState

    private var searchJob: Job? = null

    fun search(query: String) {
        searchJob?.cancel()
        if (query.isBlank()) {
            clearSearch()
            return
        }

        searchJob = viewModelScope.launch {
            delay(300)
            val results = repository.searchDishes(query)
            val current = _uiState.value
            if (current is HomeUiState.Success) {
                _uiState.value = current.copy(
                    searchResults = results,
                    selectedCategory = null
                )
            }
        }
    }

    fun filterByCategory(category: String) {
        viewModelScope.launch {
            val results = repository.searchDishes(category)
            val current = _uiState.value
            if (current is HomeUiState.Success) {
                _uiState.value = current.copy(
                    searchResults = results,
                    selectedCategory = category
                )
            }
        }
    }

    fun clearSearch() {
        _uiState.value = HomeUiState.Idle
    }
}
