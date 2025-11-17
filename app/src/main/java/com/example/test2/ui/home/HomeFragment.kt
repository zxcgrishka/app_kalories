package com.example.test2.ui.home  // Твой package

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.example.test2.AddDishActivity
import com.example.test2.ui.home.AddDish.CreateDish.AddProduct.AddProductsActivity
import com.example.test2.LoginActivity
import com.example.test2.MainActivity
import com.example.test2.databinding.FragmentHomeBinding
import com.example.test2.ui.AuthViewModel
import com.example.test2.ui.AuthViewModelFactory

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    // Shared ViewModel с Activity (scope: activity)
    private val viewModel: AuthViewModel by activityViewModels {
        // Factory из MainActivity (repository и context)
        AuthViewModelFactory((requireActivity() as MainActivity).repository, requireActivity())
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d("HomeFragment", "onViewCreated started")

        binding.btnAddDish.setOnClickListener {
            startActivity(Intent(requireContext(), AddDishActivity::class.java))
        }

        Log.d("HomeFragment", "onViewCreated finished")
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}