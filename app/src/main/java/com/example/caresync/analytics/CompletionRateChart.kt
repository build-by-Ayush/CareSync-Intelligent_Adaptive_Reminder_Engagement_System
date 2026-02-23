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
    // ✅ Only use 7 days data
    val displayData = remember(data) {
        data.takeLast(7)
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "📈 Completion Rate (Last 7 Days)",
            fontSize = 18.sp,
            color = Color.White,
            fontWeight = FontWeight.Bold
        )

        // ✅ REMOVED: Date range selector (only 7 days)
        // Row with buttons removed

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

@Composable
private fun CompletionRateLineChart(data: List<CompletionRateData>) {
    val chartEntries = data.mapIndexed { index, item ->
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
            itemPlacer = remember {
                AxisItemPlacer.Vertical.default(maxItemCount = 11)
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
            },
            itemPlacer = remember {
                AxisItemPlacer.Horizontal.default(spacing = 1)  // Show every day
            }
        ),
        modifier = Modifier.fillMaxSize()
    )
}
