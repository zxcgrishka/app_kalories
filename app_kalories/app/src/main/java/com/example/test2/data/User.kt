package com.example.test2.data
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "users")
data class User(
    @PrimaryKey val id: Int,
    val username: String,
    val hashedPassword: String,
    val lastModified: Long = System.currentTimeMillis()
)