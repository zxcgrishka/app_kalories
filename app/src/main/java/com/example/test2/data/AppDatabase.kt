package com.example.test2.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.test2.data.User.User
import com.example.test2.data.User.UserDao
import com.example.test2.ui.home.AddDish.CreateDish.AddProduct.Product
import com.example.test2.ui.home.AddDish.CreateDish.AddProduct.ProductDao
import com.example.test2.data.Meal
import com.example.test2.data.MealDao
import com.example.test2.data.DailyMeal.DailyMeal
import com.example.test2.data.DailyMealDao

@Database(
    entities = [User::class, Product::class, Meal::class, DailyMeal::class],
    version = 28,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun productDao(): ProductDao
    abstract fun mealDao(): MealDao
    abstract fun dailyMealDao(): DailyMealDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "app_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}