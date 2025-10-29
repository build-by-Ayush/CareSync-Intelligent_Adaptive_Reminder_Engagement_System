package com.example.caresync.ui.components

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.caresync.utils.AppBlockManager
import com.example.caresync.utils.sendUnblockGuiltNotification

class BlockScreenActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val packageName = intent.getStringExtra("packageName") ?: ""
        val remaining = intent.getIntExtra("remaining", 0)

        setContent {
            BlockScreen(
                packageName = packageName,
                remainingMinutes = remaining,
                onUnlock = {
                    // Remove block
                    AppBlockManager.unblockApp(packageName)

                    // Send guilt notification
                    sendUnblockGuiltNotification(this)

                    // Close this screen
                    finish()
                }
            )
        }
    }
}

@Composable
fun BlockScreen(
    packageName: String,
    remainingMinutes: Int,
    onUnlock: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            // Icon
            Text(
                text = "🚫",
                fontSize = 72.sp,
                modifier = Modifier.padding(bottom = 24.dp)
            )

            // Title - ✅ UPDATED
            Text(
                text = "App Blocked by CareSync",
                color = Color.White,
                fontSize = 28.sp,  // Slightly smaller for better fit
                fontWeight = FontWeight.Bold,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,  // ✅ ADD THIS
                modifier = Modifier.fillMaxWidth()  // ✅ ADD THIS
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Remaining time
            Text(
                text = "Unblocks in $remainingMinutes minutes",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 18.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center  // ✅ ADD THIS
            )

            Spacer(modifier = Modifier.height(48.dp))

            // Unlock button
            Button(
                onClick = onUnlock,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF750182)
                ),
                modifier = Modifier
                    .fillMaxWidth(0.7f)
                    .height(56.dp)
            ) {
                Text(
                    text = "Unlock Anyway",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}
