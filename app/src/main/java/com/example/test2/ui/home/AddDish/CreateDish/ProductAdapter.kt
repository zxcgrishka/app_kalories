package com.example.test2.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.test2.databinding.ItemProductBinding
import com.example.test2.ui.home.AddDish.CreateDish.AddProduct.Product

class ProductAdapter(private val onSelected: (Product, Boolean) -> Unit) : RecyclerView.Adapter<ProductAdapter.ViewHolder>() {
    private var products = listOf<Product>()

    class ViewHolder(private val binding: ItemProductBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(product: Product, onSelected: (Product, Boolean) -> Unit) {
            binding.tvProductName.text = product.ProductName
            binding.tvProductCalories.text = "${product.ProductCalories} ккал"
            binding.cbProduct.isChecked = false  // Начально не выбран
            binding.cbProduct.setOnCheckedChangeListener { _, isChecked ->
                onSelected(product, isChecked)  // Callback для расчёта калорий
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemProductBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(products[position], onSelected)
    }

    override fun getItemCount() = products.size

    fun updateProducts(newProducts: List<Product>) {
        products = newProducts
        notifyDataSetChanged()  // Обнови список
    }
}