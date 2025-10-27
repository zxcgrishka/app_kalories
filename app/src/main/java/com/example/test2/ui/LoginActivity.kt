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
import com.example.test2.databinding.ActivityLoginBinding
import com.example.test2.network.NetworkModule
import com.example.test2.ui.AuthViewModel
import com.example.test2.ui.AuthViewModelFactory

class LoginActivity : AppCompatActivity() {
    private lateinit var binding: ActivityLoginBinding
    private lateinit var repository: UserRepository
    private val viewModel: AuthViewModel by viewModels {
        AuthViewModelFactory(repository, this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            Log.d("LoginActivity", "onCreate started")  // Лог 1

            // Создай repository перед ViewModel
            repository = UserRepository(
                AppDatabase.getDatabase(this).userDao(),
                NetworkModule.provideApiService(),
                this
            )
            Log.d("LoginActivity", "Repository created")  // Лог 2

            binding = ActivityLoginBinding.inflate(layoutInflater)
            setContentView(binding.root)
            Log.d("LoginActivity", "Binding set")  // Лог 3

            // Авто-логин при запуске
            Log.d("LoginActivity", "Checking autoLogin...")  // Лог 4
            val isAuto = viewModel.autoLogin()
            Log.d("LoginActivity", "autoLogin result: $isAuto")  // Лог 5
            if (isAuto) {
                Log.d("LoginActivity", "Navigating to Main")  // Лог 6
                startActivity(Intent(this, MainActivity::class.java))
                finish()
                return
            }
            Log.d("LoginActivity", "No auto-login, showing screen")  // Лог 7

            // Наблюдение за результатом
            viewModel.authResult.observe(this) { result ->
                Log.d("LoginActivity", "authResult changed: $result")  // Лог 8
                if (result.isSuccess) {
                    Toast.makeText(this, result.getOrNull(), Toast.LENGTH_SHORT).show()
                    startActivity(Intent(this, MainActivity::class.java))
                    finish()
                } else {
                    val error = result.exceptionOrNull()?.message ?: "Unknown error"
                    Log.e("LoginActivity", "Login error: $error")  // Лог ошибки
                    Toast.makeText(this, error, Toast.LENGTH_SHORT).show()
                }
            }

            // Кнопка Login
            binding.btnLogin.setOnClickListener {
                Log.d("LoginActivity", "Login button clicked")  // Лог 9
                val username = binding.etUsername.text.toString()
                val password = binding.etPassword.text.toString()
                if (username.isNotBlank() && password.isNotBlank()) {
                    viewModel.login(username, password)
                    Log.d("LoginActivity", "Login called with $username")  // Лог 10
                } else {
                    Toast.makeText(this, "Fill all fields", Toast.LENGTH_SHORT).show()
                }
            }

            // Переход на регистрацию
            binding.tvRegister.setOnClickListener {
                Log.d("LoginActivity", "Register link clicked")  // Лог 11
                startActivity(Intent(this, RegisterActivity::class.java))
            }

            Log.d("LoginActivity", "onCreate finished")  // Лог 12
        } catch (e: Exception) {
            Log.e("LoginActivity", "onCreate error: ${e.message}", e)  // Полный лог краша
            Toast.makeText(this, "Error in LoginActivity: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}