package com.example.test2.ui.home.AddDish.CreateDish.AddProduct

import android.app.Activity
import android.content.Intent
import android.graphics.*
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.view.animation.AlphaAnimation
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.lifecycleScope
import com.example.test2.data.AppDatabase
import com.example.test2.databinding.ActivityAddProductsBinding
import com.example.test2.ml.Detection
import com.example.test2.ml.YoloDetector
import com.example.test2.utils.CameraActivity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min
import java.io.InputStream

class AddProductsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAddProductsBinding
    private lateinit var detector: YoloDetector
    private lateinit var originalBitmap: Bitmap
    private lateinit var database: AppDatabase

    private var allDetections: List<Detection> = emptyList()
    private var currentDetectionIndex: Int = 0
    private var processedImages: MutableSet<Int> = mutableSetOf()
    private var currentSelectedProductName: String = ""

    private val REQUEST_PICK_IMAGE = 10
    private val REQUEST_CAMERA = 20

    companion object {
        private const val TAG = "AddProductsActivity"
    }

    // База данных продуктов с БЖУ (на 100г) для всех 43 классов
    private val foodNutritionDatabase = mapOf(
        // Фрукты
        "Яблоко" to FoodNutrition(52, 0, 0, 14),
        "Банан" to FoodNutrition(89, 1, 0, 23),
        "Апельсин" to FoodNutrition(47, 1, 0, 12),
        "Виноград" to FoodNutrition(69, 1, 0, 18),
        "Грейпфрут" to FoodNutrition(42, 1, 0, 11),
        "Лимон" to FoodNutrition(29, 1, 0, 9),
        "Персик" to FoodNutrition(39, 1, 0, 10),
        "Груша" to FoodNutrition(57, 0, 0, 15),
        "Клубника" to FoodNutrition(32, 1, 0, 8),
        "Арбуз" to FoodNutrition(30, 1, 0, 8),

        // Овощи
        "Болгарский перец" to FoodNutrition(31, 1, 0, 6),
        "Брокколи" to FoodNutrition(34, 3, 0, 7),
        "Морковь" to FoodNutrition(41, 1, 0, 10),
        "Огурец" to FoodNutrition(15, 1, 0, 3),
        "Помидор" to FoodNutrition(18, 1, 0, 4),
        "Картофель" to FoodNutrition(77, 2, 0, 17),
        "Салат" to FoodNutrition(15, 1, 0, 3),

        // Мучное и выпечка
        "Хлеб" to FoodNutrition(265, 9, 3, 49),
        "Торт" to FoodNutrition(371, 4, 16, 53),
        "Печенье" to FoodNutrition(500, 6, 24, 65),
        "Круассан" to FoodNutrition(406, 8, 21, 45),
        "Пончик" to FoodNutrition(452, 5, 25, 51),
        "Маффин" to FoodNutrition(425, 6, 20, 56),
        "Блин" to FoodNutrition(227, 6, 9, 32),
        "Вафля" to FoodNutrition(291, 6, 14, 38),

        // Основные блюда и продукты
        "Сыр" to FoodNutrition(402, 25, 33, 1),
        "Пицца" to FoodNutrition(266, 11, 10, 33),
        "Гамбургер" to FoodNutrition(295, 17, 14, 24),
        "Картофель фри" to FoodNutrition(312, 3, 15, 41),
        "Паста" to FoodNutrition(131, 5, 1, 25),
        "Суши" to FoodNutrition(150, 5, 1, 30),
        "Яйцо" to FoodNutrition(155, 13, 11, 1),

        // Русская/славянская кухня
        "Борщ" to FoodNutrition(54, 2, 2, 8),
        "Гречка" to FoodNutrition(92, 3, 1, 20),
        "Котлета" to FoodNutrition(230, 15, 16, 8),
        "Пельмени" to FoodNutrition(248, 12, 10, 29),
        "Макароны" to FoodNutrition(131, 5, 1, 25),
        "Пюре картофельное" to FoodNutrition(83, 2, 3, 15),
        "Молочная каша" to FoodNutrition(93, 3, 3, 15),
        "Окрошка" to FoodNutrition(64, 3, 2, 9),
        "Рис" to FoodNutrition(130, 3, 0, 28),
        "Колбаса" to FoodNutrition(336, 16, 29, 1),
        "Суп" to FoodNutrition(60, 3, 2, 8)
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

        // Настройка кнопок
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
            saveCurrentProduct() //лог1
        }

        // Настройка кнопок оверлея
        binding.btnCloseOverlay?.setOnClickListener {
            hideImageOverlay()
        }

        binding.btnSelectProduct?.setOnClickListener {
            selectCurrentProductAndCloseOverlay()
        }

        binding.btnManualInput?.setOnClickListener {
            hideImageOverlay()
            // Очищаем только название, остальные поля остаются для ручного ввода
            binding.edName?.setText("")
        }

        // Кнопки для навигации между продуктами в оверлее
        binding.btnPrevProduct?.setOnClickListener {
            showPreviousProductInOverlay()
        }

        binding.btnNextProduct?.setOnClickListener {
            showNextProductInOverlay()
        }

        // Инициализация UI
        binding.tvList?.text = "Выберите изображение с едой"

        // Скрываем оверлей по умолчанию
        binding.imageOverlayContainer?.visibility = View.GONE

        val foodName = intent.getStringExtra("food_name")
        if (!foodName.isNullOrBlank()) {
            binding.edName?.setText(foodName)
            // Автоматически ищем в базе данных
            searchFoodInDatabase(foodName)
        }
    }

    // =============== ФУНКЦИИ ОВЕРЛЕЯ ===============

    private fun showImageOverlay() {
        binding.imageOverlayContainer?.visibility = View.VISIBLE
        binding.contentContainer?.isEnabled = false

        // Анимация появления
        val fadeIn = AlphaAnimation(0f, 1f)
        fadeIn.duration = 300
        binding.imageOverlayContainer?.startAnimation(fadeIn)

        // Блокируем прокрутку
        binding.contentContainer?.isScrollContainer = false
    }

    private fun hideImageOverlay() {
        // Анимация исчезновения
        val fadeOut = AlphaAnimation(1f, 0f)
        fadeOut.duration = 200
        binding.imageOverlayContainer?.startAnimation(fadeOut)

        // Скрываем оверлей после анимации
        binding.imageOverlayContainer?.postDelayed({
            binding.imageOverlayContainer?.visibility = View.GONE
            binding.contentContainer?.isEnabled = true
            binding.contentContainer?.isScrollContainer = true
        }, 200)
    }

    private fun selectCurrentProductAndCloseOverlay() {
        if (allDetections.isNotEmpty() && currentDetectionIndex < allDetections.size) {
            val detection = allDetections[currentDetectionIndex]
            currentSelectedProductName = detection.label

            // Заполняем форму данными текущего продукта
            fillNutritionFieldsInForm(currentSelectedProductName)

            // Закрываем оверлей
            hideImageOverlay()

            Toast.makeText(
                this,
                "Выбран продукт: $currentSelectedProductName",
                Toast.LENGTH_SHORT
            ).show()

            // Не показываем диалог здесь - он будет показан после сохранения
        } else {
            hideImageOverlay()
        }
    }

    private fun checkForContinueAfterSave() {
        if (allDetections.isNotEmpty() && currentDetectionIndex < allDetections.size - 1) {
            // Если есть еще продукты и мы не на последнем
            showContinueDialog()
        } else {
            // Если больше нет продуктов или мы на последнем - выходим
            finish()
        }
    }
    private fun showContinueDialog() {
        AlertDialog.Builder(this)
            .setTitle("Добавить еще продукт?")
            .setMessage("На изображении найдены еще продукты. Хотите добавить следующий?")
            .setPositiveButton("Да, добавить следующий") { _, _ ->
                // Показываем следующий продукт в оверлее
                showNextProductInOverlay()
                showImageOverlay()
            }
            .setNegativeButton("Нет, закончить") { _, _ ->
                // Возвращаемся на предыдущую активность
                finish()
            }
            .setCancelable(false)
            .show()
    }

    private fun updateOverlayUI() {
        if (allDetections.isEmpty()) return

        val detection = allDetections[currentDetectionIndex]
        val confidencePercent = (detection.confidence * 100).toInt()

        // Обновляем информацию о продукте в формате "Продукт 1 из 3 - Название"
        val counterText = "Продукт ${currentDetectionIndex + 1} из ${allDetections.size} - ${detection.label}"
        binding.tvProductInfo?.text = counterText

        // Показываем изображение с bounding box
        val annotatedBitmap = drawSquareDetectionForOverlay(originalBitmap, detection)
        showImageInOverlay(annotatedBitmap)

        // Обновляем состояние кнопок навигации
        updateOverlayNavigationButtons()
    }

    private fun drawSquareDetectionForOverlay(bitmap: Bitmap, detection: Detection): Bitmap {
        // Создаем квадратную область для обрезки
        val box = detection.box
        val cropSize = kotlin.math.max(box.width(), box.height())

        // Центрируем область обрезки
        val centerX = box.centerX()
        val centerY = box.centerY()
        val halfSize = cropSize / 2

        val cropRect = Rect(
            kotlin.math.max(0, (centerX - halfSize).toInt()),
            kotlin.math.max(0, (centerY - halfSize).toInt()),
            kotlin.math.min(bitmap.width, (centerX + halfSize).toInt()),
            kotlin.math.min(bitmap.height, (centerY + halfSize).toInt())
        )

        // Вырезаем квадратную область
        val croppedBitmap = Bitmap.createBitmap(bitmap, cropRect.left, cropRect.top, cropRect.width(), cropRect.height())

        // Создаем квадратный битмап для отображения
        val squareSize = kotlin.math.max(cropRect.width(), cropRect.height())
        val squareBitmap = Bitmap.createBitmap(squareSize, squareSize, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(squareBitmap)

        // Заливаем фон
        canvas.drawColor(Color.WHITE)

        // Масштабируем и центрируем изображение
        val scale = squareSize.toFloat() / kotlin.math.max(croppedBitmap.width, croppedBitmap.height).toFloat()
        val scaledWidth = (croppedBitmap.width * scale).toInt()
        val scaledHeight = (croppedBitmap.height * scale).toInt()
        val offsetX = (squareSize - scaledWidth) / 2
        val offsetY = (squareSize - scaledHeight) / 2

        val scaledBitmap = Bitmap.createScaledBitmap(croppedBitmap, scaledWidth, scaledHeight, true)
        canvas.drawBitmap(scaledBitmap, offsetX.toFloat(), offsetY.toFloat(), null)

        // Рисуем bounding box на квадратном изображении
        val boxPaint = Paint().apply {
            color = Color.GREEN
            style = Paint.Style.STROKE
            strokeWidth = kotlin.math.max(4f, 6f * (squareSize / 1000f))
        }

        // Масштабируем координаты bounding box
        val scaledBox = RectF(
            offsetX.toFloat(),
            offsetY.toFloat(),
            offsetX + scaledWidth.toFloat(),
            offsetY + scaledHeight.toFloat()
        )

        canvas.drawRect(scaledBox, boxPaint)

        // Вычисляем confidencePercent
        val confidencePercent = (detection.confidence * 100).toInt()

        // Добавляем текст с названием
        val label = "${detection.label} ($confidencePercent%)"
        val textPaint = Paint().apply {
            color = Color.RED
            textSize = kotlin.math.max(24f, 32f * (squareSize / 1000f))
            style = Paint.Style.FILL
            isAntiAlias = true
        }

        val textBgPaint = Paint().apply {
            color = Color.argb(200, 0, 0, 0)
            style = Paint.Style.FILL
        }

        val textWidth = textPaint.measureText(label)
        val textHeight = textPaint.textSize

        // Фон для текста
        val textBgRect = RectF(
            scaledBox.left,
            scaledBox.top - textHeight - 10,
            scaledBox.left + textWidth + 20,
            scaledBox.top - 5
        )
        canvas.drawRoundRect(textBgRect, 5f, 5f, textBgPaint)

        // Сам текст
        canvas.drawText(
            label,
            scaledBox.left + 10,
            scaledBox.top - 15,
            textPaint
        )

        return squareBitmap
    }

    private fun showImageInOverlay(bitmap: Bitmap) {
        val tvList = binding.tvList ?: return
        val container = binding.imageSquareContainer

        // Получаем размеры с безопасным вызовом
        val containerWidth = container?.width ?: 0
        val containerHeight = container?.height ?: 0

        if (containerWidth <= 0 || containerHeight <= 0) {
            // Если контейнер еще не измерен, ждем следующего кадра
            container?.post {
                showImageInOverlay(bitmap)
            }
            return
        }

        // Создаем квадратное изображение для контейнера
        val squareSize = kotlin.math.min(containerWidth, containerHeight)
        val previewBitmap = Bitmap.createScaledBitmap(bitmap, squareSize, squareSize, true)

        tvList.text = ""
        tvList.setBackgroundBitmap(previewBitmap)
    }

    private fun showPreviousProductInOverlay() {
        if (allDetections.isEmpty()) return

        currentDetectionIndex--
        if (currentDetectionIndex < 0) {
            currentDetectionIndex = allDetections.size - 1
        }

        updateOverlayUI()
    }

    private fun showNextProductInOverlay() {
        if (allDetections.isEmpty()) return

        currentDetectionIndex++
        if (currentDetectionIndex >= allDetections.size) {
            currentDetectionIndex = 0
        }

        updateOverlayUI()
    }

    private fun updateOverlayNavigationButtons() {
        if (allDetections.isEmpty()) {
            binding.btnPrevProduct?.visibility = View.GONE
            binding.btnNextProduct?.visibility = View.GONE
            binding.btnSelectProduct?.isEnabled = false
        } else {
            binding.btnSelectProduct?.isEnabled = true

            // Показываем стрелки если есть больше 1 продукта
            if (allDetections.size > 1) {
                binding.btnPrevProduct?.visibility = View.VISIBLE
                binding.btnNextProduct?.visibility = View.VISIBLE
            } else {
                binding.btnPrevProduct?.visibility = View.GONE
                binding.btnNextProduct?.visibility = View.GONE
            }
        }
    }

    private fun fillNutritionFieldsInForm(foodName: String) {
        Log.d(TAG, "Заполнение полей формы для продукта: $foodName")

        // Устанавливаем название продукта
        binding.edName?.setText(foodName)

        // Ищем питательные значения
        val nutrition = findNutritionInDatabase(foodName)

        if (nutrition != null) {
            // Нашли в локальной базе
            binding.edKalories?.setText(nutrition.calories.toString())
            binding.edProteins?.setText(nutrition.proteins.toString())
            binding.edFats?.setText(nutrition.fats.toString())
            binding.edCarbohydrates?.setText(nutrition.carbs.toString())

            Log.d(TAG, "Заполнены данные из локальной базы для $foodName")
        } else {
            // Не нашли в локальной базе, используем детектор или стандартные значения
            try {
                if (::detector.isInitialized) {
                    val nutritionFromDetector = detector.getNutritionForFood(foodName)
                    binding.edKalories?.setText(nutritionFromDetector.calories.toString())
                    binding.edProteins?.setText(nutritionFromDetector.proteins.toString())
                    binding.edFats?.setText(nutritionFromDetector.fats.toString())
                    binding.edCarbohydrates?.setText(nutritionFromDetector.carbs.toString())

                    Log.d(TAG, "Заполнены данные из детектора для $foodName")
                } else {
                    setDefaultNutritionValues()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Ошибка получения данных из детектора: ${e.message}", e)
                setDefaultNutritionValues()
            }
        }
    }

    // Обновленная функция обработки изображения
    private fun processImageAndShowOverlay(bitmap: Bitmap) {
        Log.d(TAG, "Начало обработки изображения, размер: ${bitmap.width}x${bitmap.height}")

        // Показываем оверлей с сообщением о загрузке
        showImageOverlay()
        binding.tvList?.text = "Обработка изображения нейросетью..."
        binding.tvProductInfo?.text = "Обработка..."

        Thread {
            try {
                if (!::detector.isInitialized) {
                    runOnUiThread {
                        Toast.makeText(this, "Нейросеть не готова", Toast.LENGTH_SHORT).show()
                        hideImageOverlay()
                    }
                    return@Thread
                }

                val detections = detector.detectFoodOnly(bitmap)
                Log.d(TAG, "Найдено объектов: ${detections.size}")

                runOnUiThread {
                    if (detections.isEmpty()) {
                        binding.tvList?.text = "Еда не обнаружена\nПопробуйте другое изображение"
                        binding.tvProductInfo?.text = "Объекты не найдены"
                        Toast.makeText(
                            this,
                            "Не удалось распознать еду. Убедитесь, что еда хорошо видна на фото",
                            Toast.LENGTH_LONG
                        ).show()
                        // Не скрываем оверлей сразу, показываем сообщение
                        return@runOnUiThread
                    }

                    // Сохраняем все детекции
                    allDetections = detections
                    currentDetectionIndex = 0
                    processedImages.clear()

                    Toast.makeText(
                        this,
                        "Найдено ${detections.size} продуктов!",
                        Toast.LENGTH_LONG
                    ).show()

                    // Обновляем UI оверлея
                    updateOverlayUI()

                    // Показываем кнопки навигации
                    updateOverlayNavigationButtons()
                }

            } catch (e: Exception) {
                Log.e(TAG, "Ошибка детекции: ${e.message}", e)
                runOnUiThread {
                    Toast.makeText(
                        this,
                        "Ошибка обработки изображения нейросетью: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                    binding.tvList?.text = "Ошибка обработки\nПопробуйте снова"
                    binding.tvProductInfo?.text = "Ошибка"
                }
            }
        }.start()
    }

// Удалите старую функцию processImageWithDetection или оставьте для совместимости
// но основную логику перенесите в processImageAndShowOverlay

    // =============== ОСТАЛЬНЫЕ ФУНКЦИИ ===============

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

            contentResolver.openInputStream(uri)?.use { inputStream ->
                val options = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }

                BitmapFactory.decodeStream(inputStream, null, options)
                inputStream.close()

                val newInputStream = contentResolver.openInputStream(uri)
                newInputStream?.use { newStream ->
                    val scale = calculateInSampleSize(options, 1024, 1024)
                    val scaledOptions = BitmapFactory.Options().apply {
                        inSampleSize = scale
                        inPreferredConfig = Bitmap.Config.ARGB_8888
                    }
                    bitmap = BitmapFactory.decodeStream(newStream, null, scaledOptions)
                }
            }

            if (bitmap == null) {
                Toast.makeText(this, "Не удалось загрузить изображение", Toast.LENGTH_SHORT).show()
                return
            }

            bitmap = rotateImageIfRequired(bitmap!!, uri)
            originalBitmap = bitmap!!.copy(Bitmap.Config.ARGB_8888, true)

            // Обрабатываем изображение и показываем оверлей
            processImageAndShowOverlay(originalBitmap)

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

    private fun setDefaultNutritionValues() {
        binding.edKalories?.setText("150")
        binding.edProteins?.setText("5")
        binding.edFats?.setText("5")
        binding.edCarbohydrates?.setText("15")
    }

    private fun findNutritionInDatabase(foodName: String): FoodNutrition? {
        val lowerFoodName = foodName.lowercase()

        // 1. Прямое совпадение по русским названиям
        foodNutritionDatabase.forEach { (key, value) ->
            if (key.equals(foodName, ignoreCase = true)) {
                return value
            }
        }

        // 2. Поиск по частичному совпадению
        return when {
            lowerFoodName.contains("яблок") -> foodNutritionDatabase["Яблоко"]
            lowerFoodName.contains("банан") -> foodNutritionDatabase["Банан"]
            lowerFoodName.contains("апельсин") -> foodNutritionDatabase["Апельсин"]
            lowerFoodName.contains("виноград") -> foodNutritionDatabase["Виноград"]
            lowerFoodName.contains("грейпфрут") -> foodNutritionDatabase["Грейпфрут"]
            lowerFoodName.contains("лимон") -> foodNutritionDatabase["Лимон"]
            lowerFoodName.contains("персик") -> foodNutritionDatabase["Персик"]
            lowerFoodName.contains("груш") -> foodNutritionDatabase["Груша"]
            lowerFoodName.contains("клубник") -> foodNutritionDatabase["Клубника"]
            lowerFoodName.contains("арбуз") -> foodNutritionDatabase["Арбуз"]

            lowerFoodName.contains("перец") && lowerFoodName.contains("болгар") -> foodNutritionDatabase["Болгарский перец"]
            lowerFoodName.contains("броккол") -> foodNutritionDatabase["Брокколи"]
            lowerFoodName.contains("морков") -> foodNutritionDatabase["Морковь"]
            lowerFoodName.contains("огурец") -> foodNutritionDatabase["Огурец"]
            lowerFoodName.contains("помидор") -> foodNutritionDatabase["Помидор"]
            lowerFoodName.contains("картофель") && !lowerFoodName.contains("фри") && !lowerFoodName.contains("пюре") -> foodNutritionDatabase["Картофель"]
            lowerFoodName.contains("салат") -> foodNutritionDatabase["Салат"]

            lowerFoodName.contains("хлеб") -> foodNutritionDatabase["Хлеб"]
            lowerFoodName.contains("торт") -> foodNutritionDatabase["Торт"]
            lowerFoodName.contains("печень") -> foodNutritionDatabase["Печенье"]
            lowerFoodName.contains("круассан") -> foodNutritionDatabase["Круассан"]
            lowerFoodName.contains("пончик") || lowerFoodName.contains("донат") -> foodNutritionDatabase["Пончик"]
            lowerFoodName.contains("маффин") -> foodNutritionDatabase["Маффин"]
            lowerFoodName.contains("блин") -> foodNutritionDatabase["Блин"]
            lowerFoodName.contains("вафл") -> foodNutritionDatabase["Вафля"]

            lowerFoodName.contains("сыр") -> foodNutritionDatabase["Сыр"]
            lowerFoodName.contains("пицц") -> foodNutritionDatabase["Пицца"]
            lowerFoodName.contains("гамбургер") || lowerFoodName.contains("бургер") -> foodNutritionDatabase["Гамбургер"]
            (lowerFoodName.contains("картош") || lowerFoodName.contains("картофель")) && lowerFoodName.contains("фри") -> foodNutritionDatabase["Картофель фри"]
            lowerFoodName.contains("паст") || lowerFoodName.contains("макарон") -> foodNutritionDatabase["Паста"]
            lowerFoodName.contains("суши") -> foodNutritionDatabase["Суши"]
            lowerFoodName.contains("яйц") -> foodNutritionDatabase["Яйцо"]

            lowerFoodName.contains("борщ") -> foodNutritionDatabase["Борщ"]
            lowerFoodName.contains("греч") -> foodNutritionDatabase["Гречка"]
            lowerFoodName.contains("котлет") -> foodNutritionDatabase["Котлета"]
            lowerFoodName.contains("пельмен") -> foodNutritionDatabase["Пельмени"]
            lowerFoodName.contains("пюре") && lowerFoodName.contains("картош") -> foodNutritionDatabase["Пюре картофельное"]
            lowerFoodName.contains("молоч") && lowerFoodName.contains("каш") -> foodNutritionDatabase["Молочная каша"]
            lowerFoodName.contains("окрошк") -> foodNutritionDatabase["Окрошка"]
            lowerFoodName.contains("рис") -> foodNutritionDatabase["Рис"]
            lowerFoodName.contains("колбас") -> foodNutritionDatabase["Колбаса"]
            lowerFoodName.contains("суп") && !lowerFoodName.contains("борщ") -> foodNutritionDatabase["Суп"]

            else -> null
        }
    }

    private fun searchFoodInDatabase(query: String) {
        Log.d(TAG, "Поиск продукта: '$query'")

        if (query.isBlank()) {
            Toast.makeText(this, "Введите название для поиска", Toast.LENGTH_SHORT).show()
            return
        }

        val nutrition = findNutritionInDatabase(query)

        if (nutrition != null) {
            binding.edName?.setText(query)
            binding.edKalories?.setText(nutrition.calories.toString())
            binding.edProteins?.setText(nutrition.proteins.toString())
            binding.edFats?.setText(nutrition.fats.toString())
            binding.edCarbohydrates?.setText(nutrition.carbs.toString())

            Toast.makeText(this, "Найден продукт: $query", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            try {
                val productsFlow = database.productDao().getAllProducts()
                val products = try {
                    productsFlow.first()
                } catch (e: Exception) {
                    Log.e(TAG, "Ошибка получения продуктов: ${e.message}")
                    emptyList()
                }

                val filteredProducts = products.filter { product ->
                    product.ProductName.contains(query, ignoreCase = true)
                }

                if (filteredProducts.isEmpty()) {
                    runOnUiThread {
                        Toast.makeText(
                            this@AddProductsActivity,
                            "Продукт '$query' не найден в базе данных",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                } else {
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

    private fun saveCurrentProduct() {
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

        // Сохраняем продукт в базу данных
        lifecycleScope.launch {
            try {
                // ЕСТЬ ЛИ УЖЕ ТАКОЙ ПРОДУКТ В БАЗЕ
                val allProducts = database.productDao().getAllProducts().first()
                val productExists = allProducts.any {
                    it.ProductName.trim().equals(name.trim(), ignoreCase = true)
                }

                if (productExists) {
                    runOnUiThread {
                        Toast.makeText(
                            this@AddProductsActivity,
                            "Продукт '$name' уже есть в базе!",
                            Toast.LENGTH_LONG
                        ).show()
                        Log.d(TAG, "Продукт '$name' не сохранён - уже существует в базе")

                        // Проверяем, нужно ли продолжать
                        if (allDetections.isNotEmpty()) {
                            processedImages.add(currentDetectionIndex)
                            checkForContinueAfterSave()
                        }
                    }
                    return@launch  // не сохраняем дубликат
                }

                // если продукта нет - создаём новый
                val product = Product(
                    id = null,
                    ProductName = name,
                    ProductCalories = calories,
                    ProductProteins = proteins,
                    ProductFats = fats,
                    ProductCarbohydrates = carbs
                )

                database.productDao().insert(product)

                runOnUiThread {
                    // Помечаем продукт как обработанный (для нейросети)
                    if (allDetections.isNotEmpty()) {
                        processedImages.add(currentDetectionIndex)
                    }

                    Toast.makeText(
                        this@AddProductsActivity,
                        "Продукт '$name' сохранён!",
                        Toast.LENGTH_SHORT
                    ).show()
                    Log.d(TAG, "Продукт сохранен: $product")

                    // Проверяем, нужно ли продолжать с другими продуктами
                    checkForContinueAfterSave()
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

    private fun clearFormFields() {
        runOnUiThread {
            binding.edName?.setText("")
            binding.edKalories?.setText("")
            binding.edProteins?.setText("")
            binding.edFats?.setText("")
            binding.edCarbohydrates?.setText("")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
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
