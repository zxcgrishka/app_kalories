package com.example.test2.ui

import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import androidx.recyclerview.widget.RecyclerView
import com.example.test2.databinding.ItemProductBinding
import com.example.test2.ui.home.AddDish.CreateDish.AddProduct.Product
import android.util.Log

class ProductAdapter(
    private val onSelected: (Product, Boolean, Int) -> Unit,
    private val onWeightChanged: (Product, Int) -> Unit
) : RecyclerView.Adapter<ProductAdapter.ViewHolder>() {

    private var products = listOf<Product>()
    private val selectedWeights = mutableMapOf<Int, Int>() // productId to weight
    private val selectedProducts = mutableSetOf<Int>() // productId of selected products

    inner class ViewHolder(private val binding: ItemProductBinding) : RecyclerView.ViewHolder(binding.root) {
        private var currentProduct: Product? = null
        private var currentWeight: Int = 100
        private var isTextChangingByCode = false

        fun bind(
            product: Product,
            onSelected: (Product, Boolean, Int) -> Unit,
            onWeightChanged: (Product, Int) -> Unit,
            initialWeight: Int,
            isInitiallySelected: Boolean = false
        ) {
            currentProduct = product
            currentWeight = initialWeight

            Log.d("ProductAdapter", "Binding product: ${product.ProductName}, selected: $isInitiallySelected")

            // Устанавливаем основные данные
            binding.tvProductName.text = product.ProductName
            binding.tvProductCalories.text = "${product.ProductCalories} ккал/100г"

            // Показываем БЖУ продукта
            val nutritionText = "Б:${product.ProductProteins}г Ж:${product.ProductFats}г У:${product.ProductCarbohydrates}г"
            binding.tvCalculatedValues.text = nutritionText

            // Устанавливаем начальное состояние CheckBox
            binding.cbProduct.isChecked = isInitiallySelected

            // Устанавливаем начальный вес
            isTextChangingByCode = true
            binding.etProductWeight.setText(initialWeight.toString())
            isTextChangingByCode = false

            // Управляем видимостью кнопок управления весом
            updateWeightControlsVisibility(isInitiallySelected)

            // Отладочная проверка видимости
            binding.weightContainer.post {
                Log.d("ProductAdapter", "Container visibility: ${binding.weightContainer.visibility}")
                Log.d("ProductAdapter", "Minus button: width=${binding.btnMinus.width}, height=${binding.btnMinus.height}")
                Log.d("ProductAdapter", "Plus button: width=${binding.btnPlus.width}, height=${binding.btnPlus.height}")
            }

            // Удаляем старые слушатели
            binding.etProductWeight.removeTextChangedListener(binding.etProductWeight.tag as? TextWatcher)
            binding.etProductWeight.setOnEditorActionListener(null)
            binding.etProductWeight.setOnFocusChangeListener(null)
            binding.cbProduct.setOnCheckedChangeListener(null)
            binding.btnMinus.setOnClickListener(null)
            binding.btnPlus.setOnClickListener(null)

            // Создаем и устанавливаем новый TextWatcher
            val textWatcher = object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

                override fun afterTextChanged(s: Editable?) {
                    // Игнорируем изменения, вызванные программно (кнопками +/-)
                    if (isTextChangingByCode) return

                    val newWeight = s.toString().toIntOrNull() ?: 100
                    if (newWeight in 1..10000 && newWeight != currentWeight) {
                        currentWeight = newWeight
                        updateCalculatedValues(product, newWeight)

                        // Только если продукт выбран, вызываем колбэки
                        if (binding.cbProduct.isChecked) {
                            onWeightChanged(product, newWeight)
                        }
                    }
                }
            }

            binding.etProductWeight.addTextChangedListener(textWatcher)
            binding.etProductWeight.tag = textWatcher // Сохраняем ссылку для удаления

            // Обработка нажатия "галочки" на клавиатуре
            binding.etProductWeight.setOnEditorActionListener { v, actionId, event ->
                if (actionId == EditorInfo.IME_ACTION_DONE ||
                    actionId == EditorInfo.IME_ACTION_NEXT ||
                    (event != null && event.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)) {

                    // Получаем текст из EditText
                    val editText = v as EditText
                    val weight = editText.text.toString().toIntOrNull() ?: 100

                    if (weight in 1..10000 && weight != currentWeight) {
                        isTextChangingByCode = true
                        currentWeight = weight
                        binding.etProductWeight.setText(weight.toString())
                        updateCalculatedValues(product, weight)

                        if (binding.cbProduct.isChecked) {
                            onWeightChanged(product, weight)
                        }
                        isTextChangingByCode = false
                    }

                    // Снимаем фокус с поля
                    v.clearFocus()

                    // Скрываем клавиатуру
                    hideKeyboard(v)

                    // Возвращаем true, чтобы не показывать клавиатуру снова
                    return@setOnEditorActionListener true
                }
                false
            }

            // При изменении переключателя
            binding.cbProduct.setOnCheckedChangeListener { _, isChecked ->
                Log.d("ProductAdapter", "CheckBox changed: $isChecked for ${product.ProductName}")
                updateWeightControlsVisibility(isChecked)

                if (isChecked) {
                    val weight = binding.etProductWeight.text.toString().toIntOrNull() ?: 100
                    currentWeight = weight
                    updateCalculatedValues(product, weight)
                    onSelected(product, true, weight)
                } else {
                    onSelected(product, false, 0)
                }
            }

            // При потере фокуса (обрабатываем только потерю фокуса, не при нажатии галочки)
            binding.etProductWeight.setOnFocusChangeListener { v, hasFocus ->
                if (!hasFocus) {
                    val editText = v as EditText
                    val weight = editText.text.toString().toIntOrNull() ?: 100
                    if (weight in 1..10000 && weight != currentWeight) {
                        isTextChangingByCode = true
                        currentWeight = weight
                        binding.etProductWeight.setText(weight.toString())
                        updateCalculatedValues(product, weight)

                        if (binding.cbProduct.isChecked) {
                            onWeightChanged(product, weight)
                        }
                        isTextChangingByCode = false
                    }
                }
            }

            // Кнопка "минус" - уменьшить вес
            binding.btnMinus.setOnClickListener {
                Log.d("ProductAdapter", "Minus button clicked for ${product.ProductName}")
                isTextChangingByCode = true
                var newWeight = currentWeight - 10
                newWeight = newWeight.coerceAtLeast(1) // Не меньше 1 грамма

                binding.etProductWeight.setText(newWeight.toString())
                currentWeight = newWeight
                updateCalculatedValues(product, newWeight)

                if (binding.cbProduct.isChecked) {
                    onWeightChanged(product, newWeight)
                }
                isTextChangingByCode = false
            }

            // Кнопка "плюс" - увеличить вес
            binding.btnPlus.setOnClickListener {
                Log.d("ProductAdapter", "Plus button clicked for ${product.ProductName}")
                isTextChangingByCode = true
                var newWeight = currentWeight + 10
                newWeight = newWeight.coerceAtMost(10000) // Не больше 10000 грамм

                binding.etProductWeight.setText(newWeight.toString())
                currentWeight = newWeight
                updateCalculatedValues(product, newWeight)

                if (binding.cbProduct.isChecked) {
                    onWeightChanged(product, newWeight)
                }
                isTextChangingByCode = false
            }

            // Инициализация расчетных значений
            updateCalculatedValues(product, initialWeight)
        }

        private fun updateWeightControlsVisibility(isVisible: Boolean) {
            Log.d("ProductAdapter", "updateWeightControlsVisibility: $isVisible")

            if (isVisible) {
                binding.weightContainer.visibility = View.VISIBLE
                binding.tvCalculatedValues.visibility = View.VISIBLE
            } else {
                binding.weightContainer.visibility = View.GONE
                binding.tvCalculatedValues.visibility = View.GONE
            }
        }

        private fun updateCalculatedValues(product: Product, weight: Int) {
            val multiplier = weight / 100f
            val calories = (product.ProductCalories * multiplier).toInt()
            val proteins = (product.ProductProteins * multiplier).toInt()
            val fats = (product.ProductFats * multiplier).toInt()
            val carbs = (product.ProductCarbohydrates * multiplier).toInt()

            binding.tvCalculatedValues.text = "Вес: ${weight}г → ${calories} ккал (Б:${proteins}г Ж:${fats}г У:${carbs}г)"
        }

        private fun hideKeyboard(view: View) {
            val imm = view.context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(view.windowToken, 0)
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

    override fun onViewRecycled(holder: ViewHolder) {
        super.onViewRecycled(holder)
        // Очищаем слушатели при переиспользовании ViewHolder
        holder.itemView.findViewById<EditText>(com.example.test2.R.id.etProductWeight)?.let { editText ->
            (editText.tag as? TextWatcher)?.let {
                editText.removeTextChangedListener(it)
            }
            editText.tag = null
        }
    }

    fun updateProducts(newProducts: List<Product>) {
        products = newProducts
        notifyDataSetChanged()
    }

    fun updateWeight(productId: Int, weight: Int) {
        selectedWeights[productId] = weight
        val position = products.indexOfFirst { it.id == productId }
        if (position >= 0) {
            notifyItemChanged(position)
        }
    }

    // Метод для автоматического выбора продукта
    fun setProductSelected(productId: Int, weight: Int = 100) {
        productId.let {
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
        productId.let {
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
        return productId.let { selectedProducts.contains(it) } ?: false
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

    // Очистка всех выбранных продуктов
    fun clearSelection() {
        val previousSelected = selectedProducts.toList()
        selectedProducts.clear()
        selectedWeights.clear()

        // Уведомляем об изменении для каждого ранее выбранного продукта
        previousSelected.forEach { productId ->
            val position = products.indexOfFirst { it.id == productId }
            if (position >= 0) {
                notifyItemChanged(position)
            }
        }
    }
}