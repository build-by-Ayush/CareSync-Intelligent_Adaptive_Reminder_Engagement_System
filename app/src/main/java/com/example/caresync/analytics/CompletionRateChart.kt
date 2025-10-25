package com.example.caresync.analytics

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.caresync.analytics.domain.CompletionRateData
import com.example.caresync.analytics.viewmodel.DateRange
import com.patrykandpatrick.vico.compose.axis.horizontal.rememberBottomAxis
import com.patrykandpatrick.vico.compose.axis.vertical.rememberStartAxis
import com.patrykandpatrick.vico.compose.chart.Chart
import com.patrykandpatrick.vico.compose.chart.line.lineChart
import com.patrykandpatrick.vico.core.entry.ChartEntryModelProducer
import com.patrykandpatrick.vico.core.entry.entryOf
import java.text.SimpleDateFormat
import java.util.*
import com.patrykandpatrick.vico.compose.component.textComponent
import com.patrykandpatrick.vico.core.axis.AxisItemPlacer

@Composable
fun CompletionRateChart(
    data: List<CompletionRateData>,
    selectedDateRange: DateRange,
    onDateRangeChange: (DateRange) -> Unit,
    modifier: Modifier = Modifier
) {
    // ✅ Aggregate data based on selected range
    val displayData = remember(data, selectedDateRange) {
        aggregateDataForRange(data, selectedDateRange)
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Header
        Text(
            text = "📈 Completion Rate",
            fontSize = 18.sp,
            color = Color.White,
            fontWeight = FontWeight.Bold
        )

        // DATE RANGE SELECTOR
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            DateRange.values().forEach { range ->
                OutlinedButton(
                    onClick = { onDateRangeChange(range) },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = if (selectedDateRange == range)
                            Color(0xFF560154)
                        else
                            Color.Transparent,
                        contentColor = if (selectedDateRange == range)
                            Color.White
                        else
                            Color(0xFFAAAAAA)
                    ),
                    border = ButtonDefaults.outlinedButtonBorder.copy(
                        width = 1.dp,
                        brush = androidx.compose.ui.graphics.SolidColor(
                            if (selectedDateRange == range)
                                Color(0xFF560154)
                            else
                                Color(0xFF808080)
                        )
                    )
                ) {
                    Text(
                        text = range.label,
                        fontSize = 14.sp,
                        fontWeight = if (selectedDateRange == range)
                            FontWeight.Bold
                        else
                            FontWeight.Normal
                    )
                }
            }
        }

        // Chart card
        Card(
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF221F2C)
            ),
            shape = RoundedCornerShape(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(250.dp)
                    .padding(16.dp)
            ) {
                if (displayData.isEmpty()) {
                    Text(
                        text = "No data available",
                        color = Color(0xFFAAAAAA),
                        fontSize = 14.sp,
                        modifier = Modifier.align(androidx.compose.ui.Alignment.Center)
                    )
                } else {
                    CompletionRateLineChart(data = displayData)
                }
            }
        }
    }
}

/**
 * Aggregate data points for better visualization on longer time ranges
 * - 7 days: Show all days (7 points)
 * - 30 days: Average every 7 days (~4 points)
 * - 90 days: Average every 18 days (~5 points)
 */
private fun aggregateDataForRange(data: List<CompletionRateData>, range: DateRange): List<CompletionRateData> {
    if (data.isEmpty()) return emptyList()

    val bucketSize = when (range) {
        DateRange.SEVEN_DAYS -> 1   // Show all days
        DateRange.THIRTY_DAYS -> 7  // 4-5 points
        DateRange.NINETY_DAYS -> 18 // 5 points
    }

    if (bucketSize == 1) return data  // No aggregation for 7 days

    // Group data into buckets and average
    val aggregated = mutableListOf<CompletionRateData>()

    for (i in data.indices step bucketSize) {
        val bucket = data.subList(i, minOf(i + bucketSize, data.size))
        val avgRate = bucket.map { it.completionRate }.average().toFloat()
        val middleDate = bucket[bucket.size / 2].date  // Use middle date of bucket

        aggregated.add(
            CompletionRateData(
                date = middleDate,
                completionRate = avgRate
            )
        )
    }

    return aggregated
}

@Composable
private fun CompletionRateLineChart(data: List<CompletionRateData>) {
    val chartEntries = data.mapIndexed { index, item ->
        // ✅ MULTIPLY BY 100 FOR PERCENTAGE (0-100 scale)
        entryOf(index.toFloat(), item.completionRate * 100)
    }

    val chartEntryModelProducer = remember(data) {
        ChartEntryModelProducer(chartEntries)
    }

    val dateFormatter = SimpleDateFormat("MM/dd", Locale.getDefault())

    Chart(
        chart = lineChart(
            lines = listOf(
                com.patrykandpatrick.vico.core.chart.line.LineChart.LineSpec(
                    // ✅ NEON GREEN with slight glow
                    lineColor = android.graphics.Color.parseColor("#00FF88"),
                    lineThicknessDp = 3f,
                )
            )
        ),
        chartModelProducer = chartEntryModelProducer,
        startAxis = rememberStartAxis(
            label = textComponent(
                color = Color.White,
                textSize = 12.sp
            ),
            valueFormatter = { value, _ ->
                "${value.toInt()}%"
            },
            // ✅ Force Y-axis to multiples of 10
            itemPlacer = remember {
                AxisItemPlacer.Vertical.default(
                    maxItemCount = 11  // 0, 10, 20, ... 100 (11 values)
                )
            }
        ),
        bottomAxis = rememberBottomAxis(
            label = textComponent(
                color = Color.White,
                textSize = 10.sp
            ),
            valueFormatter = { value, _ ->
                val index = value.toInt()
                if (index in data.indices) {
                    dateFormatter.format(Date(data[index].date))
                } else {
                    ""
                }
            }
        ),
        modifier = Modifier.fillMaxSize()
    )
}
