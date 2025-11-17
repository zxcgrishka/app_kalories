package com.example.test2

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.test2.data.AppDatabase
import com.example.test2.data.Meal
import com.example.test2.data.User.UserRepository
import com.example.test2.databinding.ActivityCreateDishBinding
import com.example.test2.network.NetworkModule
import com.example.test2.ui.ProductAdapter
import com.example.test2.ui.ProductViewModel
import com.example.test2.ui.ProductViewModelFactory
import com.example.test2.ui.home.AddDish.CreateDish.AddProduct.AddProductsActivity
import com.example.test2.ui.home.AddDish.CreateDish.AddProduct.Product
import kotlinx.coroutines.launch
import java.util.Date

class CreateDishActivity : AppCompatActivity() {
    private lateinit var binding: ActivityCreateDishBinding
    private lateinit var productViewModel: ProductViewModel
    private lateinit var adapter: ProductAdapter

    private var selectedProducts = mutableListOf<Product>()
    private var totalCalories = 0
    private var userId = -1L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("CreateDishActivity", "onCreate started")

        binding = ActivityCreateDishBinding.inflate(layoutInflater)
        setContentView(binding.root)
        Log.d("CreateDishActivity", "Binding set — UI ready")

        userId = getUserIdFromPrefs()

        // Создай локальный Repository
        val database = AppDatabase.getDatabase(this)
        val localRepository = UserRepository(
            database,
            NetworkModule.provideMyApiService(this),
            this
        )

        // ViewModel для продуктов (с локальным Repository)
        productViewModel = viewModels<ProductViewModel> {
            ProductViewModelFactory(localRepository)
        }.value

        setupRecyclerView()
        loadProducts()

        // Кнопка "Сохранить блюдо" (с lifecycleScope.launch для suspend)
        binding.btnSaveDish.setOnClickListener {
            val dishName = binding.etDishName.text.toString().trim()
            if (dishName.isNotBlank() && selectedProducts.isNotEmpty()) {
                val productsIds = selectedProducts.map { it.id }.joinToString(",")
                val meal = Meal(
                    userId = userId,
                    name = dishName,
                    calories = totalCalories,
                    date = Date(),
                    productsIds = productsIds
                )
                // Suspend вызов в корутине (lifecycleScope.launch)
                lifecycleScope.launch {
                    localRepository.insertMeal(meal)
                    Log.d("CreateDishActivity", "Meal saved: $dishName")
                    setResult(Activity.RESULT_OK)
                    finish()  // Вернись в Home
                }
            } else {
                Log.w("CreateDishActivity", "Validation failed")
            }
        }

        Log.d("CreateDishActivity", "onCreate finished")

        binding.btnAddProduct.setOnClickListener {
            Log.d("CreateDishActivity", "btnAddProduct clicked")
            startActivity(Intent(this, AddProductsActivity::class.java))
        }
    }

    private fun setupRecyclerView() {
        adapter = ProductAdapter { product, isSelected ->
            if (isSelected) {
                selectedProducts.add(product)
                totalCalories += product.ProductCalories
            } else {
                selectedProducts.remove(product)
                totalCalories -= product.ProductCalories
            }
            binding.tvTotalCalories.text = "Общие калории: $totalCalories"
            Log.d("CreateDishActivity", "Selected: ${product.ProductName}, total: $totalCalories")
        }
        binding.rvProducts.layoutManager = LinearLayoutManager(this)
        binding.rvProducts.adapter = adapter
    }

    private fun loadProducts() {
        productViewModel.loadProducts()
        productViewModel.products.observe(this) { products ->
            adapter.updateProducts(products)
            Log.d("CreateDishActivity", "Loaded ${products.size} products")
        }
    }

    private fun getUserIdFromPrefs(): Long {
        val sharedPref = getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
        return sharedPref.getLong("current_user_id", -1L)
    }
}