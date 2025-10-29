package com.example.caresync.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.caresync.R

// 30 guilt-trip templates
val UNBLOCK_GUILT_TEMPLATES = listOf(
    "Oops! Stay strong next time! 💪",
    "Discipline builds momentum. You can do it!",
    "Every unlock is a lost battle. Try again!",
    "Focus is a muscle—keep training it! 🧠",
    "You got this! Don't give up now! ✨",
    "One step back, but you can still move forward!",
    "Distraction won this time. You'll win next time! 🎯",
    "Keep pushing! Success needs consistency! 🚀",
    "It's okay. Tomorrow is a new chance! 🌅",
    "Remember why you started! 💡",
    "Small setbacks, big comebacks! 💥",
    "You're stronger than you think! 💪",
    "This won't stop you. Keep going! 🔥",
    "Every master was once a beginner! 📚",
    "You learn more from falling than standing still!",
    "Progress, not perfection! 🎨",
    "You'll thank yourself later! ⏰",
    "Focus today, freedom tomorrow! 🌟",
    "Great things take time! ⏳",
    "Don't quit. You're almost there! 🏁",
    "Your future self is counting on you! 🔮",
    "Success is built one choice at a time! 🧱",
    "You can rest after you achieve it! 💤",
    "Distractions are temporary, goals are forever! 🎯",
    "Stay focused. Stay hungry! 🔥",
    "Winners don't unlock early! 🏆",
    "You're on the right path! Keep walking! 🚶",
    "Delay gratification, earn greatness! 👑",
    "One more hour of focus = One step closer! 📈",
    "You got distracted. It happens. Reset! 🔄"
)

/**
 * Send a guilt notification when user unlocks a blocked app
 * This bypasses all notification pipelines, quotas, and analytics
 */
fun sendUnblockGuiltNotification(context: Context) {
    val message = UNBLOCK_GUILT_TEMPLATES.random()
    val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    val channelId = "unblock_guilt"

    // Create notification channel (Android 8+)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val channel = NotificationChannel(
            channelId,
            "App Unblock Reminders",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Motivation messages when you unlock blocked apps"
        }
        notificationManager.createNotificationChannel(channel)
    }

    // Build notification (NO action buttons, just message)
    val notification = NotificationCompat.Builder(context, channelId)
        .setSmallIcon(R.drawable.ic_notification)  // Use your existing icon
        .setContentTitle("Focus Interrupted 😔")
        .setContentText(message)
        .setStyle(NotificationCompat.BigTextStyle().bigText(message))
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setAutoCancel(true)  // Dismiss when tapped
        .build()

    // Send notification (unique ID based on timestamp)
    notificationManager.notify(System.currentTimeMillis().toInt(), notification)
}
