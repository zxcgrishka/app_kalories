package com.example.test2.ui.home.AddDish.CreateDish.AddProduct

import android.app.Activity
import android.content.Intent
import android.graphics.*
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.lifecycleScope
import com.example.test2.data.AppDatabase
import com.example.test2.databinding.ActivityAddProductsBinding
import com.example.test2.ml.YoloDetector
import com.example.test2.utils.CameraActivity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.max
import java.io.InputStream

class AddProductsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAddProductsBinding
    private lateinit var detector: YoloDetector
    private lateinit var originalBitmap: Bitmap
    private lateinit var database: AppDatabase

    private var detectedFood: String? = null
    private var detectionConfidence: Float = 0f

    private val REQUEST_PICK_IMAGE = 10
    private val REQUEST_CAMERA = 20

    companion object {
        private const val TAG = "AddProductsActivity"
    }

    // База данных продуктов с БЖУ (на 100г)
    private val foodNutritionDatabase = mapOf(
        "banana" to FoodNutrition(
            calories = 89,
            proteins = 1,
            fats = 0,
            carbs = 23
        ),
        "apple" to FoodNutrition(
            calories = 52,
            proteins = 0,
            fats = 0,
            carbs = 14
        ),
        "sandwich" to FoodNutrition(
            calories = 250,
            proteins = 10,
            fats = 8,
            carbs = 35
        ),
        "orange" to FoodNutrition(
            calories = 47,
            proteins = 1,
            fats = 0,
            carbs = 12
        ),
        "broccoli" to FoodNutrition(
            calories = 34,
            proteins = 3,
            fats = 0,
            carbs = 7
        ),
        "carrot" to FoodNutrition(
            calories = 41,
            proteins = 1,
            fats = 0,
            carbs = 10
        ),
        "hot dog" to FoodNutrition(
            calories = 290,
            proteins = 10,
            fats = 26,
            carbs = 4
        ),
        "pizza" to FoodNutrition(
            calories = 266,
            proteins = 11,
            fats = 10,
            carbs = 33
        ),
        "donut" to FoodNutrition(
            calories = 452,
            proteins = 5,
            fats = 25,
            carbs = 51
        ),
        "cake" to FoodNutrition(
            calories = 371,
            proteins = 4,
            fats = 16,
            carbs = 53
        )
    )

    data class FoodNutrition(
        val calories: Int,
        val proteins: Int,
        val fats: Int,
        val carbs: Int
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddProductsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Инициализация базы данных
        database = AppDatabase.getDatabase(this)

        try {
            detector = YoloDetector(this)
            Log.d(TAG, "YoloDetector инициализирован успешно")
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка инициализации YoloDetector: ${e.message}", e)
            Toast.makeText(this, "Ошибка загрузки модели нейросети", Toast.LENGTH_LONG).show()
        }

        // Используем безопасные вызовы для nullable полей
        binding.btnOpenGallery?.setOnClickListener {
            Log.d(TAG, "Кнопка галереи нажата")
            pickImageFromGallery()
        }

        binding.btnOpenCamera?.setOnClickListener {
            Log.d(TAG, "Кнопка камеры нажата")
            openCamera()
        }

        binding.btnTextSearch?.setOnClickListener {
            val query = binding.edName?.text?.toString()?.trim() ?: ""
            if (query.isNotBlank()) {
                searchFoodInDatabase(query)
            } else {
                Toast.makeText(this, "Введите название для поиска", Toast.LENGTH_SHORT).show()
            }
        }

        binding.button2?.setOnClickListener {
            saveProduct()
        }
    }

    private fun pickImageFromGallery() {
        Log.d(TAG, "Открытие галереи")
        try {
            val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            intent.type = "image/*"
            startActivityForResult(intent, REQUEST_PICK_IMAGE)
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка при открытии галереи: ${e.message}", e)
            Toast.makeText(this, "Не удалось открыть галерею", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openCamera() {
        Log.d(TAG, "Открытие камеры")
        try {
            val intent = Intent(this, CameraActivity::class.java)
            startActivityForResult(intent, REQUEST_CAMERA)
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка при открытии камеры: ${e.message}", e)
            Toast.makeText(this, "Не удалось открыть камеру", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        Log.d(TAG, "onActivityResult: requestCode=$requestCode, resultCode=$resultCode, data=$data")

        if (resultCode != Activity.RESULT_OK) {
            Log.w(TAG, "Результат не RESULT_OK")
            return
        }

        when (requestCode) {
            REQUEST_PICK_IMAGE -> {
                Log.d(TAG, "Получен результат из галереи")
                val uri = data?.data
                if (uri != null) {
                    Log.d(TAG, "URI изображения: $uri")
                    loadAndProcessImage(uri)
                } else {
                    Log.e(TAG, "URI изображения null")
                    Toast.makeText(this, "Не удалось получить изображение", Toast.LENGTH_SHORT).show()
                }
            }
            REQUEST_CAMERA -> {
                Log.d(TAG, "Получен результат из камеры")
                val uriString = data?.getStringExtra("photo_uri")
                if (uriString != null) {
                    Log.d(TAG, "URI фото с камеры: $uriString")
                    loadAndProcessImage(Uri.parse(uriString))
                } else {
                    Log.e(TAG, "URI фото с камеры null")
                    Toast.makeText(this, "Не удалось получить фото с камеры", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun loadAndProcessImage(uri: Uri) {
        Log.d(TAG, "Загрузка изображения из URI: $uri")

        try {
            var bitmap: Bitmap? = null

            // Простой способ загрузки
            contentResolver.openInputStream(uri)?.use { inputStream ->
                // Сначала получаем размеры изображения
                val options = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }

                // Декодируем только для получения размеров
                BitmapFactory.decodeStream(inputStream, null, options)

                // Возвращаемся в начало потока
                inputStream.close()

                // Открываем поток заново
                val newInputStream = contentResolver.openInputStream(uri)
                newInputStream?.use { newStream ->
                    // Рассчитываем коэффициент масштабирования
                    val scale = calculateInSampleSize(options, 1024, 1024)

                    val scaledOptions = BitmapFactory.Options().apply {
                        inSampleSize = scale
                        inPreferredConfig = Bitmap.Config.ARGB_8888
                    }

                    // Декодируем с масштабированием
                    bitmap = BitmapFactory.decodeStream(newStream, null, scaledOptions)
                }
            }

            if (bitmap == null) {
                Toast.makeText(this, "Не удалось загрузить изображение", Toast.LENGTH_SHORT).show()
                return
            }

            // Поворачиваем если нужно
            bitmap = rotateImageIfRequired(bitmap!!, uri)

            // Сохраняем оригинал
            originalBitmap = bitmap!!.copy(Bitmap.Config.ARGB_8888, true)

            // Обрабатываем
            processImageWithDetection(originalBitmap)

        } catch (e: Exception) {
            Log.e(TAG, "Ошибка загрузки изображения: ${e.message}", e)
            Toast.makeText(this, "Ошибка загрузки изображения: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }

    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val height = options.outHeight
        val width = options.outWidth
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2

            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }

        Log.d(TAG, "inSampleSize = $inSampleSize для размеров $width x $height")
        return inSampleSize
    }

    private fun rotateImageIfRequired(bitmap: Bitmap, uri: Uri): Bitmap {
        var rotatedBitmap = bitmap

        try {
            val inputStream = contentResolver.openInputStream(uri)
            inputStream?.use { stream ->
                val exif = ExifInterface(stream)
                val orientation = exif.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )

                rotatedBitmap = when (orientation) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> rotateBitmap(bitmap, 90f)
                    ExifInterface.ORIENTATION_ROTATE_180 -> rotateBitmap(bitmap, 180f)
                    ExifInterface.ORIENTATION_ROTATE_270 -> rotateBitmap(bitmap, 270f)
                    else -> bitmap
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка при чтении EXIF: ${e.message}", e)
        }

        return rotatedBitmap
    }

    private fun rotateBitmap(bitmap: Bitmap, degrees: Float): Bitmap {
        val matrix = Matrix()
        matrix.postRotate(degrees)
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun processImageWithDetection(bitmap: Bitmap) {
        Log.d(TAG, "Начало обработки изображения, размер: ${bitmap.width}x${bitmap.height}")
        binding.tvList?.text = "Обработка изображения..."

        try {
            if (!::detector.isInitialized) {
                Log.e(TAG, "Детектор не инициализирован")
                Toast.makeText(this, "Нейросеть не готова", Toast.LENGTH_SHORT).show()
                return
            }

            // Показываем превью изображения
            showImagePreview(bitmap)

            // Детекция объектов (в фоновом потоке)
            Thread {
                try {
                    val detections = detector.detectFoodOnly(bitmap)
                    Log.d(TAG, "Найдено объектов: ${detections.size}")

                    runOnUiThread {
                        if (detections.isEmpty()) {
                            binding.tvList?.text = "Еда не обнаружена\nПопробуйте другое изображение"
                            Toast.makeText(
                                this,
                                "Не удалось распознать еду. Убедитесь, что еда хорошо видна на фото",
                                Toast.LENGTH_LONG
                            ).show()
                            return@runOnUiThread
                        }

                        // Рисуем bounding boxes на изображении
                        val annotatedBitmap = drawDetections(bitmap, detections)
                        showImagePreview(annotatedBitmap)

                        // Берем самый уверенный результат
                        val bestDetection = detections.maxByOrNull { it.confidence }
                        detectedFood = bestDetection?.label
                        detectionConfidence = bestDetection?.confidence ?: 0f

                        // Автозаполнение полей
                        detectedFood?.let { foodName ->
                            binding.edName?.setText(foodName)

                            // Получаем калории и БЖУ из нашей базы данных
                            fillNutritionFields(foodName)

                            val confidencePercent = (detectionConfidence * 100).toInt()
                            Toast.makeText(
                                this,
                                "Обнаружено: $foodName (${confidencePercent}% уверенности)",
                                Toast.LENGTH_LONG
                            ).show()

                            Log.d(TAG, "Обнаружено: $foodName с уверенностью ${confidencePercent}%")
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Ошибка детекции: ${e.message}", e)
                    runOnUiThread {
                        Toast.makeText(
                            this,
                            "Ошибка обработки изображения нейросетью: ${e.message}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }.start()

        } catch (e: Exception) {
            Log.e(TAG, "Ошибка в processImageWithDetection: ${e.message}", e)
            Toast.makeText(this, "Ошибка обработки: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun fillNutritionFields(foodName: String) {
        // Приводим название к нижнему регистру для поиска
        val lowerFoodName = foodName.lowercase()

        // Ищем продукт в базе данных (с учетом возможных вариаций названий)
        val nutrition = findNutritionInDatabase(lowerFoodName)

        if (nutrition != null) {
            binding.edKalories?.setText(nutrition.calories.toString())
            binding.edProteins?.setText(nutrition.proteins.toString())
            binding.edFats?.setText(nutrition.fats.toString())
            binding.edCarbohydrates?.setText(nutrition.carbs.toString())

            Log.d(TAG, "Заполнены данные для $foodName: ${nutrition.calories} ккал, " +
                    "Б: ${nutrition.proteins}, Ж: ${nutrition.fats}, У: ${nutrition.carbs}")
        } else {
            // Если продукт не найден в базе, используем базовые значения из YoloDetector
            val calories = detector.getCaloriesForFood(foodName)
            binding.edKalories?.setText(calories.toString())
            binding.edProteins?.setText("0")
            binding.edFats?.setText("0")
            binding.edCarbohydrates?.setText("0")

            Log.d(TAG, "Продукт $foodName не найден в базе, использованы базовые значения")
        }
    }

    private fun findNutritionInDatabase(foodName: String): FoodNutrition? {
        return when {
            foodName.contains("banana") -> foodNutritionDatabase["banana"]
            foodName.contains("apple") -> foodNutritionDatabase["apple"]
            foodName.contains("sandwich") -> foodNutritionDatabase["sandwich"]
            foodName.contains("orange") -> foodNutritionDatabase["orange"]
            foodName.contains("broccoli") -> foodNutritionDatabase["broccoli"]
            foodName.contains("carrot") -> foodNutritionDatabase["carrot"]
            foodName.contains("hot dog") || foodName.contains("hotdog") -> foodNutritionDatabase["hot dog"]
            foodName.contains("pizza") -> foodNutritionDatabase["pizza"]
            foodName.contains("donut") || foodName.contains("doughnut") -> foodNutritionDatabase["donut"]
            foodName.contains("cake") -> foodNutritionDatabase["cake"]
            else -> null
        }
    }

    private fun showImagePreview(bitmap: Bitmap) {
        // Проверяем размер TextView перед масштабированием
        val tvList = binding.tvList ?: return

        if (tvList.width <= 0 || tvList.height <= 0) {
            // Если размеры еще не известны, используем небольшие размеры
            val previewBitmap = Bitmap.createScaledBitmap(bitmap, 300, 300, true)
            tvList.text = ""
            tvList.setBackgroundBitmap(previewBitmap)
        } else {
            // Масштабируем для превью
            val previewBitmap = Bitmap.createScaledBitmap(
                bitmap,
                tvList.width,
                tvList.height,
                true
            )

            tvList.text = ""
            tvList.setBackgroundBitmap(previewBitmap)
        }
    }

    private fun drawDetections(bitmap: Bitmap, detections: List<com.example.test2.ml.Detection>): Bitmap {
        val mutableBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(mutableBitmap)

        // Масштабируем толщину линии и текст
        val scale = bitmap.width / 1000f

        // Создаем Paint объекты
        val boxPaint = Paint().apply {
            color = Color.GREEN
            style = Paint.Style.STROKE
            strokeWidth = max(3f, 4f * scale)
        }

        val textPaint = Paint().apply {
            color = Color.RED
            textSize = max(24f, 32f * scale)
            style = Paint.Style.FILL
            isAntiAlias = true
        }

        val textBgPaint = Paint().apply {
            color = Color.argb(150, 0, 0, 0)
            style = Paint.Style.FILL
        }

        for (detection in detections) {
            // Рисуем bounding box
            canvas.drawRect(detection.box, boxPaint)

            // Текст с меткой и уверенностью
            val label = "${detection.label} ${(detection.confidence * 100).toInt()}%"
            val textWidth = textPaint.measureText(label)
            val textHeight = textPaint.textSize

            // Фон для текста
            val textBgRect = RectF(
                detection.box.left,
                detection.box.top - textHeight - 5,
                detection.box.left + textWidth + 10,
                detection.box.top - 5
            )
            canvas.drawRect(textBgRect, textBgPaint)

            // Сам текст
            canvas.drawText(
                label,
                detection.box.left + 5,
                detection.box.top - 10,
                textPaint
            )
        }

        return mutableBitmap
    }

    private fun searchFoodInDatabase(query: String) {
        Log.d(TAG, "Поиск продукта: '$query'")

        if (query.isBlank()) {
            Toast.makeText(this, "Введите название для поиска", Toast.LENGTH_SHORT).show()
            return
        }

        // Сначала проверяем локальную базу данных БЖУ
        val lowerQuery = query.lowercase()
        val nutrition = findNutritionInDatabase(lowerQuery)

        if (nutrition != null) {
            // Нашли в локальной базе БЖУ
            binding.edName?.setText(query)
            binding.edKalories?.setText(nutrition.calories.toString())
            binding.edProteins?.setText(nutrition.proteins.toString())
            binding.edFats?.setText(nutrition.fats.toString())
            binding.edCarbohydrates?.setText(nutrition.carbs.toString())

            Toast.makeText(
                this,
                "Найден продукт: $query",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        // Если не нашли в локальной базе, ищем в Room базе данных
        lifecycleScope.launch {
            try {
                // Получаем все продукты из базы данных Room
                val productsFlow = database.productDao().getAllProducts()
                val products = try {
                    productsFlow.first() // Получаем первый элемент из Flow
                } catch (e: Exception) {
                    Log.e(TAG, "Ошибка получения продуктов: ${e.message}")
                    emptyList()
                }

                Log.d(TAG, "Всего продуктов в базе: ${products.size}")

                // Простой поиск по названию
                val filteredProducts = products.filter { product ->
                    product.ProductName.contains(query, ignoreCase = true)
                }

                Log.d(TAG, "Найдено продуктов: ${filteredProducts.size}")

                if (filteredProducts.isEmpty()) {
                    runOnUiThread {
                        Toast.makeText(
                            this@AddProductsActivity,
                            "Продукт '$query' не найден в базе данных",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                } else {
                    // Берем первый найденный продукт
                    val product = filteredProducts.first()
                    runOnUiThread {
                        binding.edName?.setText(product.ProductName)
                        binding.edKalories?.setText(product.ProductCalories.toString())
                        binding.edProteins?.setText(product.ProductProteins.toString())
                        binding.edFats?.setText(product.ProductFats.toString())
                        binding.edCarbohydrates?.setText(product.ProductCarbohydrates.toString())

                        Toast.makeText(
                            this@AddProductsActivity,
                            "Найден продукт: ${product.ProductName}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Ошибка поиска продукта в Room БД: ${e.message}", e)
                runOnUiThread {
                    Toast.makeText(
                        this@AddProductsActivity,
                        "Ошибка поиска: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun saveProduct() {
        val name = binding.edName?.text?.toString()?.trim() ?: ""
        val caloriesText = binding.edKalories?.text?.toString() ?: "0"
        val proteinsText = binding.edProteins?.text?.toString() ?: "0"
        val fatsText = binding.edFats?.text?.toString() ?: "0"
        val carbsText = binding.edCarbohydrates?.text?.toString() ?: "0"

        val calories = caloriesText.toIntOrNull() ?: 0
        val proteins = proteinsText.toIntOrNull() ?: 0
        val fats = fatsText.toIntOrNull() ?: 0
        val carbs = carbsText.toIntOrNull() ?: 0

        if (name.isEmpty()) {
            Toast.makeText(this, "Введите название продукта", Toast.LENGTH_SHORT).show()
            return
        }

        if (calories <= 0) {
            Toast.makeText(this, "Введите корректное количество калорий", Toast.LENGTH_SHORT).show()
            return
        }

        val product = Product(
            id = null,
            ProductName = name,
            ProductCalories = calories,
            ProductProteins = proteins,
            ProductFats = fats,
            ProductCarbohydrates = carbs
        )

        // Сохраняем продукт в базу данных через coroutine
        lifecycleScope.launch {
            try {
                database.productDao().insert(product)

                runOnUiThread {
                    Toast.makeText(
                        this@AddProductsActivity,
                        "Продукт '$name' успешно сохранен!",
                        Toast.LENGTH_SHORT
                    ).show()
                    Log.d(TAG, "Продукт сохранен: $product")

                    // Очищаем поля после сохранения
                    clearFields()
                }

            } catch (e: Exception) {
                Log.e(TAG, "Ошибка сохранения продукта: ${e.message}", e)
                runOnUiThread {
                    Toast.makeText(
                        this@AddProductsActivity,
                        "Ошибка сохранения: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun clearFields() {
        binding.edName?.setText("")
        binding.edKalories?.setText("")
        binding.edProteins?.setText("")
        binding.edFats?.setText("")
        binding.edCarbohydrates?.setText("")
        binding.tvList?.text = "Продукт сохранен"
        binding.tvList?.setBackgroundBitmap(null)
    }

    override fun onDestroy() {
        super.onDestroy()
        // Не закрываем базу данных здесь, так как AppDatabase использует singleton
        // и автоматически управляет соединением
    }
}

// Extension для установки Bitmap как фона
fun android.widget.TextView.setBackgroundBitmap(bitmap: Bitmap?) {
    if (bitmap == null) {
        background = null
    } else {
        val drawable = android.graphics.drawable.BitmapDrawable(context.resources, bitmap)
        background = drawable
    }
}