package com.example.caresync.analytics

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip  // ✅ ADD THIS
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.caresync.intelligence.riskdetection.RiskLevel
import com.example.caresync.intelligence.riskdetection.RiskSuggestion
import java.util.Locale


/**
 * Risk Detection Section for Analytics Dashboard
 * Shows tasks with low completion rates and suggests improvements
 *
 * Location: Displayed as 6th component in AnalyticsScreen
 * alongside other 5 charts (CompletionRateChart, ProductivityHoursChart, etc.)
 */
@Composable
fun RiskDetectionSection(
    atRiskCount: Int,
    riskSuggestions: List<RiskSuggestion>,
    onViewDetails: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (atRiskCount == 0) return  // Don't show if no risks

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Section header with badge
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "⚠️ Tasks at Risk",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1A1A1A)
            )
            if (atRiskCount > 0) {
                Badge(
                    modifier = Modifier.padding(start = 8.dp),
                    containerColor = Color(0xFFD32F2F),  // ✅ CHANGED: backgroundColor -> containerColor
                    contentColor = Color.White
                ) {
                    Text(
                        atRiskCount.toString(),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp)
                    )
                }
            }
        }

        // Risk Banner (clickable to view all details)
        RiskBanner(
            atRiskCount = atRiskCount,
            onViewDetails = onViewDetails
        )

        // Show first 2 at-risk tasks as quick preview
        riskSuggestions.take(2).forEach { risk ->
            RiskTaskCard(risk)
        }

        // "View all" link if more than 2 tasks
        if (riskSuggestions.size > 2) {
            Text(
                "View all ${riskSuggestions.size} at-risk tasks ->",  // ✅ CHANGED: → to ->
                fontSize = 12.sp,
                color = Color(0xFF560154),
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clickable { onViewDetails() }
                    .padding(8.dp)
            )
        }
    }
}

/**
 * Risk Banner - clickable banner showing at-risk count
 */
@Composable
private fun RiskBanner(
    atRiskCount: Int,
    onViewDetails: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onViewDetails() },
        color = Color(0xFFFFEBEE),  // Light red background
        shadowElevation = 4.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                // Warning icon
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "Risk warning",
                    tint = Color(0xFFD32F2F),
                    modifier = Modifier.size(24.dp)
                )

                // Text content
                Column {
                    Text(
                        "$atRiskCount tasks need attention",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFD32F2F)
                    )
                    Text(
                        "Low completion rate detected",
                        fontSize = 12.sp,
                        color = Color(0xFF666666)
                    )
                }
            }

            // Arrow indicator
            Text(
                "->",  // ✅ CHANGED: → to ->
                fontSize = 20.sp,
                color = Color(0xFFD32F2F),
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/**
 * Individual risk task card - shown as preview
 */
@Composable
private fun RiskTaskCard(risk: RiskSuggestion) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp)),
        color = when (risk.riskLevel) {
            RiskLevel.CRITICAL -> Color(0xFFFFCDD2)  // ✅ CHANGED: → to ->
            RiskLevel.HIGH -> Color(0xFFFFF9C4)      // ✅ CHANGED: → to ->
            RiskLevel.MEDIUM -> Color(0xFFF1F8E9)    // ✅ CHANGED: → to ->
        },
        shadowElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Task title + completion percentage
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    risk.taskTitle,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF1A1A1A),
                    modifier = Modifier.weight(1f)
                )

                // Completion percentage badge
                Surface(
                    color = when (risk.riskLevel) {
                        RiskLevel.CRITICAL -> Color(0xFFD32F2F)  // ✅ CHANGED: → to ->
                        RiskLevel.HIGH -> Color(0xFFF57F17)      // ✅ CHANGED: → to ->
                        RiskLevel.MEDIUM -> Color(0xFF558B2F)    // ✅ CHANGED: → to ->
                    },
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.padding(start = 8.dp)
                ) {
                    Text(
                        "${risk.completionPercentage}%",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }

            // Main suggestion/action
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp)),
                color = Color.White,
                shadowElevation = 1.dp
            ) {
                Text(
                    risk.mainAction,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF560154),
                    modifier = Modifier.padding(8.dp)
                )
            }
        }
    }
}

/**
 * Risk Detection Modal - shows all at-risk tasks with full details
 */
@Composable
fun RiskDetectionModal(
    riskSuggestions: List<RiskSuggestion>,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "⚠️ Tasks at Risk (${riskSuggestions.size})",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1A1A1A)
            )
        },
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(riskSuggestions) { risk ->
                    RiskDetailCard(risk)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = Color(0xFF560154)
                )
            ) {
                Text("Close", fontWeight = FontWeight.Bold)
            }
        },
        containerColor = Color.White,
        titleContentColor = Color(0xFF1A1A1A)
    )
}

/**
 * Detailed risk card - shown in modal with full suggestion list
 */
@Composable
private fun RiskDetailCard(risk: RiskSuggestion) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp)),
        color = when (risk.riskLevel) {
            RiskLevel.CRITICAL -> Color(0xFFFFCDD2)  // ✅ CHANGED: → to ->
            RiskLevel.HIGH -> Color(0xFFFFF9C4)      // ✅ CHANGED: → to ->
            RiskLevel.MEDIUM -> Color(0xFFF1F8E9)    // ✅ CHANGED: → to ->
        },
        shadowElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Title + completion percentage
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    risk.taskTitle,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1A1A1A),
                    modifier = Modifier.weight(1f)
                )

                // Risk badge
                Surface(
                    color = when (risk.riskLevel) {
                        RiskLevel.CRITICAL -> Color(0xFFD32F2F)  // ✅ CHANGED: → to ->
                        RiskLevel.HIGH -> Color(0xFFF57F17)      // ✅ CHANGED: → to ->
                        RiskLevel.MEDIUM -> Color(0xFF558B2F)    // ✅ CHANGED: → to ->
                    },
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Text(
                        "${risk.completionPercentage}%",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }

            // Main action suggestion
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp)),
                color = Color.White,
                shadowElevation = 1.dp
            ) {
                Text(
                    risk.mainAction,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF560154),
                    modifier = Modifier.padding(8.dp)
                )
            }

            // Detailed suggestions list
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                risk.suggestions.forEach { suggestion ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            "•",
                            fontSize = 11.sp,
                            color = Color(0xFF424242),
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            suggestion,
                            fontSize = 11.sp,
                            color = Color(0xFF424242),
                            lineHeight = 16.sp
                        )
                    }
                }
            }

            // Data quality indicator
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = Color(0xFFFAFAFA),
                        shape = RoundedCornerShape(6.dp)
                    )
                    .padding(6.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "ℹ️",
                    fontSize = 11.sp
                )
                Text(
                    "Based on ${risk.dataPoints} hours (${String.format(Locale.US, "%.0f", risk.confidence * 100)}% confident)",  // ✅ ADDED: Locale
                    fontSize = 10.sp,
                    color = Color(0xFF999999),
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                )
            }
        }
    }
}
