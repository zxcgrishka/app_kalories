package com.example.test2.ml

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import android.util.Log
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import kotlin.math.max
import kotlin.math.min

data class Detection(
    val label: String,
    val confidence: Float,
    val box: RectF
)

data class FoodNutrition(
    val calories: Int,
    val proteins: Int,
    val fats: Int,
    val carbs: Int
)

class YoloDetector(context: Context) {

    // ==== ПАРАМЕТРЫ ДЛЯ ВАШЕЙ МОДЕЛИ С 43 КЛАССАМИ ====
    private val INPUT_SIZE = 640            // 640x640
    private val NUM_CLASSES = 43            // 43 класса как у вас
    private val OUTPUT_CHANNELS = 4 + NUM_CLASSES // 4 (bbox) + 43 (классы) = 47
    private val NUM_BOXES = 8400           // 8400 предсказаний

    private val interpreter: Interpreter
    private val labels: List<String>

    // Карта калорий для всех 43 классов (калории на 100г)
    private val foodCaloriesMap = mapOf(
        // Фрукты
        "Яблоко" to 52,
        "Банан" to 89,
        "Апельсин" to 47,
        "Виноград" to 69,
        "Грейпфрут" to 42,
        "Лимон" to 29,
        "Персик" to 39,
        "Груша" to 57,
        "Клубника" to 32,
        "Арбуз" to 30,

        // Овощи
        "Болгарский перец" to 31,
        "Брокколи" to 34,
        "Морковь" to 41,
        "Огурец" to 15,
        "Помидор" to 18,
        "Картофель" to 77,
        "Салат" to 15,

        // Мучное и выпечка
        "Хлеб" to 265,
        "Торт" to 371,
        "Печенье" to 500,
        "Круассан" to 406,
        "Пончик" to 452,
        "Маффин" to 425,
        "Блин" to 227,
        "Вафля" to 291,

        // Основные блюда
        "Сыр" to 402,
        "Пицца" to 266,
        "Гамбургер" to 295,
        "Картофель фри" to 312,
        "Паста" to 131,
        "Суши" to 150,
        "Борщ" to 54,
        "Суп" to 60,

        // Прочее
        "Яйцо" to 155,
        "Пельмени" to 248,
        "Котлета" to 230,
        "Колбаса" to 336,
        "Молочная каша" to 93,
        "Гречка" to 92,
        "Рис" to 130,
        "Макароны" to 131,
        "Пюре картофельное" to 83,
        "Окрошка" to 64
    )

    // Карта БЖУ для всех 43 классов
    private val foodNutritionMap = mapOf(
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

        // Основные блюда
        "Сыр" to FoodNutrition(402, 25, 33, 1),
        "Пицца" to FoodNutrition(266, 11, 10, 33),
        "Гамбургер" to FoodNutrition(295, 17, 14, 24),
        "Картофель фри" to FoodNutrition(312, 3, 15, 41),
        "Паста" to FoodNutrition(131, 5, 1, 25),
        "Суши" to FoodNutrition(150, 5, 1, 30),
        "Борщ" to FoodNutrition(54, 2, 2, 8),
        "Суп" to FoodNutrition(60, 3, 2, 8),

        // Прочее
        "Яйцо" to FoodNutrition(155, 13, 11, 1),
        "Пельмени" to FoodNutrition(248, 12, 10, 29),
        "Котлета" to FoodNutrition(230, 15, 16, 8),
        "Колбаса" to FoodNutrition(336, 16, 29, 1),
        "Молочная каша" to FoodNutrition(93, 3, 3, 15),
        "Гречка" to FoodNutrition(92, 3, 1, 20),
        "Рис" to FoodNutrition(130, 3, 0, 28),
        "Макароны" to FoodNutrition(131, 5, 1, 25),
        "Пюре картофельное" to FoodNutrition(83, 2, 3, 15),
        "Окрошка" to FoodNutrition(64, 3, 2, 9)
    )

    companion object {
        private const val TAG = "YoloDetector"
    }

