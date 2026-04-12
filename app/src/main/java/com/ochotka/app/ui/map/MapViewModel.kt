package com.ochotka.app.ui.map

import android.app.Application
import android.location.Location
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.ochotka.app.common.utils.LocationHelper
import com.ochotka.app.data.model.Restaurant
import kotlinx.coroutines.launch

data class MapUiState(
    val restaurants: List<Restaurant> = emptyList(),
    val userLocation: Location? = null,
    val isLoading: Boolean = false,
    val error: String? = null
)

class MapViewModel(application: Application) : AndroidViewModel(application) {

    private val locationHelper = LocationHelper(application)

    private val _uiState = MutableLiveData(MapUiState())
    val uiState: LiveData<MapUiState> = _uiState

    init {
        loadInitialState()
    }

    private fun loadInitialState() {
        viewModelScope.launch {
            try {
                var userLocation: Location? = null
                if (locationHelper.hasPermission()) {
                    userLocation = locationHelper.getLastLocation()
                }
                _uiState.value = MapUiState(
                    restaurants = emptyList(),
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

    fun setRestaurants(restaurants: List<Restaurant>) {
        val current = _uiState.value ?: MapUiState(isLoading = false)
        _uiState.value = current.copy(
            restaurants = restaurants,
            isLoading = false,
            error = null
        )
    }

    fun refreshLocation() {
        viewModelScope.launch {
            if (!locationHelper.hasPermission()) return@launch

            val current = _uiState.value ?: return@launch
            val loc = locationHelper.getCurrentLocation()
                ?: locationHelper.getLastLocation()

            _uiState.value = current.copy(
                userLocation = loc,
                error = if (loc == null) "Nie udało się pobrać lokalizacji." else null
            )
        }
    }

}
