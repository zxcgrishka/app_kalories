package com.example.test2

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase


@Database(entities = [Product::class], version = 1, exportSchema = false)
abstract class MainDb : RoomDatabase() {

    abstract fun tempProductDebugDao(): Dao
    abstract fun getDao(): ProductDao

    companion object {
        @Volatile
        private var INSTANCE: MainDb? = null

        fun getDb(context: Context): MainDb {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    MainDb::class.java,
                    "main_database.db" // Имя файла БД
                )
                    // .addMigrations(MIGRATION_1_2) // Если есть миграции
                    .fallbackToDestructiveMigration() // Для разработки, если не хочешь писать миграции
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}