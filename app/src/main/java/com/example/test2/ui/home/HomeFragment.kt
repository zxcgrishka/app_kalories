package com.example.test2.ui.home  // Твой package

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels  // Для shared ViewModel
import com.example.test2.AddProductsActivity
import com.example.test2.LoginActivity
import com.example.test2.MainActivity  // Импорт для cast
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
        Log.d("HomeFragment", "onViewCreated started")  // Новый лог для фрагмента

        // Кнопка Add Product
        binding.btnAddProduct.setOnClickListener {
            Log.d("HomeFragment", "Add Product clicked")  // Лог 4 (адаптировал)
            startActivity(Intent(requireContext(), AddProductsActivity::class.java))
        }

        // Кнопка Logout (используем shared ViewModel)
        binding.btnLogout.setOnClickListener {
            Log.d("HomeFragment", "Logout clicked")  // Лог 5 (адаптирован)
            viewModel.logout()
            Toast.makeText(requireContext(), "Logged out", Toast.LENGTH_SHORT).show()
            startActivity(Intent(requireContext(), LoginActivity::class.java))
            requireActivity().finish()  // Закрываем Activity
        }

        Log.d("HomeFragment", "onViewCreated finished")
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}