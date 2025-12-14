package com.example.test2.ui.calendar

import android.content.Context
import android.graphics.*
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStoreOwner
import com.example.test2.R
import com.example.test2.databinding.FragmentCalendarBinding
import com.example.test2.data.AppDatabase
import com.example.test2.data.DailyMeal.DailyMeal
import com.example.test2.data.User.UserRepository
import com.example.test2.network.NetworkModule
import com.example.test2.ui.DailyMealViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs

class CalendarFragment : Fragment() {

    private var _binding: FragmentCalendarBinding? = null
    private val binding get() = _binding!!

    // Цвета для градиента столбцов (сверху вниз)
    private val colorBarTop = Color.parseColor("#E3F3FC")
    private val colorBarBottom = Color.parseColor("#C6FADC")

    // Цвета для дизайна
    private val colorZeroLine = Color.parseColor("#C6FADC")
    private val colorGridLine = Color.parseColor("#E3F3FC")
    private val colorTriangle = Color.parseColor("#C6FADC")
    private val colorGridText = Color.parseColor("#FFFFFF")

    // Настройки
    private val strokeWidthZeroLine = 20f
    private val strokeWidthGrid = 2f
    private val triangleHeight = 36f
    private val textSizeDate = 32f // УВЕЛИЧЕНО с 28f до 32f (даты под столбцами)
    private val textSizeValue = 28f // УВЕЛИЧЕНО с 22f до 28f (значения сверху столбцов)
    private val textSizePeriod = 48f // УВЕЛИЧЕНО с 44f до 48f (период сверху)
    private val textSizeGrid = 42f // УВЕЛИЧЕНО с 36f до 42f (сетка графика)
    private val textSizeEmptyMessage = 36f
    private val marginLeft = 50f
    private val marginRight = 30f
    private val marginTop = 100f
    private val marginBottom = 140f
    private val barSpacing = 10f
    private val gridLineHeight = 20f
    private val maxColumns = 7
    private val gridLinesCount = 3
    private val dateOffsetBelowTriangle = 30f
    private val gridTextOffsetAbove = 25f
    private val valueTextOffsetOnColumn = 35f

    private lateinit var graphView: CustomGraphView
    private val dataPoints = LinkedHashMap<String, Float>()
    private var userId = -1L

    // Для хранения выбранного периода
    private var selectedStartDate: Date? = null
    private var selectedEndDate: Date? = null

    // Используем существующий UserRepository
    private lateinit var userRepository: UserRepository
    private lateinit var dailyMealViewModel: DailyMealViewModel

    // Для выбора периода
    private val fullDateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
    private val displayDateFormat = SimpleDateFormat("dd.MM", Locale.getDefault())

    // Цвет из ресурсов - инициализируем в onCreateView
    private var colorC5: Int = 0

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCalendarBinding.inflate(inflater, container, false)

        // Инициализируем цвет из ресурсов
        colorC5 = ContextCompat.getColor(requireContext(), R.color.c5)

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Получаем ID пользователя
        userId = getUserIdFromPrefs()

        // Инициализируем репозиторий так же как в DailyMealViewModel
        val database = AppDatabase.getDatabase(requireContext())
        userRepository = UserRepository(
            database,
            NetworkModule.provideMyApiService(requireContext()),
            requireContext()
        )

        // Создаем ViewModel с тем же репозиторием
        dailyMealViewModel = ViewModelProvider(
            requireActivity() as ViewModelStoreOwner,
            DailyMealViewModelFactory(userRepository)
        )[DailyMealViewModel::class.java]

        binding.graphContainer.setBackgroundResource(R.drawable.gradient_background_rotated)

        graphView = CustomGraphView(requireContext())
        binding.graphContainer.removeAllViews()
        binding.graphContainer.addView(graphView)

        // Устанавливаем обработчики для выбора дат
        setupDatePickers()

