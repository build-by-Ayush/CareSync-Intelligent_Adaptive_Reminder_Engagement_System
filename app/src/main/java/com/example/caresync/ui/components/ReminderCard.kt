package com.example.caresync.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.caresync.domain.NotifyMethod
import com.example.caresync.domain.Priority
import com.example.caresync.domain.ReminderSettings
import com.example.caresync.domain.TriggerMode
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

// ============= BUTTON POSITION CONFIGURATION =============
// Adjust these values to fine-tune button positions

// Toggle button positioning
private val TOGGLE_HORIZONTAL_OFFSET = (-8).dp  // Negative = move left, Positive = move right
private val TOGGLE_VERTICAL_OFFSET = 6.dp       // Positive = move down, Negative = move up

// Delete button positioning
private val DELETE_HORIZONTAL_OFFSET = (-6).dp  // Negative = move left, Positive = move right
private val DELETE_VERTICAL_OFFSET = 0.dp       // Positive = move down, Negative = move up

/**
 * Compact reminder card with balanced layout
 *
 * Features:
 * - Solid status strip on left edge (auto-clips with rounded corners)
 * - Mode and Priority tags
 * - Title with 24sp font
 * - Custom toggle switch (adjustable position)
 * - Notification method icons
 * - Color-coded due date
 * - Delete button (adjustable position)
 */
