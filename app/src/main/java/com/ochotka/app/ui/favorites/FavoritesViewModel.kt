package com.ochotka.app.ui.favorites

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.ochotka.app.data.model.Restaurant
import com.ochotka.app.data.repository.RestaurantRepository
import kotlinx.coroutines.launch

class FavoritesViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = RestaurantRepository.getInstance(application)
    private val prefs = application.getSharedPreferences("ochotka_prefs", Context.MODE_PRIVATE)

    private val _favorites = MutableLiveData<List<Restaurant>>()
    val favorites: LiveData<List<Restaurant>> = _favorites

    init { loadFavorites() }

    fun loadFavorites() {
        viewModelScope.launch {
            val ids = prefs.getStringSet("favorite_ids", emptySet()) ?: emptySet()
            val all = repository.getAllRestaurants()
            _favorites.value = all.filter { it.id in ids }
        }
    }

    fun toggleFavorite(restaurantId: String) {
        val current = prefs.getStringSet("favorite_ids", emptySet())?.toMutableSet() ?: mutableSetOf()
        if (restaurantId in current) current.remove(restaurantId) else current.add(restaurantId)
        prefs.edit().putStringSet("favorite_ids", current).apply()
        loadFavorites()
    }

    fun isFavorite(restaurantId: String): Boolean {
        val ids = prefs.getStringSet("favorite_ids", emptySet()) ?: emptySet()
        return restaurantId in ids
    }
}