package com.ochotka.app.data.model

import com.google.gson.annotations.SerializedName

data class Dish(
    @SerializedName("id")
    val id: String,

    @SerializedName("restaurant_id")
    val restaurantId: String,

    @SerializedName("category")
    val category: String,

    @SerializedName("name")
    val name: String,

    @SerializedName("description")
    val description: String,

    @SerializedName("ingredients")
    val ingredients: List<String>,

    @SerializedName("price_min")
    val priceMin: Double,

    @SerializedName("price_max")
    val priceMax: Double,

    @SerializedName("variants")
    val variants: List<Variant>,

    @SerializedName("search_blob")
    val searchBlob: String
)
