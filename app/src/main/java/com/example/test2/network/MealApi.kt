package com.example.test2.network

import com.google.gson.annotations.SerializedName

data class MealApi(
    val id: Long? = null,
    val name: String,
    val calories: Int,
    val proteins: Int,
    val fats: Int,
    val carbohydrates: Int,
    @SerializedName("products_ids") val products_ids: String,
    val date: String? = null
)