package com.ochotka.app.ui.home

import android.app.Application
import android.location.Location
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.ochotka.app.common.utils.LocationHelper
import com.ochotka.app.data.model.Restaurant
import com.ochotka.app.data.repository.RestaurantRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = RestaurantRepository.getInstance(application)
    private val locationHelper = LocationHelper(application)

    private val _uiState = MutableLiveData<HomeUiState>(HomeUiState.Loading)
    val uiState: LiveData<HomeUiState> = _uiState

    private var searchJob: Job? = null

    private var allRestaurants: List<Restaurant> = emptyList()
    private var restaurantMap: Map<String, Restaurant> = emptyMap()
    private var userLat: Double = LocationHelper.POZNAN_LAT
    private var userLng: Double = LocationHelper.POZNAN_LNG

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            _uiState.value = HomeUiState.Loading
            try {
                if (locationHelper.hasPermission()) {
                    val loc: Location? = locationHelper.getLastLocation()
                    if (loc != null) {
                        userLat = loc.latitude
                        userLng = loc.longitude
                    }
                }

                allRestaurants = repository.getAllRestaurants()
                restaurantMap = repository.getRestaurantMap()

                val featured = repository.getFeaturedSearchResults(6)
                    .map { it.dish to it.restaurant }

                val sorted = allRestaurants.sortedBy { rest ->
                    val results = FloatArray(1)
                    Location.distanceBetween(userLat, userLng, rest.lat, rest.lng, results)
                    results[0]
                }

                _uiState.value = HomeUiState.Success(
                    featuredDishes = featured,
                    popularRestaurants = sorted,
                    searchResults = null,
                    selectedCategory = null,
                    userLat = userLat,
                    userLng = userLng
                )
            } catch (e: Exception) {
                _uiState.value = HomeUiState.Error(e.message ?: "Błąd ładowania danych")
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
        val current = _uiState.value
        if (current is HomeUiState.Success) {
            _uiState.value = current.copy(
                searchResults = null,
                selectedCategory = null
            )
        }
    }

    fun refresh() = loadData()
}