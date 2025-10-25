package com.example.caresync.scheduler.workers

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.caresync.data.AppDatabase
import com.example.caresync.data.ReminderRepository
import com.example.caresync.domain.EventTypes
import com.example.caresync.scheduler.NotificationDecisionPipeline
import java.util.Calendar
// ✅ NEW: ONNX Runtime imports
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.nio.FloatBuffer
import java.nio.LongBuffer
/**
 * ML Check Worker - Runs periodically for Model mode tasks
 *
 * Responsibilities:
 * - Collect device context (app usage, session length)
 * - Call ML model to decide if notification should fire
 * - Check min occurrence quota
 * - Fire notification if (ML says YES) OR (quota not met)
 *
 * Scheduled by: ModelModeScheduler (one worker per Model mode task)
 * Job ID format: "ml-check-{reminderId}"
 */
class MLCheckWorker(
    private val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val repo = ReminderRepository(context)
    private val eventDao = AppDatabase.get(context).reminderEventDao()

    override suspend fun doWork(): Result {
        // Get task ID from input data
        val reminderId = inputData.getLong("reminderId", -1L)
        if (reminderId == -1L) {
            Log.e(TAG, "No reminderId provided")
            return Result.failure()
        }

        Log.d(TAG, "⏰ ML check started for task: $reminderId")

        // Load task settings
        val reminder = repo.get(reminderId) ?: run {
            Log.e(TAG, "Task $reminderId not found")
            return Result.failure()
        }

        // Check if task still enabled
        if (!reminder.enabled) {
            Log.d(TAG, "Task $reminderId is disabled, skipping")
            return Result.success()
        }

        try {
            // STEP 1: Collect device context
            val deviceContext = collectDeviceContext()
            Log.d(TAG, "📱 Device context: $deviceContext")

            // STEP 2: Call ML model
            val mlDecision = callMLModel(deviceContext)
            Log.d(TAG, "🤖 ML decision: ${mlDecision.shouldFire} (confidence: ${mlDecision.confidence})")

            // STEP 3: Check min occurrence quota
            val quotaCheck = checkMinOccurrenceQuota(reminder)
            Log.d(TAG, "📊 Quota: ${quotaCheck.fired}/${quotaCheck.required} (force: ${quotaCheck.shouldForce})")

            // STEP 4: Decide whether to fire
            val shouldFire = mlDecision.shouldFire || quotaCheck.shouldForce

            if (shouldFire) {
                // STEP 5: Run through decision pipeline
                val pipeline = NotificationDecisionPipeline(context)
                val decision = pipeline.shouldSendNotification(
                    reminder,
                    mlPrediction = mlDecision.shouldFire,
                    mlConfidence = mlDecision.confidence,
                    bypassCooldown = false,  // ✅ ADD: Apply cooldown for ML checks
                    triggerSource = "ML_CHECK"  // ✅ ADD: Mark as ML check
                )

                if (decision.shouldSend) {
                    // Fire notification with full context
                    fireNotification(reminder, mlDecision.confidence, deviceContext)  // ✅ ADD deviceContext
                    Log.d(TAG, "🔔 Notification fired for task: $reminderId")
                }
                else {
                    // Blocked by pipeline
                    Log.d(TAG, "🚫 Blocked by ${decision.blockingRule}: ${decision.reason}")
                    logBlockedEvent(reminderId, decision.blockingRule, decision.reason)
                }
            } else {
                Log.d(TAG, "⏭️ Skipping notification (ML: NO, Quota: OK)")
            }

            return Result.success()

        } catch (e: Exception) {
            Log.e(TAG, "❌ ML check failed for task $reminderId", e)
            return Result.retry() // Retry on failure
        }finally {
            // Optional: Log memory usage for debugging
            val runtime = Runtime.getRuntime()
            val usedMemory = (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024
            Log.d(TAG, "💾 Memory usage: ${usedMemory}MB")
        }
    }

    /**
     * STEP 1: Collect device context for ML model
     */
    private fun collectDeviceContext(): DeviceContext {
        try {
            // Get StateDetector from Application
            val app = context.applicationContext as com.example.caresync.CareSyncApplication
            val stateDetector = app.stateDetector

            // Check device state first
            when (stateDetector.getCurrentState()) {
                com.example.caresync.utils.DeviceState.OFF -> {
                    Log.d(TAG, "📴 Device state: OFF")
                    return DeviceContext(
                        category = "OFF",
                        minsSinceOpen = 0.0f,
                        qualifiedFrequency = 0.0f,
                        isNight = if (isNightTime()) "Yes" else "No",
                        isWeekend = if (isWeekend()) "Yes" else "No"
                    )
                }
                com.example.caresync.utils.DeviceState.IDLE -> {
                    Log.d(TAG, "😴 Device state: IDLE")
                    return DeviceContext(
                        category = "IDLE",
                        minsSinceOpen = 0.0f,
                        qualifiedFrequency = 0.0f,
                        isNight = if (isNightTime()) "Yes" else "No",
                        isWeekend = if (isWeekend()) "Yes" else "No"
                    )
                }
                else -> {
                    // Device is ACTIVE, continue to get app category
                }
            }

            // Get UsageStatsManager
            val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE)
                    as? android.app.usage.UsageStatsManager

            if (usageStatsManager == null) {
                Log.w(TAG, "⚠️ UsageStatsManager not available")
                return getDefaultContext()
            }

            // Query last hour's usage
            val now = System.currentTimeMillis()
            val oneHourAgo = now - 60 * 60 * 1000L

            val usageStats = usageStatsManager.queryUsageStats(
                android.app.usage.UsageStatsManager.INTERVAL_BEST,
                oneHourAgo,
                now
            )

            if (usageStats.isNullOrEmpty()) {
                Log.w(TAG, "⚠️ No usage stats available")
                return getDefaultContext()
            }

            // Find most recently used app
            val mostRecentApp = usageStats.maxByOrNull { it.lastTimeUsed }

            if (mostRecentApp == null) {
                return getDefaultContext()
            }

            // Update state detector (mark activity)
            stateDetector.updateActivity()

            // Get app label
            val pm = context.packageManager
            val appLabel = try {
                pm.getApplicationLabel(pm.getApplicationInfo(mostRecentApp.packageName, 0)).toString()
            } catch (e: Exception) {
                mostRecentApp.packageName // Fallback to package name
            }

            // Map to category using CategoryMapper
            val categoryResult = com.example.caresync.utils.CategoryMapper.getCategory(appLabel)

            // Calculate session length
            val sessionLength = ((now - mostRecentApp.lastTimeUsed) / 1000.0 / 60.0).toFloat()
            val minsSinceOpen = minOf(sessionLength, 180.0f)

            // Calculate qualified frequency
            val qualifiedFrequency = usageStats.count { stat ->
                val duration = stat.totalTimeInForeground / 1000.0 / 60.0
                duration >= 5.0
            }.toFloat()

            val context = DeviceContext(
                category = categoryResult.category,
                minsSinceOpen = minsSinceOpen,
                qualifiedFrequency = qualifiedFrequency,
                isNight = if (isNightTime()) "Yes" else "No",
                isWeekend = if (isWeekend()) "Yes" else "No"
            )

            Log.d(TAG, "📱 Context: app=$appLabel, category=${categoryResult.category} (${categoryResult.matchType}, ${categoryResult.confidence}%), session=${minsSinceOpen}min, freq=$qualifiedFrequency")

            return context

        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to collect device context", e)
            return getDefaultContext()
        }
    }

    /**
     * Default context when data collection fails
     */
    private fun getDefaultContext(): DeviceContext {
        return DeviceContext(
            category = "Unknown",
            minsSinceOpen = 0.0f,
            qualifiedFrequency = 0.0f,
            isNight = if (isNightTime()) "Yes" else "No",
            isWeekend = if (isWeekend()) "Yes" else "No"
        )
    }


    /**
     * STEP 2: Call ONNX ML model
     */
    private fun callMLModel(deviceContext: DeviceContext): MLDecision {
        try {
            // Get ONNX session (cached singleton)
            val session = OnnxModelHolder.getSession(context)

            if (session == null) {
                Log.w(TAG, "⚠️ ONNX model not available, using fallback logic")
                return fallbackMLDecision(deviceContext)
            }

            // Create input tensors
            // NEW (without allocator):
            val inputs = createInputTensors(deviceContext)

            // Run inference
            val outputs = session.run(inputs)

            // Parse outputs
            val result = parseModelOutput(outputs)

            // Cleanup tensors
            inputs.values.forEach { it.close() }
            outputs.forEach { it.value?.close() }

            Log.d(TAG, "🤖 ML inference: ${result.shouldFire} (confidence: ${result.confidence})")
            return result

        } catch (e: Exception) {
            Log.e(TAG, "❌ ML inference failed, using fallback", e)
            return fallbackMLDecision(deviceContext)
        }
    }

    /**
     * Create ONNX input tensors from device context
     */
    private fun createInputTensors(
        context: DeviceContext
    ): Map<String, OnnxTensor> {

        val env = OrtEnvironment.getEnvironment()

        // Input 1: Category (String)
        val categoryTensor = OnnxTensor.createTensor(
            env,
            arrayOf(arrayOf(context.category))  // Shape: [1, 1]
        )

        // Input 2: Mins_Since_Open (Float)
        val minsBuffer = FloatBuffer.allocate(1)
        minsBuffer.put(context.minsSinceOpen)
        minsBuffer.rewind()
        val minsTensor = OnnxTensor.createTensor(
            env,
            minsBuffer,
            longArrayOf(1, 1)  // Shape: [1, 1]
        )

        // Input 3: Qualified_Frequency (Float)
        val freqBuffer = FloatBuffer.allocate(1)
        freqBuffer.put(context.qualifiedFrequency)
        freqBuffer.rewind()
        val freqTensor = OnnxTensor.createTensor(
            env,
            freqBuffer,
            longArrayOf(1, 1)  // Shape: [1, 1]
        )

        // Input 4: isNight (String)
        val isNightTensor = OnnxTensor.createTensor(
            env,
            arrayOf(arrayOf(context.isNight))  // Shape: [1, 1]
        )

        // Input 5: Is_Weekend (String)
        val isWeekendTensor = OnnxTensor.createTensor(
            env,
            arrayOf(arrayOf(context.isWeekend))  // Shape: [1, 1]
        )

        // Return map with EXACT names from your ONNX model
        return mapOf(
            "Category" to categoryTensor,
            "Mins_Since_Open" to minsTensor,
            "Qualified_Frequency" to freqTensor,
            "isNight" to isNightTensor,
            "Is_Weekend" to isWeekendTensor
        )
    }

    /**
     * Parse ONNX model output (handles multiple output formats)
     *
     * Model outputs:
     * - Output 0: Label (0 or 1) - Can be OnnxTensor, LongArray, or Array
     * - Output 1: Probabilities [prob_class_0, prob_class_1] - Can be OnnxTensor, FloatArray, or Array
     */
    private fun parseModelOutput(outputs: OrtSession.Result): MLDecision {
        try {
            // Output 0: Label (0 or 1)
            val labelValue = outputs.get(0).value
            val label = when (labelValue) {
                is OnnxTensor -> {
                    // Standard ONNX tensor format
                    (labelValue.longBuffer.get(0)).toInt()
                }
                is LongArray -> {
                    // Direct array format (common in some ONNX versions)
                    labelValue[0].toInt()
                }
                is Array<*> -> {
                    // Nested array format
                    when (val first = labelValue[0]) {
                        is Long -> first.toInt()
                        is LongArray -> first[0].toInt()
                        else -> {
                            Log.w(TAG, "Unknown nested label type: ${first?.javaClass?.simpleName}")
                            0
                        }
                    }
                }
                else -> {
                    Log.w(TAG, "Unknown label type: ${labelValue.javaClass.simpleName}")
                    0
                }
            }

            // Output 1: Probabilities [prob_class_0, prob_class_1]
            val probValue = outputs.get(1).value
            val probClass1 = when (probValue) {
                is OnnxTensor -> {
                    // Standard ONNX tensor format
                    val buffer = probValue.floatBuffer
                    if (buffer.remaining() >= 2) {
                        buffer.get(1)  // Get probability for class 1
                    } else {
                        Log.w(TAG, "Probability buffer has insufficient data")
                        0.5f
                    }
                }
                is FloatArray -> {
                    // Direct array format
                    if (probValue.size >= 2) {
                        probValue[1]
                    } else {
                        Log.w(TAG, "Probability array too short: ${probValue.size}")
                        0.5f
                    }
                }
                is Array<*> -> {
                    // Nested array format
                    when (val first = probValue[0]) {
                        is FloatArray -> {
                            if (first.size >= 2) first[1] else 0.5f
                        }
                        is Float -> first
                        else -> {
                            Log.w(TAG, "Unknown nested probability type: ${first?.javaClass?.simpleName}")
                            0.5f
                        }
                    }
                }
                else -> {
                    Log.w(TAG, "Unknown probability type: ${probValue.javaClass.simpleName}")
                    0.5f
                }
            }

            Log.d(TAG, "✅ ML output parsed: label=$label (${if (label == 1) "FIRE" else "SKIP"}), confidence=${String.format("%.2f", probClass1 * 100)}%")

            return MLDecision(
                shouldFire = label == 1,
                confidence = probClass1
            )

        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to parse model output", e)
            Log.e(TAG, "Output 0 type: ${outputs.get(0).value.javaClass.simpleName}")
            Log.e(TAG, "Output 1 type: ${outputs.get(1).value.javaClass.simpleName}")

            // Return safe fallback
            return MLDecision(
                shouldFire = false,
                confidence = 0.0f
            )
        }
    }

    /**
     * Fallback ML decision (if ONNX model fails or not available)
     * Uses simple rule-based logic
     */
    private fun fallbackMLDecision(deviceContext: DeviceContext): MLDecision {
        // Simple rules from your training logic
        val shouldFire = deviceContext.minsSinceOpen >= 15.0f ||
                deviceContext.qualifiedFrequency >= 3.0f

        return MLDecision(
            shouldFire = shouldFire,
            confidence = if (shouldFire) 0.75f else 0.25f  // Lower confidence for fallback
        )
    }


    /**
     * STEP 3: Check min occurrence quota for THIS task
     */
    private suspend fun checkMinOccurrenceQuota(reminder: com.example.caresync.domain.ReminderSettings): QuotaCheck {
        val minOccurrence = reminder.repeatInterval ?: 0

        // ✅ If min occurrence is 0, no quota enforcement (pure ML mode)
        if (minOccurrence <= 0) {
            Log.d(TAG, "📊 Quota: Pure ML mode (min occurrence = 0, no fallback)")
            return QuotaCheck(fired = 0, required = 0, shouldForce = false)
        }

        val unit = reminder.repeatIntervalUnit ?: com.example.caresync.domain.IntervalUnit.HOUR

        // Get time window based on unit
        val windowStart = when (unit) {
            com.example.caresync.domain.IntervalUnit.HOUR -> {
                System.currentTimeMillis() - 60 * 60 * 1000L
            }
            com.example.caresync.domain.IntervalUnit.DAY -> {
                getMidnightToday()
            }
            com.example.caresync.domain.IntervalUnit.MINUTE -> {
                // Minute-based quotas (e.g., "5 notifications per 30 minutes")
                val minutes = reminder.repeatInterval ?: 60
                System.currentTimeMillis() - (minutes * 60 * 1000L)
            }
        }

        // Count TRIGGERED events for THIS task in window
        val firedCount = eventDao.getEventsBetween(
            reminder.id,
            windowStart,
            System.currentTimeMillis()
        ).count { it.eventType == EventTypes.TRIGGERED }

        val shouldForce = firedCount < minOccurrence

        Log.d(TAG, "📊 Quota: $firedCount/$minOccurrence per $unit (force fallback: $shouldForce)")

        return QuotaCheck(
            fired = firedCount,
            required = minOccurrence,
            shouldForce = shouldForce
        )
    }

    /**
     * STEP 5: Fire notification
     */
    private suspend fun fireNotification(
        reminder: com.example.caresync.domain.ReminderSettings,
        mlConfidence: Float,
        deviceContext: DeviceContext
    ) {
        // ✅ GENERATE MESSAGE AND GET TONE FIRST
        val (personalizedMessage, actualTone) = try {
            com.example.caresync.messaging.MessageGenerator(context).generateMessage(reminder)
        } catch (e: Exception) {
            Pair(reminder.notes ?: "Time to work!", "AUTO")
        }

        // ✅ CREATE EVENT WITH ACTUAL TONE
        val event = createEventWithContext(
            reminderId = reminder.id,
            reminder = reminder,
            actualTone = actualTone,  // ← ADD THIS
            deviceContext = deviceContext,
            mlConfidence = mlConfidence,
            triggerSource = "ML_MODEL"
        )

        // Insert event
        eventDao.insert(event)

        Log.d(TAG, "🔔 Logged TRIGGERED event with full metadata")

        // Show notification
        com.example.caresync.scheduler.ReminderWorker.showNotificationFromML(
            context,
            reminder.id,
            reminder.title,
            personalizedMessage
        )
    }


    /**
     * Log blocked event
     */
    private suspend fun logBlockedEvent(reminderId: Long, rule: String?, reason: String) {
        repo.logEvent(
            reminderId,
            "BLOCKED",
            """{"rule":"$rule","reason":"$reason","source":"ML_CHECK"}"""
        )
    }

    // ==========================================
    // HELPER FUNCTIONS
    // ==========================================

    private fun isNightTime(): Boolean {
        val hourNow = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return hourNow in 0..5 // 12 AM - 6 AM
    }

    private fun isWeekend(): Boolean {
        val dayOfWeek = Calendar.getInstance().get(Calendar.DAY_OF_WEEK)
        return dayOfWeek == Calendar.SATURDAY || dayOfWeek == Calendar.SUNDAY
    }

    private fun getMidnightToday(): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    private fun getStartOfWeek(): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    companion object {
        private const val TAG = "MLCheckWorker"

        // ✅ NEW: Singleton for ONNX model (load once, reuse)
        private object OnnxModelHolder {
            private var ortEnvironment: OrtEnvironment? = null
            private var ortSession: OrtSession? = null
            private var modelLoadError: Exception? = null

            @Synchronized
            fun getSession(context: Context): OrtSession? {
                // Return cached session if already loaded
                if (ortSession != null) return ortSession

                // If previous load failed, don't retry every time
                if (modelLoadError != null) {
                    Log.w(TAG, "Model load failed previously: ${modelLoadError?.message}")
                    return null
                }

                try {
                    Log.d(TAG, "📦 Loading ONNX model...")

                    // Create environment (once)
                    if (ortEnvironment == null) {
                        ortEnvironment = OrtEnvironment.getEnvironment()
                    }

                    // Load model from assets
                    val modelBytes = context.assets.open("ml_models/notification_model.onnx").use {
                        it.readBytes()
                    }

                    // Create session
                    ortSession = ortEnvironment!!.createSession(modelBytes)

                    Log.d(TAG, "✅ ONNX model loaded successfully (${modelBytes.size / 1024} KB)")
                    Log.d(TAG, "Model inputs: ${ortSession!!.inputNames}")
                    Log.d(TAG, "Model outputs: ${ortSession!!.outputNames}")

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
    /**
     * Create event with full context metadata
     */
    private fun createEventWithContext(
        reminderId: Long,
        reminder: com.example.caresync.domain.ReminderSettings,
        actualTone: String,
        deviceContext: DeviceContext,
        mlConfidence: Float,
        triggerSource: String
    ): com.example.caresync.data.ReminderEventEntity {
        val now = Calendar.getInstance()

        return com.example.caresync.data.ReminderEventEntity(
            reminderId = reminderId,
            eventType = EventTypes.TRIGGERED,
            timestamp = System.currentTimeMillis(),

            // Time context
            hourOfDay = now.get(Calendar.HOUR_OF_DAY),
            dayOfWeek = now.get(Calendar.DAY_OF_WEEK) - 1,
            isWeekend = now.get(Calendar.DAY_OF_WEEK) in listOf(
                Calendar.SATURDAY,
                Calendar.SUNDAY
            ),

            // Device context
            deviceState = deviceContext.category,  // "OFF", "IDLE", "ACTIVE", or app category
            activeAppCategory = if (deviceContext.category !in listOf("OFF", "IDLE", "Unknown")) {
                deviceContext.category
            } else null,
            screenTimeMinutes = deviceContext.minsSinceOpen.toInt(),
            batteryLevel = getBatteryLevel(context),

            // Notification details
            notificationPriority = reminder.priority.name,
            notificationMethod = reminder.notifyMethods.firstOrNull()?.name ?: "PUSH",
            toneUsed = reminder.toneUri,
            vibrationUsed = reminder.vibration,

            // ML model data
            modelConfidence = mlConfidence,
            triggerSource = triggerSource,

            // Metadata JSON
            metadataJson = """
        {
            "minsSinceOpen": ${deviceContext.minsSinceOpen},
            "qualifiedFrequency": ${deviceContext.qualifiedFrequency},
            "isNight": "${deviceContext.isNight}",
            "isWeekend": "${deviceContext.isWeekend}"
        }
        """.trimIndent()
        )
    }

    /**
     * Get battery level
     */
    private fun getBatteryLevel(context: Context): Int? {
        return try {
            val batteryIntent = context.registerReceiver(
                null,
                android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED)
            )
            val level = batteryIntent?.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = batteryIntent?.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1) ?: -1

            if (level >= 0 && scale > 0) {
                (level * 100 / scale)
            } else null
        } catch (e: Exception) {
            null
        }
    }
}

// ==========================================
// DATA CLASSES
// ==========================================

/**
 * Device context collected for ML model input
 */
data class DeviceContext(
    val category: String,
    val minsSinceOpen: Float,
    val qualifiedFrequency: Float,
    val isNight: String,
    val isWeekend: String
)

/**
 * ML model decision result
 */
data class MLDecision(
    val shouldFire: Boolean,
    val confidence: Float
)

/**
 * Quota check result for min occurrence
 */
data class QuotaCheck(
    val fired: Int,
    val required: Int,
    val shouldForce: Boolean
)
