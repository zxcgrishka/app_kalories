package com.example.test2.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.test2.data.User.UserRepository

class DailyMealViewModelFactory(private val repository: UserRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(DailyMealViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return DailyMealViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}