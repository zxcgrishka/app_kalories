package com.example.test2.ui  // Твоя package

import android.content.Context
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.test2.data.UserRepository
import kotlinx.coroutines.launch

class AuthViewModel(private val repository: UserRepository, private val context: Context) : ViewModel() {
    val authResult = MutableLiveData<Result<String>>()

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
}