        // По умолчанию загружаем данные за последние 7 дней
        setDefaultPeriod()
        loadDataFromDatabase()
    }

    private fun setupDatePickers() {
        // Обработчик для начала периода
        binding.etStartDate.setOnClickListener {
            showDatePickerDialog(true)
        }

        // Обработчик для конца периода
        binding.etEndDate.setOnClickListener {
            showDatePickerDialog(false)
        }
    }

    private fun showDatePickerDialog(isStartDate: Boolean) {
        val calendar = Calendar.getInstance()
        val currentDate = calendar.time

        DatePickerDialogFragment { year, month, day ->
            calendar.set(year, month, day)
            val selectedDate = calendar.time

            if (isStartDate) {
                selectedStartDate = selectedDate
                binding.etStartDate.setText(fullDateFormat.format(selectedDate))
            } else {
                selectedEndDate = selectedDate
                binding.etEndDate.setText(fullDateFormat.format(selectedDate))
            }

            // Автоматически обновляем график при выборе даты
            loadDataFromDatabase()

        }.show(parentFragmentManager, "datePicker")
    }

    private fun setDefaultPeriod() {
        val calendar = Calendar.getInstance()
        selectedEndDate = calendar.time

        calendar.add(Calendar.DAY_OF_YEAR, -6) // 7 дней назад (включая сегодня)
        selectedStartDate = calendar.time

        binding.etStartDate.setText(fullDateFormat.format(selectedStartDate))
        binding.etEndDate.setText(fullDateFormat.format(selectedEndDate))
    }

    private fun loadDataFromDatabase() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Используем выбранные даты или значения по умолчанию
                val start = selectedStartDate ?: run {
                    val cal = Calendar.getInstance()
                    cal.add(Calendar.DAY_OF_YEAR, -6)
                    cal.time
                }

                val end = selectedEndDate ?: Calendar.getInstance().time

                // Получаем все DailyMeal для пользователя через репозиторий
                val allDailyMeals = getAllDailyMealsForUser(userId)

                // Фильтруем по выбранному периоду
                val dailyMeals = allDailyMeals.filter { dailyMeal ->
                    dailyMeal.date.time in start.time..end.time
                }

                // Группируем по дате и суммируем калории
                val groupedData = groupAndSumCaloriesByDate(dailyMeals)

                // Обрабатываем данные для отображения
                val processedData = processDataForDisplay(groupedData, start, end)

                // Очищаем старые данные
                dataPoints.clear()

                // Добавляем новые данные
                withContext(Dispatchers.Main) {
                    // Добавляем только данные, где есть калории (value > 0)
                    processedData.forEach { (date, calories) ->
                        if (calories > 0) {
                            addDataPoint(date, calories.toFloat())
                        }
                    }

                    graphView.invalidate()
                }

            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun processDataForDisplay(
        groupedData: Map<String, Int>,
        startDate: Date,
        endDate: Date
    ): Map<String, Int> {
        if (groupedData.isEmpty()) return emptyMap()

        // Получаем список дат с данными, отсортированных по дате
        val datesWithData = groupedData.entries
            .filter { it.value > 0 }
            .sortedBy { parseDateString(it.key) }

        // Рассчитываем количество дней с данными
        val daysWithDataCount = datesWithData.size

        return if (daysWithDataCount <= maxColumns) {
            // Если дней с данными <= 7, показываем все дни с данными
            datesWithData.associate { it.key to it.value }
        } else {
            // Если дней с данными > 7, группируем их в интервалы
            groupDataIntoIntervals(datesWithData)
        }
    }

    private fun groupDataIntoIntervals(
        sortedEntries: List<Map.Entry<String, Int>>
    ): Map<String, Int> {
        val result = mutableMapOf<String, Int>()

        val totalEntries = sortedEntries.size
        val intervalCount = maxColumns
        val entriesPerInterval = totalEntries.toFloat() / intervalCount

        // Разбиваем записи с данными на интервалы
        for (i in 0 until intervalCount) {
            val startIndex = (i * entriesPerInterval).toInt()
            val endIndex = minOf(((i + 1) * entriesPerInterval).toInt() - 1, totalEntries - 1)

            if (startIndex > endIndex) continue

            val intervalEntries = sortedEntries.subList(startIndex, endIndex + 1)

            // Рассчитываем среднее значение для интервала
            val sum = intervalEntries.sumOf { it.value }
            val avgCalories = if (intervalEntries.isNotEmpty()) sum / intervalEntries.size else 0

            // Находим дату, значение которой ближе всего к среднему
            val closestDateEntry = intervalEntries.minByOrNull {
                abs(it.value - avgCalories)
            }

            // Используем дату из записи
            if (closestDateEntry != null && avgCalories > 0) {
                result[closestDateEntry.key] = avgCalories
            }
        }

        return result
    }

    // Метод для получения всех DailyMeal для пользователя
    private suspend fun getAllDailyMealsForUser(userId: Long): List<DailyMeal> {
        return try {
            // Используем Flow из репозитория и берем первое значение
            val dailyMealsFlow = userRepository.getTodayDailyMealsByUser(userId)
            dailyMealsFlow.first() // Получаем первое значение из Flow
        } catch (e: Exception) {
            // В случае ошибки возвращаем пустой список
            emptyList()
        }
    }

    private fun groupAndSumCaloriesByDate(dailyMeals: List<DailyMeal>): Map<String, Int> {
        val result = mutableMapOf<String, Int>()

        dailyMeals.forEach { dailyMeal ->
            // Форматируем дату в "dd.MM"
            val dateKey = displayDateFormat.format(dailyMeal.date)

            // Суммируем калории для этой даты
            result[dateKey] = (result[dateKey] ?: 0) + dailyMeal.totalCalories
        }

        return result
    }

    private fun parseDateString(dateString: String): Date {
        return try {
            displayDateFormat.parse(dateString) ?: Date()
        } catch (e: Exception) {
            Date()
        }
    }

    fun addDataPoint(date: String, value: Float) {
        dataPoints[date] = value
        graphView.invalidate()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    inner class CustomGraphView(context: Context) : View(context) {

        private val dataPoints: LinkedHashMap<String, Float>
            get() = this@CalendarFragment.dataPoints

        private val paintBar = Paint().apply {
            style = Paint.Style.FILL
            isAntiAlias = true
        }

        private val paintText = Paint().apply {
            color = this@CalendarFragment.colorC5 // Используем @color/c5
            textSize = 30f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            isAntiAlias = true
        }

        private val paintGrid = Paint().apply {
            color = colorGridLine
            style = Paint.Style.FILL
            isAntiAlias = true
        }

        private val paintZeroLine = Paint().apply {
            color = colorZeroLine
            style = Paint.Style.FILL
            isAntiAlias = true
        }

        private val paintTriangle = Paint().apply {
            color = colorTriangle
            style = Paint.Style.FILL
            isAntiAlias = true
        }

        private val paintGridText = Paint().apply {
            color = colorGridText
            textSize = textSizeGrid // 42f - увеличенный шрифт для сетки
            isAntiAlias = true
        }

        private val paintPeriod = Paint().apply {
            color = this@CalendarFragment.colorC5 // Используем @color/c5
            textSize = textSizePeriod // 48f - увеличенный шрифт
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            isAntiAlias = true
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)

            // Инициализируем шрифт для gridText
            try {
                val typeface = ResourcesCompat.getFont(context, R.font.khula_light)
                paintGridText.typeface = typeface
            } catch (e: Exception) {
                // Используем стандартный шрифт в случае ошибки
                paintGridText.typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
            }

            if (dataPoints.isEmpty()) {
                val emptyTextPaint = Paint().apply {
                    textSize = textSizeEmptyMessage
                    color = this@CalendarFragment.colorC5 // Используем @color/c5
                    textAlign = Paint.Align.CENTER
                    isAntiAlias = true
                }
                canvas.drawText(
                    "Нет данных за выбранный период",
                    width / 2f,
                    height / 2f,
                    emptyTextPaint
                )
                return
            }

            val width = width.toFloat()
            val height = height.toFloat()
            val graphWidth = width - marginLeft - marginRight
            val graphHeight = height - marginTop - marginBottom

            val maxValue = dataPoints.values.maxOrNull() ?: 1f

            val maxValueScaled = maxValue * 1.05f
            val scaleValue = if (maxValueScaled > 0) graphHeight / maxValueScaled else 1f

            // Показываем выбранный период в заголовке
            displaySelectedPeriod(canvas, width)

            drawGridLinesText(canvas, width, height, maxValueScaled, scaleValue)

            drawGridLines(canvas, width, height, maxValueScaled, scaleValue)

            drawZeroLine(canvas, width, height)

            drawColumns(canvas, width, height, graphWidth, scaleValue)
        }

        private fun displaySelectedPeriod(canvas: Canvas, width: Float) {
            val startDate = selectedStartDate ?: return
            val endDate = selectedEndDate ?: return

            val startStr = fullDateFormat.format(startDate)
            val endStr = fullDateFormat.format(endDate)

            canvas.drawText(
                "$startStr - $endStr",
                width / 2f,
                marginTop - 30,
                paintPeriod
            )
        }

        private fun drawGridLinesText(
            canvas: Canvas,
            width: Float,
            height: Float,
            maxValue: Float,
            scaleValue: Float
        ) {
            val maxValueInt = maxValue.toInt()

            for (i in 0 until gridLinesCount) {
                val percentage = i.toFloat() / gridLinesCount
                val value = (maxValueInt * percentage).toInt()

                val y = height - marginBottom - (value * scaleValue)

                if (y < marginTop || y > height - marginBottom) continue

                if (value > 0) {
                    paintGridText.textAlign = Paint.Align.LEFT
                    canvas.drawText(
                        value.toString(),
                        marginLeft + 5,
                        y - gridTextOffsetAbove,
                        paintGridText
                    )

                    paintGridText.textAlign = Paint.Align.RIGHT
                    canvas.drawText(
                        value.toString(),
                        width - marginRight - 5,
                        y - gridTextOffsetAbove,
                        paintGridText
                    )
                }
            }
        }

        private fun drawGridLines(
            canvas: Canvas,
            width: Float,
            height: Float,
            maxValue: Float,
            scaleValue: Float
        ) {
            val maxValueInt = maxValue.toInt()

            for (i in 0 until gridLinesCount) {
                val percentage = i.toFloat() / gridLinesCount
                val value = (maxValueInt * percentage).toInt()

                val y = height - marginBottom - (value * scaleValue)

                if (y < marginTop || y > height - marginBottom) continue

                canvas.drawRect(
                    marginLeft,
                    y - gridLineHeight / 2,
                    width - marginRight,
                    y + gridLineHeight / 2,
                    paintGrid
                )
            }
        }

        private fun drawZeroLine(canvas: Canvas, width: Float, height: Float) {
            val zeroY = height - marginBottom

            if (zeroY < marginTop || zeroY > height) return

            paintGridText.textAlign = Paint.Align.LEFT
            canvas.drawText(
                "0",
                marginLeft + 5,
                zeroY - strokeWidthZeroLine / 2 - gridTextOffsetAbove,
                paintGridText
            )

            paintGridText.textAlign = Paint.Align.RIGHT
            canvas.drawText(
                "0",
                width - marginRight - 5,
                zeroY - strokeWidthZeroLine / 2 - gridTextOffsetAbove,
                paintGridText
            )

            canvas.drawRect(
                marginLeft,
                zeroY - strokeWidthZeroLine / 2,
                width - marginRight,
                zeroY + strokeWidthZeroLine / 2,
                paintZeroLine
            )
        }

        private fun drawColumns(
            canvas: Canvas,
            width: Float,
            height: Float,
            graphWidth: Float,
            scaleValue: Float
        ) {
            if (dataPoints.isEmpty()) return

            val totalPositions = 9
            val actualCount = dataPoints.size

            val barWidth = (graphWidth - (barSpacing * (totalPositions - 1))) / totalPositions

            // Рассчитываем ширину для всех 9 позиций
            val totalWidthNeeded = totalPositions * barWidth + (totalPositions - 1) * barSpacing
            val startX = marginLeft + (graphWidth - totalWidthNeeded) / 2

            val zeroY = height - marginBottom
            val triangleStartY = zeroY + strokeWidthZeroLine / 2

            // Рассчитываем смещение для центрирования
            val offsetX = if (actualCount < maxColumns) {
                val emptySpaces = maxColumns - actualCount
                (emptySpaces * (barWidth + barSpacing)) / 2
            } else {
                0f
            }

            // Начальная позиция для рисования
            var currentX = startX + barWidth + barSpacing + offsetX

            val entries = dataPoints.entries.toList()

            for (i in 0 until minOf(actualCount, maxColumns)) {
                val entry = entries[i]
                val date = entry.key
                val value = entry.value

                val barHeight = value * scaleValue
                val barTop = zeroY - barHeight
                val columnCenterX = currentX + barWidth / 2

                // Рисуем столбец только если есть данные (value > 0)
                if (value > 0 && barTop < zeroY && barTop >= marginTop) {
                    val gradient = LinearGradient(
                        currentX, barTop,
                        currentX, zeroY,
                        colorBarTop,
                        colorBarBottom,
                        Shader.TileMode.CLAMP
                    )
                    paintBar.shader = gradient

                    canvas.drawRect(
                        currentX,
                        barTop,
                        currentX + barWidth,
                        zeroY,
                        paintBar
                    )
                    paintBar.shader = null

                    // Рисуем значение сверху столбца с увеличенным шрифтом
                    val valueY = barTop + valueTextOffsetOnColumn
                    canvas.drawText(
                        value.toInt().toString(),
                        columnCenterX,
                        valueY,
                        paintText.apply {
                            textSize = textSizeValue // 28f - увеличенный шрифт для значений
                            color = this@CalendarFragment.colorC5
                        }
                    )

                    // Рисуем треугольник
                    val trianglePath = Path()
                    trianglePath.moveTo(currentX, triangleStartY)
                    trianglePath.lineTo(currentX + barWidth, triangleStartY)
                    trianglePath.lineTo(columnCenterX, triangleStartY + triangleHeight)
                    trianglePath.close()

                    canvas.drawPath(trianglePath, paintTriangle)

                    // Рисуем дату под треугольником с увеличенным шрифтом
                    val dateY = triangleStartY + triangleHeight + dateOffsetBelowTriangle
                    canvas.drawText(
                        date,
                        columnCenterX,
                        dateY,
                        paintText.apply {
                            textSize = textSizeDate // 32f - увеличенный шрифт для дат
                            color = this@CalendarFragment.colorC5
                        }
                    )
                }

                currentX += barWidth + barSpacing
            }
        }

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val minHeight = 850
            val height = MeasureSpec.getSize(heightMeasureSpec).coerceAtLeast(minHeight)
            setMeasuredDimension(widthMeasureSpec, MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY))
        }
    }

    private fun getUserIdFromPrefs(): Long {
        val sharedPref = requireContext().getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
        return sharedPref.getLong("current_user_id", -1L)
    }
}

// ViewModelFactory с корректной реализацией
class DailyMealViewModelFactory(private val repository: UserRepository) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(DailyMealViewModel::class.java)) {
            return DailyMealViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

// Диалог для выбора даты
class DatePickerDialogFragment(
    private val onDateSelected: (year: Int, month: Int, day: Int) -> Unit
) : androidx.fragment.app.DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): android.app.Dialog {
        val calendar = Calendar.getInstance()
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH)
        val day = calendar.get(Calendar.DAY_OF_MONTH)

        return android.app.DatePickerDialog(
            requireContext(),
            { _, selectedYear, selectedMonth, selectedDay ->
                onDateSelected(selectedYear, selectedMonth, selectedDay)
            },
            year,
            month,
            day
        )
    }
}