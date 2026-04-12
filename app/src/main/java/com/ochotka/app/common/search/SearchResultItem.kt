package com.ochotka.app.common.search

import com.ochotka.app.data.model.Dish
import com.ochotka.app.data.model.Restaurant

data class SearchResultItem(
    val dish: Dish,
    val restaurant: Restaurant,
    val score: Float
)
