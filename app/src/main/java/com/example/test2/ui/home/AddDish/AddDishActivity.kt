package com.example.test2

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.viewModels
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
import com.example.test2.ui.MealViewModelFactory
import com.example.test2.ui.home.AddDish.CreateDish.MealViewModel
import kotlinx.coroutines.launch
import java.util.Date

class AddDishActivity : AppCompatActivity() {
    private lateinit var binding: ActivityAddDishBinding
    private lateinit var localRepository: UserRepository
    private lateinit var mealViewModel: MealViewModel
    private lateinit var adapter: MealAdapter

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

        // Локальный Repository
        val database = AppDatabase.getDatabase(this)
        localRepository = UserRepository(
            database,
            NetworkModule.provideMyApiService(this),
            this
        )

        mealViewModel = viewModels<MealViewModel> {
            MealViewModelFactory(localRepository)
        }.value

        setupRecyclerView()
        loadMeals()

        binding.btnReady.setOnClickListener {
            if (selectedMeals.isNotEmpty()) {
                // Собираем ID выбранных блюд - только ID, не создаем новые объекты
                val mealIds = selectedMeals.map { it.id }.joinToString(",")

                val dailyMeal = DailyMeal(
                    id = 0, // Автоинкремент
                    userId = userId,
                    date = Date(),
                    totalCalories = totalCalories,
                    mealIds = mealIds
                )

                lifecycleScope.launch {
                    try {
                        // ЗАПИСЬ: Сохраняем ТОЛЬКО DailyMeal
                        localRepository.insertDailyMeal(dailyMeal)
                        Log.d("AddDishActivity", "DailyMeal saved: $totalCalories кал, meals IDs: $mealIds")

                        // ПРОВЕРКА: Показываем, что блюда НЕ добавляются заново
                        selectedMeals.forEach { meal ->
                            Log.d("AddDishActivity", "Using existing meal - ID: ${meal.id}, Name: ${meal.name}")
                        }

                        setResult(RESULT_OK)
                        finish()
                    } catch (e: Exception) {
                        Log.e("AddDishActivity", "Error saving DailyMeal", e)
                    }
                }
            } else {
                Log.w("AddDishActivity", "No meals selected")
            }
        }

        binding.btnCreateDish.setOnClickListener {
            Log.d("AddDishActivity", "btnCreateDish clicked — redirecting to CreateDish")
            val intent = Intent(this, CreateDishActivity::class.java)
            intent.putExtra("userId", userId)
            startActivityForResult(intent, CREATE_DISH_REQUEST_CODE)
        }

        Log.d("AddDishActivity", "onCreate finished")
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == CREATE_DISH_REQUEST_CODE && resultCode == RESULT_OK) {
            Log.d("AddDishActivity", "New dish created — reloading meals")
            loadMeals()
        }
    }

    private fun setupRecyclerView() {
        adapter = MealAdapter { meal, isSelected ->
            if (isSelected) {
                selectedMeals.add(meal)
                totalCalories += meal.calories
                Log.d("AddDishActivity", "Meal selected - ID: ${meal.id}, Name: ${meal.name}, Total selected: ${selectedMeals.size}")
            } else {
                selectedMeals.remove(meal)
                totalCalories -= meal.calories
                Log.d("AddDishActivity", "Meal deselected - ID: ${meal.id}, Name: ${meal.name}")
            }

        }
        binding.rvMeals.layoutManager = LinearLayoutManager(this)
        binding.rvMeals.adapter = adapter
    }

    private fun loadMeals() {
        mealViewModel.loadMeals(userId)
        mealViewModel.meals.observe(this) { meals ->
            // ПРОВЕРКА: Логируем загруженные блюда
            Log.d("AddDishActivity", "Loading ${meals.size} meals from database")
            meals.forEachIndexed { index, meal ->
                Log.d("AddDishActivity", "Meal $index - ID: ${meal.id}, Name: ${meal.name}, Calories: ${meal.calories}")
            }

            adapter.updateMeals(meals)

            // Сбрасываем выбранные блюда при загрузке
            selectedMeals.clear()
            totalCalories = 0
        }
    }

    private fun getUserIdFromPrefs(): Long {
        val sharedPref = getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
        return sharedPref.getLong("current_user_id", -1L)
    }
}