package com.example.caresync.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.example.caresync.domain.ReminderSettings
import com.example.caresync.messaging.MessageTone
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Manages voice notifications using Android TTS
 *
 * Features:
 * - Queue-based message delivery (waits for TTS to initialize)
 * - Automatic cleanup after 5 seconds if TTS fails
 * - Callback-based initialization (no arbitrary waits)
 */
class VoiceNotificationManager(private val context: Context) {

    private var tts: TextToSpeech? = null
    private var isReady = false
    private val pendingMessages = mutableListOf<PendingSpeech>()

    init {
        initializeTTS()
        startTimeoutWatchdog()
    }

    /**
     * Initialize TTS with callback
     */
    private fun initializeTTS() {
        Log.d("VoiceTTS", "⏳ Initializing TTS...")

        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.US
                isReady = true
                Log.d("VoiceTTS", "✅ TTS initialized successfully")

                // ✅ Speak all queued messages
                CoroutineScope(Dispatchers.Main).launch {
                    processPendingMessages()
                }
            } else {
                Log.e("VoiceTTS", "❌ TTS initialization failed")
                pendingMessages.clear()  // Clear queue on failure
            }
        }

        // Set progress listener
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                Log.d("VoiceTTS", "🔊 Speaking started: $utteranceId")
            }

            override fun onDone(utteranceId: String?) {
                Log.d("VoiceTTS", "✅ Speaking completed: $utteranceId")
            }

            override fun onError(utteranceId: String?) {
                Log.e("VoiceTTS", "❌ Speaking error: $utteranceId")
            }
        })
    }

    /**
     * Process all pending messages in queue
     */
    private suspend fun processPendingMessages() {
        if (pendingMessages.isEmpty()) return

        Log.d("VoiceTTS", "📋 Processing ${pendingMessages.size} pending messages")

        pendingMessages.forEach { pending ->
            speakNow(pending.voiceMessage, pending.taskId, pending.voiceModel)
            delay(100)  // Small delay between messages
        }

        pendingMessages.clear()
    }

    /**
     * Safety watchdog: Clear queue after 5 seconds if TTS never initializes
     */
    private fun startTimeoutWatchdog() {
        CoroutineScope(Dispatchers.Default).launch {
            delay(5000)  // 5 seconds timeout

            if (!isReady && pendingMessages.isNotEmpty()) {
                Log.w("VoiceTTS", "⏰ Timeout: TTS didn't initialize in 5s, clearing ${pendingMessages.size} messages")
                pendingMessages.clear()
            }
        }
    }

    /**
     * Main entry point: Speak a notification
     */
    fun speakNotification(
        reminder: ReminderSettings,
        textMessage: String,
        tone: MessageTone,
        voiceModel: String
    ) {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                // Adapt message for voice
                val adapter = VoiceMessageAdapter(context)
                val voiceMessage = adapter.adaptForVoice(textMessage, tone)

                if (isReady) {
                    // ✅ TTS ready, speak immediately
                    speakNow(voiceMessage, reminder.id, voiceModel)
                } else {
                    // ⏳ TTS not ready, queue it
                    pendingMessages.add(
                        PendingSpeech(
                            voiceMessage = voiceMessage,
                            taskId = reminder.id,
                            voiceModel = voiceModel
                        )
                    )
                    Log.d("VoiceTTS", "⏳ Queued message (TTS initializing...)")
                }
            } catch (e: Exception) {
                Log.e("VoiceTTS", "Error preparing message", e)
            }
        }
    }

    /**
     * Actually speak the message (TTS must be ready)
     */
    private fun speakNow(voiceMessage: String, taskId: Long, voiceModel: String) {
        if (!isReady) {
            Log.w("VoiceTTS", "Cannot speak: TTS not ready")
            return
        }

        try {
            // Set voice based on model
            when (voiceModel) {
                "Female" -> {
                    // Try best female voices in order of quality
                    val femaleVoice = tts?.voices?.firstOrNull {
                        it.name.contains("en-us-x-tpf-local", ignoreCase = true)  // Best female
                    } ?: tts?.voices?.firstOrNull {
                        it.name.contains("en-us-x-iog-local", ignoreCase = true)  // Backup female
                    } ?: tts?.voices?.firstOrNull {
                        it.name.contains("female", ignoreCase = true)  // Any female
                    } ?: tts?.defaultVoice  // Final fallback

                    tts?.voice = femaleVoice
                    Log.d("VoiceTTS", "🎤 Using female voice: ${femaleVoice?.name}")
                }
                "Male" -> {
                    // Try best male voices in order of quality
                    val maleVoice = tts?.voices?.firstOrNull {
                        it.name.contains("en-us-x-tpm-local", ignoreCase = true)  // Best male
                    } ?: tts?.voices?.firstOrNull {
                        it.name.contains("en-us-x-iom-local", ignoreCase = true)  // Backup male
                    } ?: tts?.voices?.firstOrNull {
                        it.name.contains("male", ignoreCase = true) &&
                                !it.name.contains("female", ignoreCase = true)  // Any male
                    } ?: tts?.defaultVoice  // Final fallback

                    tts?.voice = maleVoice
                    Log.d("VoiceTTS", "🎤 Using male voice: ${maleVoice?.name}")
                }
                else -> {
                    // Fallback to default
                    tts?.voice = tts?.defaultVoice
                    Log.d("VoiceTTS", "🎤 Using default voice: ${tts?.defaultVoice?.name}")
                }
            }

            // Speak
            val utteranceId = "task_$taskId"
            tts?.speak(voiceMessage, TextToSpeech.QUEUE_ADD, null, utteranceId)

            Log.d("VoiceTTS", "🔊 Speaking now: $voiceMessage")
        } catch (e: Exception) {
            Log.e("VoiceTTS", "Error speaking", e)
        }
    }

    /**
     * Cleanup TTS resources
     */
    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        pendingMessages.clear()
        Log.d("VoiceTTS", "🛑 TTS shutdown")
    }
}

/**
 * Data class for queued speech
 */
private data class PendingSpeech(
    val voiceMessage: String,
    val taskId: Long,
    val voiceModel: String
)
