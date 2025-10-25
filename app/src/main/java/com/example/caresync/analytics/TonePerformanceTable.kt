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
import com.example.caresync.analytics.domain.ToneStats

@Composable
fun TonePerformanceTable(
    toneStats: List<ToneStats>,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Header
        Text(
            text = "📊 Message Tone Performance",
            fontSize = 18.sp,
            color = Color.White,
            fontWeight = FontWeight.Bold
        )

        if (toneStats.isEmpty()) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF221F2C)
                )
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No tone data yet",
                        color = Color(0xFFAAAAAA),
                        fontSize = 14.sp
                    )
                }
            }
        } else {
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
                    // Table header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Tone",
                            color = Color(0xFFAAAAAA),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "Sent",
                            color = Color(0xFFAAAAAA),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.width(50.dp)
                        )
                        Text(
                            text = "Done",
                            color = Color(0xFFAAAAAA),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.width(50.dp)
                        )
                        Text(
                            text = "Rate",
                            color = Color(0xFFAAAAAA),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.width(60.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Divider(color = Color(0xFF808080).copy(alpha = 0.3f))

                    Spacer(modifier = Modifier.height(12.dp))

                    // Table rows
                    toneStats.forEach { stat ->
                        ToneRow(stat = stat)
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun ToneRow(stat: ToneStats) {
    val rateColor = when {
        stat.completionRate >= 0.80f -> Color(0xFF4CAF50)  // Green
        stat.completionRate >= 0.60f -> Color(0xFF2196F3)  // Blue
        stat.completionRate >= 0.40f -> Color(0xFFFF9800)  // Orange
        else -> Color(0xFFF44336)  // Red
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stat.toneName,
            color = Color.White,
            fontSize = 14.sp,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = "${stat.sentCount}",
            color = Color(0xFFAAAAAA),
            fontSize = 14.sp,
            modifier = Modifier.width(50.dp)
        )
        Text(
            text = "${stat.completedCount}",
            color = Color(0xFFAAAAAA),
            fontSize = 14.sp,
            modifier = Modifier.width(50.dp)
        )
        Text(
            text = "${(stat.completionRate * 100).toInt()}%",
            color = rateColor,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(60.dp)
        )
    }
}
