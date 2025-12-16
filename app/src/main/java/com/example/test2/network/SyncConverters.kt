package com.example.test2.network

import com.example.test2.data.Meal
import com.example.test2.data.DailyMeal.DailyMeal
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())

// Meal
fun Meal.toApiModel(): MealApi = MealApi(
    id = if (id == 0L) null else id,
    name = name,
    calories = calories,
    proteins = proteins,
    fats = fats,
    carbohydrates = carbohydrates,
    products_ids = products_ids
)

fun MealApi.toLocalMeal(userId: Long): Meal = Meal(
    userId = userId,
    name = name,
    calories = calories,
    proteins = proteins,
    fats = fats,
    carbohydrates = carbohydrates,
    date = date?.let { isoFormat.parse(it) } ?: Date(),
    products_ids = products_ids,
    productsWeights = ""
)

// DailyMeal
fun DailyMeal.toApiModel(): DailyMealApi = DailyMealApi(
    id = if (id == 0L) null else id,
    total_calories = totalCalories,
    meal_ids = meal_ids
)

fun DailyMealApi.toLocalDailyMeal(userId: Long): DailyMeal = DailyMeal(
    userId = userId,
    date = date?.let { isoFormat.parse(it) } ?: Date(),
    totalCalories = total_calories,
    meal_ids = meal_ids
)
