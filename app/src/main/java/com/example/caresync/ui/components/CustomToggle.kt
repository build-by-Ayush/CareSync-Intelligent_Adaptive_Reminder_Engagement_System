package com.example.caresync.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Custom toggle switch matching your purple theme design
 *
 * Design specs:
 * - Track: 52×28 dp rounded rectangle
 * - Thumb: 24 dp white circle
 * - Colors: Purple #7B2CBF (ON) / Dark gray #3E3951 (OFF)
 * - Animation: 200ms smooth slide
 *
 * Usage:
 * ```
 * CustomToggle(
 *     checked = reminder.enabled,
 *     onCheckedChange = { enabled -> viewModel.toggleReminder(id, enabled) }
 * )
 * ```
 */
@Composable
fun CustomToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    // Colors matching your toggle image
    val trackColor = if (checked) {
        Color(0xFFBA00FD)  // Purple when ON (matches your uploaded image)
    } else {
        Color(0xFF3E3951)  // Dark gray when OFF (matches your app theme)
    }

    val thumbColor = Color.White

    // Animate thumb position smoothly
    val thumbOffset by animateDpAsState(
        targetValue = if (checked) 26.dp else 2.dp,  // Adjusted for proper centering
        animationSpec = tween(durationMillis = 200),
        label = "thumb_offset"
    )

    Box(
        modifier = modifier
            .width(52.dp)
            .height(28.dp)
            .clip(RoundedCornerShape(14.dp))  // Fully rounded ends
            .background(trackColor)
            .clickable(
                indication = null,  // Remove ripple effect for cleaner look
                interactionSource = remember { MutableInteractionSource() }
            ) {
                onCheckedChange(!checked)
            }
            .padding(2.dp),  // Padding for thumb
        contentAlignment = Alignment.CenterStart
    ) {
        // Thumb (white circle)
        Box(
            modifier = Modifier
                .offset(x = thumbOffset)
                .size(20.dp)
                .clip(CircleShape)
                .background(thumbColor)
        )
    }
}
