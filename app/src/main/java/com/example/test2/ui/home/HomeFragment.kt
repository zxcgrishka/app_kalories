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
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.test2.AddDishActivity
import com.example.test2.LoginActivity
import com.example.test2.MainActivity
import com.example.test2.data.DailyMeal.DailyMeal
import com.example.test2.databinding.FragmentHomeBinding
import com.example.test2.ui.AuthViewModel
import com.example.test2.ui.AuthViewModelFactory
import com.example.test2.ui.DailyMealAdapter
import com.example.test2.ui.DailyMealViewModel
import com.example.test2.ui.DailyMealViewModelFactory
import kotlinx.coroutines.launch

class HomeFragment : Fragment() {
    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private val authViewModel: AuthViewModel by activityViewModels {
        AuthViewModelFactory((requireActivity() as MainActivity).repository, requireActivity())
    }

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

        binding.btnAddDish.setOnClickListener {
            Log.d("HomeFragment", "btnAddDish clicked")
            startActivityForResult(Intent(requireContext(), AddDishActivity::class.java), ADD_DISH_REQUEST_CODE)
        }
        
        setupDailyMealRecyclerView()
        loadDailyMeals()

        Log.d("HomeFragment", "onViewCreated finished")
    }

    private fun setupDailyMealRecyclerView() {
        dailyMealAdapter = DailyMealAdapter { dailyMeal ->
            Log.d("HomeFragment", "DailyMeal clicked: ${dailyMeal.totalCalories}")
        }
        binding.rvDailyMeals.layoutManager = LinearLayoutManager(requireContext())
        binding.rvDailyMeals.adapter = dailyMealAdapter
    }

    private fun loadDailyMeals() {
        dailyMealViewModel.loadDailyMeals(userId)
        dailyMealViewModel.dailyMeals.observe(viewLifecycleOwner) { dailyMeals ->
            dailyMealAdapter.updateDailyMeals(dailyMeals)
            Log.d("HomeFragment", "Loaded ${dailyMeals.size} daily meals")
        }
    }

    private fun getUserIdFromPrefs(): Long {
        val sharedPref = requireContext().getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
        return sharedPref.getLong("current_user_id", -1L)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == ADD_DISH_REQUEST_CODE && resultCode == RESULT_OK) {
            Log.d("HomeFragment", "New daily meal created — reloading")
            loadDailyMeals()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val ADD_DISH_REQUEST_CODE = 1002
    }
}