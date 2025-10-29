package com.example.caresync.voice

import android.content.Context
import android.os.Build
import com.example.caresync.data.ProfileDataStore
import com.example.caresync.messaging.MessageTone
import kotlinx.coroutines.flow.first
import java.util.Calendar

/**
 * Enhanced Voice Message Adapter
 *
 * Features:
 * - Time-aware greetings (Good morning/afternoon/evening)
 * - SSML prosody for emotional voice control
 * - 10 greeting variations per tone (reduced repetition)
 * - 10 wrapper variations per tone (more variety)
 * - Smart SSML fallback for unsupported devices
 */
class VoiceMessageAdapter(private val context: Context) {

    private val profileDataStore = ProfileDataStore(context)

    /**
     * Adapt notification message for voice delivery
     */
    suspend fun adaptForVoice(
        textMessage: String,
        tone: MessageTone
    ): String {
        // Get user's name
        val (username, _, _) = profileDataStore.profileData.first()
        val name = username.ifEmpty { "there" }

        // Clean emojis
        val cleanMessage = cleanEmojis(textMessage)

        // Get time-aware greeting
        val greeting = getTimeAwareGreeting(name, tone)

        // Get tone wrapper
        val wrapper = getToneWrapper(tone)

        // Build message
        val voiceMessage = buildVoiceMessage(greeting, cleanMessage, wrapper)

        // ✅ Add SSML prosody for emotional control
        return addSSMLProsody(voiceMessage, tone)
    }

    /**
     * Remove emojis that don't speak well
     */
    private fun cleanEmojis(message: String): String {
        return message
            .replace(Regex("[🌙💤🛌🌟✨💙🔔⏰💨🕐📢🎉🏆🔥💪😔⚠️🚨🔴⏳🎮🗡️🎯🌿💰📊📈🎓🏃‍♂️🧘‍♀️💻📚🎨📅📋⏰📌✅❌💬🔕😴🎯💡🌟⭐✨🎉🙌👏💪🔥🚀]"), "")
            .trim()
            .replace("  ", " ")
    }

    /**
     * ✅ NEW: Time-aware greeting with tone variation
     */
    private fun getTimeAwareGreeting(name: String, tone: MessageTone): String {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val timeOfDay = when (hour) {
            in 5..11 -> "morning"
            in 12..16 -> "afternoon"
            in 17..20 -> "evening"
            else -> "night"
        }

        return when (tone) {
            MessageTone.ENCOURAGING -> listOf(
                "Good $timeOfDay $name!",
                "Hi $name!",
                "Hey $name,",
                "Hello $name!",
                "$name, good $timeOfDay!",
                "Hey there $name!",
                "Hi there $name,",
                "$name!",
                "Good $timeOfDay! Hey $name,",
                "Hello $name, hope you're doing well!"
            ).random()

            MessageTone.PLAYFUL -> listOf(
                "Yo $name!",
                "Hey $name!",
                "Hi there $name!",
                "What's up $name!",
                "$name! Ready?",
                "Hey hey $name!",
                "Hiya $name!",
                "Sup $name!",
                "$name! Time to shine!",
                "Alright $name!"
            ).random()

            MessageTone.GUILT_TRIP -> listOf(
                "$name...",
                "Listen $name,",
                "You know $name,",
                "$name,",
                "Come on $name...",
                "$name, we need to talk.",
                "Honestly $name...",
                "$name, you promised.",
                "Look $name,",
                "$name... really?"
            ).random()

            MessageTone.AGGRESSIVE -> listOf(
                "$name.",
                "Attention $name.",
                "Listen up $name.",
                "$name, now.",
                "Enough $name.",
                "$name, this is serious.",
                "Wake up $name.",
                "$name, no more excuses.",
                "Stop $name.",
                "$name, final warning."
            ).random()

            MessageTone.CELEBRATORY -> listOf(
                "Amazing work $name!",
                "Incredible $name!",
                "Wow $name!",
                "$name, this is awesome!",
                "Outstanding $name!",
                "You're crushing it $name!",
                "Phenomenal $name!",
                "$name, you're unstoppable!",
                "Legendary $name!",
                "Bravo $name!"
            ).random()

            MessageTone.URGENT -> listOf(
                "$name, urgent reminder!",
                "Important $name!",
                "$name, attention needed!",
                "Priority alert $name!",
                "$name, this can't wait!",
                "Critical $name!",
                "$name, immediate action required!",
                "Urgent $name!",
                "$name, time sensitive!",
                "Alert $name!"
            ).random()

            MessageTone.MOTIVATING -> listOf(
                "$name, you've got this!",
                "Ready $name?",
                "Let's go $name!",
                "$name, time to shine!",
                "Make it happen $name!",
                "$name, show them what you're made of!",
                "You're capable $name!",
                "$name, seize the moment!",
                "Believe in yourself $name!",
                "$name, go for it!"
            ).random()

            else -> "Hey $name,"
        }
    }

