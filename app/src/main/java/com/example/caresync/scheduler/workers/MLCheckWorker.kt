package com.example.caresync.scheduler.workers

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkerParameters
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.example.caresync.data.AppDatabase
import com.example.caresync.data.ReminderRepository
import com.example.caresync.domain.EventTypes
import com.example.caresync.domain.ReminderSettings
import com.example.caresync.scheduler.NotificationDecisionPipeline
import com.example.caresync.scheduler.SessionContextCollector
import com.example.caresync.scheduler.DeviceContext
import java.util.Calendar
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.nio.FloatBuffer

/**
 * ML Check Worker - FULLY CORRECTED VERSION (Multi-Reminder Aware)
 *
 * Responsibilities:
 * - Handle BOTH entry points: WorkManager (time-based) and Session-End (event-based)
 * - Collect device context using SessionContextCollector
 * - Call ML model for each Model Mode reminder
 * - Pass REAL reminder object (never null!) to pipeline
 * - Let priority system handle conflicts
 *
 * Two Entry Points (Data Paths):
 * ==============================
 * 1. WorkManager Path (ModelModeScheduler):
 *    - Input: "triggerSource" = "WORKMANAGER"
 *    - Logic: Get all Model Mode reminders from database
 *    - Context: SessionContextCollector.collectContextFromWorkManager()
 *    - Result: ML check every hour for all active reminders
 *
 * 2. Session-End Path (SessionAlarmReceiver):
 *    - Input: "triggerSource" = "SESSION_STILL_ACTIVE" or "SESSION_JUST_ENDED"
 *    - Logic: SessionAlarmReceiver already queries all Model Mode reminders
 *    - Context: SessionContextCollector.collectContextFromSessionEnd()
 *    - Result: Instant ML check when app threshold reached
 *
 * Critical Architecture:
 * ✅ Query ALL Model Mode reminders (from database)
 * ✅ For EACH reminder: Create separate ML check with REAL object
 * ✅ Pass REAL ReminderSettings to pipeline (never null!)
 * ✅ Each reminder gets proper context and decision
 * ✅ Priority system handles conflicts
 * ✅ NO dummy reminders, NO null problems
 */
