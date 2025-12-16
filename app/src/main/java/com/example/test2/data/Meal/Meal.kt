package com.example.test2.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

@Entity(tableName = "meals")
data class Meal(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val userId: Long,
    val name: String,
    val calories: Int,
    val proteins: Int = 0,
    val fats: Int = 0,
    val carbohydrates: Int = 0,
    val date: Date,
    val products_ids: String,
    val productsWeights: String = "",
    val server_id: Long? = null,  // ← Новое поле: ID на сервере
    val synced: Boolean = false   // ← Новое поле: синхронизировано
)