package com.example.test2.network

data class Token(
    val access_token: String,
    val token_type: String
)

data class User(
    val id: Int,
    val username: String,
    val is_active: String,
    val lastModified: Long
)

// Запрос для текстового распознавания
data class NutritionixTextRequest(
    val query: String
)

// Ответ от Nutritionix Image API
data class NutritionixImageResponse(
    val foods: List<NutritionixFood>
)

// Ответ от Nutritionix Text API
data class NutritionixTextResponse(
    val foods: List<NutritionixFood>
)

// Модель продукта от Nutritionix
data class NutritionixFood(
    val food_name: String,
    val serving_qty: Double?,
    val serving_unit: String?,
    val nf_calories: Double?,
    val nf_protein: Double?,
    val nf_total_fat: Double?,
    val nf_total_carbohydrate: Double?,
    val photo: NutritionixPhoto?
)

data class NutritionixPhoto(
    val thumb: String?
)