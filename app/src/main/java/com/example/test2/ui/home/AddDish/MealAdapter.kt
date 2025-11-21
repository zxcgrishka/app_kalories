package com.example.test2.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.test2.data.Meal
import com.example.test2.databinding.ItemMealBinding

class MealAdapter(private val onSelected: (Meal, Boolean) -> Unit) : RecyclerView.Adapter<MealAdapter.ViewHolder>() {
    private var meals = listOf<Meal>()

    class ViewHolder(private val binding: ItemMealBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(meal: Meal, onSelected: (Meal, Boolean) -> Unit) {
            binding.tvMealName.text = meal.name
            binding.tvMealCalories.text = "${meal.calories} ккал"
            binding.cbMeal.isChecked = false
            binding.cbMeal.setOnCheckedChangeListener { _, isChecked ->
                onSelected(meal, isChecked)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemMealBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(meals[position], onSelected)
    }

    override fun getItemCount() = meals.size

    fun updateMeals(newMeals: List<Meal>) {
        meals = newMeals
        notifyDataSetChanged()
    }
}