@Composable
fun ReminderCard(
    reminder: ReminderSettings,
    onToggle: (Boolean) -> Unit,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Calculate status strip color
    var stripColor by remember { mutableStateOf(StatusStripCalculator.StatusBlue) }

    LaunchedEffect(reminder.id, reminder.enabled, reminder.updatedAt) {
        scope.launch {
            stripColor = StatusStripCalculator.calculateStripColor(reminder, context)
        }
    }

    // Calculate due date info
    val dueDateInfo = remember(reminder.dueDate) {
        calculateDueDateInfo(reminder.dueDate)
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(120.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xE138344B)  // Your custom color
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            // ✅ STATUS STRIP - Solid color, auto-clips with card corners
            Box(
                modifier = Modifier
                    .width(5.dp)
                    .fillMaxHeight()
                    .background(stripColor)
            )

            Spacer(modifier = Modifier.width(4.dp))

            // MAIN CONTENT
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(start = 6.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // TOP ROW: Tags + Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left: Mode + Priority Tags
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Mode Tag
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFF5E35B1)  // Purple
                        ) {
                            Row(
                                modifier = Modifier.padding(
                                    horizontal = 7.dp,
                                    vertical = 3.dp
                                ),
                                horizontalArrangement = Arrangement.spacedBy(3.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = getModeIcon(reminder.triggerMode),
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(11.dp)
                                )
                                Text(
                                    text = getModeName(reminder.triggerMode),
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        // Priority Tag
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = getPriorityColor(reminder.priority).copy(alpha = 0.9f)
                        ) {
                            Text(
                                text = getPriorityName(reminder.priority),
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(
                                    horizontal = 7.dp,
                                    vertical = 3.dp
                                )
                            )
                        }
                    }

                    // Right: Toggle (with adjustable positioning)
                    Box(
                        modifier = Modifier.offset(
                            x = TOGGLE_HORIZONTAL_OFFSET,
                            y = TOGGLE_VERTICAL_OFFSET
                        )
                    ) {
                        CustomToggle(
                            checked = reminder.enabled,
                            onCheckedChange = onToggle
                        )
                    }
                }

                // MIDDLE ROW: Title
                Text(
                    text = reminder.title,
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 6.dp)  // Indent
                )

                // BOTTOM ROW: Icons + Due + Delete
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left: Notification Method Icons
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        reminder.notifyMethods.forEach { method ->
                            Icon(
                                imageVector = getMethodIcon(method),
                                contentDescription = method.name,
                                tint = Color(0xFFB3B3B3),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    // Center: Due Date or "No due date"
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(start = 75.dp)
                    ) {
                        if (dueDateInfo != null) {
                            // ✅ Has due date - show colored info
                            Icon(
                                imageVector = Icons.Filled.Schedule,
                                contentDescription = null,
                                tint = dueDateInfo.color,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = dueDateInfo.text,
                                color = dueDateInfo.color,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        } else {
                            // ✅ NEW: No due date - show gray text
                            Text(
                                text = "No due date",
                                color = Color(0xFFA9A9A9), // Gray
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Normal
                            )
                        }
                    }

                    // Right: Delete Button (with adjustable positioning)
                    Box(
                        modifier = Modifier.offset(
                            x = DELETE_HORIZONTAL_OFFSET,
                            y = DELETE_VERTICAL_OFFSET
                        )
                    ) {
                        IconButton(
                            onClick = onDelete,
                            modifier = Modifier.size(30.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Delete,
                                contentDescription = "Delete",
                                tint = Color(0xFFE57373), // Light red
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

// ============= Helper Functions =============

private data class DueDateInfo(val text: String, val color: Color)

private fun calculateDueDateInfo(dueDate: Long?): DueDateInfo? {
    if (dueDate == null || dueDate == 0L) return null

    val now = System.currentTimeMillis()
    val diffMillis = dueDate - now
    val diffDays = TimeUnit.MILLISECONDS.toDays(diffMillis)
    val diffHours = TimeUnit.MILLISECONDS.toHours(diffMillis)

    return when {
        // ❌ RED: Overdue (past due date)
        diffMillis < 0 -> {
            val daysPast = -diffDays
            DueDateInfo(
                text = if (daysPast == 0L) "Overdue" else "${daysPast}d late",
                color = Color(0xFFF44336)  // Red
            )
        }

        // 🟡 YELLOW: Urgent (0-7 days away)
        diffDays in 0..3 -> {
            val text = when {
                diffDays == 0L -> if (diffHours <= 1) "${diffHours}h left" else "Today"
                diffDays == 1L -> "Tomorrow"
                else -> "${diffDays}d left"
            }
            DueDateInfo(
                text = text,
                color = Color(0xFFFFC107)  // Yellow/Amber
            )
        }

        // 🟢 GREEN: Safe (8+ days away)
        else -> {
            val text = if (diffDays <= 30) {
                "${diffDays}d left"
            } else {
                val dateFormat = SimpleDateFormat("MMM dd", Locale.getDefault())
                dateFormat.format(Date(dueDate))
            }
            DueDateInfo(
                text = text,
                color = Color(0xFF4CAF50)  // Green
            )
        }
    }
}

private fun getPriorityColor(priority: Priority): Color {
    return when (priority) {
        Priority.CRITICAL -> Color(0xFFF44336)
        Priority.HIGH -> Color(0xFFFF9800)
        Priority.NORMAL -> Color(0xFF4CAF50)
        Priority.LOW -> Color(0xFF757575)
    }
}

private fun getPriorityName(priority: Priority): String {
    return when (priority) {
        Priority.CRITICAL -> "CRITICAL"
        Priority.HIGH -> "HIGH"
        Priority.NORMAL -> "NORMAL"
        Priority.LOW -> "LOW"
    }
}

private fun getModeIcon(mode: TriggerMode): ImageVector {
    return when (mode) {
        TriggerMode.MODEL_ASSISTED -> Icons.Filled.SmartToy
        TriggerMode.FIXED_TIME -> Icons.Filled.Schedule
        TriggerMode.HYBRID -> Icons.Filled.AutoAwesome
        TriggerMode.MANUAL -> Icons.Filled.TouchApp
    }
}

private fun getModeName(mode: TriggerMode): String {
    return when (mode) {
        TriggerMode.MODEL_ASSISTED -> "Smart"
        TriggerMode.FIXED_TIME -> "Fixed"
        TriggerMode.HYBRID -> "Hybrid"
        TriggerMode.MANUAL -> "Manual"
    }
}

private fun getMethodIcon(method: NotifyMethod): ImageVector {
    return when (method) {
        NotifyMethod.PUSH -> Icons.Filled.Notifications
        NotifyMethod.VOICE -> Icons.AutoMirrored.Filled.VolumeUp
        NotifyMethod.SMS -> Icons.AutoMirrored.Filled.Message
    }
}
