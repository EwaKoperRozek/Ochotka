package com.ochotka.app.ui.map

import android.app.Application
import android.location.Location
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.ochotka.app.common.utils.LocationHelper
import com.ochotka.app.data.model.Restaurant
import com.ochotka.app.data.repository.RestaurantRepository
import kotlinx.coroutines.launch

data class MapUiState(
    val restaurants: List<Restaurant> = emptyList(),
    val userLocation: Location? = null,
    val isLoading: Boolean = true,
    val error: String? = null
)

class MapViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = RestaurantRepository.getInstance(application)
    private val locationHelper = LocationHelper(application)

    private val _uiState = MutableLiveData(MapUiState())
    val uiState: LiveData<MapUiState> = _uiState

    init {
        loadRestaurants()
    }

    fun loadRestaurants() {
        viewModelScope.launch {
            _uiState.value = MapUiState(isLoading = true)
            try {
                val restaurants = repository.getAllRestaurants()
                var userLocation: Location? = null
                if (locationHelper.hasPermission()) {
                    userLocation = locationHelper.getLastLocation()
                }
                _uiState.value = MapUiState(
                    restaurants = restaurants,
                    userLocation = userLocation,
                    isLoading = false
                )
            } catch (e: Exception) {
                _uiState.value = MapUiState(
                    isLoading = false,
                    error = e.message ?: "Błąd ładowania mapy"
                )
            }
        }
    }

    fun refreshLocation() {
        viewModelScope.launch {
            if (!locationHelper.hasPermission()) return@launch
            val loc = locationHelper.getCurrentLocation()
            val current = _uiState.value ?: return@launch
            _uiState.value = current.copy(userLocation = loc)
        }
    }
}
