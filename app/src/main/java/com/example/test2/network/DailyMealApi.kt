package com.example.test2.network

import com.google.gson.annotations.SerializedName

data class DailyMealApi(
    val id: Long? = null,
    @SerializedName("total_calories") val total_calories: Int,
    @SerializedName("meal_ids") val meal_ids: String,
    val date: String? = null
)