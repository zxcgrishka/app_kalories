package com.example.test2

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.test2.data.AppDatabase
import com.example.test2.data.DailyMeal.DailyMeal
import com.example.test2.data.Meal
import com.example.test2.data.User.UserRepository
import com.example.test2.databinding.ActivityAddDishBinding
import com.example.test2.network.NetworkModule
import com.example.test2.ui.MealAdapter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Date

class AddDishActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAddDishBinding
    private lateinit var adapter: MealAdapter
    private lateinit var repository: UserRepository

    private var userId = -1L
    private var selectedMeals = mutableListOf<Meal>()
    private var totalCalories = 0

    companion object {
        const val CREATE_DISH_REQUEST_CODE = 1001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("AddDishActivity", "onCreate started")

        binding = ActivityAddDishBinding.inflate(layoutInflater)
        setContentView(binding.root)

        userId = getUserIdFromPrefs()
        Log.d("AddDishActivity", "Loaded userId from prefs = $userId")

        if (userId == -1L) {
            Toast.makeText(this, "Ошибка авторизации", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val database = AppDatabase.getDatabase(this)
        repository = UserRepository(
            database,
            NetworkModule.provideMyApiService(this),
            this
        )

        setupRecyclerView()
        loadMealsFromDB()  // Первая загрузка

        binding.btnCreateDish.setOnClickListener {
            val intent = Intent(this, CreateDishActivity::class.java)
            intent.putExtra("userId", userId)
            startActivityForResult(intent, CREATE_DISH_REQUEST_CODE)
        }

        binding.btnReady.setOnClickListener {
            if (selectedMeals.isNotEmpty()) {
                val mealIds = selectedMeals.mapNotNull { it.id }.joinToString(",")
                val dailyMeal = DailyMeal(
                    userId = userId,
                    date = Date(),
                    totalCalories = totalCalories,
                    meal_ids = mealIds
                )

                lifecycleScope.launch {
                    repository.insertDailyMeal(dailyMeal)
                    Log.d("AddDishActivity", "DailyMeal saved with $totalCalories kcal")
                    setResult(Activity.RESULT_OK)
                    finish()
                }
            } else {
                Toast.makeText(this, "Выберите хотя бы одно блюдо", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupRecyclerView() {
        adapter = MealAdapter { meal, isSelected ->
            if (isSelected) {
                selectedMeals.add(meal)
                totalCalories += meal.calories
            } else {
                selectedMeals.remove(meal)
                totalCalories -= meal.calories
            }
            Log.d("AddDishActivity", "Selected: ${selectedMeals.size}, total: $totalCalories kcal")
        }
        binding.rvMeals.layoutManager = LinearLayoutManager(this)
        binding.rvMeals.adapter = adapter
    }

    private fun loadMealsFromDB() {
        lifecycleScope.launch {
            val meals = repository.getMealsByUser(userId).first()
            Log.d("AddDishActivity", "Loaded ${meals.size} meals from DB")
            meals.forEach { meal ->
                Log.d("AddDishActivity", "Meal: ${meal.name}, calories: ${meal.calories}")
            }
            adapter.updateMeals(meals)
            selectedMeals.clear()
            totalCalories = 0
        }
    }

    override fun onResume() {
        super.onResume()
        Log.d("AddDishActivity", "onResume — reloading meals")
        loadMealsFromDB()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == CREATE_DISH_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            Log.d("AddDishActivity", "Returned from CreateDish — reloading via onResume")
        }
    }

    private fun getUserIdFromPrefs(): Long {
        val sharedPref = getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
        val id = sharedPref.getLong("current_user_id", -1L)
        Log.d("AddDishActivity", "getUserIdFromPrefs = $id")
        return id
    }
}