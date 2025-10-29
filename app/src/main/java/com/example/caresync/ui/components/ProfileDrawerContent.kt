package com.example.caresync.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.caresync.data.AppDatabase
import com.example.caresync.data.ProfileDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ProfileDrawerContent(
    username: String,
    age: String,
    purpose: String,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val profileDataStore = remember { ProfileDataStore(context) }
    val scope = rememberCoroutineScope()

    // ✅ Dynamic motivational quotes
    val motivationalQuotes = listOf(
        "Stay on track." to "Every reminder is a step toward your best self! 🚀",
        "Keep going." to "One reminder at a time builds unstoppable habits! 💪",
        "Don’t give up." to "Your reminders are shaping tomorrow’s success! 🌱",
        "Focus today." to "Great habits start with one task today! ✨",
        "Be consistent." to "Remind, repeat, and rise every day! 🔁",
        "You can do this." to "Turn every reminder into a victory! 🏆",
        "Push forward." to "Each task ticked off strengthens your routine! 💥",
        "One step at a time." to "Small reminders create big change! 🌟",
        "Keep showing up." to "Your future self thanks you for every task! 🙌",
        "Make it happen." to "A single reminder today, a stronger habit tomorrow! 💫",

        "Stay positive." to "Your reminders keep positivity on schedule! ☀️",
        "Stay productive." to "Tiny reminders, massive progress! 📈",
        "Don’t stop now." to "Momentum grows with each completed task! 🔥",
        "Stay disciplined." to "Remind yourself to rise — discipline is destiny! 🛠️",
        "Keep building." to "Every reminder adds another brick to your habits! 🧱",
        "Stay mindful." to "Your reminders bring focus to every moment! 🌼",
        "Stay motivated." to "Motivation starts with one simple reminder! 💡",
        "Stay organized." to "Reminders today mean peace of mind tomorrow! 🕊️",
        "Keep improving." to "Every reminder is a chance to grow stronger! 🌱",
        "Stay determined." to "Reminders keep your goals within reach! 🎯",

        "Build momentum." to "Let your reminders power your progress! ⚡",
        "Form good habits." to "One reminder closer to the lifestyle you want! 💜",
        "Stay sharp." to "Remind yourself — progress loves precision! 🧠",
        "Stay steady." to "Small consistent reminders beat big bursts! 💪",
        "Keep it simple." to "Simple reminders, strong routines! 🧩",
        "Stay strong." to "Consistency turns reminders into results! 🏋️",
        "Stay true." to "Every reminder brings you closer to your purpose! 💫",
        "Focus forward." to "Your reminders guide your next great step! 🚶",
        "Keep growing." to "A habit a day keeps doubt away! 🌻",
        "Stay the course." to "Your reminders are your roadmap to success! 🗺️",

        "Never settle." to "Remind yourself to aim higher every day! 🚀",
        "Dream big." to "Your reminders keep your dreams on schedule! ⏰",
        "Start today." to "The best time to build a habit is right now! 🌅",
        "Take action." to "Reminders help dreams become to-dos! ✅",
        "Keep learning." to "Every reminder helps you level up! 🧭",
        "Stay accountable." to "Your reminders keep promises to yourself! 🤝",
        "Stay focused." to "Your reminders are your daily compass! 🧭",
        "Keep believing." to "Remind yourself: progress is happening! 🌈",
        "Stay patient." to "Good habits grow with gentle reminders! 🌾",
        "Stay balanced." to "Reminders bring harmony to your hustle! ⚖️",

        "Be consistent." to "Remind, repeat, and watch habits bloom! 🌸",
        "Keep at it." to "Your reminders are tiny sparks of success! 🔥",
        "Don’t lose focus." to "Reminders keep your vision crystal clear! 👀",
        "Stay committed." to "Every alert is a promise kept! 💎",
        "Keep it going." to "Reminders fuel your everyday wins! 🏁",
        "Stay inspired." to "Every task completed inspires the next! ✨",
        "Stay grounded." to "Your reminders keep progress real and steady! 🌍",
        "Stay in control." to "Reminders help you lead your day with purpose! 🕹️",
        "Keep moving." to "Each reminder keeps momentum alive! 🚴",
        "Build habits." to "Reminders today become achievements tomorrow! 🏆"
    )
    val randomQuote = remember { motivationalQuotes.random() }

    // ✅ REAL DATA from database
    var totalTasks by remember { mutableStateOf(0) }
    var activeTasks by remember { mutableStateOf(0) }
    var currentStreak by remember { mutableStateOf(0) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        isLoading = true
        withContext(Dispatchers.IO) {
            try {
                val database = AppDatabase.get(context)

                // Get task counts
                val reminders = database.reminderDao().getAllReminders()
                totalTasks = reminders.size
                activeTasks = reminders.count { it.enabled }

                // ✅ Get REAL streak from analytics
                val progress = database.analyticsDao().getUserProgress()
                currentStreak = progress?.currentStreak ?: 0

            } catch (e: Exception) {
                // Fallback values
                totalTasks = 0
                activeTasks = 0
                currentStreak = 0
            } finally {
                isLoading = false
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxHeight()
            .fillMaxWidth(0.75f)
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF2D2D3A),
                        Color(0xFF232430)
                    )
                )
            )
    ) {
        if (isLoading) {
            // Loading state
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = Color(0xFF9C27B0)
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(40.dp))

                // Profile Circle
                Box(
                    modifier = Modifier
                        .size(200.dp)
                        .clip(CircleShape)
                        .background(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    Color(0xFF560154),
                                    Color(0xFF3E1147)
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "😊", fontSize = 96.sp)
                }

                Spacer(modifier = Modifier.height(28.dp))

                // User Name
                Text(
                    text = username.ifEmpty { "User" },
                    color = Color.White,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(8.dp))

                // User Age & Purpose
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF3E3E50)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Person,
                                contentDescription = null,
                                tint = Color(0xFFB3B3B3),
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = age.ifEmpty { "18" },
                                color = Color(0xFFB3B3B3),
                                fontSize = 14.sp
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF3E3E50)
                    ) {
                        Text(
                            text = purpose.ifEmpty { "Student" },
                            color = Color(0xFFB3B3B3),
                            fontSize = 14.sp,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(36.dp))

                // ✅ REAL Stats Section
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    StatCard(
                        icon = Icons.Filled.CheckCircle,
                        value = totalTasks.toString(),
                        label = "Total Tasks"
                    )

                    StatCard(
                        icon = Icons.Filled.Task,
                        value = activeTasks.toString(),
                        label = "Active"
                    )

                    StatCard(
                        icon = Icons.Filled.LocalFireDepartment,
                        value = "${currentStreak}d",
                        label = "Streak"
                    )
                }

                Spacer(modifier = Modifier.height(36.dp))

                // ✅ Dynamic Motivational Quote
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFF3E3E50).copy(alpha = 0.5f)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Filled.FormatQuote,
                            contentDescription = null,
                            tint = Color(0xFF9C27B0),
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = randomQuote.first,  // ✅ Random quote
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = randomQuote.second,  // ✅ Random subtext
                            color = Color(0xFFB3B3B3),
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                // Logout Button
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            scope.launch {
                                profileDataStore.fullLogout(context)  // ✅ Nuclear logout
                                onLogout()
                            }
                        },
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFF1A1A24)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Logout,
                            contentDescription = "Logout",
                            tint = Color(0xFFE57373),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Logout",
                            color = Color(0xFFE57373),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun StatCard(
    icon: ImageVector,
    value: String,
    label: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Surface(
            shape = CircleShape,
            color = Color(0xFF3E3E50)
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .padding(12.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color(0xFF9C27B0),
                    modifier = Modifier.size(32.dp)
                )
            }
        }

        Text(
            text = value,
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = label,
            color = Color(0xFF9E9E9E),
            fontSize = 11.sp,
            textAlign = TextAlign.Center
        )
    }
}
