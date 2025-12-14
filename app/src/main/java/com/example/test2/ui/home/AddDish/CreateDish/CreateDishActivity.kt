package com.example.test2

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.test2.data.AppDatabase
import com.example.test2.data.Meal
import com.example.test2.data.User.UserRepository
import com.example.test2.databinding.ActivityCreateDishBinding
import com.example.test2.ml.YoloDetector
import com.example.test2.network.NetworkModule
import com.example.test2.ui.ProductAdapter
import com.example.test2.ui.ProductViewModel
import com.example.test2.ui.ProductViewModelFactory
import com.example.test2.ui.home.AddDish.CreateDish.AddProduct.AddProductsActivity
import com.example.test2.ui.home.AddDish.CreateDish.AddProduct.Product
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.Date

class CreateDishActivity : AppCompatActivity() {
    private lateinit var binding: ActivityCreateDishBinding
    private lateinit var productViewModel: ProductViewModel
    private lateinit var adapter: ProductAdapter
    private lateinit var detector: YoloDetector

    private var selectedProducts = mutableListOf<SelectedProduct>()
    private var totalCalories = 0
    private var totalProteins = 0
    private var totalFats = 0
    private var totalCarbs = 0
    private var userId = -1L
    private var allProducts = listOf<Product>()

    data class SelectedProduct(
        val product: Product,
        val weight: Int = 100
    )

