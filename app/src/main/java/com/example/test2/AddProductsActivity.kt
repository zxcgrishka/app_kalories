package com.example.test2

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.asLiveData
import com.example.test2.databinding.ActivityAddProductsBinding
import com.example.test2.network.NetworkModule
import com.example.test2.network.NutritionixRepository
import com.example.test2.utils.CameraActivity
import com.example.test2.ProductDao
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AddProductsActivity : AppCompatActivity() {
    private lateinit var binding: ActivityAddProductsBinding

    // Nutritionix репозиторий
    private val nutritionixRepository by lazy {
        NutritionixRepository(NetworkModule.provideNutritionixService())
    }

    // Регистрация для получения результата от камеры
    private val cameraLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val photoUri = result.data?.getStringExtra("photo_uri")
            photoUri?.let { uriString ->
                processPhotoWithNutritionix(uriString)
                Toast.makeText(this, "Фото получено, анализируем...", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(this, "Съемка фото отменена", Toast.LENGTH_SHORT).show()
        }
    }

    // функция для загрузки из галереи
    private val galleryLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            processPhotoWithNutritionix(it.toString())
            Toast.makeText(this, "Фото из галереи загружено", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddProductsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val db = MainDb.getDb(this)

        // Наблюдатель за списком продуктов
        db.getDao().getAllProducts().asLiveData().observe(this) { products ->
            binding.tvList.text = ""
            products.forEach { product ->
                val text = "Id: ${product.id} Name: ${product.ProductName} " +
                        "Calories: ${product.ProductCalories} " +
                        "Proteins: ${product.ProductProteins} " +
                        "Fats: ${product.ProductFats} " +
                        "Carbohydrates: ${product.ProductCarbohydrates}\n"
                binding.tvList.append(text)
            }
        }

        // Кнопка добавления продукта вручную
        binding.button2.setOnClickListener {
            addProductManually(db)
        }

        // Кнопка для открытия камеры
        binding.btnOpenCamera?.setOnClickListener {
            openCamera()
        }

        // Кнопка для загрузки из галереи
        binding.btnOpenGallery?.setOnClickListener {
            openGallery()
        }

        // Кнопка для текстового поиска в Nutritionix
        binding.btnTextSearch?.setOnClickListener {
            showTextSearchDialog()
        }
    }

    private fun addProductManually(db: MainDb) {
        println("DEBUG: addProductManually called")

        // Проверим все поля
        println("DEBUG: edName is null: ${binding.edName == null}")
        println("DEBUG: edKalories is null: ${binding.edKalories == null}")
        println("DEBUG: edProteins is null: ${binding.edProteins == null}")
        println("DEBUG: edFats is null: ${binding.edFats == null}")
        println("DEBUG: edCarbohydrates is null: ${binding.edCarbohydrates == null}")

        val name = binding.edName?.text?.toString() ?: ""
        println("DEBUG: Name value: '$name'")

        if (name.isBlank()) {
            println("DEBUG: Name is blank, showing toast")
            Toast.makeText(this, "Введите название продукта", Toast.LENGTH_SHORT).show()
            return
        }

        val product = Product(
            null,
            name,
            binding.edKalories.text.toString().toIntOrNull() ?: 0,
            binding.edProteins?.text?.toString()?.toIntOrNull() ?: 0,
            binding.edFats?.text?.toString()?.toIntOrNull() ?: 0,
            binding.edCarbohydrates?.text?.toString()?.toIntOrNull() ?: 0
        )

        CoroutineScope(Dispatchers.IO).launch {
            try {
                db.getDao().insert(product)

                withContext(Dispatchers.Main) {
                    clearInputFields()
                    Toast.makeText(
                        this@AddProductsActivity,
                        "Продукт '${product.ProductName}' успешно добавлен!",
                        Toast.LENGTH_SHORT
                    ).show()
                    println("DEBUG: Product inserted: ${product.ProductName}")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@AddProductsActivity,
                        "Ошибка БД: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                    println("DEBUG: Database error: ${e.message}")
                }
            }
        }
    }

    private fun openCamera() {
        val intent = Intent(this, CameraActivity::class.java)
        cameraLauncher.launch(intent)
    }

    private fun openGallery() {
        galleryLauncher.launch("image/*")
    }

    private fun processPhotoWithNutritionix(photoUri: String) {
        showLoading(true)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val uri = Uri.parse(photoUri)

                val result = nutritionixRepository.recognizeFoodFromImage(uri, this@AddProductsActivity)

                withContext(Dispatchers.Main) {
                    showLoading(false)

                    when {
                        result.isSuccess -> {
                            val foods = result.getOrNull()
                            if (foods.isNullOrEmpty()) {
                                showManualInputFallback("Не удалось распознать продукт на фото")
                            } else {
                                showFoodSelectionDialog(foods)
                            }
                        }
                        result.isFailure -> {
                            val error = result.exceptionOrNull()
                            if (error?.message?.contains("API error: 404") == true) {
                                showManualInputFallback("Продукт не найден в базе Nutritionix")
                            } else {
                                showErrorFallback("Ошибка API: ${error?.message}")
                            }
                        }
                    }
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    showLoading(false)
                    showErrorFallback("Ошибка обработки: ${e.message}")
                }
            }
        }
    }

    private fun showTextSearchDialog() {
        val input = android.widget.EditText(this)
        input.hint = "Например: яблоко, куриная грудка, банан"

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Поиск продукта")
            .setMessage("Введите название продукта для поиска в Nutritionix:")
            .setView(input)
            .setPositiveButton("Найти") { dialog, _ ->
                val query = input.text.toString().trim()
                if (query.isNotBlank()) {
                    searchFoodByText(query)
                } else {
                    Toast.makeText(this, "Введите название продукта", Toast.LENGTH_SHORT).show()
                }
                dialog.dismiss()
            }
            .setNegativeButton("Отмена") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun searchFoodByText(query: String) {
        showLoading(true)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val result = nutritionixRepository.recognizeFoodFromText(query)

                withContext(Dispatchers.Main) {
                    showLoading(false)

                    when {
                        result.isSuccess -> {
                            val foods = result.getOrNull()
                            if (foods.isNullOrEmpty()) {
                                showManualInputFallback("Продукт '$query' не найден")
                            } else {
                                showFoodSelectionDialog(foods)
                            }
                        }
                        result.isFailure -> {
                            val error = result.exceptionOrNull()
                            showErrorFallback("Ошибка поиска: ${error?.message}")
                        }
                    }
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    showLoading(false)
                    showErrorFallback("Ошибка поиска: ${e.message}")
                }
            }
        }
    }

    private fun showFoodSelectionDialog(foods: List<com.example.test2.network.NutritionixFood>) {
        val foodNames = foods.mapIndexed { index, food ->
            "${index + 1}. ${food.food_name} - ${food.nf_calories?.toInt() ?: 0} ккал"
        }

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Выберите продукт")
            .setItems(foodNames.toTypedArray()) { dialog, which ->
                val selectedFood = foods[which]
                fillFieldsWithNutritionixData(selectedFood)
                dialog.dismiss()
            }
            .setNegativeButton("Отмена") { dialog, _ ->
                dialog.dismiss()
            }
            .setNeutralButton("Показать детали") { dialog, _ ->
                dialog.dismiss()
                showFoodDetailsDialog(foods)
            }
            .show()
    }

    private fun showFoodDetailsDialog(foods: List<com.example.test2.network.NutritionixFood>) {
        val foodDetails = foods.joinToString("\n\n") { food ->
            """
            🍽 ${food.food_name}
            🔥 Калории: ${food.nf_calories?.toInt() ?: 0} ккал
            💪 Белки: ${food.nf_protein?.toInt() ?: 0}г
            🥑 Жиры: ${food.nf_total_fat?.toInt() ?: 0}г
            🍚 Углеводы: ${food.nf_total_carbohydrate?.toInt() ?: 0}г
            📏 Порция: ${food.serving_qty ?: 1} ${food.serving_unit ?: "шт"}
            """.trimIndent()
        }

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Найденные продукты")
            .setMessage(foodDetails)
            .setPositiveButton("Выбрать первый") { dialog, _ ->
                fillFieldsWithNutritionixData(foods.first())
                dialog.dismiss()
            }
            .setNegativeButton("Закрыть") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun fillFieldsWithNutritionixData(food: com.example.test2.network.NutritionixFood) {
        binding.edName?.setText(food.food_name)
        binding.edKalories.setText((food.nf_calories?.toInt() ?: 0).toString())
        binding.edProteins?.setText((food.nf_protein?.toInt() ?: 0).toString())
        binding.edFats?.setText((food.nf_total_fat?.toInt() ?: 0).toString())
        binding.edCarbohydrates?.setText((food.nf_total_carbohydrate?.toInt() ?: 0).toString())

        // Показываем информацию о порции
        val servingInfo = if (food.serving_qty != null && food.serving_unit != null) {
            " (${food.serving_qty} ${food.serving_unit})"
        } else {
            ""
        }

        Toast.makeText(
            this,
            "Данные '${food.food_name}'$servingInfo загружены из Nutritionix",
            Toast.LENGTH_LONG
        ).show()
    }

    private fun showLoading(show: Boolean) {
        // Можно добавить ProgressBar в layout
        if (show) {
            Toast.makeText(this, "Анализируем...", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showErrorFallback(errorMessage: String) {
        Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show()

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Ошибка")
            .setMessage("$errorMessage\nХотите ввести данные вручную?")
            .setPositiveButton("Ввести вручную") { dialog, _ ->
                dialog.dismiss()
                binding.edName?.requestFocus()
            }
            .setNegativeButton("Попробовать снова") { dialog, _ ->
                dialog.dismiss()
                openCamera()
            }
            .setNeutralButton("Текстовый поиск") { dialog, _ ->
                dialog.dismiss()
                showTextSearchDialog()
            }
            .show()
    }

    private fun showManualInputFallback(message: String) {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Информация")
            .setMessage("$message\nВведите данные вручную или попробуйте текстовый поиск")
            .setPositiveButton("Ввести вручную") { dialog, _ ->
                dialog.dismiss()
                binding.edName?.requestFocus()
            }
            .setNegativeButton("Текстовый поиск") { dialog, _ ->
                dialog.dismiss()
                showTextSearchDialog()
            }
            .setNeutralButton("Снять снова") { dialog, _ ->
                dialog.dismiss()
                openCamera()
            }
            .show()
    }

    private fun clearInputFields() {
        binding.edName?.setText("")
        binding.edKalories.setText("")
        binding.edProteins?.setText("")
        binding.edFats?.setText("")
        binding.edCarbohydrates?.setText("")
    }
}