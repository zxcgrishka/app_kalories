package com.example.test2.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.test2.databinding.ItemProductBinding
import com.example.test2.ui.home.AddDish.CreateDish.AddProduct.Product


class ProductAdapter(
    private val onSelected: (Product, Boolean, Int) -> Unit,
    private val onWeightChanged: (Product, Int) -> Unit
) : RecyclerView.Adapter<ProductAdapter.ViewHolder>() {

    private var products = listOf<Product>()
    private val selectedWeights = mutableMapOf<Int, Int>() // productId to weight
    private val selectedProducts = mutableSetOf<Int>() // productId of selected products

    class ViewHolder(private val binding: ItemProductBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(
            product: Product,
            onSelected: (Product, Boolean, Int) -> Unit,
            onWeightChanged: (Product, Int) -> Unit,
            currentWeight: Int,
            isInitiallySelected: Boolean = false
        ) {
            binding.tvProductName.text = product.ProductName
            binding.tvProductCalories.text = "${product.ProductCalories} ккал/100г"

            // Показываем БЖУ продукта
            val nutritionText = "Б:${product.ProductProteins}г Ж:${product.ProductFats}г У:${product.ProductCarbohydrates}г"
            binding.tvCalculatedValues.text = nutritionText

            // Устанавливаем начальное состояние
            binding.cbProduct.isChecked = isInitiallySelected

            // Показываем/скрываем поля веса в зависимости от начального состояния
            if (isInitiallySelected) {
                binding.weightLayout.visibility = View.VISIBLE
                binding.btnDecreaseWeight.visibility = View.VISIBLE
                binding.btnIncreaseWeight.visibility = View.VISIBLE
                binding.tvCalculatedValues.visibility = View.VISIBLE
            } else {
                binding.weightLayout.visibility = View.GONE
                binding.btnDecreaseWeight.visibility = View.GONE
                binding.btnIncreaseWeight.visibility = View.GONE
                binding.tvCalculatedValues.visibility = View.GONE
            }

            // Устанавливаем вес
            binding.etProductWeight.setText(currentWeight.toString())

            // При изменении переключателя
            binding.cbProduct.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    // Показываем поля веса
                    binding.weightLayout.visibility = View.VISIBLE
                    binding.btnDecreaseWeight.visibility = View.VISIBLE
                    binding.btnIncreaseWeight.visibility = View.VISIBLE
                    binding.tvCalculatedValues.visibility = View.VISIBLE

                    val weight = binding.etProductWeight.text.toString().toIntOrNull() ?: 100
                    onSelected(product, true, weight)
                } else {
                    // Скрываем поля веса
                    binding.weightLayout.visibility = View.GONE
                    binding.btnDecreaseWeight.visibility = View.GONE
                    binding.btnIncreaseWeight.visibility = View.GONE
                    binding.tvCalculatedValues.visibility = View.GONE
                    onSelected(product, false, 0)
                }
            }

            // При изменении веса
            binding.etProductWeight.setOnFocusChangeListener { _, hasFocus ->
                if (!hasFocus) {
                    val weight = binding.etProductWeight.text.toString().toIntOrNull() ?: 100
                    if (weight > 0) {
                        binding.etProductWeight.setText(weight.toString())
                        onWeightChanged(product, weight)
                        updateCalculatedValues(product, weight)
                    }
                }
            }

            // Кнопка увеличения веса
            binding.btnIncreaseWeight.setOnClickListener {
                val current = binding.etProductWeight.text.toString().toIntOrNull() ?: 100
                val newWeight = current + 10
                binding.etProductWeight.setText(newWeight.toString())
                onWeightChanged(product, newWeight)
                updateCalculatedValues(product, newWeight)
            }

            // Кнопка уменьшения веса
            binding.btnDecreaseWeight.setOnClickListener {
                val current = binding.etProductWeight.text.toString().toIntOrNull() ?: 100
                val newWeight = maxOf(1, current - 10)
                binding.etProductWeight.setText(newWeight.toString())
                onWeightChanged(product, newWeight)
                updateCalculatedValues(product, newWeight)
            }

            // Инициализация расчетных значений
            updateCalculatedValues(product, currentWeight)
        }

        private fun updateCalculatedValues(product: Product, weight: Int) {
            val multiplier = weight / 100f
            val calories = (product.ProductCalories * multiplier).toInt()
            val proteins = (product.ProductProteins * multiplier).toInt()
            val fats = (product.ProductFats * multiplier).toInt()
            val carbs = (product.ProductCarbohydrates * multiplier).toInt()

            binding.tvCalculatedValues.text = "Вес: ${weight}г → ${calories} ккал (Б:${proteins}г Ж:${fats}г У:${carbs}г)"
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemProductBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val product = products[position]
        val weight = selectedWeights[product.id ?: 0] ?: 100
        val isSelected = product.id?.let { selectedProducts.contains(it) } ?: false
        holder.bind(product, onSelected, onWeightChanged, weight, isSelected)
    }

    override fun getItemCount() = products.size

    fun updateProducts(newProducts: List<Product>) {
        products = newProducts
        notifyDataSetChanged()
    }

    fun updateWeight(productId: Int, weight: Int) {
        selectedWeights[productId] = weight
        notifyItemChanged(products.indexOfFirst { it.id == productId })
    }

    // Новый метод для автоматического выбора продукта
    fun setProductSelected(productId: Int, weight: Int = 100) {
        productId?.let {
            selectedProducts.add(it)
            selectedWeights[it] = weight

            // Находим позицию и обновляем
            val position = products.indexOfFirst { product -> product.id == productId }
            if (position >= 0) {
                notifyItemChanged(position)
            }
        }
    }

    // Метод для снятия выбора
    fun setProductUnselected(productId: Int) {
        productId?.let {
            selectedProducts.remove(it)
            selectedWeights.remove(it)

            // Находим позицию и обновляем
            val position = products.indexOfFirst { product -> product.id == productId }
            if (position >= 0) {
                notifyItemChanged(position)
            }
        }
    }

    // Метод для проверки, выбран ли продукт
    fun isProductSelected(productId: Int): Boolean {
        return productId?.let { selectedProducts.contains(it) } ?: false
    }

    // Метод для получения выбранных продуктов
    fun getSelectedProducts(): List<Pair<Product, Int>> {
        return selectedProducts.mapNotNull { productId ->
            products.find { it.id == productId }?.let { product ->
                val weight = selectedWeights[productId] ?: 100
                Pair(product, weight)
            }
        }
    }
}