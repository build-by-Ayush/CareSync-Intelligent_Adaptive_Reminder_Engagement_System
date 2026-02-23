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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import com.example.caresync.data.AppDatabase
import com.example.caresync.data.ProfileDataStore
import com.example.caresync.utils.getDeviceType
import com.example.caresync.utils.DeviceType
import kotlinx.coroutines.launch

@Composable
fun ProfileDrawerContent(
    username: String,
    age: String,
    purpose: String,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier
) {
    // ✅ NEW: Get device type
    val deviceType = getDeviceType()

    // ✅ NEW: Responsive values
    val profileCircleSize = when (deviceType) {
        DeviceType.PHONE -> 200.dp
        DeviceType.TABLET -> 280.dp
    }

    val usernameFontSize = when (deviceType) {
        DeviceType.PHONE -> 28.sp
        DeviceType.TABLET -> 40.sp
    }

    val statsValueFontSize = when (deviceType) {
        DeviceType.PHONE -> 18.sp
        DeviceType.TABLET -> 24.sp
    }

    val statsLabelFontSize = when (deviceType) {
        DeviceType.PHONE -> 11.sp
        DeviceType.TABLET -> 14.sp
    }

    val topSpacing = when (deviceType) {
        DeviceType.PHONE -> 40.dp
        DeviceType.TABLET -> 60.dp
    }

    val sectionSpacing = when (deviceType) {
        DeviceType.PHONE -> 28.dp
        DeviceType.TABLET -> 40.dp
    }

    val statCardSize = when (deviceType) {
        DeviceType.PHONE -> 56.dp
        DeviceType.TABLET -> 72.dp
    }

    val iconSize = when (deviceType) {
        DeviceType.PHONE -> 32.dp
        DeviceType.TABLET -> 48.dp
    }

    val quoteCardPadding = when (deviceType) {
        DeviceType.PHONE -> 20.dp
        DeviceType.TABLET -> 30.dp
    }

    val logoutButtonHeight = when (deviceType) {
        DeviceType.PHONE -> 62.dp
        DeviceType.TABLET -> 78.dp
    }

    val logoutFontSize = when (deviceType) {
        DeviceType.PHONE -> 18.sp
        DeviceType.TABLET -> 24.sp
    }

    val context = LocalContext.current
    val profileDataStore = remember { ProfileDataStore(context) }
    val scope = rememberCoroutineScope()

    // ✅ Dynamic motivational quotes
    val motivationalQuotes = listOf(
        "Stay on track." to "Every reminder is a step toward your best self! 🚀",
        "Keep going." to "One reminder at a time builds unstoppable habits! 💪",
        "Don't give up." to "Your reminders are shaping tomorrow's success! 🌱",
        "Focus today." to "Great habits start with one task today! ✨",
        "Be consistent." to "Remind, repeat, and rise every day! 🔁",
        "You can do this." to "Turn every reminder into a victory! 🏆",
        "Push forward." to "Each task ticked off strengthens your routine! 💥",
        "One step at a time." to "Small reminders create big change! 🌟",
        "Keep showing up." to "Your future self thanks you for every task! 🙌",
        "Make it happen." to "A single reminder today, a stronger habit tomorrow! 💫",
        "Stay positive." to "Your reminders keep positivity on schedule! ☀️",
        "Stay productive." to "Tiny reminders, massive progress! 📈",
        "Don't stop now." to "Momentum grows with each completed task! 🔥",
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
        "Don't lose focus." to "Reminders keep your vision crystal clear! 👀",
        "Stay committed." to "Every alert is a promise kept! 💎",
        "Keep it going." to "Reminders fuel your everyday wins! 🏁",
        "Stay inspired." to "Every task completed inspires the next! ✨",
        "Stay grounded." to "Your reminders keep progress real and steady! 🌍",
        "Stay in control." to "Reminders help you lead your day with purpose! 🕹️",
        "Keep moving." to "Each reminder keeps momentum alive! 🚴",
        "Build habits." to "Reminders today become achievements tomorrow! 🏆"
    )
    val randomQuote = remember { motivationalQuotes.random() }

    // ✅ Observe real-time changes from database
    val database = remember { AppDatabase.get(context) }
    val remindersFlow = remember { database.reminderDao().observeAll() }
    val analyticsFlow = remember { database.analyticsDao().getUserProgressFlow() }

    val reminders by remindersFlow.collectAsState(initial = emptyList())
    val analytics by analyticsFlow.collectAsState(initial = null)

    var totalTasks by remember { mutableStateOf(0) }
    var activeTasks by remember { mutableStateOf(0) }
    var currentStreak by remember { mutableStateOf(0) }

    // ✅ Update whenever database changes
    LaunchedEffect(reminders, analytics) {
        totalTasks = reminders.size
        activeTasks = reminders.count { it.enabled }
        currentStreak = analytics?.currentStreak ?: 0
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(topSpacing))  // ✅ RESPONSIVE

            // Profile Circle - ✅ RESPONSIVE
            Box(
                modifier = Modifier
                    .size(profileCircleSize)
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
                Text(text = "😊", fontSize = (profileCircleSize.value / 2).sp)
            }

            Spacer(modifier = Modifier.height(sectionSpacing))  // ✅ RESPONSIVE

            // User Name - ✅ RESPONSIVE
            Text(
                text = username.ifEmpty { "User" },
                color = Color.White,
                fontSize = usernameFontSize,
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

            Spacer(modifier = Modifier.height(sectionSpacing))  // ✅ RESPONSIVE

            // Real Stats Section - ✅ RESPONSIVE
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatCard(
                    icon = Icons.Filled.CheckCircle,
                    value = totalTasks.toString(),
                    label = "Total Tasks",
                    cardSize = statCardSize,
                    valueFontSize = statsValueFontSize,
                    labelFontSize = statsLabelFontSize,
                    iconSize = iconSize
                )

                StatCard(
                    icon = Icons.Filled.Task,
                    value = activeTasks.toString(),
                    label = "Active",
                    cardSize = statCardSize,
                    valueFontSize = statsValueFontSize,
                    labelFontSize = statsLabelFontSize,
                    iconSize = iconSize
                )

                StatCard(
                    icon = Icons.Filled.LocalFireDepartment,
                    value = "${currentStreak}d",
                    label = "Streak",
                    cardSize = statCardSize,
                    valueFontSize = statsValueFontSize,
                    labelFontSize = statsLabelFontSize,
                    iconSize = iconSize
                )
            }

            Spacer(modifier = Modifier.height(sectionSpacing))  // ✅ RESPONSIVE

            // Dynamic Motivational Quote - ✅ RESPONSIVE
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFF3E3E50).copy(alpha = 0.5f)
            ) {
                Column(
                    modifier = Modifier.padding(quoteCardPadding),  // ✅ RESPONSIVE
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Filled.FormatQuote,
                        contentDescription = null,
                        tint = Color(0xFF9C27B0),
                        modifier = Modifier.size(iconSize)  // ✅ RESPONSIVE
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = randomQuote.first,
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = randomQuote.second,
                        color = Color(0xFFB3B3B3),
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Logout Button - ✅ RESPONSIVE
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(logoutButtonHeight)
                    .clickable {
                        scope.launch {
                            profileDataStore.fullLogout(context)
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
                        fontSize = logoutFontSize,  // ✅ RESPONSIVE
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

// ✅ UPDATED: StatCard with responsive parameters
@Composable
private fun StatCard(
    icon: ImageVector,
    value: String,
    label: String,
    cardSize: Dp,
    valueFontSize: TextUnit,
    labelFontSize: TextUnit,
    iconSize: Dp
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
                    .size(cardSize)  // ✅ RESPONSIVE
                    .padding(12.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color(0xFF9C27B0),
                    modifier = Modifier.size(iconSize)  // ✅ RESPONSIVE
                )
            }
        }

        Text(
            text = value,
            color = Color.White,
            fontSize = valueFontSize,  // ✅ RESPONSIVE
            fontWeight = FontWeight.Bold
        )

        Text(
            text = label,
            color = Color(0xFF9E9E9E),
            fontSize = labelFontSize,  // ✅ RESPONSIVE
            textAlign = TextAlign.Center
        )
    }
}
