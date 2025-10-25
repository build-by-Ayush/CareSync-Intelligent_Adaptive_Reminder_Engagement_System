package com.example.caresync

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.compose.ui.draw.drawBehind
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.caresync.analytics.*
import com.example.caresync.analytics.viewmodel.AnalyticsViewModel
import kotlin.math.sin
import kotlin.math.PI
import androidx.compose.foundation.horizontalScroll


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyticsScreen(navController: NavHostController) {
    // Get ViewModel
    val viewModel: AnalyticsViewModel = viewModel()
    val uiState by viewModel.uiState.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF000000))
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // ✅ WAVE HEADER - Wave positioned lower to eliminate blank space
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)  // ← Adjusted height to match wave coverage
                    .drawBehind {
                        val width = size.width
                        val height = size.height
                        val waveLength = width / 1f
                        val amplitude = 30f  // ← ORIGINAL - Not changed

                        val backWave = Path().apply {
                            val baseY = height * 0.80f  // ← MOVED DOWN from 0.50f to 0.80f
                            val steps = 300
                            val tilt = 20f  // ← ORIGINAL - Not changed
                            for (i in 0..steps) {
                                val x = (i.toFloat() / steps) * width
                                val angle = (x / waveLength) * (2 * PI).toFloat()
                                val y = baseY + amplitude * sin(angle) + (x / width) * tilt
                                if (i == 0) moveTo(x, y) else lineTo(x, y)
                            }
                            lineTo(width, 0f)
                            lineTo(0f, 0f)
                            close()
                        }
                        drawPath(path = backWave, color = Color(0xB74B0049))

                        val frontWave = Path().apply {
                            val baseY = height * 0.85f  // ← MOVED DOWN from 0.55f to 0.85f
                            val steps = 500
                            val tilt = 35f  // ← ORIGINAL - Not changed
                            for (i in 0..steps) {
                                val x = (i.toFloat() / steps) * width
                                val angle = (x / waveLength) * (4 * PI).toFloat() + (PI / 6).toFloat()
                                val y = baseY + amplitude * 0.8f * sin(angle) - (x / width) * tilt
                                if (i == 0) moveTo(x, y) else lineTo(x, y)
                            }
                            lineTo(width, 0f)
                            lineTo(0f, 0f)
                            close()
                        }
                        drawPath(path = frontWave, color = Color(0xC9560154).copy(alpha = 0.8f))
                    }
            ){
                // Header content
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { navController.navigateUp() }) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        Text(
                            text = "Productivity Dashboard",
                            fontSize = 20.sp,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )

                        // Refresh button
                        IconButton(onClick = { viewModel.refreshData() }) {
                            Icon(
                                imageVector = Icons.Default.Refresh,  // ← Changed from ArrowBack to Refresh
                                contentDescription = "Refresh",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                    }
                }
            }

            // ✅ MAIN CONTENT
            if (uiState.isLoading) {
                // Loading state
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF000000)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Color(0xFF560154))
                }
            } else if (uiState.error != null) {
                // Error state
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF000000)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "⚠️",
                            fontSize = 48.sp
                        )
                        Text(
                            text = "Error loading dashboard",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = uiState.error ?: "Unknown error",
                            color = Color(0xFFAAAAAA),
                            fontSize = 14.sp
                        )
                        Button(
                            onClick = { viewModel.refreshData() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF560154)
                            )
                        ) {
                            Text("Retry")
                        }
                    }
                }
            } else {
                // Success state - Show dashboard
                DashboardContent(
                    uiState = uiState,
                    onDateRangeChange = { viewModel.changeDateRange(it) }
                )
            }
        }
    }
}

@Composable
private fun DashboardContent(
    uiState: com.example.caresync.analytics.viewmodel.AnalyticsUiState,
    onDateRangeChange: (com.example.caresync.analytics.viewmodel.DateRange) -> Unit
) {
    val stats = uiState.statistics

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF000000))
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ✅ LEVEL & XP SECTION
        uiState.levelInfo?.let { levelInfo ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF221F2C)
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Level ${levelInfo.currentLevel} - ${levelInfo.levelName}",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "${levelInfo.currentPoints} / ${levelInfo.nextLevelPoints} XP",
                        color = Color(0xFFAAAAAA),
                        fontSize = 14.sp
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    LinearProgressIndicator(
                        progress = levelInfo.progress,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp),
                        color = Color(0xFF560154),
                        trackColor = Color(0xFF808080).copy(alpha = 0.3f)
                    )
                }
            }
        }

        // ✅ STATISTICS CARDS
        stats?.let { statistics ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // ✅ BETTER: Show notifications sent, not tasks created
                StatisticsCard(
                    emoji = "📋",
                    value = "${stats.totalTasksCreated}",  // Now shows TRIGGERED count
                    label = "Notifications",  // ← Changed label
                    modifier = Modifier
                        .weight(1f)
                        .height(140.dp)
                )

                StatisticsCard(
                    emoji = "🔥",
                    value = "${statistics.currentStreak}",
                    label = "Day Streak",
                    valueColor = Color(0xFFFF9800),
                    modifier = Modifier
                        .weight(1f)
                        .height(140.dp)
                )
            }

            StatisticsCard(
                emoji = "✅",
                value = "${(statistics.completionRate * 100).toInt()}%",
                label = "Completion Rate",
                valueColor = Color(0xFF4CAF50),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
            )
        }

        // Achievements Section
        Text(
            text = "🏆 Achievements (${uiState.unlockedCount}/${uiState.achievements.size})",
            fontSize = 18.sp,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 8.dp)
        )

        // ✅ HORIZONTAL SCROLLABLE ROW
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),  // ← Add horizontal scroll
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            uiState.achievements.forEach { achievement ->  // ← Show ALL, not just first 4
                AchievementBadge(
                    achievement = achievement,
                    modifier = Modifier.size(80.dp)  // ← Fixed size instead of weight
                )
            }
        }

        // ✅ ADD SCROLL HINT
        Text(
            text = "← Swipe to see all achievements →",
            fontSize = 12.sp,
            color = Color(0xFF808080),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )


        // ✅ COMPLETION RATE CHART
        CompletionRateChart(
            data = uiState.completionRateData,
            selectedDateRange = uiState.selectedDateRange,
            onDateRangeChange = onDateRangeChange,
            modifier = Modifier.fillMaxWidth()
        )

        // ✅ PRODUCTIVITY HOURS CHART
        ProductivityHoursChart(
            data = uiState.productivityHours,
            modifier = Modifier.fillMaxWidth()
        )

        // ✅ WEEKLY HEATMAP
        WeeklyHeatmap(
            data = uiState.weeklyData,
            modifier = Modifier.fillMaxWidth()
        )

        // ✅ INSIGHTS
        InsightsSection(
            insights = uiState.insights,
            modifier = Modifier.fillMaxWidth()
        )

        // ✅ TONE PERFORMANCE
        TonePerformanceTable(
            toneStats = uiState.toneStats,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(32.dp))
        // After collecting uiState
        LaunchedEffect(uiState) {
            Log.d("DASHBOARD_DEBUG", "Stats: ${uiState.statistics}")
            Log.d("DASHBOARD_DEBUG", "Completion rate data: ${uiState.completionRateData.size} entries")
            Log.d("DASHBOARD_DEBUG", "Productivity hours: ${uiState.productivityHours.size} entries")
            Log.d("DASHBOARD_DEBUG", "Weekly data: ${uiState.weeklyData.size} entries")
        }
    }
}
