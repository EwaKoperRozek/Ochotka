package com.ochotka.app.data.model

import com.google.gson.annotations.SerializedName

data class OchotkaData(
    @SerializedName("version")
    val version: Int,

    @SerializedName("restaurants")
    val restaurants: List<Restaurant>,

    @SerializedName("dishes")
    val dishes: List<Dish>
)
