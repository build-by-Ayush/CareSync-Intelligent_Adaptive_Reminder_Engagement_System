package com.example.caresync.analytics

import androidx.compose.foundation.background
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
import java.util.Calendar

@Composable
fun WeeklyHeatmap(
    data: List<DailyCompletion>,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Header
        Text(
            text = "📅 Activity Heatmap",
            fontSize = 18.sp,
            color = Color.White,
            fontWeight = FontWeight.Bold
        )

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
                            .height(200.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No activity data yet",
                            color = Color(0xFFAAAAAA),
                            fontSize = 14.sp
                        )
                    }
                } else {
                    // Day labels
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun").forEach { day ->
                            Text(
                                text = day,
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // ✅ Heatmap grid with proper calendar alignment
                    HeatmapGrid(data = data)

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

@Composable
private fun HeatmapGrid(data: List<DailyCompletion>) {
    // ✅ Create 4 rows × 7 columns grid aligned to calendar
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.height(180.dp)
    ) {
        // Calculate starting day of week for the oldest date
        val oldestDate = data.firstOrNull()?.date ?: return
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = oldestDate

        // Get day of week (0 = Monday, 6 = Sunday)
        val startDayOfWeek = when (calendar.get(Calendar.DAY_OF_WEEK)) {
            Calendar.MONDAY -> 0
            Calendar.TUESDAY -> 1
            Calendar.WEDNESDAY -> 2
            Calendar.THURSDAY -> 3
            Calendar.FRIDAY -> 4
            Calendar.SATURDAY -> 5
            Calendar.SUNDAY -> 6
            else -> 0
        }

        // Create grid
        val grid = Array(4) { Array<DailyCompletion?>(7) { null } }
        var dataIndex = 0

        // Fill grid row by row, left to right
        outerLoop@ for (row in 0..3) {
            for (col in 0..6) {
                // Skip cells before start day in first row
                if (row == 0 && col < startDayOfWeek) {
                    continue
                }

                // Fill with data
                if (dataIndex < data.size) {
                    grid[row][col] = data[dataIndex]
                    dataIndex++
                } else {
                    break@outerLoop
                }
            }
        }

        // Render grid
        for (row in 0..3) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.weight(1f)
            ) {
                for (col in 0..6) {
                    val dayData = grid[row][col]
                    HeatmapCell(
                        completionCount = dayData?.completionCount ?: 0,
                        isEmpty = dayData == null,
                        modifier = Modifier
                            .weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun HeatmapCell(
    completionCount: Int,
    isEmpty: Boolean,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(36.dp)  // ✅ FIXED SIZE instead of aspectRatio
            .background(
                color = if (isEmpty) {
                    Color(0xFF1A1A1A)  // Dark gray for empty cells
                } else {
                    getHeatmapColor(completionCount)
                },
                shape = RoundedCornerShape(4.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        // Empty content - just showing background color
    }
}


// ✅ NEON RED TO YELLOW GRADIENT
private fun getHeatmapColor(completionCount: Int): Color {
    return when {
        completionCount == 0 -> Color(0xFF1A1A1A)  // Dark empty
        completionCount <= 2 -> Color(0xFFFF3366)  // Neon Red (low activity)
        completionCount <= 5 -> Color(0xFFFF6644)  // Neon Orange-Red
        completionCount <= 10 -> Color(0xFFFF9933)  // Neon Orange
        else -> Color(0xFFFFDD00)  // Neon Yellow (high activity)
    }
}
