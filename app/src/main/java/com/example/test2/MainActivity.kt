package com.example.test2

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.NavController
import androidx.navigation.findNavController
import androidx.navigation.ui.setupWithNavController
import com.example.test2.data.AppDatabase
import com.example.test2.data.User.UserRepository
import com.example.test2.databinding.ActivityMainBinding
import com.example.test2.network.NetworkModule
import com.example.test2.utils.TokenManager
import com.example.test2.ui.AuthViewModel
import com.example.test2.ui.AuthViewModelFactory

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    lateinit var repository: UserRepository

    private val viewModel: AuthViewModel by viewModels {
        AuthViewModelFactory(repository, this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("MainActivity", "onCreate started")

        // Инициализируем БД и репозиторий (нужно для TokenManager и ViewModel)
        val database = AppDatabase.getDatabase(this)
        repository = UserRepository(
            database,
            NetworkModule.provideMyApiService(this),
            this
        )

        // Ключевая проверка: залогинен ли пользователь?
        if (!TokenManager.isLoggedIn(this)) {
            Log.d("MainActivity", "User not logged in — redirecting to LoginActivity")
            val intent = Intent(this, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
            return
        }

        Log.d("MainActivity", "User is logged in — setting up UI")

        // Если залогинен — настраиваем интерфейс
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        try {
            val navController: NavController? = findNavController(R.id.nav_host_fragment)
            Log.d("MainActivity", "NavController: $navController")

            if (navController != null) {
                binding.bottomNavigation.setupWithNavController(navController)

                binding.bottomNavigation.setOnItemSelectedListener { item ->
                    Log.d("MainActivity", "Bottom nav clicked: ${item.title}")
                    try {
                        navController.navigate(item.itemId)
                    } catch (e: Exception) {
                        Log.e("MainActivity", "Navigation error: ${e.message}", e)
                    }
                    true
                }
            } else {
                Log.e("MainActivity", "NavController is null! Check layout: nav_host_fragment id")
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error setting up UI: ${e.message}", e)
        }
    }

    // Опционально: если фрагментам нужен доступ к AuthViewModel
    fun getAuthViewModel(): AuthViewModel = viewModel
}