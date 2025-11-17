package com.example.test2.ui

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.test2.data.User.UserRepository
import com.example.test2.ui.home.AddDish.CreateDish.AddProduct.Product
import kotlinx.coroutines.launch

class ProductViewModel(private val repository: UserRepository) : ViewModel() {
    val products = MutableLiveData<List<Product>>()

    fun loadProducts() {
        if (repository == null) {
            Log.w("ProductViewModel", "Repository null — load empty list")
            products.value = emptyList()  // Fallback: пустой список, если нет DB
            return
        }
        viewModelScope.launch {
            repository.getAllProducts().collect { list ->
                products.value = list
            }
        }
    }
}