package com.example.test2

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.asLiveData
import com.example.test2.databinding.ActivityAddProductsBinding
import com.example.test2.utils.CameraActivity

class AddProductsActivity : AppCompatActivity() {
    private lateinit var binding: ActivityAddProductsBinding

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
        db.tempProductDebugDao().getAllProducts1().asLiveData().observe(this) { products ->
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
    }

    private fun addProductManually(db: MainDb) {
        try {
            val product = Product(
                null,
                binding.edName?.text?.toString() ?: "",
                binding.edKalories.text.toString().toIntOrNull() ?: 0,
                binding.edProteins?.text?.toString()?.toIntOrNull() ?: 0,
                binding.edFats?.text?.toString()?.toIntOrNull() ?: 0,
                binding.edCarbohydrates?.text?.toString()?.toIntOrNull() ?: 0
            )

            if (product.ProductName.isBlank()) {
                Toast.makeText(this, "Введите название продукта", Toast.LENGTH_SHORT).show()
                return
            }

            Thread {
                db.tempProductDebugDao().insertProduct(product)
            }.start()

            clearInputFields()
            Toast.makeText(this, "Продукт добавлен", Toast.LENGTH_SHORT).show()

        } catch (e: Exception) {
            Toast.makeText(this, "Ошибка при добавлении продукта: ${e.message}", Toast.LENGTH_SHORT).show()
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
        // TODO: Реализовать интеграцию с Nutritionix API
        fillFieldsWithTestData()
    }

    private fun fillFieldsWithTestData() {
        binding.edName?.setText("Яблоко")
        binding.edKalories.setText("52")
        binding.edProteins?.setText("0")
        binding.edFats?.setText("0")
        binding.edCarbohydrates?.setText("14")
        Toast.makeText(this, "Данные заполнены автоматически", Toast.LENGTH_SHORT).show()
    }

    private fun clearInputFields() {
        binding.edName?.setText("")
        binding.edKalories.setText("")
        binding.edProteins?.setText("")
        binding.edFats?.setText("")
        binding.edCarbohydrates?.setText("")
    }
}