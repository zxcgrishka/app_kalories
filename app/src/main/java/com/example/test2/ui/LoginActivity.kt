package com.example.test2

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.test2.data.AppDatabase
import com.example.test2.data.User.UserRepository
import com.example.test2.databinding.ActivityLoginBinding
import com.example.test2.network.NetworkModule
import com.example.test2.ui.AuthViewModel
import com.example.test2.ui.AuthViewModelFactory
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {
    private lateinit var binding: ActivityLoginBinding
    private lateinit var repository: UserRepository
    private lateinit var viewModel: AuthViewModel  // ← Теперь lateinit

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("LoginActivity", "onCreate started")

        // Инициализируем БД и репозиторий
        val database = AppDatabase.getDatabase(this)
        repository = UserRepository(
            database,
            NetworkModule.provideMyApiService(this),
            this
        )
        Log.d("LoginActivity", "Repository created")

        // Создаём ViewModel вручную ПОСЛЕ инициализации repository
        viewModel = AuthViewModelFactory(repository, this).create(AuthViewModel::class.java)

        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)
        Log.d("LoginActivity", "UI set")

        // Авто-логин
        if (viewModel.autoLogin()) {
            Log.d("LoginActivity", "Auto-login successful — going to MainActivity")
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }

        // Наблюдение за результатом логина
        viewModel.authResult.observe(this) { result ->
            Log.d("LoginActivity", "authResult: $result")
            if (result.isSuccess) {
                val username = binding.etUsername.text.toString().trim()

                saveUsername(username)

                // Сохраняем user_id после логина
                lifecycleScope.launch {
                    try {
                        val loginResponse = repository.api.login(
                            com.example.test2.network.LoginRequest(username = username, password = binding.etPassword.text.toString())
                        )
                        val userId = loginResponse.user_id.toLong()
                        saveCurrentUserId(userId)
                        Log.d("LoginActivity", "Saved current_user_id = $userId")
                    } catch (e: Exception) {
                        Log.e("LoginActivity", "Failed to save user_id: ${e.message}")
                    }

                    Toast.makeText(this@LoginActivity, "Вход успешен", Toast.LENGTH_SHORT).show()
                    startActivity(Intent(this@LoginActivity, MainActivity::class.java))
                    finish()
                }
            } else {
                Toast.makeText(this, result.exceptionOrNull()?.message ?: "Ошибка входа", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnLogin.setOnClickListener {
            val username = binding.etUsername.text.toString().trim()
            val password = binding.etPassword.text.toString()
            if (username.isNotEmpty() && password.isNotEmpty()) {
                viewModel.login(username, password)
            } else {
                Toast.makeText(this, "Заполните все поля", Toast.LENGTH_SHORT).show()
            }
        }

        binding.tvRegister.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }
    }

    private fun saveUsername(username: String) {
        val prefs = getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("current_username", username).apply()
        Log.d("LoginActivity", "Username saved: $username")
    }

    private fun saveCurrentUserId(userId: Long) {
        val prefs = getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
        prefs.edit().putLong("current_user_id", userId).apply()
        Log.d("LoginActivity", "Saved current_user_id = $userId")
    }
}