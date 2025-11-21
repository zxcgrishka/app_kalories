package com.example.test2.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.test2.data.DailyMeal.DailyMeal
import com.example.test2.databinding.ItemDailyMealBinding

class DailyMealAdapter(private val onClicked: (DailyMeal) -> Unit) : RecyclerView.Adapter<DailyMealAdapter.ViewHolder>() {
    private var dailyMeals = listOf<DailyMeal>()

    class ViewHolder(private val binding: ItemDailyMealBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(dailyMeal: DailyMeal, onClicked: (DailyMeal) -> Unit) {
            binding.tvDailyMealDate.text = dailyMeal.date.toString()
            binding.tvDailyMealCalories.text = "${dailyMeal.totalCalories} ккал"
            binding.root.setOnClickListener { onClicked(dailyMeal) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemDailyMealBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(dailyMeals[position], onClicked)
    }

    override fun getItemCount() = dailyMeals.size

    fun updateDailyMeals(newDailyMeals: List<DailyMeal>) {
        dailyMeals = newDailyMeals
        notifyDataSetChanged()
    }
}