class MLCheckWorker(
    private val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val repo = ReminderRepository(context)
    private val eventDao = AppDatabase.get(context).reminderEventDao()

    override suspend fun doWork(): Result {
        try {
            val triggerSource = inputData.getString("triggerSource") ?: "UNKNOWN"

            Log.d(TAG, """
                🚀 ML Check Worker started
                   Trigger: $triggerSource
            """.trimIndent())

            // ===== Extract data from inputData =====
            val packageName = inputData.getString("packageName") ?: ""
            val minsSinceOpen = inputData.getFloat("minsSinceOpen", 0.0f)

            // ===== Determine context based on trigger path =====
            val deviceContext = when (triggerSource) {
                "WORKMANAGER" -> {
                    Log.d(TAG, "📱 Using WorkManager path (time-based)")
                    SessionContextCollector.collectContextFromWorkManager(context)
                }
                "SESSION_STILL_ACTIVE", "SESSION_JUST_ENDED" -> {
                    Log.d(TAG, "📱 Using Session-Alarm path (event-based)")
                    // ✅ CHANGED: Use the new session-alarm collector with actual duration
                    SessionContextCollector.collectContextFromSessionAlarm(
                        context = context,
                        packageName = packageName,
                        minsSinceOpen = minsSinceOpen,
                        triggerSource = triggerSource
                    )
                }
                else -> {
                    Log.w(TAG, "Unknown trigger source: $triggerSource, using default")
                    SessionContextCollector.getDefaultContext()
                }
            }

            // Log collected context
            Log.d(TAG, """
                📊 Device Context:
                   Category: ${deviceContext.category}
                   Mins Since Open: ${String.format("%.2f", deviceContext.minsSinceOpen)}
                   Qualified Frequency: ${deviceContext.qualifiedFrequency}
                   Is Night: ${deviceContext.isNight}
                   Is Weekend: ${deviceContext.isWeekend}
            """.trimIndent())

            // ===== STEP 1: Call ML model =====
            val mlDecision = callMLModel(deviceContext)
            Log.d(TAG, "🤖 ML decision: ${if (mlDecision.shouldFire) "FIRE" else "SKIP"} (confidence: ${String.format("%.0f", mlDecision.confidence * 100)}%)")

            if (!mlDecision.shouldFire) {
                Log.d(TAG, "⏭️ ML said NO, skipping pipeline")
                return Result.success()
            }

            // ===== STEP 2: Boost confidence if in preferred time =====
            val boostedDecision = boostConfidenceIfPreferredTime(mlDecision)
            if (boostedDecision.confidence != mlDecision.confidence) {
                Log.d(TAG, "🚀 Boosted confidence: ${String.format("%.0f", mlDecision.confidence * 100)}% → ${String.format("%.0f", boostedDecision.confidence * 100)}%")
            }

            // ===== STEP 3: Determine which reminders to check =====
            val remindersToCheck = when (triggerSource) {
                "WORKMANAGER" -> {
                    // WorkManager path: Get all Model Mode reminders
                    Log.d(TAG, "📋 WorkManager path: Loading all Model Mode reminders...")
                    repo.getAllWithModelMode()
                }
                "SESSION_STILL_ACTIVE", "SESSION_JUST_ENDED" -> {
                    // Session-End path: SessionAlarmReceiver should have passed reminderId
                    val reminderId = inputData.getLong("reminderId", -1L)
                    if (reminderId != -1L) {
                        val reminder = repo.get(reminderId)
                        if (reminder != null) listOf(reminder) else emptyList()
                    } else {
                        // Fallback: Get all Model Mode reminders
                        repo.getAllWithModelMode()
                    }
                }
                else -> emptyList()
            }

            if (remindersToCheck.isEmpty()) {
                Log.d(TAG, "No reminders to check")
                return Result.success()
            }

            Log.d(TAG, """
                📊 Found ${remindersToCheck.size} reminders to check
                   ML: ${if (boostedDecision.shouldFire) "YES" else "NO"}
                   Confidence: ${String.format("%.0f", boostedDecision.confidence * 100)}%
            """.trimIndent())

            // ===== STEP 4: Run through pipeline for EACH reminder =====
            val pipeline = NotificationDecisionPipeline(context)

            for ((index, reminder) in remindersToCheck.withIndex()) {
                Log.d(TAG, """
                    ✅ Checking reminder $index/${remindersToCheck.size}
                       Title: ${reminder.title} (ID: ${reminder.id})
                """.trimIndent())

                // ✅ CRITICAL: Pass REAL reminder object (never null!)
                val decision = pipeline.shouldSendNotification(
                    reminder = reminder,  // ✅ REAL ReminderSettings!
                    mlPrediction = boostedDecision.shouldFire,
                    mlConfidence = boostedDecision.confidence,
                    bypassCooldown = false,
                    triggerSource = triggerSource
                )

                if (decision.shouldSend) {
                    Log.d(TAG, """
                        ✅ APPROVED: Notification will be sent
                           Reminder: ${reminder.title}
                           Reason: Pipeline approved
                    """.trimIndent())

                    // ✅ Fire notification with all context
                    fireNotification(reminder, deviceContext, boostedDecision.confidence)

                    // ✅ Log event
                    logMLCheckEvent(
                        reminderId = reminder.id,
                        eventType = EventTypes.TRIGGERED,
                        mlConfidence = boostedDecision.confidence,
                        triggerSource = triggerSource
                    )
                } else {
                    Log.d(TAG, """
                        🚫 BLOCKED: ${decision.blockingRule}
                           Reminder: ${reminder.title}
                           Reason: ${decision.reason}
                    """.trimIndent())

                    // ✅ Log rejection
                    logMLCheckEvent(
                        reminderId = reminder.id,
                        eventType = "ML_CHECK_BLOCKED",
                        mlConfidence = boostedDecision.confidence,
                        triggerSource = triggerSource,
                        reason = "${decision.blockingRule}: ${decision.reason}"
                    )
                }
            }

            return Result.success()

        } catch (e: Exception) {
            Log.e(TAG, "❌ ML check failed", e)
            return Result.retry()
        }
    }

    /**
     * Call ONNX ML model with device context
     */
    private fun callMLModel(deviceContext: DeviceContext): MLDecision {
        try {
            val session = OnnxModelHolder.getSession(context)

            if (session == null) {
                Log.w(TAG, "⚠️ ONNX model not available, using fallback")
                return fallbackMLDecision(deviceContext)
            }

            val inputs = createInputTensors(deviceContext)
            val outputs = session.run(inputs)
            val result = parseModelOutput(outputs)

            inputs.values.forEach { it.close() }
            outputs.forEach { it.value?.close() }

            return result

        } catch (e: Exception) {
            Log.e(TAG, "❌ ML inference failed", e)
            return fallbackMLDecision(deviceContext)
        }
    }

    /**
     * Create ONNX input tensors from device context
     */
    private fun createInputTensors(context: DeviceContext): Map<String, OnnxTensor> {
        val env = OrtEnvironment.getEnvironment()

        val categoryTensor = OnnxTensor.createTensor(
            env,
            arrayOf(arrayOf(context.category))
        )

        val minsBuffer = FloatBuffer.allocate(1)
        minsBuffer.put(context.minsSinceOpen)
        minsBuffer.rewind()
        val minsTensor = OnnxTensor.createTensor(env, minsBuffer, longArrayOf(1, 1))

        val freqBuffer = FloatBuffer.allocate(1)
        freqBuffer.put(context.qualifiedFrequency)
        freqBuffer.rewind()
        val freqTensor = OnnxTensor.createTensor(env, freqBuffer, longArrayOf(1, 1))

        val isNightTensor = OnnxTensor.createTensor(
            env,
            arrayOf(arrayOf(context.isNight))
        )

        val isWeekendTensor = OnnxTensor.createTensor(
            env,
            arrayOf(arrayOf(context.isWeekend))
        )

        return mapOf(
            "Category" to categoryTensor,
            "Mins_Since_Open" to minsTensor,
            "Qualified_Frequency" to freqTensor,
            "isNight" to isNightTensor,
            "Is_Weekend" to isWeekendTensor
        )
    }

    /**
     * Parse ONNX model output
     */
    private fun parseModelOutput(outputs: OrtSession.Result): MLDecision {
        try {
            val labelValue = outputs.get(0).value
            val label = when (labelValue) {
                is OnnxTensor -> (labelValue.longBuffer.get(0)).toInt()
                is LongArray -> labelValue[0].toInt()
                is Array<*> -> {
                    when (val first = labelValue[0]) {
                        is Long -> first.toInt()
                        is LongArray -> first[0].toInt()
                        else -> 0
                    }
                }
                else -> 0
            }

            val probValue = outputs.get(1).value
            val probClass1 = when (probValue) {
                is OnnxTensor -> {
                    val buffer = probValue.floatBuffer
                    if (buffer.remaining() >= 2) buffer.get(1) else 0.5f
                }
                is FloatArray -> if (probValue.size >= 2) probValue[1] else 0.5f
                is Array<*> -> {
                    when (val first = probValue[0]) {
                        is FloatArray -> if (first.size >= 2) first[1] else 0.5f
                        is Float -> first
                        else -> 0.5f
                    }
                }
                else -> 0.5f
            }

            return MLDecision(
                shouldFire = label == 1,
                confidence = probClass1
            )

        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to parse model output", e)
            return MLDecision(shouldFire = false, confidence = 0.0f)
        }
    }

    /**
     * Fallback ML decision if model fails
     */
    private fun fallbackMLDecision(deviceContext: DeviceContext): MLDecision {
        val shouldFire = deviceContext.minsSinceOpen >= 15.0f ||
                deviceContext.qualifiedFrequency >= 3.0f

        return MLDecision(
            shouldFire = shouldFire,
            confidence = if (shouldFire) 0.75f else 0.25f
        )
    }

    /**
     * Boost ML confidence if in preferred time
     */
    private fun boostConfidenceIfPreferredTime(baseDecision: MLDecision): MLDecision {
        // TODO: If you have preferred time learning, boost here
        // For now, return base decision
        return baseDecision
    }

    /**
     * Fire notification with context
     */
    private fun fireNotification(
        reminder: ReminderSettings,
        deviceContext: DeviceContext,
        confidence: Float
    ) {
        Log.d(TAG, "🔔 Firing notification: ${reminder.title}")
        // TODO: Implement your notification firing logic here
        // Pass reminder, deviceContext, and confidence for customization
    }

    /**
     * Log ML check event
     */
    private suspend fun logMLCheckEvent(
        reminderId: Long,
        eventType: String,
        mlConfidence: Float,
        triggerSource: String,
        reason: String? = null
    ) {
        try {
            val metadata = """{"mlConfidence":$mlConfidence,"trigger":"$triggerSource","reason":"${reason ?: "N/A"}"}"""
            eventDao.insert(
                com.example.caresync.data.ReminderEventEntity(
                    reminderId = reminderId,
                    eventType = eventType,
                    timestamp = System.currentTimeMillis(),
                    modelConfidence = mlConfidence,
                    triggerSource = triggerSource,
                    metadataJson = metadata
                )
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to log event", e)
        }
    }

    companion object {
        private const val TAG = "MLCheckWorker"

        /**
         * ✅ NEW: Trigger MLCheckWorker with REAL reminder object
         *
         * Called from SessionAlarmReceiver for each Model Mode reminder
         */
        fun triggerForReminder(
            context: Context,
            reminder: ReminderSettings,
            packageName: String,
            minsSinceOpen: Float,
            triggerSource: String
        ) {
            Log.d(TAG, "📋 Triggering MLCheckWorker for reminder: ${reminder.title}")

            val input = workDataOf(
                "triggerSource" to triggerSource,
                "reminderId" to reminder.id,  // ✅ Pass real ID!
                "packageName" to packageName,
                "minsSinceOpen" to minsSinceOpen
            )

            val request = OneTimeWorkRequestBuilder<MLCheckWorker>()
                .setInputData(input)
                .build()

            WorkManager.getInstance(context).enqueue(request)
        }

        /**
         * Legacy method (kept for compatibility, but use triggerForReminder instead)
         */
        fun triggerNowWithContext(
            context: Context,
            packageName: String,
            minsSinceOpen: Float,
            triggerSource: String
        ) {
            Log.d(TAG, "⚠️ triggerNowWithContext() called (legacy, use triggerForReminder())")

            val input = workDataOf(
                "triggerSource" to triggerSource,
                "packageName" to packageName,
                "minsSinceOpen" to minsSinceOpen
            )

            val request = OneTimeWorkRequestBuilder<MLCheckWorker>()
                .setInputData(input)
                .build()

            WorkManager.getInstance(context).enqueue(request)
        }

        // ===== ONNX Model Singleton =====
        private object OnnxModelHolder {
            private var ortEnvironment: OrtEnvironment? = null
            private var ortSession: OrtSession? = null
            private var modelLoadError: Exception? = null

            @Synchronized
            fun getSession(context: Context): OrtSession? {
                if (ortSession != null) return ortSession
                if (modelLoadError != null) return null

                try {
                    Log.d(TAG, "📦 Loading ONNX model...")

                    if (ortEnvironment == null) {
                        ortEnvironment = OrtEnvironment.getEnvironment()
                    }

                    val modelBytes = context.assets.open("ml_models/notification_model.onnx").use {
                        it.readBytes()
                    }

                    ortSession = ortEnvironment!!.createSession(modelBytes)

                    Log.d(TAG, "✅ ONNX model loaded (${modelBytes.size / 1024} KB)")

                    return ortSession

                } catch (e: Exception) {
                    modelLoadError = e
                    Log.e(TAG, "❌ Failed to load ONNX model", e)
                    return null
                }
            }

            fun cleanup() {
                ortSession?.close()
                ortSession = null
                ortEnvironment = null
                modelLoadError = null
            }
        }
    }
}

/**
 * ML model decision result
 */
data class MLDecision(
    val shouldFire: Boolean,
    val confidence: Float
)
