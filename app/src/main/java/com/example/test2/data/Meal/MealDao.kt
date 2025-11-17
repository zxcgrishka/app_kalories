package com.example.test2.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MealDao {
    @Query("SELECT * FROM meals WHERE userId = :userId ORDER BY date DESC")
    fun getMealsByUser(userId: Long): Flow<List<Meal>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMeal(meal: Meal)

    @Query("DELETE FROM meals WHERE userId = :userId")
    suspend fun clearMealsByUser(userId: Long)
}