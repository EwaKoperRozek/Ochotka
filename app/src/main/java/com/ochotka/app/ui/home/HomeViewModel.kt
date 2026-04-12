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

    fun search(query: String) {
        searchJob?.cancel()
        if (query.isBlank()) {
            clearSearch()
            return
        }

        searchJob = viewModelScope.launch {
            delay(300)
            val results = repository.searchDishes(query)
            _uiState.value = HomeUiState.Success(
                searchResults = results,
                selectedCategory = null,
                activeQuery = query
            )
        }
    }

    fun filterByCategory(category: String) {
        viewModelScope.launch {
            val results = repository.searchDishes(category)
            _uiState.value = HomeUiState.Success(
                searchResults = results,
                selectedCategory = category,
                activeQuery = ""
            )
        }
    }

    fun clearSearch() {
        _uiState.value = HomeUiState.Idle
    }

    fun saveCameraState(target: LatLng, zoom: Float) {
        savedCameraTarget = target
        savedCameraZoom = zoom
    }

    fun getSavedCameraTarget(): LatLng? = savedCameraTarget

    fun getSavedCameraZoom(): Float? = savedCameraZoom
}
