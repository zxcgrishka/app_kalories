package com.example.test2.ui  // Твоя package

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.test2.data.User.UserRepository
import kotlinx.coroutines.launch

class AuthViewModel(private val repository: UserRepository, private val context: Context) : ViewModel() {
    val authResult = MutableLiveData<Result<String>>()

    // LiveData для username
    private val _username = MutableLiveData<String>()
    val username: LiveData<String> = _username

    fun register(username: String, password: String) {
        viewModelScope.launch {
            authResult.value = repository.register(username, password)
        }
    }

    fun login(username: String, password: String) {
        viewModelScope.launch {
            authResult.value = repository.login(username, password)
        }
    }

    fun autoLogin(): Boolean {
        return repository.autoLogin()
    }

    fun logout() {
        viewModelScope.launch {
            repository.logout()
        }
    }

    // Метод для загрузки username из SharedPreferences
    fun loadCurrentUser() {
        val sharedPref = context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
        val savedUsername = sharedPref.getString("current_username", "Гость") ?: "Гость"
        _username.value = savedUsername
    }
}