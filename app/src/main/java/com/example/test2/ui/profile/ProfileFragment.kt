package com.example.test2.ui.profile  // Твой package

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.example.test2.LoginActivity
import com.example.test2.MainActivity  // Импорт для cast к Activity
import com.example.test2.databinding.FragmentProfileBinding
import com.example.test2.ui.AuthViewModel
import com.example.test2.ui.AuthViewModelFactory

class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    // Shared ViewModel с Activity (с factory из MainActivity)
    private val viewModel: AuthViewModel by activityViewModels {
        // Factory с repository и context из MainActivity
        AuthViewModelFactory((requireActivity() as MainActivity).repository, requireActivity())
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root

    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        Log.d("ProfileFragment", "onViewCreated started")  // Дебаж

        // Загрузи username (если добавили метод в ViewModel)
        viewModel.loadCurrentUser()

        // Observe username и обнови TextView
        viewModel.username.observe(viewLifecycleOwner) { userName ->
            binding.tvUserName.text = "Username: $userName"
            Log.d("ProfileFragment", "Username set: $userName")
        }

        // Кнопка редактирования (твоя логика)
        binding.btnEditProfile.setOnClickListener {
            Log.d("ProfileFragment", "Edit profile clicked")
            // Здесь добавь диалог или Intent для редактирования
        }

        binding.btnLogout.setOnClickListener {
            Log.d("HomeFragment", "Logout clicked")
            viewModel.logout()
            Toast.makeText(requireContext(), "Logged out", Toast.LENGTH_SHORT).show()
            startActivity(Intent(requireContext(), LoginActivity::class.java))
            requireActivity().finish()  // Закрываем Activity
        }

        Log.d("ProfileFragment", "onViewCreated finished")
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}