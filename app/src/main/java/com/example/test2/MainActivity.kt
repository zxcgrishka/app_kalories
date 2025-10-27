package com.example.test2  // Твоя package

import android.content.Intent
import android.os.Bundle
import android.util.Log  // Для лога
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.example.test2.data.AppDatabase
import com.example.test2.data.UserRepository
import com.example.test2.databinding.ActivityMainBinding
import com.example.test2.network.NetworkModule
import com.example.test2.ui.AuthViewModel
import com.example.test2.ui.AuthViewModelFactory


class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var repository: UserRepository
    private val viewModel: AuthViewModel by viewModels {
        AuthViewModelFactory(repository, this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("LoginActivity", "onCreate started")  // Лог 1
        try {
            // Создай repository
            repository = UserRepository(
                AppDatabase.getDatabase(this).userDao(),
                NetworkModule.provideApiService(),
                this
            )
            Log.d("MainActivity", "Repository created")  // Лог 2

            binding = ActivityMainBinding.inflate(layoutInflater)
            setContentView(binding.root)
            Log.d("MainActivity", "Binding set")  // Лог 3

            // Кнопка Add Product
            binding.btnAddProduct.setOnClickListener {
                Log.d("MainActivity", "Add Product clicked")  // Лог 4
                startActivity(Intent(this, AddProductsActivity::class.java))
            }

            // Кнопка Logout
            binding.btnLogout.setOnClickListener {
                Log.d("MainActivity", "Logout clicked")  // Лог 5
                viewModel.logout()
                Toast.makeText(this, "Logged out", Toast.LENGTH_SHORT).show()
                startActivity(Intent(this, LoginActivity::class.java))
                finish()
            }

            Log.d("MainActivity", "onCreate finished")  // Лог 6
        } catch (e: Exception) {
            Log.e("MainActivity", "onCreate error: ${e.message}", e)  // Полный лог краша
            Toast.makeText(this, "Error in MainActivity: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

}