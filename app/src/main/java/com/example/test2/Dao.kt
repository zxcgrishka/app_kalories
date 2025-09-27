package com.example.test2

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface Dao {
    @Insert
    fun insertProduct(product: Product)
    @Query("SELECT * FROM products")
    fun getAllProducts1(): Flow<List<Product>>
}