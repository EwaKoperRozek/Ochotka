package com.ochotka.app.data.model

import com.google.gson.annotations.SerializedName

data class Restaurant(
    @SerializedName("id")
    val id: String,

    @SerializedName("name")
    val name: String,

    @SerializedName("url")
    val url: String,

    @SerializedName("address")
    val address: String,

    @SerializedName("postcode")
    val postcode: String,

    @SerializedName("city")
    val city: String,

    @SerializedName("lat")
    val lat: Double,

    @SerializedName("lng")
    val lng: Double,

    @SerializedName("description")
    val description: String,

    @SerializedName("allergen_phone")
    val allergenPhone: String,

    @SerializedName("opening_hours")
    val openingHours: Map<String, List<String>>
)