    // Для выбора изображения из галереи
    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { processSelectedImage(it, "gallery") }
    }

    // Для камеры
    private val takePictureLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicturePreview()
    ) { bitmap: Bitmap? ->
        bitmap?.let { processCameraImage(it) }
    }

    companion object {
        private const val TAG = "CreateDishActivity"
        private const val CAMERA_PERMISSION_REQUEST_CODE = 100
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate started")

        binding = ActivityCreateDishBinding.inflate(layoutInflater)
        setContentView(binding.root)
        Log.d(TAG, "Binding set — UI ready")

        userId = getUserIdFromPrefs()

        // Инициализация нейросети
        try {
            detector = YoloDetector(this)
            Log.d(TAG, "YoloDetector инициализирован успешно")
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка инициализации YoloDetector: ${e.message}", e)
            Toast.makeText(this, "Нейросеть временно недоступна", Toast.LENGTH_SHORT).show()
        }

        // Создаем локальный Repository
        val database = AppDatabase.getDatabase(this)
        val localRepository = UserRepository(
            database,
            NetworkModule.provideMyApiService(this),
            this
        )

        // ViewModel для продуктов
        productViewModel = viewModels<ProductViewModel> {
            ProductViewModelFactory(localRepository)
        }.value

        setupRecyclerView()
        loadProducts()

        // Кнопка "Сохранить блюдо"
        binding.btnSaveDish.setOnClickListener {
            val dishName = binding.etDishName.text.toString().trim()
            if (dishName.isNotBlank() && selectedProducts.isNotEmpty()) {
                val productsIds = selectedProducts.map { it.product.id }.joinToString(",")
                val weights = selectedProducts.map { it.weight }.joinToString(",")

                val meal = Meal(
                    userId = userId,
                    name = dishName,
                    calories = totalCalories,
                    date = Date(),
                    productsIds = productsIds,
                    productsWeights = weights
                )

                lifecycleScope.launch {
                    localRepository.insertMeal(meal)
                    Log.d(TAG, "Meal saved: $dishName, $totalCalories ккал")
                    Toast.makeText(
                        this@CreateDishActivity,
                        "Блюдо '$dishName' сохранено!",
                        Toast.LENGTH_SHORT
                    ).show()
                    setResult(Activity.RESULT_OK)
                    finish()
                }
            } else {
                if (dishName.isBlank()) {
                    Toast.makeText(this, "Введите название блюда", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Выберите хотя бы один продукт", Toast.LENGTH_SHORT).show()
                }
            }
        }

        // Кнопка "новый продукт"
        binding.btnAddProduct.setOnClickListener {
            Log.d(TAG, "btnAddProduct clicked")
            startActivity(Intent(this, AddProductsActivity::class.java))
        }

        // Кнопка для нейросети из галереи
        binding.btnNeuralGallery.setOnClickListener {
            Log.d(TAG, "btnNeuralGallery clicked")
            openImagePicker()
        }

        // Кнопка для нейросети из камеры
        binding.btnNeuralCamera.setOnClickListener {
            Log.d(TAG, "btnNeuralCamera clicked")
            openCamera()
        }

        Log.d(TAG, "onCreate finished")
    }

    private fun openImagePicker() {
        pickImageLauncher.launch("image/*")
    }

    private fun openCamera() {
        if (checkCameraPermission()) {
            takePictureLauncher.launch(null)
        } else {
            requestCameraPermission()
        }
    }

    private fun checkCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestCameraPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.CAMERA),
            CAMERA_PERMISSION_REQUEST_CODE
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                openCamera()
            } else {
                Toast.makeText(
                    this,
                    "Для использования камеры нужно разрешение",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun processCameraImage(bitmap: Bitmap) {
        lifecycleScope.launch {
            try {
                binding.tvStatus.text = "Обработка фото с камеры..."
                analyzeImageWithNeuralNetwork(bitmap, "camera")
            } catch (e: Exception) {
                Log.e(TAG, "Ошибка обработки фото с камеры: ${e.message}", e)
                Toast.makeText(
                    this@CreateDishActivity,
                    "Ошибка обработки фото",
                    Toast.LENGTH_SHORT
                ).show()
                binding.tvStatus.text = ""
            }
        }
    }

    private fun processSelectedImage(uri: Uri, source: String) {
        lifecycleScope.launch {
            try {
                binding.tvStatus.text = "Загрузка изображения..."

                val bitmap = withContext(Dispatchers.IO) {
                    contentResolver.openInputStream(uri)?.use { inputStream ->
                        MediaStore.Images.Media.getBitmap(contentResolver, uri)
                    }
                }

                if (bitmap != null) {
                    analyzeImageWithNeuralNetwork(bitmap, source)
                } else {
                    Toast.makeText(
                        this@CreateDishActivity,
                        "Не удалось загрузить изображение",
                        Toast.LENGTH_SHORT
                    ).show()
                    binding.tvStatus.text = ""
                }
            } catch (e: Exception) {
                Log.e(TAG, "Ошибка обработки изображения: ${e.message}", e)
                Toast.makeText(
                    this@CreateDishActivity,
                    "Ошибка обработки изображения",
                    Toast.LENGTH_SHORT
                ).show()
                binding.tvStatus.text = ""
            }
        }
    }

    private fun analyzeImageWithNeuralNetwork(bitmap: Bitmap, source: String) {
        lifecycleScope.launch {
            try {
                binding.tvStatus.text = "Распознавание еды..."

                withContext(Dispatchers.Default) {
                    if (!::detector.isInitialized) {
                        runOnUiThread {
                            Toast.makeText(
                                this@CreateDishActivity,
                                "Нейросеть не инициализирована",
                                Toast.LENGTH_SHORT
                            ).show()
                            binding.tvStatus.text = ""
                        }
                        return@withContext
                    }

                    val detections = detector.detectFoodOnly(bitmap)

                    if (detections.isEmpty()) {
                        runOnUiThread {
                            Toast.makeText(
                                this@CreateDishActivity,
                                "Еда не обнаружена на фото",
                                Toast.LENGTH_SHORT
                            ).show()
                            binding.tvStatus.text = ""
                        }
                        return@withContext
                    }

                    Log.d(TAG, "Найдено объектов из $source: ${detections.size}")

                    // Берем только уникальные названия с лучшей уверенностью
                    val bestDetections = mutableMapOf<String, com.example.test2.ml.Detection>()
                    detections.forEach { detection ->
                        val existing = bestDetections[detection.label]
                        if (existing == null || detection.confidence > existing.confidence) {
                            bestDetections[detection.label] = detection
                        }
                    }

                    val detectedFoods = bestDetections.keys.toList()

                    runOnUiThread {
                        processDetectedFoods(detectedFoods)

                        val confidenceList = bestDetections.values.map {
                            "${it.label} (${(it.confidence * 100).toInt()}%)"
                        }.joinToString(", ")

                        Toast.makeText(
                            this@CreateDishActivity,
                            "Найдено: ${detectedFoods.size} продуктов: $confidenceList",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Ошибка нейросети: ${e.message}", e)
                runOnUiThread {
                    Toast.makeText(
                        this@CreateDishActivity,
                        "Ошибка распознавания",
                        Toast.LENGTH_SHORT
                    ).show()
                    binding.tvStatus.text = ""
                }
            }
        }
    }

    private fun processDetectedFoods(detectedFoods: List<String>) {
        binding.tvStatus.text = "Поиск продуктов в базе..."

        val notFoundFoods = mutableListOf<String>()
        val foundProducts = mutableListOf<Product>()

        detectedFoods.forEach { foodName ->
            // Ищем продукт в базе данных
            val product = findProductByName(foodName)
            if (product != null) {
                foundProducts.add(product)
            } else {
                notFoundFoods.add(foodName)
            }
        }

        if (foundProducts.isNotEmpty()) {
            // Автоматически выбираем найденные продукты
            foundProducts.forEach { product ->
                val productId = product.id ?: 0
                if (!selectedProducts.any { it.product.id == productId }) {
                    selectedProducts.add(SelectedProduct(product, 100))
                    // Автоматически отмечаем продукт в адаптере
                    adapter.setProductSelected(productId, 100)
                }
            }
            updateTotals()

            Toast.makeText(
                this,
                "Добавлено ${foundProducts.size} продуктов из ${detectedFoods.size} найденных",
                Toast.LENGTH_SHORT
            ).show()
        }

        if (notFoundFoods.isNotEmpty()) {
            // Показываем диалог с ненайденными продуктами
            showNotFoundFoodsDialog(notFoundFoods)
        }

        binding.tvStatus.text = ""
    }

    private fun setProductAsSelected(productId: Int) {
        // Находим позицию продукта в списке
        val position = allProducts.indexOfFirst { it.id == productId }
        if (position >= 0) {
            // Обновляем конкретный элемент
            adapter.notifyItemChanged(position)
        }
    }

    private fun findProductByName(foodName: String): Product? {
        val lowerFoodName = foodName.lowercase()

        // 1. Точное совпадение
        allProducts.forEach { product ->
            if (product.ProductName.equals(foodName, ignoreCase = true)) {
                return product
            }
        }

        // 2. Частичное совпадение в названии
        allProducts.forEach { product ->
            val lowerProductName = product.ProductName.lowercase()
            if (lowerProductName.contains(lowerFoodName) ||
                lowerFoodName.contains(lowerProductName)) {
                return product
            }
        }

        // 3. Поиск по ключевым словам для русских названий
        val searchMap = mapOf(
            // Фрукты
            "яблоко" to listOf("apple", "яблок"),
            "банан" to listOf("banana", "банан"),
            "апельсин" to listOf("orange", "апельсин"),
            "виноград" to listOf("grape", "виноград"),
            "грейпфрут" to listOf("grapefruit", "грейпфрут"),
            "лимон" to listOf("lemon", "лимон"),
            "персик" to listOf("peach", "персик"),
            "груша" to listOf("pear", "груш"),
            "клубника" to listOf("strawberry", "straw", "клубник"),
            "арбуз" to listOf("watermelon", "арбуз"),

            // Овощи
            "болгарский перец" to listOf("bell pepper", "pepper", "перец", "болгар"),
            "брокколи" to listOf("broccoli", "брокколи", "брокол"),
            "морковь" to listOf("carrot", "морков"),
            "огурец" to listOf("cucumber", "огурец"),
            "помидор" to listOf("tomato", "помидор"),
            "картофель" to listOf("potato", "картофель", "картош"),
            "салат" to listOf("lettuce", "salad", "салат"),

            // Мучное
            "хлеб" to listOf("bread", "хлеб"),
            "торт" to listOf("cake", "торт"),
            "печенье" to listOf("cookie", "печень"),
            "круассан" to listOf("croissant", "круассан"),
            "пончик" to listOf("donut", "doughnut", "пончик"),
            "маффин" to listOf("muffin", "маффин"),
            "блин" to listOf("pancake", "блин"),
            "вафля" to listOf("waffle", "вафл"),

            // Основные блюда
            "сыр" to listOf("cheese", "сыр"),
            "пицца" to listOf("pizza", "пицц"),
            "гамбургер" to listOf("hamburger", "burger", "гамбургер", "бургер"),
            "картофель фри" to listOf("french fries", "fries", "картофель фри", "фри"),
            "паста" to listOf("pasta", "паст", "макарон"),
            "суши" to listOf("sushi", "суши"),
            "яйцо" to listOf("egg", "яйц"),

            // Русская кухня
            "борщ" to listOf("borscht", "борщ"),
            "гречка" to listOf("buckwheat", "греч"),
            "котлета" to listOf("cutlet", "котлет"),
            "пельмени" to listOf("dumplings", "пельмен"),
            "пюре картофельное" to listOf("mashed potato", "пюре"),
            "молочная каша" to listOf("milk porridge", "каш", "молоч"),
            "окрошка" to listOf("okroshka", "окрошк"),
            "рис" to listOf("rice", "рис"),
            "колбаса" to listOf("sausage", "колбас"),
            "суп" to listOf("soup", "суп")
        )

        searchMap.forEach { (productName, searchTerms) ->
            searchTerms.forEach { term ->
                if (lowerFoodName.contains(term)) {
                    // Ищем продукт с этим именем
                    allProducts.forEach { product ->
                        if (product.ProductName.contains(productName, ignoreCase = true)) {
                            return product
                        }
                    }
                }
            }
        }

        return null
    }

    private fun showNotFoundFoodsDialog(notFoundFoods: List<String>) {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Продукты не найдены")
            .setMessage(
                "Следующие продукты не найдены в базе данных:\n" +
                        notFoundFoods.joinToString("\n") { "• $it" } +
                        "\n\nХотите добавить их в базу?"
            )
            .setPositiveButton("Добавить") { _, _ ->
                // Переходим на экран добавления продукта
                val intent = Intent(this, AddProductsActivity::class.java)
                if (notFoundFoods.isNotEmpty()) {
                    // Передаем первый продукт для автозаполнения
                    intent.putExtra("food_name", notFoundFoods.first())
                }
                startActivity(intent)
            }
            .setNegativeButton("Пропустить", null)
            .show()
    }

    private fun setupRecyclerView() {
        adapter = ProductAdapter(
            onSelected = { product, isSelected, weight ->
                if (isSelected) {
                    val selectedProduct = SelectedProduct(product, weight)
                    selectedProducts.add(selectedProduct)
                } else {
                    selectedProducts.removeAll { it.product.id == product.id }
                }
                updateTotals()
            },
            onWeightChanged = { product, weight ->
                val existingProduct = selectedProducts.find { it.product.id == product.id }
                if (existingProduct != null) {
                    selectedProducts.remove(existingProduct)
                    selectedProducts.add(SelectedProduct(product, weight))
                    updateTotals()
                }
            }
        )
        binding.rvProducts.layoutManager = LinearLayoutManager(this)
        binding.rvProducts.adapter = adapter
    }

    private fun updateTotals() {
        totalCalories = 0
        totalProteins = 0
        totalFats = 0
        totalCarbs = 0

        selectedProducts.forEach { selectedProduct ->
            val product = selectedProduct.product
            val weight = selectedProduct.weight
            val multiplier = weight / 100f

            totalCalories += (product.ProductCalories * multiplier).toInt()
            totalProteins += (product.ProductProteins * multiplier).toInt()
            totalFats += (product.ProductFats * multiplier).toInt()
            totalCarbs += (product.ProductCarbohydrates * multiplier).toInt()
        }

        binding.tvTotalCalories.text = "$totalCalories"

        binding.tvTotalProteins?.text = "Б: ${totalProteins}г"
        binding.tvTotalFats?.text = "Ж: ${totalFats}г"
        binding.tvTotalCarbs?.text = "У: ${totalCarbs}г"

        Log.d(TAG, "Totals updated: $totalCalories ккал")
    }

    private fun loadProducts() {
        productViewModel.loadProducts()
        productViewModel.products.observe(this) { products ->
            allProducts = products
            adapter.updateProducts(products)
            Log.d(TAG, "Loaded ${products.size} products")
        }
    }

    private fun getUserIdFromPrefs(): Long {
        val sharedPref = getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
        return sharedPref.getLong("current_user_id", -1L)
    }

    override fun onResume() {
        super.onResume()
        // Обновляем список продуктов при возвращении с экрана добавления
        productViewModel.loadProducts()
    }
}