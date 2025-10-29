package com.example.caresync.ui.components

import android.os.Build
import android.widget.NumberPicker
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView

/**
 * Hours and minutes picker using NumberPicker wheels
 */
@Composable
fun HoursPickerUI(
    selectedHour: Int,
    selectedMinute: Int,
    onHourChange: (Int) -> Unit,
    onMinuteChange: (Int) -> Unit
) {
    Text(
        text = "Select Hours and Minutes",
        color = Color.White,
        fontSize = 16.sp,
        modifier = Modifier.padding(bottom = 12.dp)
    )

    // Rounded rectangle container for NumberPickers
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF221F2C), RoundedCornerShape(12.dp))
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            // Hours Picker
            AndroidView(
                factory = { context ->
                    NumberPicker(context).apply {
                        minValue = 0
                        maxValue = 23
                        value = selectedHour
                        setOnValueChangedListener { _, _, newVal ->
                            onHourChange(newVal)
                        }
                        setBackgroundColor(android.graphics.Color.TRANSPARENT)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            textColor = android.graphics.Color.WHITE
                        } else {
                            descendantFocusability = NumberPicker.FOCUS_BLOCK_DESCENDANTS
                        }
                    }
                },
                modifier = Modifier.width(100.dp)
            )

            // Colon separator
            Text(
                text = ":",
                color = Color.White,
                fontSize = 32.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 8.dp)
            )

            // Minutes Picker
            AndroidView(
                factory = { context ->
                    NumberPicker(context).apply {
                        minValue = 0
                        maxValue = 59
                        value = selectedMinute
                        setOnValueChangedListener { _, _, newVal ->
                            onMinuteChange(newVal)
                        }
                        setBackgroundColor(android.graphics.Color.TRANSPARENT)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            textColor = android.graphics.Color.WHITE
                        } else {
                            descendantFocusability = NumberPicker.FOCUS_BLOCK_DESCENDANTS
                        }
                    }
                },
                modifier = Modifier.width(100.dp)
            )
        }
    }
}

/**
 * Day interval picker with +/- buttons
 */
@Composable
fun DaysPickerUI(
    dayCount: Int,
    onDayCountChange: (Int) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF221F2C), RoundedCornerShape(50))
            .padding(horizontal = 17.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = { if (dayCount > 1) onDayCountChange(dayCount - 1) }
        ) {
            Icon(Icons.Filled.Remove, contentDescription = "Decrease", tint = Color.White)
        }

        Text(
            text = "In $dayCount ${if (dayCount == 1) "Day" else "Days"}",
            color = Color.White,
            fontSize = 18.sp,
            modifier = Modifier.padding(horizontal = 10.dp)
        )

        IconButton(onClick = { onDayCountChange(dayCount + 1) }) {
            Icon(Icons.Filled.Add, contentDescription = "Increase", tint = Color.White)
        }
    }
}

/**
 * Weekday selector with clickable day chips
 * Note: This is a standalone version - the Hours mode weekday chips are still inline
 */
@Composable
fun WeekdaysPickerUI() {
    val days = listOf("S", "M", "T", "W", "T", "F", "S")
    var selectedDays by remember { mutableStateOf(setOf<Int>()) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        days.forEachIndexed { index, label ->
            val isSelected = selectedDays.contains(index)
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(if (isSelected) Color(0xFF765AE5) else Color(0xFF3E3951))
                    .clickable {
                        selectedDays = if (isSelected) {
                            selectedDays - index
                        } else {
                            selectedDays + index
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(label, color = Color.White, fontSize = 16.sp)
            }
        }
    }
}
