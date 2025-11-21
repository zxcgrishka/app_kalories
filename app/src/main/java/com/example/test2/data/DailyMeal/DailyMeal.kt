package com.example.test2.data.DailyMeal

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

@Entity(tableName = "daily_meals")
data class DailyMeal (
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val userId: Long,
    val date: Date,
    val totalCalories: Int,
    val mealIds: String
)