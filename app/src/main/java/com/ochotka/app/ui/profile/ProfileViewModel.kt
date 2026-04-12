package com.ochotka.app.ui.profile

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ochotka.app.data.model.Restaurant
import com.ochotka.app.data.repository.RestaurantRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class ProfileUiState(
    val favoriteRestaurants: List<Restaurant> = emptyList(),
    val favoriteCount: Int = 0,
    val searchCount: Int = 0,
    val restaurantCount: Int = 0,
    val dishCount: Int = 0,
    val appVersion: String = ""
)

class ProfileViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = RestaurantRepository.getInstance(application)
    private val prefs = application.getSharedPreferences("ochotka_prefs", Context.MODE_PRIVATE)

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState

    fun loadProfile() {
        viewModelScope.launch {
            val allRestaurants = repository.getAllRestaurants()
            val allDishes = repository.getAllDishes()

            val favoriteIds = prefs.getStringSet("favorite_ids", emptySet()) ?: emptySet()
            val favoriteRestaurants = allRestaurants.filter { it.id in favoriteIds }

            val searchCount = prefs.getInt("search_count", 0)

            val appVersion = try {
                val pInfo = getApplication<Application>().packageManager
                    .getPackageInfo(getApplication<Application>().packageName, 0)
                "Wersja ${pInfo.versionName}"
            } catch (e: Exception) {
                "Wersja 1.0"
            }

            _uiState.value = ProfileUiState(
                favoriteRestaurants = favoriteRestaurants,
                favoriteCount = favoriteIds.size,
                searchCount = searchCount,
                restaurantCount = allRestaurants.size,
                dishCount = allDishes.size,
                appVersion = appVersion
            )
        }
    }
}