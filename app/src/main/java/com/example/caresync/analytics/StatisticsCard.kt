package com.example.caresync.analytics

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun StatisticsCard(
    emoji: String,
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    valueColor: Color = Color.White
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF221F2C)  // Dark Gray
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = emoji,
                fontSize = 32.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = value,
                color = valueColor,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = label,
                color = Color(0xFFAAAAAA),  // Light Gray
                fontSize = 12.sp
            )
        }
    }
}
