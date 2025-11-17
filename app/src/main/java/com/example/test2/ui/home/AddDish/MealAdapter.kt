package com.example.test2.ui.home.AddDish

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.test2.data.Meal
import com.example.test2.databinding.ItemMealBinding

class MealAdapter(private val onMealClicked: (Meal) -> Unit) : RecyclerView.Adapter<MealAdapter.ViewHolder>() {
    private var meals = listOf<Meal>()

    class ViewHolder(private val binding: ItemMealBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(meal: Meal, onClicked: (Meal) -> Unit) {
            binding.tvMealName.text = meal.name
            binding.tvMealCalories.text = "${meal.calories} ккал"
            binding.tvMealDate.text = meal.date.toString()  // Форматируй, напр. SimpleDateFormat
            binding.root.setOnClickListener { onClicked(meal) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemMealBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(meals[position], onMealClicked)
    }

    override fun getItemCount() = meals.size

    fun updateMeals(newMeals: List<Meal>) {
        meals = newMeals
        notifyDataSetChanged()
    }
}