package com.example.test2

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.test2.data.AppDatabase
import com.example.test2.data.Meal
import com.example.test2.data.User.UserRepository
import com.example.test2.databinding.ActivityAddDishBinding
import com.example.test2.network.NetworkModule
import com.example.test2.ui.MealViewModelFactory
import com.example.test2.ui.home.AddDish.MealAdapter
import com.example.test2.ui.home.MealViewModel

class AddDishActivity : AppCompatActivity() {
    private lateinit var binding: ActivityAddDishBinding
    private lateinit var localRepository: UserRepository
    private lateinit var mealViewModel: MealViewModel
    private lateinit var adapter: MealAdapter

    private var userId = -1L

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

        // ViewModel для Meal
        mealViewModel = viewModels<MealViewModel> {
            MealViewModelFactory(localRepository)
        }.value

        setupRecyclerView()
        loadMeals()

        // Кнопка "Создать новое блюдо"
        binding.btnCreateDish.setOnClickListener {
            Log.d("AddDishActivity", "btnCreateDish clicked")
            val intent = Intent(this, CreateDishActivity::class.java)
            intent.putExtra("userId", userId)  // Передай userId
            startActivityForResult(intent, CREATE_DISH_REQUEST_CODE)  // НОВОЕ: ForResult для обновления
        }

        Log.d("AddDishActivity", "onCreate finished")
    }

    // НОВОЕ: Обработка возврата из CreateDish
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == CREATE_DISH_REQUEST_CODE && resultCode == RESULT_OK) {
            Log.d("AddDishActivity", "Dish created — reloading list")
            loadMeals()  // Перезагрузи список
        }
    }

    private fun setupRecyclerView() {
        adapter = MealAdapter { meal ->  // Callback для клика на блюдо (напр. редактирование)
            Log.d("AddDishActivity", "Meal clicked: ${meal.name}")
            // Твоя логика (Intent на детали блюда)
        }
        binding.rvMeals.layoutManager = LinearLayoutManager(this)  // rvMeals — id в XML
        binding.rvMeals.adapter = adapter
    }

    private fun loadMeals() {
        mealViewModel.loadMeals(userId)
        mealViewModel.meals.observe(this) { meals ->
            adapter.updateMeals(meals)  // Adapter для Meal (создай ниже)
            Log.d("AddDishActivity", "Loaded ${meals.size} meals")
        }
    }

    private fun getUserIdFromPrefs(): Long {
        val sharedPref = getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
        return sharedPref.getLong("current_user_id", -1L)
    }

    companion object {
        const val CREATE_DISH_REQUEST_CODE = 1001  // Код для onActivityResult
    }
}