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
import com.example.caresync.analytics.domain.ProductivityInsight
import com.example.caresync.analytics.domain.getColorForType
import com.example.caresync.analytics.domain.getIconForType

@Composable
fun InsightsSection(
    insights: List<ProductivityInsight>,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Header
        Text(
            text = "💡 Insights",
            fontSize = 18.sp,
            color = Color.White,
            fontWeight = FontWeight.Bold
        )

        if (insights.isEmpty()) {
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
                        text = "No insights yet. Complete more tasks!",
                        color = Color(0xFFAAAAAA),
                        fontSize = 14.sp
                    )
                }
            }
        } else {
            insights.forEach { insight ->
                InsightCard(insight = insight)
            }
        }
    }
}

@Composable
private fun InsightCard(
    insight: ProductivityInsight,
    onActionClick: ((ProductivityInsight) -> Unit)? = null  // ← Add parameter
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF221F2C)
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(  // ← Change Row to Column
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left border color indicator
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .height(48.dp)
                        .background(
                            color = Color(insight.getColorForType()),
                            shape = RoundedCornerShape(2.dp)
                        )
                )

                Spacer(modifier = Modifier.width(12.dp))

                // Icon
                Text(
                    text = insight.getIconForType(),
                    fontSize = 24.sp
                )

                Spacer(modifier = Modifier.width(12.dp))

                // Message
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = insight.message,
                        color = Color.White,
                        fontSize = 14.sp
                    )
                }
            }

            // ✅ ACTION BUTTON (if actionable)
            if (insight.actionable && insight.actionText != null && onActionClick != null) {
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedButton(
                    onClick = { onActionClick(insight) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color(0xFF9C27B0)
                    )
                ) {
                    Text(insight.actionText)
                }
            }
        }
    }
}