package com.example.test2.ui.home


import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.test2.data.Meal
import com.example.test2.data.User.UserRepository
import kotlinx.coroutines.launch

class MealViewModel(private val repository: UserRepository) : ViewModel() {
    val meals = MutableLiveData<List<Meal>>()

    fun loadMeals(userId: Long) {
        viewModelScope.launch {
            repository.getMealsByUser(userId).collect { list ->
                meals.value = list
            }
        }
    }

    fun addMeal(meal: Meal) {
        viewModelScope.launch {
            repository.insertMeal(meal)
        }
    }
}