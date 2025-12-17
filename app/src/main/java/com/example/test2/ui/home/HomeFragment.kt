package com.example.test2.ui.home

import android.app.Activity.RESULT_OK
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.test2.AddDishActivity
import com.example.test2.MainActivity
import com.example.test2.data.AppDatabase
import com.example.test2.data.User.UserRepository
import com.example.test2.databinding.FragmentHomeBinding
import com.example.test2.ui.DailyMealAdapter
import com.example.test2.network.NetworkModule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class HomeFragment : Fragment() {
    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private lateinit var repository: UserRepository
    private lateinit var dailyMealAdapter: DailyMealAdapter

    private var userId = -1L

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d("HomeFragment", "onViewCreated started")

        userId = getUserIdFromPrefs()

        if (userId == -1L) {
            Log.e("HomeFragment", "User ID not found")
            Toast.makeText(requireContext(), "Ошибка авторизации", Toast.LENGTH_SHORT).show()
            return
        }

        val database = AppDatabase.getDatabase(requireContext())
        repository = UserRepository(
            database,
            NetworkModule.provideMyApiService(requireContext()),
            requireContext()
        )

        setupRecyclerView()
        loadDailyMealsFromDB()  // Первая загрузка

        binding.btnAddDish.setOnClickListener {
            val intent = Intent(requireContext(), AddDishActivity::class.java)
            startActivityForResult(intent, ADD_DISH_REQUEST_CODE)
        }

        Log.d("HomeFragment", "onViewCreated finished")
    }

    private fun setupRecyclerView() {
        dailyMealAdapter = DailyMealAdapter { dailyMeal ->
            Log.d("HomeFragment", "DailyMeal clicked: ${dailyMeal.totalCalories} kcal")
            // Здесь можно открыть детали
        }
        binding.rvDailyMeals.layoutManager = LinearLayoutManager(requireContext())
        binding.rvDailyMeals.adapter = dailyMealAdapter
    }

    private fun loadDailyMealsFromDB() {
        viewLifecycleOwner.lifecycleScope.launch {
            val dailyMeals = repository.getTodayDailyMealsByUser(userId).first()
            Log.d("HomeFragment", "Loaded ${dailyMeals.size} daily meals from DB")
            dailyMeals.forEach {
                Log.d("HomeFragment", "DailyMeal: ${it.totalCalories} kcal, meals: ${it.meal_ids}")
            }
            dailyMealAdapter.updateDailyMeals(dailyMeals)
        }
    }

    override fun onResume() {
        super.onResume()
        Log.d("HomeFragment", "onResume — reloading daily meals")
        loadDailyMealsFromDB()  // Гарантированное обновление после sync и возврата
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == ADD_DISH_REQUEST_CODE && resultCode == RESULT_OK) {
            Log.d("HomeFragment", "Returned from AddDish — reloading via onResume")
            // onResume сработает автоматически
        }
    }

    private fun getUserIdFromPrefs(): Long {
        val sharedPref = requireContext().getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
        val id = sharedPref.getLong("current_user_id", -1L)
        Log.d("HomeFragment", "getUserIdFromPrefs = $id")
        return id
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val ADD_DISH_REQUEST_CODE = 1002
    }
}
