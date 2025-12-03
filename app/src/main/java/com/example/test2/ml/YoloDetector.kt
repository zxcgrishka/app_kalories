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

class YoloDetector(context: Context) {

    // ==== ПАРАМЕТРЫ МОДЕЛИ ====
    private val INPUT_SIZE = 640          // 640x640
    private val NUM_CLASSES = 80          // COCO
    private val OUTPUT_CHANNELS = 84      // 4 (bbox) + 80 (classes)
    private val NUM_BOXES = 8400          // 8400 боксов

    private val interpreter: Interpreter
    private val labels: List<String>

    // Классы, которые считаем «едой»
    private val FOOD_CLASSES = setOf(
        "apple","banana","orange","broccoli","carrot",
        "hot dog","pizza","donut","cake","sandwich",
        "bowl","cup","fork","knife","spoon","bottle","wine glass",
        "dining table","chair"
    )

    companion object {
        private const val TAG = "YoloDetector"
    }

    init {
        Log.d(TAG, "Инициализация YoloDetector...")

        // ===== ЗАГРУЗКА МОДЕЛИ =====
        try {
            val model = FileUtil.loadMappedFile(context, "yolov8s_float32.tflite")
            Log.d(TAG, "Модель загружена, размер: ${model.capacity()} байт")

            val options = Interpreter.Options().apply {
                setNumThreads(4)
            }
            interpreter = Interpreter(model, options)
            Log.d(TAG, "Интерпретатор создан успешно")
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка загрузки модели: ${e.message}", e)
            throw e
        }

        // ===== ЗАГРУЗКА МЕТКОВ =====
        try {
            labels = FileUtil.loadLabels(context, "labels.txt")
            Log.d(TAG, "Загружено ${labels.size} меток")

            val foodLabels = labels.filter { isFood(it) }
            Log.d(TAG, "Метки еды в labels.txt: ${foodLabels.joinToString()}")

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

        // 3) Выходной массив: [1, 84, 8400]
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

        // 5) Постпроцессинг YOLOv8
        val rawDetections = processYoloV8Output(output, bitmap)
        Log.d(TAG, "Всего сырых детекций: ${rawDetections.size}")

        // 6) NMS
        val finalDetections = applyNMS(rawDetections, iouThreshold = 0.45f)
        Log.d(TAG, "После NMS осталось: ${finalDetections.size} объектов")

        return finalDetections
    }

    /**
     * Парсинг выхода YOLOv8.
     *
     * ВАЖНО:
     * ДЛЯ YOLOv8 НЕТ ОТДЕЛЬНОГО OBJECTNESS-КАНАЛА.
     * Формат: [x, y, w, h, class0, class1, ..., class79]
     * Т.е. всего 4 + NUM_CLASSES = 84 каналов.
     */
    private fun processYoloV8Output(
        output: Array<Array<FloatArray>>,
        bitmap: Bitmap
    ): List<Detection> {

        val detections = mutableListOf<Detection>()
        var objectsFound = 0
        var foodFound = 0

        val scoreThreshold = 0.25f   // порог уверенности
        val step = 1                 // какое количество боксов пропускать (1 = все)

        Log.d(TAG, "Обработка вывода YOLOv8: channels=$OUTPUT_CHANNELS, boxes=$NUM_BOXES")

        // идём по всем 8400 боксам
        for (i in 0 until NUM_BOXES step step) {
            try {
                val cx = output[0][0][i]   // center x (в норм. координатах)
                val cy = output[0][1][i]   // center y
                val w  = output[0][2][i]   // width
                val h  = output[0][3][i]   // height

                // ===== ВАЖНО: НЕТ objectness =====
                // Каналы 4..83 — это просто классы.

                var bestClass = -1
                var bestScore = 0f

                for (c in 0 until NUM_CLASSES) {
                    val classProb = output[0][4 + c][i]
                    if (classProb > bestScore) {
                        bestScore = classProb
                        bestClass = c
                    }
                }

                if (bestClass == -1) continue

                val finalScore = bestScore  // никакого obj * class

                if (finalScore < scoreThreshold) continue
                objectsFound++

                val label = labels.getOrNull(bestClass) ?: "class_$bestClass"

                // Для дебага логируем сильные боксы
                if (finalScore > 0.5f) {
                    Log.d(
                        TAG,
                        "Box $i: '$label' (class $bestClass) score=${"%.2f".format(finalScore)}"
                    )
                }

                // Преобразуем нормализованные координаты в пиксели
                val x1 = (cx - w / 2f) * bitmap.width
                val y1 = (cy - h / 2f) * bitmap.height
                val x2 = (cx + w / 2f) * bitmap.width
                val y2 = (cy + h / 2f) * bitmap.height

                val left = max(0f, x1)
                val top = max(0f, y1)
                val right = min(bitmap.width.toFloat(), x2)
                val bottom = min(bitmap.height.toFloat(), y2)

                if (right <= left || bottom <= top) continue

                val det = Detection(
                    label = label,
                    confidence = finalScore,
                    box = RectF(left, top, right, bottom)
                )
                detections.add(det)

                if (isFood(label)) {
                    foodFound++
                }

            } catch (e: Exception) {
                Log.e(TAG, "Ошибка при обработке бокса $i: ${e.message}")
            }
        }

        Log.d(TAG, "Анализ завершён: всего объектов: $objectsFound, из них еды (по меткам): $foodFound")
        return detections
    }

    // ========================================================================
    //                                УТИЛИТЫ
    // ========================================================================

    private fun isFood(label: String): Boolean {
        val lower = label.lowercase().trim()

        // точное совпадение
        if (FOOD_CLASSES.any { it.lowercase() == lower }) return true

        // частичное совпадение
        if (FOOD_CLASSES.any { food ->
                lower.contains(food.lowercase()) || food.lowercase().contains(lower)
            }) return true

        // по ключевым словам
        val keywords = listOf("fruit","food","meal","eat","drink","snack","vegetable")
        if (keywords.any { lower.contains(it) }) return true

        return false
    }

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
        val onlyFood = all.filter { isFood(it.label) }
        Log.d(TAG, "Обнаружено всего: ${all.size}, из них еды: ${onlyFood.size}")
        if (onlyFood.isEmpty() && all.isNotEmpty()) {
            Log.d(TAG, "Найденные не-едовые объекты:")
            all.forEach {
                Log.d(TAG, "  ${it.label} (${(it.confidence * 100).toInt()}%)")
            }
        }
        return onlyFood
    }

