package com.example.caresync.analytics

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.caresync.analytics.domain.DailyCompletion
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun WeeklyHeatmap(
    data: List<DailyCompletion>,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Header with dynamic date range
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "📅 Last 28 Days Activity",
                fontSize = 18.sp,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )

            // Dynamic date range
            if (data.isNotEmpty()) {
                Text(
                    text = formatDateRange(data.first().date, data.last().date),
                    fontSize = 10.sp,
                    color = Color(0xFFAAAAAA)
                )
            }
        }

        // Heatmap card
        Card(
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF221F2C)
            ),
            shape = RoundedCornerShape(8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                if (data.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp),  // ✅ Same as original
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No activity data yet",
                            color = Color(0xFFAAAAAA),
                            fontSize = 14.sp
                        )
                    }
                } else {
                    // Simple grid without day labels
                    SimpleHeatmapGrid(data = data)

                    Spacer(modifier = Modifier.height(12.dp))

                    // Legend
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Less",
                            color = Color.White,
                            fontSize = 10.sp
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        repeat(5) { level ->
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .background(
                                        color = getHeatmapColor(level * 5),
                                        shape = RoundedCornerShape(2.dp)
                                    )
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                        }
                        Text(
                            text = "More",
                            color = Color.White,
                            fontSize = 10.sp
                        )
                    }
                }
            }
        }
    }
}

/**
 * ✅ Simple 4×7 grid with EXACT original sizing
 */
@Composable
private fun SimpleHeatmapGrid(data: List<DailyCompletion>) {
    val gridData = data.takeLast(28)

    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),  // ✅ Same as original
        modifier = Modifier.height(180.dp)  // ✅ Same as original
    ) {
        // 4 rows of 7 columns
        for (row in 0..3) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),  // ✅ Same as original
                modifier = Modifier.weight(1f)
            ) {
                for (col in 0..6) {
                    val index = row * 7 + col
                    val dayData = gridData.getOrNull(index)
                    val isToday = index == gridData.size - 1

                    HeatmapCell(
                        completionCount = dayData?.completionCount ?: 0,
                        isEmpty = dayData == null,
                        isToday = isToday,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

/**
 * ✅ EXACT same cell as original (36dp fixed size)
 */
@Composable
private fun HeatmapCell(
    completionCount: Int,
    isEmpty: Boolean,
    isToday: Boolean,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(36.dp)  // ✅ EXACT same as original
            .background(
                color = if (isEmpty) {
                    Color(0xFF1A1A1A)
                } else {
                    getHeatmapColor(completionCount)
                },
                shape = RoundedCornerShape(4.dp)  // ✅ Same as original
            )
            .then(
                if (isToday) {
                    Modifier.border(
                        width = 2.dp,
                        color = Color.White,
                        shape = RoundedCornerShape(4.dp)
                    )
                } else {
                    Modifier
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        // Empty
    }
}

/**
 * ✅ NEON RED TO YELLOW GRADIENT (same as original)
 */
private fun getHeatmapColor(completionCount: Int): Color {
    return when {
        completionCount == 0 -> Color(0xFF1A1A1A)
        completionCount <= 2 -> Color(0xFFFF3366)
        completionCount <= 5 -> Color(0xFFFF6644)
        completionCount <= 10 -> Color(0xFFFF9933)
        else -> Color(0xFFFFDD00)
    }
}

/**
 * ✅ Dynamic date range
 */
private fun formatDateRange(startDate: Long, endDate: Long): String {
    val dateFormat = SimpleDateFormat("MMM d", Locale.getDefault())
    return "${dateFormat.format(Date(startDate))} - ${dateFormat.format(Date(endDate))}"
}
