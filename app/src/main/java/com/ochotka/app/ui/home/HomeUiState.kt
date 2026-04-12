package com.ochotka.app.ui.home

import com.ochotka.app.common.search.SearchResultItem
import com.ochotka.app.data.model.Dish
import com.ochotka.app.data.model.Restaurant

sealed class HomeUiState {
    object Loading : HomeUiState()

    data class Success(
        val featuredDishes: List<Pair<Dish, Restaurant>>,
        val popularRestaurants: List<Restaurant>,
        val searchResults: List<SearchResultItem>?,
        val selectedCategory: String?,
        val userLat: Double,
        val userLng: Double
    ) : HomeUiState()

    data class Error(val message: String) : HomeUiState()
}
