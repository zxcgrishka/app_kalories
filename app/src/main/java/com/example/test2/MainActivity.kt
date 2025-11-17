package com.example.test2

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
import com.example.test2.ui.AuthViewModel
import com.example.test2.ui.AuthViewModelFactory

class MainActivity : AppCompatActivity() {
    lateinit var binding: ActivityMainBinding
    lateinit var repository: UserRepository

    private val viewModel: AuthViewModel by viewModels {
        AuthViewModelFactory(repository, this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("MainActivity", "onCreate started")
        val database = AppDatabase.getDatabase(this)
        try {
            repository = UserRepository(
                database,
                NetworkModule.provideMyApiService(this),
                this
            )

            binding = ActivityMainBinding.inflate(layoutInflater)
            setContentView(binding.root)

            // Настройка навигации
            val navController: NavController? = findNavController(R.id.nav_host_fragment)
            Log.d("MainActivity", "NavController: $navController")

            if (navController != null) {
                binding.bottomNavigation.setupWithNavController(navController)

                binding.bottomNavigation.setOnItemSelectedListener { item ->
                    Log.d("MainActivity", "Clicked: ${item.itemId} - ${item.title}")
                    try {
                        val success = navController.navigate(item.itemId)
                        Log.d("MainActivity", "Navigate success: $success")
                    } catch (e: Exception) {
                        Log.e("MainActivity", "Navigate error: ${e.message}")
                    }
                    true
                }
            } else {
                Log.e("MainActivity", "NavController is null! Check XML nav_host_fragment")
            }

        } catch (e: Exception) {
            Log.e("MainActivity", "onCreate error: ${e.message}", e)
        }
    }

    // Если фрагментам нужен доступ к ViewModel — getter (опционально)
    fun getAuthViewModel(): AuthViewModel = viewModel
}