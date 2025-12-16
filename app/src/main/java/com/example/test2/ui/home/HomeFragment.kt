package com.example.test2.ui.home

import android.app.Activity.RESULT_OK
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.test2.AddDishActivity
import com.example.test2.MainActivity
import com.example.test2.databinding.FragmentHomeBinding
import com.example.test2.ui.DailyMealAdapter
import com.example.test2.ui.DailyMealViewModel
import com.example.test2.ui.DailyMealViewModelFactory

class HomeFragment : Fragment() {
    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private val dailyMealViewModel: DailyMealViewModel by activityViewModels {
        DailyMealViewModelFactory((requireActivity() as MainActivity).repository)
    }

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

        setupDailyMealRecyclerView()
        subscribeToDailyMeals()  // Подписка один раз
        loadDailyMeals()         // Первая загрузка

        binding.btnAddDish.setOnClickListener {
            val intent = Intent(requireContext(), AddDishActivity::class.java)
            startActivityForResult(intent, ADD_DISH_REQUEST_CODE)
        }

        Log.d("HomeFragment", "onViewCreated finished")
    }

    private fun setupDailyMealRecyclerView() {
        dailyMealAdapter = DailyMealAdapter { dailyMeal ->
            Log.d("HomeFragment", "DailyMeal clicked: ${dailyMeal.totalCalories}")
        }
        binding.rvDailyMeals.layoutManager = LinearLayoutManager(requireContext())
        binding.rvDailyMeals.adapter = dailyMealAdapter
    }

    private fun subscribeToDailyMeals() {
        dailyMealViewModel.dailyMeals.observe(viewLifecycleOwner) { dailyMeals ->
            Log.d("HomeFragment", "UI updated with ${dailyMeals.size} daily meals")
            dailyMeals.forEach {
                Log.d("HomeFragment", "DailyMeal: ${it.totalCalories} kcal, meals: ${it.meal_ids}")
            }
            dailyMealAdapter.updateDailyMeals(dailyMeals)
        }
    }

    private fun loadDailyMeals() {
        Log.d("HomeFragment", "loadDailyMeals called for userId = $userId")
        dailyMealViewModel.loadDailyMeals(userId)
    }

    // Ключевой фикс: обновляем после возврата на экран (после логина и sync)
    override fun onResume() {
        super.onResume()
        Log.d("HomeFragment", "onResume — reloading daily meals")
        loadDailyMeals()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == ADD_DISH_REQUEST_CODE && resultCode == RESULT_OK) {
            Log.d("HomeFragment", "Returned from AddDish — reloading")
            loadDailyMeals()
        }
    }

    private fun getUserIdFromPrefs(): Long {
        val sharedPref = requireContext().getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
        return sharedPref.getLong("current_user_id", -1L)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val ADD_DISH_REQUEST_CODE = 1002
    }
}

