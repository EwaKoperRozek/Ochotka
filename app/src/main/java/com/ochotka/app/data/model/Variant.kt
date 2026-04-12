package com.ochotka.app.data.model

import com.google.gson.annotations.SerializedName

data class Variant(
    @SerializedName("size")
    val size: String,

    @SerializedName("price")
    val price: Double
)