    init {
        Log.d(TAG, "Инициализация YoloDetector для 43 классов...")

        // ===== ЗАГРУЗКА МОДЕЛИ =====
        try {
            val model = FileUtil.loadMappedFile(context, "yolov8s_float32.tflite")
            Log.d(TAG, "Модель загружена, размер: ${model.capacity()} байт")

            val options = Interpreter.Options().apply {
                setNumThreads(4)
            }
            interpreter = Interpreter(model, options)

            // Логируем форму выхода для контроля
            val outShape = interpreter.getOutputTensor(0).shape()
            Log.d(TAG, "Форма выхода модели: ${outShape.contentToString()}")

            Log.d(TAG, "Интерпретатор создан успешно")
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка загрузки модели: ${e.message}", e)
            throw e
        }

        // ===== ЗАГРУЗКА МЕТКОВ =====
        try {
            labels = FileUtil.loadLabels(context, "labels.txt")
            Log.d(TAG, "Загружено ${labels.size} меток")

            // Проверяем, что метки соответствуют 43 классам
            if (labels.size != NUM_CLASSES) {
                Log.w(TAG, "Ожидалось $NUM_CLASSES классов, но загружено ${labels.size}")
            }

            // Логируем все метки для отладки
            labels.forEachIndexed { index, label ->
                Log.d(TAG, "Класс $index: $label")
            }

        } catch (e: Exception) {
            Log.e(TAG, "Ошибка загрузки labels.txt: ${e.message}", e)
            throw e
        }
    }

    // ========================================================================
    //                              ОСНОВНАЯ ДЕТЕКЦИЯ
    // ========================================================================
    fun detect(bitmap: Bitmap): List<Detection> {
        Log.d(TAG, "Начало детекции, размер входа: ${bitmap.width}x${bitmap.height}")

        // 1) Ресайз под вход модели
        val resized = Bitmap.createScaledBitmap(bitmap, INPUT_SIZE, INPUT_SIZE, true)

        // 2) Подготовка входного массива [1, 640, 640, 3], нормализация 0..1
        val input = Array(1) {
            Array(INPUT_SIZE) {
                Array(INPUT_SIZE) {
                    FloatArray(3)
                }
            }
        }

        for (y in 0 until INPUT_SIZE) {
            for (x in 0 until INPUT_SIZE) {
                val px = resized.getPixel(x, y)
                val r = ((px shr 16) and 0xFF) / 255.0f
                val g = ((px shr 8) and 0xFF) / 255.0f
                val b = (px and 0xFF) / 255.0f
                input[0][y][x][0] = r
                input[0][y][x][1] = g
                input[0][y][x][2] = b
            }
        }

        Log.d(TAG, "Входные данные подготовлены")

        // 3) Выходной массив: [1, 47, 8400] (4 + 43 класса)
        val output = Array(1) {
            Array(OUTPUT_CHANNELS) {
                FloatArray(NUM_BOXES)
            }
        }

        // 4) Инференс
        try {
            Log.d(TAG, "Запуск interpreter.run ...")
            interpreter.run(input, output)
            Log.d(TAG, "Инференс завершён")
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка при вызове interpreter.run: ${e.message}", e)
            throw e
        }

        // 5) Постпроцессинг для YOLOv8 с 43 классами
        val rawDetections = processYoloV8Output(output, bitmap)
        Log.d(TAG, "Всего сырых детекций: ${rawDetections.size}")

        // 6) NMS
        val finalDetections = applyNMS(rawDetections, iouThreshold = 0.45f)
        Log.d(TAG, "После NMS осталось: ${finalDetections.size} объектов")

        return finalDetections
    }

    /**
     * Парсинг выхода YOLOv8 для модели с 43 классами:
     * Формат: [1, 47, 8400] где 47 = 4 (bbox) + 43 (классы)
     * 0: cx (center x)
     * 1: cy (center y)
     * 2: w (width)
     * 3: h (height)
     * 4-46: вероятности 43 классов
     */
    private fun processYoloV8Output(
        output: Array<Array<FloatArray>>,
        bitmap: Bitmap
    ): List<Detection> {

        val detections = mutableListOf<Detection>()
        var objectsFound = 0

        val scoreThreshold = 0.25f   // порог уверенности

        Log.d(TAG, "Обработка вывода YOLOv8 с ${NUM_CLASSES} классами")

        for (i in 0 until NUM_BOXES) {
            try {
                val cx = output[0][0][i]   // center x
                val cy = output[0][1][i]   // center y
                val w  = output[0][2][i]   // width
                val h  = output[0][3][i]   // height

                // Ищем класс с максимальной вероятностью
                var bestClass = -1
                var bestScore = 0f

                for (c in 0 until NUM_CLASSES) {
                    val classProb = output[0][4 + c][i]  // каналы 4-46
                    if (classProb > bestScore) {
                        bestScore = classProb
                        bestClass = c
                    }
                }

                if (bestClass == -1 || bestScore < scoreThreshold) continue
                objectsFound++

                val label = labels.getOrNull(bestClass) ?: "class_$bestClass"
                val finalScore = bestScore

                if (finalScore > 0.5f) {
                    Log.d(
                        TAG,
                        "Обнаружен: '$label' (класс $bestClass) уверенность=${"%.2f".format(finalScore)}"
                    )
                }

                // Конвертируем нормализованные координаты в пиксели
                val x1 = (cx - w / 2f) * bitmap.width
                val y1 = (cy - h / 2f) * bitmap.height
                val x2 = (cx + w / 2f) * bitmap.width
                val y2 = (cy + h / 2f) * bitmap.height

                val left = max(0f, x1)
                val top = max(0f, y1)
                val right = min(bitmap.width.toFloat(), x2)
                val bottom = min(bitmap.height.toFloat(), y2)

                if (right <= left || bottom <= top) continue

                detections.add(
                    Detection(
                        label = label,
                        confidence = finalScore,
                        box = RectF(left, top, right, bottom)
                    )
                )

            } catch (e: Exception) {
                Log.e(TAG, "Ошибка при обработке бокса $i: ${e.message}")
            }
        }

        Log.d(TAG, "Анализ завершён: всего объектов: $objectsFound")
        return detections
    }