    /**
     * ✅ EXPANDED: 10 wrapper variations per tone
     */
    private fun getToneWrapper(tone: MessageTone): String? {
        return when (tone) {
            MessageTone.ENCOURAGING -> listOf(
                "You can do this!",
                "I believe in you!",
                "Let's make it happen!",
                "You've got this!",
                "Keep going!",
                "Stay strong!",
                "You're doing great!",
                "One step at a time!",
                "I'm rooting for you!",
                "You're capable of amazing things!"
            ).random()

            MessageTone.PLAYFUL -> listOf(
                "Let's have some fun!",
                "Game on!",
                "Ready for action?",
                "Let's do this!",
                "Adventure awaits!",
                "Time to play!",
                "Let's rock!",
                "It's go time!",
                "Buckle up!",
                null  // Sometimes no wrapper
            ).random()

            MessageTone.CELEBRATORY -> listOf(
                "Keep it up!",
                "You're on fire!",
                "Amazing work!",
                "Don't stop now!",
                "Momentum is yours!",
                "You're unstoppable!",
                "Keep the streak alive!",
                "You're crushing it!",
                "This is your time!",
                null
            ).random()

            MessageTone.MOTIVATING -> listOf(
                "Make it count!",
                "Your goals are waiting!",
                "Success is calling!",
                "Seize the day!",
                "Be the best version of yourself!",
                "Show the world what you can do!",
                "Your future self will thank you!",
                "Rise to the challenge!",
                "Make yourself proud!",
                null
            ).random()

            // No wrappers for GUILT_TRIP, AGGRESSIVE, URGENT
            else -> null
        }
    }

    /**
     * ✅ NEW: Add SSML prosody for emotional voice control
     */
    private fun addSSMLProsody(message: String, tone: MessageTone): String {
        // Only apply SSML if device supports it (Android 6+)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return message  // Fallback to plain text
        }

        return when (tone) {
            // ✅ URGENT: Faster, louder, stressed
            MessageTone.URGENT ->
                "<speak><emphasis level='strong'><prosody rate='110%' volume='loud'>$message</prosody></emphasis></speak>"

            // ✅ GUILT_TRIP: Slower, softer, disappointed
            MessageTone.GUILT_TRIP ->
                "<speak><prosody rate='85%' volume='medium' pitch='-5%'>$message</prosody></speak>"

            // ✅ AGGRESSIVE: Forceful, firm
            MessageTone.AGGRESSIVE ->
                "<speak><emphasis level='strong'><prosody rate='95%' volume='loud'>$message</prosody></emphasis></speak>"

            // ✅ CELEBRATORY: Upbeat, enthusiastic
            MessageTone.CELEBRATORY ->
                "<speak><prosody rate='105%' pitch='+10%' volume='loud'>$message</prosody></speak>"

            // ✅ PLAYFUL: Light, bouncy
            MessageTone.PLAYFUL ->
                "<speak><prosody rate='100%' pitch='+5%'>$message</prosody></speak>"

            // ✅ ENCOURAGING: Warm, steady
            MessageTone.ENCOURAGING ->
                "<speak><prosody rate='95%' pitch='+2%'>$message</prosody></speak>"

            // MOTIVATING: Confident, clear
            MessageTone.MOTIVATING ->
                "<speak><prosody rate='100%' volume='medium'>$message</prosody></speak>"

            else -> message  // No SSML for AUTO
        }
    }

    /**
     * Assemble final voice message
     */
    private fun buildVoiceMessage(
        greeting: String,
        cleanMessage: String,
        wrapper: String?
    ): String {
        return if (wrapper != null) {
            "$greeting $cleanMessage $wrapper"
        } else {
            "$greeting $cleanMessage"
        }
    }
}
