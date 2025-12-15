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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs

class CalendarFragment : Fragment() {

    private var _binding: FragmentCalendarBinding? = null
    private val binding get() = _binding!!

    private val colorBarTop = Color.parseColor("#E3F3FC")
    private val colorBarBottom = Color.parseColor("#C6FADC")
    private val colorZeroLine = Color.parseColor("#C6FADC")
    private val colorGridLine = Color.parseColor("#E3F3FC")
    private val colorTriangle = Color.parseColor("#C6FADC")
    private val colorGridText = Color.parseColor("#FFFFFF")

    private val strokeWidthZeroLine = 20f
    private val strokeWidthGrid = 2f
    private val triangleHeight = 36f
    private val textSizeDate = 32f
    private val textSizeValue = 28f
    private val textSizePeriod = 48f
    private val textSizeGrid = 42f
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

    private lateinit var selectedStartDate: Date
    private lateinit var selectedEndDate: Date

    private lateinit var userRepository: UserRepository
    private lateinit var dailyMealViewModel: DailyMealViewModel

    private val fullDateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
    private val displayDateFormat = SimpleDateFormat("dd.MM", Locale.getDefault())

    private var colorC5: Int = 0

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCalendarBinding.inflate(inflater, container, false)
        colorC5 = ContextCompat.getColor(requireContext(), R.color.c5)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        userId = getUserIdFromPrefs()

        val database = AppDatabase.getDatabase(requireContext())
        userRepository = UserRepository(
            database,
            NetworkModule.provideMyApiService(requireContext()),
            requireContext()
        )

        dailyMealViewModel = ViewModelProvider(
            requireActivity() as ViewModelStoreOwner,
            DailyMealViewModelFactory(userRepository)
        )[DailyMealViewModel::class.java]

        binding.graphContainer.setBackgroundResource(R.drawable.gradient_background_rotated)

        graphView = CustomGraphView(requireContext())
        binding.graphContainer.removeAllViews()
        binding.graphContainer.addView(graphView)