    // ========================================================================
    //                                УТИЛИТЫ
    // ========================================================================

    private fun applyNMS(detections: List<Detection>, iouThreshold: Float): List<Detection> {
        if (detections.isEmpty()) return emptyList()

        val sorted = detections.sortedByDescending { it.confidence }
        val selected = mutableListOf<Detection>()
        val suppressed = BooleanArray(sorted.size)

        for (i in sorted.indices) {
            if (suppressed[i]) continue
            val detI = sorted[i]
            selected.add(detI)

            for (j in i + 1 until sorted.size) {
                if (suppressed[j]) continue
                val detJ = sorted[j]
                val iou = calculateIoU(detI.box, detJ.box)
                if (iou > iouThreshold) {
                    suppressed[j] = true
                }
            }
        }
        return selected
    }

    private fun calculateIoU(a: RectF, b: RectF): Float {
        val left = max(a.left, b.left)
        val top = max(a.top, b.top)
        val right = min(a.right, b.right)
        val bottom = min(a.bottom, b.bottom)

        if (right <= left || bottom <= top) return 0f

        val interArea = (right - left) * (bottom - top)
        val areaA = (a.right - a.left) * (a.bottom - a.top)
        val areaB = (b.right - b.left) * (b.bottom - b.top)

        return interArea / (areaA + areaB - interArea)
    }

    // ========================================================================
    //                     ПУБЛИЧНЫЕ ХЕЛПЕРЫ ПОД ЕДУ/КАЛОРИИ
    // ========================================================================

    fun detectFoodOnly(bitmap: Bitmap): List<Detection> {
        Log.d(TAG, "Запуск detectFoodOnly")
        val all = detect(bitmap)

        // Все 43 класса - это еда, так что возвращаем всё
        Log.d(TAG, "Обнаружено объектов: ${all.size}")
        all.forEach {
            Log.d(TAG, "  ${it.label} (${(it.confidence * 100).toInt()}%)")
        }

        return all
    }

    fun getCaloriesForFood(foodName: String, portionSize: Float = 1.0f): Int {
        // Ищем точное совпадение
        val caloriesPer100g = foodCaloriesMap[foodName]
            ?: foodCaloriesMap.entries.firstOrNull {
                foodName.contains(it.key, ignoreCase = true)
            }?.value
            ?: run {
                Log.d(TAG, "Неизвестный продукт для калорий: $foodName, берём 150 ккал по умолчанию")
                150  // среднее значение по умолчанию
            }

        val estimatedWeight = portionSize * 100f
        val calories = (caloriesPer100g * estimatedWeight / 100f).toInt()
        Log.d(TAG, "Калории для '$foodName': $calories (${caloriesPer100g} ккал/100г, порция=$portionSize)")

        return calories
    }

    fun getNutritionForFood(foodName: String): FoodNutrition {
        // Ищем точное совпадение
        val nutrition = foodNutritionMap[foodName]
            ?: foodNutritionMap.entries.firstOrNull {
                foodName.contains(it.key, ignoreCase = true)
            }?.value
            ?: run {
                Log.d(TAG, "Неизвестный продукт для БЖУ: $foodName, берём средние значения")
                FoodNutrition(
                    calories = getCaloriesForFood(foodName),
                    proteins = 5,  // среднее значение
                    fats = 5,      // среднее значение
                    carbs = 15     // среднее значение
                )
            }

        Log.d(TAG, "БЖУ для '$foodName': ${nutrition.calories} ккал, " +
                "Б: ${nutrition.proteins}, Ж: ${nutrition.fats}, У: ${nutrition.carbs}")

        return nutrition
    }

    fun estimatePortionSize(box: RectF, imageWidth: Int, imageHeight: Int): Float {
        val relativeArea = (box.width() / imageWidth) * (box.height() / imageHeight)
        val portion = min(1.0f, relativeArea * 5f)
        Log.d(TAG, "Оценка порции: площадь=$relativeArea, порция=$portion")
        return portion
    }
}
