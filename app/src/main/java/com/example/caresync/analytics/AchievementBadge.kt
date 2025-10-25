package com.example.caresync.analytics

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.caresync.analytics.domain.Achievement

@Composable
fun AchievementBadge(
    achievement: Achievement,
    modifier: Modifier = Modifier
) {
    var showDialog by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .size(80.dp)
            .clip(CircleShape)
            .background(
                if (achievement.isUnlocked) {
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF9C27B0),  // Light Purple
                            Color(0xFF560154)   // Deep Purple
                        )
                    )
                } else {
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF221F2C),  // Dark Gray
                            Color(0xFF221F2C)
                        )
                    )
                }
            )
            .border(
                width = if (achievement.isUnlocked) 2.dp else 1.dp,
                color = if (achievement.isUnlocked) Color(0xFF9C27B0) else Color(0xFF808080),
                shape = CircleShape
            )
            .clickable { showDialog = true },
        contentAlignment = Alignment.Center
    ) {
        // Icon/Emoji
        Text(
            text = if (achievement.isUnlocked) achievement.icon else "🔒",
            fontSize = if (achievement.isUnlocked) 36.sp else 32.sp,
            modifier = Modifier.alpha(if (achievement.isUnlocked) 1f else 0.5f)
        )
    }

    // Achievement details dialog
    if (showDialog) {
        Dialog(onDismissRequest = { showDialog = false }) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF221F2C)
                )
            ) {
                Column(
                    modifier = Modifier
                        .padding(24.dp)
                        .fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Icon
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .clip(CircleShape)
                            .background(
                                if (achievement.isUnlocked) {
                                    Color(0xFF560154)
                                } else {
                                    Color(0xFF221F2C)
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (achievement.isUnlocked) achievement.icon else "🔒",
                            fontSize = 40.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Name
                    Text(
                        text = achievement.name,
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Description
                    Text(
                        text = achievement.description,
                        color = Color(0xFFAAAAAA),
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Status
                    if (achievement.isUnlocked) {
                        Text(
                            text = "✅ Unlocked",
                            color = Color(0xFF4CAF50),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    } else {
                        // Progress bar
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Progress: ${(achievement.progress * 100).toInt()}%",
                                color = Color(0xFFAAAAAA),
                                fontSize = 12.sp
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            LinearProgressIndicator(
                                progress = achievement.progress,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(CircleShape),
                                color = Color(0xFF560154),
                                trackColor = Color(0xFF808080).copy(alpha = 0.3f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Close button
                    Button(
                        onClick = { showDialog = false },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF560154)
                        )
                    ) {
                        Text("Close")
                    }
                }
            }
        }
    }
}
