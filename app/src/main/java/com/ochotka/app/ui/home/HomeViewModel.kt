package com.ochotka.app.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.google.android.gms.maps.model.LatLng
import com.ochotka.app.data.repository.RestaurantRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = RestaurantRepository.getInstance(application)

    private val _uiState = MutableLiveData<HomeUiState>(HomeUiState.Idle)
    val uiState: LiveData<HomeUiState> = _uiState

    private var searchJob: Job? = null
    private var savedCameraTarget: LatLng? = null
    private var savedCameraZoom: Float? = null
    private var selectedRestaurantId: String? = null
    private var lastSuccessState: HomeUiState.Success? = null

    init {
        prewarmSearchData()
    }

    private fun prewarmSearchData() {
        viewModelScope.launch {
            runCatching {
                repository.prewarmSearchData()
            }
        }
    }

    fun search(query: String) {
        searchJob?.cancel()
        if (query.isBlank()) {
            clearSearch()
            return
        }

        searchJob = viewModelScope.launch {
            _uiState.value = HomeUiState.Loading(
                selectedCategory = null,
                activeQuery = query
            )
            delay(300)
            val results = repository.searchDishes(query)
            val successState = HomeUiState.Success(
                searchResults = results,
                selectedCategory = null,
                activeQuery = query
            )
            lastSuccessState = successState
            _uiState.value = successState
        }
    }

    fun filterByCategory(category: String) {
        viewModelScope.launch {
            _uiState.value = HomeUiState.Loading(
                selectedCategory = category,
                activeQuery = ""
            )
            val results = repository.searchDishes(category)
            val successState = HomeUiState.Success(
                searchResults = results,
                selectedCategory = category,
                activeQuery = ""
            )
            lastSuccessState = successState
            _uiState.value = successState
        }
    }

    fun clearSearch() {
        selectedRestaurantId = null
        lastSuccessState = null
        _uiState.value = HomeUiState.Idle
    }

    fun restoreLastSuccessStateIfNeeded() {
        if (_uiState.value is HomeUiState.Idle) {
            lastSuccessState?.let { _uiState.value = it }
        }
    }

    fun replayLastSuccessState() {
        lastSuccessState?.let { _uiState.value = it }
    }

    fun hasActiveSearchResults(): Boolean {
        val currentResults = (uiState.value as? HomeUiState.Success)?.searchResults
        return !currentResults.isNullOrEmpty() || !lastSuccessState?.searchResults.isNullOrEmpty()
    }

    fun selectRestaurant(restaurantId: String) {
        selectedRestaurantId = restaurantId
    }

    fun clearSelectedRestaurant() {
        selectedRestaurantId = null
    }

    fun getSelectedRestaurantId(): String? = selectedRestaurantId

    fun saveCameraState(target: LatLng, zoom: Float) {
        savedCameraTarget = target
        savedCameraZoom = zoom
    }

    fun getSavedCameraTarget(): LatLng? = savedCameraTarget

    fun getSavedCameraZoom(): Float? = savedCameraZoom
}
