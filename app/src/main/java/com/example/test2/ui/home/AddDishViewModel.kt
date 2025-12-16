package com.example.test2.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.test2.data.DailyMeal.DailyMeal
import com.example.test2.data.Meal
import com.example.test2.data.User.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

class AddDishViewModel(private val repository: UserRepository) : ViewModel() {

    private val _meals = MutableStateFlow<List<Meal>>(emptyList())
    val meals: StateFlow<List<Meal>> = _meals.asStateFlow()

    // Создаем Flow для ID пользователя, чтобы на его изменения можно было реагировать
    private val userIdFlow = MutableStateFlow(-1L)

    init {
        viewModelScope.launch {
            // flatMapLatest будет автоматически переключаться на новый Flow,
            // как только изменится userIdFlow
            userIdFlow.flatMapLatest { userId ->
                if (userId != -1L) {
                    Log.d("AddDishViewModel", "Start collecting meals for userId: $userId")
                    // Теперь ViewModel будет ПОСТОЯННО слушать этот Flow
                    repository.getMealsByUser(userId)
                } else {
                    Log.d("AddDishViewModel", "No user, returning empty meal flow")
                    flowOf(emptyList()) // Возвращаем пустой поток, если ID пользователя нет
                }
            }.collect { mealList ->
                // Этот блок будет вызываться КАЖДЫЙ РАЗ, когда данные в таблице Meal меняются
                _meals.value = mealList
                Log.d("AddDishViewModel", "ViewModel meals updated with ${mealList.size} items.")
            }
        }
    }

    // Этот метод будет вызываться из Activity для установки ID пользователя
    fun setUserId(userId: Long) {
        Log.d("AddDishViewModel", "setUserId called with: $userId")
        userIdFlow.value = userId
    }

    // Функция для сохранения выбранного приема пищи остаётся без изменений
    fun saveDailyMeal(dailyMeal: DailyMeal) {
        viewModelScope.launch {
            repository.insertDailyMeal(dailyMeal)
            Log.d("AddDishViewModel", "DailyMeal saved.")
        }
    }
}