        setupDatePickers()
        setDefaultPeriod()
        loadDataFromDatabase()
    }

    private fun setupDatePickers() {
        binding.etStartDate.setOnClickListener {
            showDatePickerDialog(true)
        }

        binding.etEndDate.setOnClickListener {
            showDatePickerDialog(false)
        }
    }

    private fun showDatePickerDialog(isStartDate: Boolean) {
        val calendar = Calendar.getInstance()

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

            loadDataFromDatabase()
        }.show(parentFragmentManager, "datePicker")
    }

    private fun setDefaultPeriod() {
        val calendar = Calendar.getInstance()
        selectedEndDate = calendar.time

        calendar.add(Calendar.DAY_OF_YEAR, -6)
        selectedStartDate = calendar.time

        binding.etStartDate.setText(fullDateFormat.format(selectedStartDate))
        binding.etEndDate.setText(fullDateFormat.format(selectedEndDate))
    }

    private fun loadDataFromDatabase() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Устанавливаем startDate на начало дня
                val startCalendar = Calendar.getInstance()
                startCalendar.time = selectedStartDate
                startCalendar.set(Calendar.HOUR_OF_DAY, 0)
                startCalendar.set(Calendar.MINUTE, 0)
                startCalendar.set(Calendar.SECOND, 0)
                startCalendar.set(Calendar.MILLISECOND, 0)
                val startDateBeginningOfDay = startCalendar.time

                // Устанавливаем endDate на конец дня
                val endCalendar = Calendar.getInstance()
                endCalendar.time = selectedEndDate
                endCalendar.set(Calendar.HOUR_OF_DAY, 23)
                endCalendar.set(Calendar.MINUTE, 59)
                endCalendar.set(Calendar.SECOND, 59)
                endCalendar.set(Calendar.MILLISECOND, 999)
                val endDateEndOfDay = endCalendar.time

                val dailyMeals = userRepository.getDailyMealsByPeriod(
                    userId,
                    startDateBeginningOfDay,
                    endDateEndOfDay
                )

                val groupedData = groupDailyMealsByDate(dailyMeals)
                val processedData = processDataForDisplay(
                    groupedData,
                    selectedStartDate,
                    selectedEndDate
                )

                dataPoints.clear()

                withContext(Dispatchers.Main) {
                    processedData.forEach { (date, calories) ->
                        if (calories > 0) {
                            addDataPoint(date, calories.toFloat())
                        }
                    }

                    graphView.invalidate()
                }

            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    dataPoints.clear()
                    graphView.invalidate()
                }
            }
        }
    }

    private fun groupDailyMealsByDate(dailyMeals: List<DailyMeal>): Map<String, Int> {
        val result = mutableMapOf<String, Int>()

        dailyMeals.forEach { dailyMeal ->
            val dateKey = displayDateFormat.format(dailyMeal.date)
            result[dateKey] = (result[dateKey] ?: 0) + dailyMeal.totalCalories
        }

        return result
    }

    private fun processDataForDisplay(
        groupedData: Map<String, Int>,
        startDate: Date,
        endDate: Date
    ): Map<String, Int> {
        if (groupedData.isEmpty()) return emptyMap()

        val datesWithData = groupedData.entries
            .filter { it.value > 0 }
            .sortedBy { parseDateString(it.key) }

        val daysWithDataCount = datesWithData.size

        return if (daysWithDataCount <= maxColumns) {
            datesWithData.associate { it.key to it.value }
        } else {
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

        for (i in 0 until intervalCount) {
            val startIndex = (i * entriesPerInterval).toInt()
            val endIndex = minOf(((i + 1) * entriesPerInterval).toInt() - 1, totalEntries - 1)

            if (startIndex > endIndex) continue

            val intervalEntries = sortedEntries.subList(startIndex, endIndex + 1)

            val sum = intervalEntries.sumOf { it.value }
            val avgCalories = if (intervalEntries.isNotEmpty()) sum / intervalEntries.size else 0

            val closestDateEntry = intervalEntries.minByOrNull {
                abs(it.value - avgCalories)
            }

            if (closestDateEntry != null && avgCalories > 0) {
                result[closestDateEntry.key] = avgCalories
            }
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
            color = this@CalendarFragment.colorC5
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
            textSize = textSizeGrid
            isAntiAlias = true
        }

        private val paintPeriod = Paint().apply {
            color = this@CalendarFragment.colorC5
            textSize = textSizePeriod
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            isAntiAlias = true
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)

            try {
                val typeface = ResourcesCompat.getFont(context, R.font.khula_light)
                paintGridText.typeface = typeface
            } catch (e: Exception) {
                paintGridText.typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
            }

            if (dataPoints.isEmpty()) {
                val emptyTextPaint = Paint().apply {
                    textSize = textSizeEmptyMessage
                    color = this@CalendarFragment.colorC5
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

            displaySelectedPeriod(canvas, width)
            drawGridLinesText(canvas, width, height, maxValueScaled, scaleValue)
            drawGridLines(canvas, width, height, maxValueScaled, scaleValue)
            drawZeroLine(canvas, width, height)
            drawColumns(canvas, width, height, graphWidth, scaleValue)
        }

        private fun displaySelectedPeriod(canvas: Canvas, width: Float) {
            val startStr = fullDateFormat.format(selectedStartDate)
            val endStr = fullDateFormat.format(selectedEndDate)

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

            val totalWidthNeeded = totalPositions * barWidth + (totalPositions - 1) * barSpacing
            val startX = marginLeft + (graphWidth - totalWidthNeeded) / 2

            val zeroY = height - marginBottom
            val triangleStartY = zeroY + strokeWidthZeroLine / 2

            val offsetX = if (actualCount < maxColumns) {
                val emptySpaces = maxColumns - actualCount
                (emptySpaces * (barWidth + barSpacing)) / 2
            } else {
                0f
            }

            var currentX = startX + barWidth + barSpacing + offsetX

            val entries = dataPoints.entries.toList()

            for (i in 0 until minOf(actualCount, maxColumns)) {
                val entry = entries[i]
                val date = entry.key
                val value = entry.value

                val barHeight = value * scaleValue
                val barTop = zeroY - barHeight
                val columnCenterX = currentX + barWidth / 2

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

                    val valueY = barTop + valueTextOffsetOnColumn
                    canvas.drawText(
                        value.toInt().toString(),
                        columnCenterX,
                        valueY,
                        paintText.apply {
                            textSize = textSizeValue
                            color = this@CalendarFragment.colorC5
                        }
                    )

                    val trianglePath = Path()
                    trianglePath.moveTo(currentX, triangleStartY)
                    trianglePath.lineTo(currentX + barWidth, triangleStartY)
                    trianglePath.lineTo(columnCenterX, triangleStartY + triangleHeight)
                    trianglePath.close()

                    canvas.drawPath(trianglePath, paintTriangle)

                    val dateY = triangleStartY + triangleHeight + dateOffsetBelowTriangle
                    canvas.drawText(
                        date,
                        columnCenterX,
                        dateY,
                        paintText.apply {
                            textSize = textSizeDate
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

class DailyMealViewModelFactory(private val repository: UserRepository) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(DailyMealViewModel::class.java)) {
            return DailyMealViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

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