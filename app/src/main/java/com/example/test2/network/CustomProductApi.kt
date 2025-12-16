package com.example.test2.network

data class CustomProductApi(
    val id: Long? = null,
    val name: String,
    val calories: Int,
    val proteins: Int = 0,
    val fats: Int = 0,
    val carbohydrates: Int = 0
)