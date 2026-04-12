package com.ochotka.app.ui.details

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.ochotka.app.data.model.Dish
import com.ochotka.app.data.model.Restaurant
import com.ochotka.app.data.repository.RestaurantRepository
import kotlinx.coroutines.launch

sealed class DetailUiState {
    object Loading : DetailUiState()
    data class RestaurantLoaded(
        val restaurant: Restaurant,
        val dishes: List<Dish>,
        val isFavorite: Boolean
    ) : DetailUiState()
    data class DishLoaded(
        val dish: Dish,
        val restaurant: Restaurant
    ) : DetailUiState()
    data class Error(val message: String) : DetailUiState()
}

class DetailViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = RestaurantRepository.getInstance(application)
    private val prefs = application.getSharedPreferences("ochotka_prefs", android.content.Context.MODE_PRIVATE)

    private val _uiState = MutableLiveData<DetailUiState>(DetailUiState.Loading)
    val uiState: LiveData<DetailUiState> = _uiState

    fun loadRestaurant(restaurantId: String) {
        viewModelScope.launch {
            _uiState.value = DetailUiState.Loading
            try {
                val restaurant = repository.getRestaurantById(restaurantId)
                    ?: throw Exception("Nie znaleziono restauracji")
                val dishes = repository.getDishesByRestaurant(restaurantId)
                val isFav = isFavorite(restaurantId)
                _uiState.value = DetailUiState.RestaurantLoaded(restaurant, dishes, isFav)
            } catch (e: Exception) {
                _uiState.value = DetailUiState.Error(e.message ?: "Błąd")
            }
        }
    }

    fun loadDish(dishId: String, restaurantId: String) {
        viewModelScope.launch {
            _uiState.value = DetailUiState.Loading
            try {
                val dish = repository.getDishById(dishId)
                    ?: throw Exception("Nie znaleziono dania")
                val restaurant = repository.getRestaurantById(restaurantId)
                    ?: throw Exception("Nie znaleziono restauracji")
                _uiState.value = DetailUiState.DishLoaded(dish, restaurant)
            } catch (e: Exception) {
                _uiState.value = DetailUiState.Error(e.message ?: "Błąd")
            }
        }
    }

    fun toggleFavorite(restaurantId: String) {
        val ids = prefs.getStringSet("favorite_ids", emptySet())?.toMutableSet() ?: mutableSetOf()
        if (restaurantId in ids) ids.remove(restaurantId) else ids.add(restaurantId)
        prefs.edit().putStringSet("favorite_ids", ids).apply()

        val current = _uiState.value
        if (current is DetailUiState.RestaurantLoaded) {
            _uiState.value = current.copy(isFavorite = restaurantId in ids)
        }
    }

    private fun isFavorite(restaurantId: String): Boolean {
        val ids = prefs.getStringSet("favorite_ids", emptySet()) ?: emptySet()
        return restaurantId in ids
    }
}