    fun getCaloriesForFood(foodName: String, portionSize: Float = 1.0f): Int {
        val caloriesPer100g = when (foodName.lowercase()) {
            "apple" -> 52
            "banana" -> 89
            "orange" -> 47
            "pizza" -> 266
            "sandwich" -> 250
            "hot dog", "hotdog" -> 290
            "donut" -> 452
            "cake" -> 371
            "broccoli" -> 34
            "carrot" -> 41
            "bottle" -> 0
            "cup" -> 0
            "bowl" -> 0
            "fork", "knife", "spoon" -> 0
            "wine glass" -> 120
            else -> {
                Log.d(TAG, "Неизвестный продукт для калорий: $foodName, берём 100 ккал по умолчанию")
                100
            }
        }

        val estimatedWeight = portionSize * 100f
        val calories = (caloriesPer100g * estimatedWeight / 100f).toInt()
        Log.d(TAG, "Калории для '$foodName': $calories (${caloriesPer100g} ккал/100г, порция=$portionSize)")

        return calories
    }

    fun estimatePortionSize(box: RectF, imageWidth: Int, imageHeight: Int): Float {
        val relativeArea = (box.width() / imageWidth) * (box.height() / imageHeight)
        val portion = min(1.0f, relativeArea * 5f)
        Log.d(TAG, "Оценка порции: площадь=$relativeArea, порция=$portion")
        return portion
    }
}
