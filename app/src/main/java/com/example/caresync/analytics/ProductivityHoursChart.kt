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
import com.example.caresync.analytics.domain.HourlyProductivity
import com.patrykandpatrick.vico.compose.axis.horizontal.rememberBottomAxis
import com.patrykandpatrick.vico.compose.axis.vertical.rememberStartAxis
import com.patrykandpatrick.vico.compose.chart.Chart
import com.patrykandpatrick.vico.compose.chart.column.columnChart
import com.patrykandpatrick.vico.core.entry.ChartEntryModelProducer
import com.patrykandpatrick.vico.core.entry.entryOf
import com.patrykandpatrick.vico.compose.component.textComponent
import com.patrykandpatrick.vico.core.axis.AxisItemPlacer

@Composable
fun ProductivityHoursChart(
    data: List<HourlyProductivity>,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Header
        Text(
            text = "⏰ Best Productivity Hours",
            fontSize = 18.sp,
            color = Color.White,
            fontWeight = FontWeight.Bold
        )

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
                if (data.all { it.completionCount == 0 }) {
                    Text(
                        text = "No productivity data yet",
                        color = Color(0xFFAAAAAA),
                        fontSize = 14.sp,
                        modifier = Modifier.align(androidx.compose.ui.Alignment.Center)
                    )
                } else {
                    ProductivityBarChart(data = data)
                }
            }
        }
    }
}

@Composable
private fun ProductivityBarChart(data: List<HourlyProductivity>) {
    val chartEntries = data.map { hourData ->
        entryOf(hourData.hour.toFloat(), hourData.completionCount.toFloat())
    }

    val chartEntryModelProducer = remember(data) {
        ChartEntryModelProducer(chartEntries)
    }

    Chart(
        chart = columnChart(),
        chartModelProducer = chartEntryModelProducer,
        startAxis = rememberStartAxis(
            label = textComponent(color = Color.White, textSize = 12.sp),
            valueFormatter = { value, _ -> value.toInt().toString() },
            itemPlacer = remember {
                AxisItemPlacer.Vertical.default(maxItemCount = 5)
            }
        ),
        bottomAxis = rememberBottomAxis(
            label = textComponent(color = Color.White, textSize = 10.sp),
            // ✅ CHANGED: Full hour labels (12 AM, 7 PM, etc.)
            valueFormatter = { value, _ ->
                val hour = value.toInt()
                when {
                    hour == 0 -> "12 AM"
                    hour < 12 -> "$hour AM"
                    hour == 12 -> "12 PM"
                    else -> "${hour - 12} PM"
                }
            }
        ),
        modifier = Modifier.fillMaxSize()
    )
}
