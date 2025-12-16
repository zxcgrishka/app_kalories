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
    suspend fun insertMeal(meal: Meal): Long

    @Query("DELETE FROM meals WHERE userId = :userId")
    suspend fun clearMealsByUser(userId: Long)

    @Query("SELECT * FROM meals WHERE userId = :userId AND synced = 0")
    fun getUnsyncedMealsByUser(userId: Long): Flow<List<Meal>>

    @Query("UPDATE meals SET server_id = :serverId, synced = 1 WHERE id = :localId")
    suspend fun updateMealSynced(localId: Long, serverId: Long?)

    @Query("UPDATE meals SET server_id = :serverId WHERE id = :localId")
    suspend fun updateServerId(localId: Long, serverId: Long?)
}