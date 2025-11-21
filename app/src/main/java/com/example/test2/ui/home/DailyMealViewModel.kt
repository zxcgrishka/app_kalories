package com.example.test2.ui

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.test2.data.DailyMeal.DailyMeal
import com.example.test2.data.User.UserRepository
import kotlinx.coroutines.launch

class DailyMealViewModel(private val repository: UserRepository) : ViewModel() {
    val dailyMeals = MutableLiveData<List<DailyMeal>>()

    fun loadDailyMeals(userId: Long) {
        viewModelScope.launch {
            repository.getTodayDailyMealsByUser(userId).collect { list ->
                dailyMeals.value = list
            }
        }
    }

    fun addDailyMeal(dailyMeal: DailyMeal) {
        viewModelScope.launch {
            repository.insertDailyMeal(dailyMeal)
        }
    }
}