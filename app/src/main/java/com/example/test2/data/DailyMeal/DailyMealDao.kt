package com.example.test2.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.test2.data.DailyMeal.DailyMeal
import kotlinx.coroutines.flow.Flow

@Dao
interface DailyMealDao {
    @Insert
    suspend fun insertDailyMeal(dailyMeal: DailyMeal)

    @Query("SELECT * FROM daily_meals WHERE userId = :userId AND date(date / 1000, 'unixepoch') = date('now') ORDER BY date DESC")
    fun getTodayDailyMealsByUser(userId: Long): Flow<List<DailyMeal>>

    @Query("SELECT * FROM daily_meals WHERE userId = :userId AND date >= :startTimestamp AND date <= :endTimestamp ORDER BY date ASC")
    suspend fun getDailyMealsByPeriod(userId: Long, startTimestamp: Long, endTimestamp: Long): List<DailyMeal>

    @Query("DELETE FROM daily_meals WHERE userId = :userId")
    suspend fun clearDailyMealsByUser(userId: Long)
}