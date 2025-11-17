package com.example.test2

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.example.test2.data.AppDatabase
import com.example.test2.data.User.UserRepository
import com.example.test2.databinding.ActivityRegisterBinding
import com.example.test2.network.NetworkModule
import com.example.test2.ui.AuthViewModel
import com.example.test2.ui.AuthViewModelFactory
import android.util.Log

class RegisterActivity : AppCompatActivity() {
    private lateinit var binding: ActivityRegisterBinding
    private lateinit var repository: UserRepository
    private val viewModel: AuthViewModel by viewModels {
        AuthViewModelFactory(repository, this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("RegisterActivity", "onCreate started")
        val database = AppDatabase.getDatabase(this)
        repository = UserRepository(
            database,
            NetworkModule.provideMyApiService(this), // no context needed
            this
        )
        Log.d("RegisterActivity", "Repository created")

        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)
        Log.d("RegisterActivity", "Binding set")

        // Наблюдение за результатом
        viewModel.authResult.observe(this) { result ->
            Log.d("RegisterActivity", "authResult changed: $result")
            if (result.isSuccess) {
                val username = binding.etUsername.text.toString()
                saveUsername(username)
                Toast.makeText(this, result.getOrNull(), Toast.LENGTH_SHORT).show()
                startActivity(Intent(this, MainActivity::class.java))
                finish()
            } else {
                val error = result.exceptionOrNull()?.message ?: "Unknown error"
                Log.e("RegisterActivity", "Register error: $error")
                Toast.makeText(this, error, Toast.LENGTH_SHORT).show()
            }
        }

        // Кнопка Register
        binding.btnRegister.setOnClickListener {
            Log.d("RegisterActivity", "Register button clicked")
            val username = binding.etUsername.text.toString()
            val password = binding.etPassword.text.toString()
            if (username.isNotBlank() && password.isNotBlank()) {
                viewModel.register(username, password)
            } else {
                Toast.makeText(this, "Fill all fields", Toast.LENGTH_SHORT).show()
            }
        }

        // Переход на логин
        binding.tvLogin.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
    }
    private fun saveUsername(username: String) {
        val sharedPref = getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
        with(sharedPref.edit()) {
            putString("current_username", username)
            apply()
        }
        Log.d("LoginActivity", "Username saved: $username")
    }
}
