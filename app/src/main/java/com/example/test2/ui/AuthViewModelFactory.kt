package com.example.test2.ui  // Твоя package

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.test2.data.UserRepository
import com.example.test2.ui.AuthViewModel
import android.content.Context

class AuthViewModelFactory(
    private val repository: UserRepository,
    private val context: Context
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AuthViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return AuthViewModel(repository, context) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}