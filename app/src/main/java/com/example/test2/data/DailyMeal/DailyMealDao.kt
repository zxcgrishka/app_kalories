package com.example.test2.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.test2.data.DailyMeal.DailyMeal
import kotlinx.coroutines.flow.Flow

@Dao
interface DailyMealDao {
    @Query("SELECT * FROM daily_meals WHERE userId = :userId AND date(date / 1000, 'unixepoch') = date('now') ORDER BY date DESC")  // Фильтр по сегодняшней дате
    fun getTodayDailyMealsByUser(userId: Long): Flow<List<DailyMeal>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDailyMeal(dailyMeal: DailyMeal)

    @Query("DELETE FROM daily_meals WHERE userId = :userId")
    suspend fun clearDailyMealsByUser(userId: Long)
}