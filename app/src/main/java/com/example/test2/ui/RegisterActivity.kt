package com.example.test2

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.test2.data.AppDatabase
import com.example.test2.data.User.UserRepository
import com.example.test2.databinding.ActivityRegisterBinding
import com.example.test2.network.NetworkModule
import com.example.test2.ui.AuthViewModel
import com.example.test2.ui.AuthViewModelFactory
import kotlinx.coroutines.launch

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
            NetworkModule.provideMyApiService(this),
            this
        )
        Log.d("RegisterActivity", "Repository created")

        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)
        Log.d("RegisterActivity", "Binding set")

        // Наблюдение за результатом регистрации
        viewModel.authResult.observe(this) { result ->
            Log.d("RegisterActivity", "authResult changed: $result")
            if (result.isSuccess) {
                val username = binding.etUsername.text.toString().trim()
                val password = binding.etPassword.text.toString()

                saveUsername(username)

                // Ключевой фикс: получаем user_id после регистрации
                lifecycleScope.launch {
                    try {
                        // Делаем login, чтобы получить user_id из ответа (как в repository.register)
                        val loginResponse = repository.api.login(
                            com.example.test2.network.LoginRequest(username = username, password = password)
                        )
                        val userId = loginResponse.user_id.toLong()

                        // Сохраняем user_id
                        val prefs = getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
                        prefs.edit().putLong("current_user_id", userId).apply()
                        Log.d("RegisterActivity", "Saved current_user_id = $userId after registration")

                        Toast.makeText(this@RegisterActivity, "Регистрация успешна", Toast.LENGTH_SHORT).show()
                        startActivity(Intent(this@RegisterActivity, MainActivity::class.java))
                        finish()
                    } catch (e: Exception) {
                        Log.e("RegisterActivity", "Failed to get user_id after register: ${e.message}")
                        Toast.makeText(this@RegisterActivity, "Регистрация прошла, но ошибка входа", Toast.LENGTH_SHORT).show()
                        // Всё равно переходим — пользователь уже создан
                        startActivity(Intent(this@RegisterActivity, MainActivity::class.java))
                        finish()
                    }
                }
            } else {
                val error = result.exceptionOrNull()?.message ?: "Неизвестная ошибка"
                Log.e("RegisterActivity", "Register error: $error")
                Toast.makeText(this, error, Toast.LENGTH_SHORT).show()
            }
        }

        // Кнопка регистрации
        binding.btnRegister.setOnClickListener {
            Log.d("RegisterActivity", "Register button clicked")
            val username = binding.etUsername.text.toString().trim()
            val password = binding.etPassword.text.toString()
            if (username.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Заполните все поля", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            viewModel.register(username, password)
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
        Log.d("RegisterActivity", "Username saved: $username")
    }
}