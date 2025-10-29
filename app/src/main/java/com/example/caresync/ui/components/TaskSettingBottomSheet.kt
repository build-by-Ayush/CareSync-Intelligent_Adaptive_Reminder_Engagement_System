package com.example.caresync.ui.components

import android.content.Context
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.ui.window.Dialog
import com.example.caresync.domain.*
import java.text.SimpleDateFormat
import java.util.*
import android.net.Uri
import android.provider.ContactsContract
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.filled.ContactPage
import androidx.compose.foundation.BorderStroke



@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskSettingBottomSheet(
    task: ReminderSettings?,
    onDismiss: () -> Unit,
    onSave: (ReminderSettings) -> Unit
) {
    var name by remember(task) { mutableStateOf(task?.title ?: "") }
    var selectedPriority by remember(task) {
        mutableStateOf(
            when (task?.priority) {
                Priority.LOW -> "Low"
                Priority.NORMAL -> "Medium"
                Priority.HIGH, Priority.CRITICAL -> "High"
                null -> "Low"  // ← Changed from "Medium"
            }
        )
    }

    val scope = rememberCoroutineScope()

    var selectedTimePeriods by remember(task) {
        mutableStateOf(
            task?.allowedTimePeriods?.map { it.name }?.toSet()
                ?: setOf("MORNING", "AFTERNOON", "EVENING")
        )
    }

    var selectedMode by remember(task) {
        mutableStateOf(
            when (task?.triggerMode) {
                TriggerMode.MODEL_ASSISTED, TriggerMode.HYBRID -> "Model"
                TriggerMode.FIXED_TIME, TriggerMode.MANUAL -> "Repetitive"
                null -> "Model"  // ← Changed from "Repetitive"
            }
        )
    }

    var selectedWeekdaysForHours by remember(task) {
        mutableStateOf(if (task?.recurrenceType == RecurrenceType.DAILY) task.daysOfWeek else setOf())
    }

    var selectedWeekdaysForWeekdaysMode by remember(task) {
        mutableStateOf(if (task?.recurrenceType == RecurrenceType.WEEKLY) task.daysOfWeek else setOf())
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    // Pre-fill time from task
    var selectedHour by remember(task) {
        mutableIntStateOf(
            task?.timeOfDayMillis?.let { millis ->
                ((millis / (1000 * 60 * 60)) % 24).toInt()
            } ?: 9
        )
    }
    var selectedMinute by remember(task) {
        mutableIntStateOf(
            task?.timeOfDayMillis?.let { millis ->
                ((millis / (1000 * 60)) % 60).toInt()
            } ?: 0
        )
    }
    var vibrationEnabled by remember(task) { mutableStateOf(task?.vibration ?: true) }

    // ✅ Multi-select state (Set instead of single String)
    var selectedReminderMethods by remember(task) {
        mutableStateOf(
            task?.notifyMethods?.toSet() ?: setOf(NotifyMethod.PUSH)
        )
    }

    val context = LocalContext.current  // Get context OUTSIDE remember

    var selectedApp by remember(task) {
        mutableStateOf<AppInfo?>(
            task?.targetAppPackage?.let { pkg ->
                getInstalledApps(context).find { it.packageName == pkg }
            }
        )
    }
    var showAppPicker by remember { mutableStateOf(false) }
    var selectedTone by remember(task) {
        mutableStateOf(
            when (task?.toneUri) {
                "AUTO" -> "🤖 Auto"
                "ENCOURAGING" -> "💙 Encouraging"
                "PLAYFUL" -> "😄 Playful"
                "GUILT_TRIP" -> "😔 Guilt-Trip"
                "AGGRESSIVE" -> "💪 Aggressive"
                null -> "🤖 Auto"
                else -> "🤖 Auto"
            }
        )
    }
    var toneExpanded by remember { mutableStateOf(false) }

    // ✅ NEW: Store selected snooze minutes
    var selectedSnoozeDuration by remember(task) {
        mutableIntStateOf(task?.snoozeDurationMinutes ?: 10)
    }

    // ✅ NEW: Boost Mode states
    var showBoostDialog by remember { mutableStateOf(false) }
    var boostDurationHours by remember(task) {
        mutableIntStateOf(if (task?.boostModeActive == true) {
            val remaining = (task.boostModeEndTime ?: 0L) - System.currentTimeMillis()
            (remaining / (60 * 60 * 1000)).toInt().coerceAtLeast(1)
        } else 2)
    }
    var boostFrequency by remember(task) {
        mutableIntStateOf(task?.boostModeFrequency ?: 5)
    }
    var isBoostActive by remember(task) {
        mutableStateOf(task?.boostModeActive == true)
    }

    var selectedUnit by remember(task) {
        mutableStateOf(
            when {
                task?.repeatIntervalUnit == IntervalUnit.HOUR -> "Per Hours"
                task?.repeatIntervalUnit == IntervalUnit.DAY && (task.repeatInterval ?: 0) >= 7 -> "Per Week"
                task?.repeatIntervalUnit == IntervalUnit.DAY -> "Per Days"
                else -> "Per Days"
            }
        )
    }
    var minOccurrenceCount by remember(task) {
        mutableIntStateOf(
            when {
                task?.repeatIntervalUnit == IntervalUnit.HOUR -> task.repeatInterval ?: 1
                task?.repeatIntervalUnit == IntervalUnit.DAY && (task.repeatInterval ?: 0) >= 7 -> (task.repeatInterval ?: 7) / 7
                task?.repeatIntervalUnit == IntervalUnit.DAY -> task.repeatInterval ?: 1
                else -> 1
            }
        )
    }
    var recurrenceType by remember(task) {
        mutableStateOf(
            when (task?.recurrenceType) {
                RecurrenceType.WEEKLY -> "Weekdays"
                RecurrenceType.DAILY -> {
                    // If daysOfWeek is set, it's Hours mode
                    // If no daysOfWeek but repeatInterval is set, it's Days mode
                    if (task.daysOfWeek.isNotEmpty()) "Hours"
                    else if (task.repeatInterval != null && task.repeatInterval > 0) "Days"
                    else "Hours"
                }
                else -> "Hours"
            }
        )
    }
    var notificationsPerDay by remember(task) {
        mutableIntStateOf(
            if (task != null && task.repeatInterval != null && task.repeatInterval > 0) {
                task.repeatInterval
            } else {
                1
            }
        )
    }

    // ✅ FIXED: Voice model with task dependency
    var selectedVoiceModel by remember(task) {
        mutableStateOf(task?.voiceModel ?: "Female")
    }

    // ✅ FIXED: Share Progress with task dependencies
    var shareProgressEnabled by remember(task) {
        mutableStateOf(task?.shareProgressEnabled ?: false)
    }
    var contactName by remember(task) {
        mutableStateOf(task?.shareProgressContactName ?: "")
    }
    var contactPhone by remember(task) {
        mutableStateOf(task?.shareProgressContactPhone ?: "")
    }
    var sendDailyReport by remember(task) {
        mutableStateOf(task?.sendDailyReport ?: true)
    }
    var sendWeeklyReport by remember(task) {
        mutableStateOf(task?.sendWeeklyReport ?: false)
    }
    var sendStrugglingAlerts by remember(task) {
        mutableStateOf(task?.sendStrugglingAlerts ?: false)
    }

    var showDatePicker by remember { mutableStateOf(false) }
    var selectedDueDate by remember(task) { mutableStateOf(task?.dueDate) }

    // ✅ FIXED: Contact picker launcher
    val contactPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickContact()
    ) { uri: Uri? ->
        uri?.let {
            try {
                // ✅ FIX: Query the correct URI with proper projection
                val projection = arrayOf(
                    ContactsContract.Contacts.DISPLAY_NAME,
                    ContactsContract.Contacts._ID
                )

                // First, get contact name and ID
                val cursor = context.contentResolver.query(
                    it,
                    projection,
                    null,
                    null,
                    null
                )

                cursor?.use { c ->
                    if (c.moveToFirst()) {
                        val nameIndex = c.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME)
                        val idIndex = c.getColumnIndex(ContactsContract.Contacts._ID)

                        contactName = c.getString(nameIndex) ?: ""
                        val contactId = c.getString(idIndex)

                        // ✅ FIX: Now query phone number separately using contact ID
                        val phoneProjection = arrayOf(
                            ContactsContract.CommonDataKinds.Phone.NUMBER
                        )

                        val phoneUri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
                        val phoneSelection = "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?"
                        val phoneSelectionArgs = arrayOf(contactId)

                        val phoneCursor = context.contentResolver.query(
                            phoneUri,
                            phoneProjection,
                            phoneSelection,
                            phoneSelectionArgs,
                            null
                        )

                        phoneCursor?.use { pc ->
                            if (pc.moveToFirst()) {
                                val phoneIndex = pc.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                                val rawPhone = pc.getString(phoneIndex) ?: ""

                                // Clean phone number (remove spaces, dashes, +91, etc.)
                                contactPhone = rawPhone.filter { it.isDigit() }.takeLast(10)
                            } else {
                                // No phone number found
                                contactPhone = ""
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("ContactPicker", "Error picking contact", e)
                // Reset on error
                contactName = ""
                contactPhone = ""
            }
        }
    }

    // Add this right after you initialize the variables
    LaunchedEffect(task) {
        Log.d("TaskSheet", "=== TASK LOADED ===")
        Log.d("TaskSheet", "Voice Model: ${task?.voiceModel}")
        Log.d("TaskSheet", "Share Progress: ${task?.shareProgressEnabled}")
        Log.d("TaskSheet", "Contact Name: ${task?.shareProgressContactName}")
        Log.d("TaskSheet", "Contact Phone: ${task?.shareProgressContactPhone}")
        Log.d("TaskSheet", "Daily: ${task?.sendDailyReport}")
        Log.d("TaskSheet", "Weekly: ${task?.sendWeeklyReport}")
        Log.d("TaskSheet", "Struggling: ${task?.sendStrugglingAlerts}")
    }


    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = null,
        containerColor = Color.Black
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            Spacer(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsTopHeight(WindowInsets.statusBars)
                    .background(Color.Black)
            )

            // 🔹 Header
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .background(
                        color = Color(0xFF560154),
                        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = Color.White, fontSize = 20.sp)
                    }
                    TextButton(
                        onClick = {
                            if (name.isNotEmpty()) {
                                // Calculate time of day in milliseconds
                                val timeOfDay = (selectedHour * 60L + selectedMinute) * 60L * 1000L

                                // Determine recurrence based on mode
                                val recurrenceTypeEnum = when {
                                    selectedMode == "Model" -> RecurrenceType.INTERVAL
                                    selectedMode == "Repetitive" && recurrenceType == "Hours" -> RecurrenceType.DAILY
                                    selectedMode == "Repetitive" && recurrenceType == "Days" -> RecurrenceType.DAILY
                                    selectedMode == "Repetitive" && recurrenceType == "Weekdays" -> RecurrenceType.WEEKLY
                                    else -> RecurrenceType.NONE
                                }

                                // Set repeat interval based on mode and recurrence type
                                val (finalRepeatInterval, finalIntervalUnit) = when {
                                    // Model mode
                                    selectedMode == "Model" && selectedUnit == "Per Hours" -> minOccurrenceCount to IntervalUnit.HOUR
                                    selectedMode == "Model" && selectedUnit == "Per Days" -> minOccurrenceCount to IntervalUnit.DAY
                                    selectedMode == "Model" && selectedUnit == "Per Week" -> (minOccurrenceCount * 7) to IntervalUnit.DAY
                                    // Repetitive Days mode
                                    selectedMode == "Repetitive" && recurrenceType == "Days" -> notificationsPerDay to IntervalUnit.DAY
                                    // Repetitive Weekdays mode
                                    selectedMode == "Repetitive" && recurrenceType == "Weekdays" -> notificationsPerDay to IntervalUnit.DAY
                                    else -> null to null
                                }

                                // Get selected weekdays based on mode
                                val weekdays = when {
                                    selectedMode == "Repetitive" && recurrenceType == "Hours" -> selectedWeekdaysForHours
                                    selectedMode == "Repetitive" && recurrenceType == "Weekdays" -> selectedWeekdaysForWeekdaysMode
                                    else -> emptySet()
                                }

                                val updatedTask = (task ?: ReminderSettings(title = "")).copy(
                                    title = name,

                                    // ✅ NEW: Auto-enable when saving
                                    enabled = true,

                                    // Priority and mode
                                    priority = when (selectedPriority) {
                                        "Low" -> Priority.LOW
                                        "High" -> Priority.HIGH
                                        else -> Priority.NORMAL
                                    },
                                    triggerMode = when (selectedMode) {
                                        "Model" -> TriggerMode.MODEL_ASSISTED
                                        else -> TriggerMode.FIXED_TIME
                                    },
                                    // Time settings
                                    timeOfDayMillis = timeOfDay,
                                    scheduledAtMillis = null,
                                    // Recurrence settings
                                    recurrenceType = recurrenceTypeEnum,
                                    repeatInterval = finalRepeatInterval,
                                    repeatIntervalUnit = finalIntervalUnit,
                                    daysOfWeek = weekdays,
                                    // Notification settings
                                    notifyMethods = selectedReminderMethods.toSet(),
                                    dueDate = selectedDueDate,

                                    // ✅ NEW: Voice model (ADD THIS)
                                    voiceModel = if (selectedReminderMethods.contains(NotifyMethod.VOICE))
                                        selectedVoiceModel else null,

                                    // ✅ NEW: Share Progress settings (ADD THESE 6 LINES)
                                    shareProgressEnabled = shareProgressEnabled,
                                    shareProgressContactName = if (shareProgressEnabled) contactName.takeIf { it.isNotEmpty() } else null,
                                    shareProgressContactPhone = if (shareProgressEnabled) contactPhone.takeIf { it.length == 10 } else null,
                                    sendDailyReport = if (shareProgressEnabled) sendDailyReport else false,
                                    sendWeeklyReport = if (shareProgressEnabled) sendWeeklyReport else false,
                                    sendStrugglingAlerts = if (shareProgressEnabled) sendStrugglingAlerts else false,


                                    smsNumber = null,  // ✅ SMS feature removed, always null

                                    // Tone - YOUR EXISTING CODE
                                    toneUri = when (selectedTone) {
                                        "Auto (Recommended)" -> "AUTO"
                                        "Encouraging" -> "ENCOURAGING"
                                        "Playful" -> "PLAYFUL"
                                        "Guilt-Trip" -> "GUILTTRIP"
                                        "Aggressive" -> "AGGRESSIVE"
                                        else -> "AUTO"
                                    },
                                    // Vibration - YOUR EXISTING CODE
                                    vibration = vibrationEnabled,
                                    // App target - YOUR EXISTING CODE
                                    targetAppPackage = selectedApp?.packageName,
                                    // Snooze duration - YOUR EXISTING CODE
                                    snoozeDurationMinutes = selectedSnoozeDuration,
                                    // Time periods - YOUR EXISTING CODE
                                    allowedTimePeriods = selectedTimePeriods.mapNotNull {
                                        try {
                                            com.example.caresync.domain.TimePeriod.valueOf(it)
                                        } catch (e: Exception) {
                                            null
                                        }
                                    },
                                    // Timestamp - YOUR EXISTING CODE
                                    updatedAt = System.currentTimeMillis()
                                )

                                onSave(updatedTask)

                                Log.d("TaskSheet", "=== SAVING ===")
                                Log.d("TaskSheet", "Voice Model: $selectedVoiceModel")
                                Log.d("TaskSheet", "Share Progress: $shareProgressEnabled")
                                Log.d("TaskSheet", "Contact: $contactName - $contactPhone")
                            }
                        }
                    ) {
                        Text("Save", color = Color.White, fontSize = 20.sp)
                    }
                }
            }
            // 🔹 SCROLLABLE CONTENT - Add verticalScroll here
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF11101C))
                    .verticalScroll(rememberScrollState()) // ✅ THIS IS THE KEY FIX
                    .padding(16.dp)
            ) {
                // 🔹 Task Name
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Task Name", color = Color.White, fontSize = 16.sp) },
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color(0xFF3E3951),
                        unfocusedContainerColor = Color(0xFF3E3951),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        cursorColor = Color.White,
                        focusedIndicatorColor = Color(0xFF750182),
                        unfocusedIndicatorColor = Color(0xFF3E3951)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = TextStyle(fontSize = 18.sp, color = Color.White)
                )

                Spacer(modifier = Modifier.height(24.dp))

                // 🔹 Priority
                Text("Priority", color = Color.White, fontSize = 18.sp, modifier = Modifier.padding(bottom = 8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    val priorities = listOf("Low", "Medium", "High")
                    priorities.forEach { priority ->
                        val isSelected = priority == selectedPriority
                        Button(
                            onClick = { selectedPriority = priority },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isSelected) Color(0xFF262131) else Color(0xFF3E3951),
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f).padding(horizontal = 4.dp)
                        ) {
                            Text(priority, fontSize = 16.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // 🔹 Mode Buttons
                Text("Mode", color = Color.White, fontSize = 18.sp, modifier = Modifier.padding(bottom = 8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    val modes = listOf("Model", "Repetitive")
                    modes.forEach { mode ->
                        val isSelected = mode == selectedMode
                        Button(
                            onClick = { selectedMode = mode },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isSelected) Color(0xFF560154) else Color(0xFF3E3951),
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f).padding(horizontal = 4.dp)
                        ) {
                            Text(mode, fontSize = 16.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // 🔹 Mode-specific content
                if (selectedMode == "Model") {
                    // -------------------- MODEL MODE --------------------
                    Text("Minimum Occurrence", color = Color.White, fontSize = 18.sp, modifier = Modifier.padding(bottom = 15.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // ... your existing Model mode content (dropdown + counter) ...
                        // ✅ UPDATED: Unit dropdown - no "Per Hours" in Model Mode
                        val units = if (selectedMode == "Model") {
                            listOf("Per Days", "Per Week")  // Model Mode: Only day/week options
                        } else {
                            listOf("Per Hours", "Per Days", "Per Week")  // Repetitive Mode: All options
                        }

                        var expanded by remember { mutableStateOf(false) }

                        Box {
                            Button(
                                onClick = { expanded = true },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF262131)),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.width(230.dp).height(36.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(selectedUnit, color = Color.White, fontSize = 16.sp, modifier = Modifier.padding(end = 4.dp))
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = "Select unit", tint = Color.White)
                                }
                            }

                            DropdownMenu(
                                expanded = expanded,
                                onDismissRequest = { expanded = false },
                                containerColor = Color(0xFF262131)
                            ) {
                                units.forEach { unit ->
                                    DropdownMenuItem(
                                        text = { Text(unit, color = Color.White) },
                                        onClick = {
                                            selectedUnit = unit
                                            expanded = false
                                        }
                                    )
                                }
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .background(Color(0xFF3E3951), RoundedCornerShape(50))
                                .padding(horizontal = 18.dp, vertical = 3.dp)
                        ) {
                            IconButton(onClick = { if (minOccurrenceCount > 1) minOccurrenceCount-- }, modifier = Modifier.size(30.dp)) {
                                Icon(Icons.Filled.Remove, contentDescription = "Decrease", tint = Color.White)
                            }
                            Text(minOccurrenceCount.toString(), color = Color.White, fontSize = 18.sp, modifier = Modifier.padding(horizontal = 10.dp))
                            IconButton(onClick = { minOccurrenceCount++ }, modifier = Modifier.size(30.dp)) {
                                Icon(Icons.Filled.Add, contentDescription = "Increase", tint = Color.White)
                            }
                        }
                    }

                } else if (selectedMode == "Repetitive") {

                    // -------------------- REPETITIVE MODE --------------------
                    // ... your existing Repetitive mode content (recurrence dropdown + pickers) ...
                    val recurrenceOptions = listOf("Hours", "Days", "Weekdays")
                    var recurrenceMenuExpanded by remember { mutableStateOf(false) }

                    Text("Advanced Recurrence", color = Color.White, fontSize = 18.sp, modifier = Modifier.padding(bottom = 8.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box {
                            Button(
                                onClick = { recurrenceMenuExpanded = true },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3E3951)),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.width(180.dp).height(48.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(recurrenceType, color = Color.White, fontSize = 16.sp, modifier = Modifier.padding(end = 4.dp))
                                    Icon(Icons.Filled.ArrowDropDown, contentDescription = "Select recurrence type", tint = Color.White)
                                }
                            }

                            DropdownMenu(
                                expanded = recurrenceMenuExpanded,
                                onDismissRequest = { recurrenceMenuExpanded = false },
                                containerColor = Color(0xFF262131)
                            ) {
                                recurrenceOptions.forEach { option ->
                                    DropdownMenuItem(
                                        text = { Text(option, color = Color.White) },
                                        onClick = {
                                            recurrenceType = option
                                            recurrenceMenuExpanded = false
                                        }
                                    )
                                }
                            }
                        }

                        if (recurrenceType == "Weekdays") {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "Notifications per day",
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    modifier = Modifier.padding(bottom = 4.dp)
                                )
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .background(Color(0xFF3E3951), RoundedCornerShape(50))
                                        .padding(horizontal = 20.dp, vertical = 3.dp)
                                ) {
                                    IconButton(onClick = { if (notificationsPerDay > 1) notificationsPerDay-- }, modifier = Modifier.size(30.dp)) {
                                        Icon(Icons.Filled.Remove, contentDescription = "Decrease", tint = Color.White)
                                    }
                                    Text(notificationsPerDay.toString(), color = Color.White, fontSize = 18.sp, modifier = Modifier.padding(horizontal = 10.dp))
                                    IconButton(onClick = { notificationsPerDay++ }, modifier = Modifier.size(30.dp)) {
                                        Icon(Icons.Filled.Add, contentDescription = "Increase", tint = Color.White)
                                    }
                                }
                            }
                        }
                    }

                    when (recurrenceType) {
                        "Hours" -> {
                            Column {
                                HoursPickerUI(
                                    selectedHour = selectedHour,
                                    selectedMinute = selectedMinute,
                                    onHourChange = { newHour -> selectedHour = newHour },
                                    onMinuteChange = { newMinute -> selectedMinute = newMinute }
                                )

                                Spacer(modifier = Modifier.height(16.dp))
                                Text("Select Days", color = Color.White, fontSize = 16.sp, modifier = Modifier.padding(bottom = 8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceEvenly
                                ) {
                                    val labels = listOf("S", "M", "T", "W", "T", "F", "S")
                                    labels.forEachIndexed { idx, lbl ->
                                        val isSelected = selectedWeekdaysForHours.contains(idx)
                                        Box(
                                            modifier = Modifier
                                                .size(40.dp)
                                                .clip(CircleShape)
                                                .background(if (isSelected) Color(0xFF750182) else Color(0xFF3E3951))
                                                .clickable {
                                                    selectedWeekdaysForHours =
                                                        if (isSelected) selectedWeekdaysForHours - idx
                                                        else selectedWeekdaysForHours + idx
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(lbl, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                        "Days" -> {
                            Column {
                                DaysPickerUI(
                                    dayCount = notificationsPerDay,
                                    onDayCountChange = { newCount -> notificationsPerDay = newCount }
                                )
                            }
                        }

                        "Weekdays" -> {
                            val labels = listOf("S", "M", "T", "W", "T", "F", "S")
                            Text("Select Days", color = Color.White, fontSize = 16.sp, modifier = Modifier.padding(bottom = 8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                labels.forEachIndexed { idx, lbl ->
                                    val isSelected = selectedWeekdaysForWeekdaysMode.contains(idx)
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .background(if (isSelected) Color(0xFF765AE5) else Color(0xFF3E3951))
                                            .clickable {
                                                selectedWeekdaysForWeekdaysMode =
                                                    if (isSelected) selectedWeekdaysForWeekdaysMode - idx
                                                    else selectedWeekdaysForWeekdaysMode + idx
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(lbl, color = Color.White, fontSize = 16.sp)
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // ✅ NEW: Time of Day Preference (show in Model, Days, Weekdays - NOT in Hours)
                if (selectedMode == "Model" || (selectedMode == "Repetitive" && recurrenceType in listOf("Days", "Weekdays"))) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                    ) {
                        Text(
                            "Notification Time Periods",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Medium
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // ✅ 3 Period Buttons (only labels)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            listOf(
                                "MORNING" to "Morning",
                                "AFTERNOON" to "Afternoon",
                                "EVENING" to "Evening"
                            ).forEach { (key, label) ->
                                val isSelected = selectedTimePeriods.contains(key)

                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(horizontal = 4.dp)
                                        .background(
                                            if (isSelected) Color(0xFF560154) else Color(0xFF221F2C),
                                            RoundedCornerShape(8.dp)
                                        )
                                        .border(
                                            1.dp,
                                            if (isSelected) Color(0xFF560154) else Color(0xFF555555),
                                            RoundedCornerShape(8.dp)
                                        )
                                        .clickable {
                                            selectedTimePeriods = if (isSelected) {
                                                if (selectedTimePeriods.size > 1) {
                                                    selectedTimePeriods - key
                                                } else {
                                                    selectedTimePeriods
                                                }
                                            } else {
                                                selectedTimePeriods + key
                                            }
                                        }
                                        .padding(vertical = 12.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        label,
                                        color = Color.White,
                                        fontSize = 14.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }
                        }

                        // ✅ Warning if trying to deselect all
                        if (selectedTimePeriods.isEmpty()) {
                            Text(
                                "⚠️ At least one time period must be selected",
                                color = Color.Red,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                }

                // ✅ NEW: Snooze Duration Selector (Option B - Segmented Control)
                Text(
                    text = "Snooze Duration",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium
                )

                // Segmented Control Container
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF221F2C), RoundedCornerShape(12.dp))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    val snoozeOptions = listOf(5, 10, 15, 30, 60)

                    snoozeOptions.forEach { minutes ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 2.dp)
                                .background(
                                    color = if (selectedSnoozeDuration == minutes)
                                        Color(0xFF560154)  // Purple when selected
                                    else
                                        Color.Transparent,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .clickable { selectedSnoozeDuration = minutes }
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = minutes.toString(),
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = if (selectedSnoozeDuration == minutes)
                                    FontWeight.Bold
                                else
                                    FontWeight.Normal
                            )
                        }
                    }
                }

                // "Minutes" label below buttons
                Text(
                    text = "Minutes",
                    color = Color(0xFFAAAAAA),
                    fontSize = 12.sp,
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .fillMaxWidth(),
                    textAlign = TextAlign.Center
                )

                // ✅ Boost Mode Card - Only show for EXISTING tasks
                if (task != null && task.id > 0) {
                    Spacer(modifier = Modifier.height(24.dp))

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isBoostActive) Color(0xFF4CAF50) else Color(0xFF221F2C)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "⚡ Boost Mode",
                                        color = Color.White,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = if (isBoostActive)
                                            "Active - Extra notifications firing"
                                        else
                                            "Add temporary intensive notifications",
                                        color = Color(0xFFAAAAAA),
                                        fontSize = 14.sp,
                                        modifier = Modifier.padding(top = 4.dp)
                                    )
                                }

                                Box(
                                    modifier = Modifier
                                        .size(12.dp)
                                        .background(
                                            if (isBoostActive) Color(0xFF4CAF50) else Color.Gray,
                                            CircleShape
                                        )
                                )
                            }

                            if (!isBoostActive) {
                                Text(
                                    text = "Boost adds extra notifications ON TOP of your normal schedule",
                                    fontSize = 12.sp,
                                    color = Color(0xFF888888),
                                    modifier = Modifier.padding(top = 8.dp)
                                )
                            }

                            if (isBoostActive) {
                                Spacer(modifier = Modifier.height(12.dp))

                                val remainingTime = remember(task) {
                                    if (task.boostModeEndTime != null) {
                                        val remaining = task.boostModeEndTime - System.currentTimeMillis()
                                        if (remaining > 0) {
                                            val hours = (remaining / (60 * 60 * 1000)).toInt()
                                            val mins = ((remaining % (60 * 60 * 1000)) / (60 * 1000)).toInt()
                                            "${hours}h ${mins}m remaining"
                                        } else "Ending soon..."
                                    } else "Active"
                                }

                                Text(
                                    text = "⏱️ $remainingTime",
                                    color = Color.White,
                                    fontSize = 14.sp
                                )

                                Text(
                                    text = "Frequency: ${task.boostModeFrequency} per hour (extra)",
                                    color = Color(0xFFF8F8F8),
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(top = 4.dp)
                                )

                                Text(
                                    text = "Normal reminders: Still active ✓",
                                    color = Color(0xDC4CAF50),
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(top = 2.dp)
                                )

                                Spacer(modifier = Modifier.height(12.dp))

                                // ✅ FIXED STOP BUTTON
                                Button(
                                    onClick = {
                                        com.example.caresync.scheduler.BoostModeScheduler.stopBoostMode(context, task.id)
                                        isBoostActive = false

                                        val updatedTask = task.copy(
                                            boostModeActive = false,
                                            boostModeEndTime = null
                                        )
                                        onSave(updatedTask)

                                        android.widget.Toast.makeText(
                                            context,
                                            "Boost stopped. Normal reminders continue.",
                                            android.widget.Toast.LENGTH_SHORT
                                        ).show()
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("STOP BOOST", color = Color.White, fontWeight = FontWeight.Bold)
                                }

                            } else {
                                Spacer(modifier = Modifier.height(12.dp))

                                Button(
                                    onClick = { showBoostDialog = true },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF560154)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("START BOOST", color = Color.White, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                } else {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "💡 Save the task first to enable Boost Mode",
                        fontSize = 14.sp,
                        color = Color(0xFFAAAAAA),
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // 🔹 Reminder Method Radio Buttons (COMMON TO BOTH MODES)
                Text(
                    text = "Reminder Method",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Column {
                    // ✅ PUSH Notification
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                // ✅ PREVENT UNCHECKING IF IT'S THE LAST ONE
                                if (selectedReminderMethods.size > 1 || !selectedReminderMethods.contains(NotifyMethod.PUSH)) {
                                    selectedReminderMethods = if (selectedReminderMethods.contains(NotifyMethod.PUSH)) {
                                        selectedReminderMethods - NotifyMethod.PUSH
                                    } else {
                                        selectedReminderMethods + NotifyMethod.PUSH
                                    }
                                }
                            }
                            .padding(vertical = 8.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = selectedReminderMethods.contains(NotifyMethod.PUSH),
                            onCheckedChange = null, // Handled by Row click
                            colors = CheckboxDefaults.colors(
                                checkedColor = Color(0xFF750182),
                                uncheckedColor = Color(0xFF555555)
                            )
                        )
                        Text(
                            "📱 Push Notification",
                            color = Color.White,
                            fontSize = 16.sp,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }

                    // ✅ VOICE Notification
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                // ✅ PREVENT UNCHECKING IF IT'S THE LAST ONE
                                if (selectedReminderMethods.size > 1 || !selectedReminderMethods.contains(NotifyMethod.VOICE)) {
                                    selectedReminderMethods = if (selectedReminderMethods.contains(NotifyMethod.VOICE)) {
                                        selectedReminderMethods - NotifyMethod.VOICE
                                    } else {
                                        selectedReminderMethods + NotifyMethod.VOICE
                                    }
                                }
                            }
                            .padding(vertical = 8.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = selectedReminderMethods.contains(NotifyMethod.VOICE),
                            onCheckedChange = null, // Handled by Row click
                            colors = CheckboxDefaults.colors(
                                checkedColor = Color(0xFF750182),
                                uncheckedColor = Color(0xFF555555)
                            )
                        )
                        Text(
                            "🔊 Voice Message",
                            color = Color.White,
                            fontSize = 16.sp,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }

// ✅ Show Voice Model Selector ONLY if Voice is selected
                AnimatedVisibility(
                    visible = selectedReminderMethods.contains(NotifyMethod.VOICE),
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Column(modifier = Modifier.padding(top = 12.dp)) {
                        Text(
                            "Voice Model",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Female Voice
                            OutlinedButton(
                                onClick = { selectedVoiceModel = "Female" },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = if (selectedVoiceModel == "Female")
                                        Color(0xFF750182) else Color.Transparent,
                                    contentColor = Color.White
                                ),
                                border = BorderStroke(
                                    1.dp,
                                    if (selectedVoiceModel == "Female") Color(0xFF750182) else Color(0xFF555555)
                                )
                            ) {
                                Text("👩 Female")
                            }

                            // Male Voice
                            OutlinedButton(
                                onClick = { selectedVoiceModel = "Male" },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = if (selectedVoiceModel == "Male")
                                        Color(0xFF750182) else Color.Transparent,
                                    contentColor = Color.White
                                ),
                                border = BorderStroke(
                                    1.dp,
                                    if (selectedVoiceModel == "Male") Color(0xFF750182) else Color(0xFF555555)
                                )
                            ) {
                                Text("👨 Male")
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // ✨ SHARE PROGRESS SECTION
                Text(
                    text = "📊 Share Progress",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Text(
                    text = "Keep someone updated on your progress with this task",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                // Enable toggle
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Enable progress sharing", color = Color.White, fontSize = 16.sp)
                    Switch(
                        checked = shareProgressEnabled,
                        onCheckedChange = { shareProgressEnabled = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Color(0xFF750182),
                            uncheckedThumbColor = Color.White,
                            uncheckedTrackColor = Color(0xFF555555)
                        )
                    )
                }

                // Content (only visible when enabled)
                AnimatedVisibility(
                    visible = shareProgressEnabled,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp)
                    ) {
                        // ✅ Contact Picker Button
                        Text(
                            "Contact",
                            color = Color.White,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                        OutlinedButton(
                            onClick = {
                                // Launch contact picker
                                contactPickerLauncher.launch(null)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, Color(0xFF750182)),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = Color(0xFF3E3951)
                            )
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        if (contactName.isNotEmpty()) contactName else "Select from contacts",
                                        color = if (contactName.isNotEmpty()) Color.White else Color.White.copy(alpha = 0.6f),
                                        fontSize = 16.sp,
                                        fontWeight = if (contactName.isNotEmpty()) FontWeight.Medium else FontWeight.Normal
                                    )
                                    if (contactPhone.isNotEmpty()) {
                                        Text(
                                            "+91 $contactPhone",
                                            color = Color.White.copy(alpha = 0.7f),
                                            fontSize = 12.sp
                                        )
                                    }
                                }
                                Icon(
                                    imageVector = Icons.Default.ContactPage,
                                    contentDescription = "Pick contact",
                                    tint = Color(0xFF750182),
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // ✅ Report Types (At least one must be selected)
                        Text(
                            "Report Types (select any/all)",
                            color = Color.White,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                        Column {
                            // Daily Report
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        // ✅ Prevent unchecking if it's the only one selected
                                        if (!sendDailyReport || sendWeeklyReport || sendStrugglingAlerts) {
                                            sendDailyReport = !sendDailyReport
                                        }
                                    }
                                    .padding(vertical = 8.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = sendDailyReport,
                                    onCheckedChange = null,
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = Color(0xFF750182),
                                        uncheckedColor = Color(0xFF555555)
                                    )
                                )
                                Column(modifier = Modifier.padding(start = 8.dp)) {
                                    Text("Daily Summary", color = Color.White, fontSize = 16.sp)
                                    Text(
                                        "Every day at 8 PM",
                                        color = Color.White.copy(alpha = 0.6f),
                                        fontSize = 12.sp
                                    )
                                }
                            }

                            // Weekly Report
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        // ✅ Prevent unchecking if it's the only one selected
                                        if (!sendWeeklyReport || sendDailyReport || sendStrugglingAlerts) {
                                            sendWeeklyReport = !sendWeeklyReport
                                        }
                                    }
                                    .padding(vertical = 8.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = sendWeeklyReport,
                                    onCheckedChange = null,
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = Color(0xFF750182),
                                        uncheckedColor = Color(0xFF555555)
                                    )
                                )
                                Column(modifier = Modifier.padding(start = 8.dp)) {
                                    Text("Weekly Progress", color = Color.White, fontSize = 16.sp)
                                    Text(
                                        "Every Sunday at 8 PM",
                                        color = Color.White.copy(alpha = 0.6f),
                                        fontSize = 12.sp
                                    )
                                }
                            }

                            // Struggling Alerts
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        // ✅ Prevent unchecking if it's the only one selected
                                        if (!sendStrugglingAlerts || sendDailyReport || sendWeeklyReport) {
                                            sendStrugglingAlerts = !sendStrugglingAlerts
                                        }
                                    }
                                    .padding(vertical = 8.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = sendStrugglingAlerts,
                                    onCheckedChange = null,
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = Color(0xFF750182),
                                        uncheckedColor = Color(0xFF555555)
                                    )
                                )
                                Column(modifier = Modifier.padding(start = 8.dp)) {
                                    Text("Smart Alerts", color = Color.White, fontSize = 16.sp)
                                    Text(
                                        "When you're struggling",
                                        color = Color.White.copy(alpha = 0.6f),
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // ✅ Due Date Calendar
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp)
                ) {
                    Text(
                        "Due Date",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Calendar button
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF221F2C), RoundedCornerShape(12.dp))
                            .clickable { showDatePicker = true }
                            .padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    if (selectedDueDate != null) {
                                        SimpleDateFormat("MMMM dd, yyyy", Locale.getDefault())
                                            .format(Date(selectedDueDate!!))
                                    } else {
                                        "No due date set"
                                    },
                                    color = Color.White,
                                    fontSize = 16.sp
                                )

                                if (selectedDueDate != null) {
                                    val daysUntil = ((selectedDueDate!! - System.currentTimeMillis()) / (24 * 60 * 60 * 1000)).toInt()
                                    Text(
                                        when {
                                            daysUntil < 0 -> "⚠️ Overdue by ${-daysUntil} days"
                                            daysUntil == 0 -> "⏰ Due today!"
                                            daysUntil == 1 -> "📅 Due tomorrow"
                                            daysUntil <= 7 -> "📅 Due in $daysUntil days"
                                            else -> "📅 $daysUntil days remaining"
                                        },
                                        color = when {
                                            daysUntil < 0 -> Color(0xFFFF5252)
                                            daysUntil <= 3 -> Color(0xFFFFA726)
                                            else -> Color(0xFFAAAAAA)
                                        },
                                        fontSize = 12.sp,
                                        modifier = Modifier.padding(top = 4.dp)
                                    )
                                }
                            }

                            Text("📅", fontSize = 24.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // 🔹 App Picker Section (COMMON TO BOTH MODES)
                Text("Block App", color = Color.White, fontSize = 18.sp, modifier = Modifier.padding(bottom = 12.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF3E3951))
                        .clickable { showAppPicker = true }
                        .padding(16.dp)
                ) {
                    if (selectedApp != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Image(
                                    painter = rememberDrawablePainter(drawable = selectedApp!!.icon),
                                    contentDescription = selectedApp!!.label,
                                    modifier = Modifier.size(32.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(text = selectedApp!!.label, color = Color.White, fontSize = 16.sp)
                            }
                            IconButton(onClick = { selectedApp = null }, modifier = Modifier.size(32.dp)) {
                                Icon(imageVector = Icons.Filled.Close, contentDescription = "Remove app", tint = Color.White)
                            }
                        }
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Filled.Apps, contentDescription = "Pick app", tint = Color.White, modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(text = "Pick an app to launch", color = Color.White, fontSize = 16.sp)
                        }
                    }
                }

                if (showAppPicker) {
                    AppPickerDialog(
                        onDismiss = { showAppPicker = false },
                        onAppSelected = { app -> selectedApp = app }
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // 🔹 Tone Selection Dropdown (COMMON TO BOTH MODES)
                val toneOptions = listOf(
                    "🤖 Auto",
                    "💙 Encouraging",
                    "😄 Playful",
                    "😔 Guilt-Trip",
                    "💪 Aggressive"
                )

                Text("Notification Tone", color = Color.White, fontSize = 18.sp, modifier = Modifier.padding(bottom = 8.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { toneExpanded = true }
                ) {
                    OutlinedTextField(
                        value = selectedTone,
                        onValueChange = {},
                        readOnly = true,
                        enabled = false,
                        trailingIcon = {
                            Icon(
                                imageVector = if (toneExpanded) Icons.Filled.ArrowDropUp else Icons.Filled.ArrowDropDown,
                                contentDescription = "Select tone",
                                tint = Color.White
                            )
                        },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color(0xFF3E3951),
                            unfocusedContainerColor = Color(0xFF3E3951),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            disabledContainerColor = Color(0xFF3E3951),
                            disabledTextColor = Color.White,
                            cursorColor = Color.White,
                            focusedIndicatorColor = Color(0xFF750182),
                            unfocusedIndicatorColor = Color(0xFF3E3951),
                            disabledIndicatorColor = Color(0xFF3E3951)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = TextStyle(fontSize = 18.sp, color = Color.White)
                    )

                    DropdownMenu(
                        expanded = toneExpanded,
                        onDismissRequest = { toneExpanded = false },
                        modifier = Modifier.fillMaxWidth(0.9f).background(Color(0xFF262131)),
                        containerColor = Color(0xFF262131)
                    ) {
                        toneOptions.forEach { tone ->
                            DropdownMenuItem(
                                text = { Text(text = tone, color = Color.White, fontSize = 16.sp) },
                                onClick = {
                                    selectedTone = tone
                                    toneExpanded = false
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(if (selectedTone == tone) Color(0xFF3E3951) else Color.Transparent)
                            )
                            if (tone != toneOptions.last()) {
                                HorizontalDivider(color = Color(0xFF3E3951), thickness = 1.dp)
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
                Spacer(modifier = Modifier.height(24.dp))
            }
            // ✅ NEW: Boost Mode Configuration Dialog
            if (showBoostDialog) {
                Dialog(onDismissRequest = { showBoostDialog = false }) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color(0xFF221F2C)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp)
                        ) {
                            Text(
                                text = "⚡ Configure Boost Mode",
                                color = Color.White,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            Text(
                                text = "Duration (hours)",
                                color = Color.White,
                                fontSize = 16.sp
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                listOf(1, 2, 4, 8).forEach { hours ->
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(4.dp)
                                            .background(
                                                if (boostDurationHours == hours) Color(0xFF560154) else Color(0xFF3E3951),
                                                RoundedCornerShape(8.dp)
                                            )
                                            .clickable { boostDurationHours = hours }
                                            .padding(vertical = 12.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "${hours}h",
                                            color = Color.White,
                                            fontSize = 16.sp,
                                            fontWeight = if (boostDurationHours == hours) FontWeight.Bold else FontWeight.Normal
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            Text(
                                text = "Frequency: $boostFrequency per hour",
                                color = Color.White,
                                fontSize = 16.sp
                            )

                            Slider(
                                value = boostFrequency.toFloat(),
                                onValueChange = { boostFrequency = it.toInt() },
                                valueRange = 5f..15f,
                                steps = 9,
                                colors = SliderDefaults.colors(
                                    thumbColor = Color(0xFF560154),
                                    activeTrackColor = Color(0xFF560154)
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )

                            Text(
                                text = "Total: ${boostDurationHours * boostFrequency} notifications",
                                color = Color(0xFFAAAAAA),
                                fontSize = 12.sp
                            )

                            Spacer(modifier = Modifier.height(24.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                TextButton(onClick = { showBoostDialog = false }) {
                                    Text("Cancel", color = Color.White)
                                }

                                Button(
                                    onClick = {
                                        // Start boost mode
                                        val updatedTask = (task ?: ReminderSettings(title = name)).copy(
                                            boostModeActive = true,
                                            boostModeEndTime = System.currentTimeMillis() + (boostDurationHours * 60 * 60 * 1000L),
                                            boostModeFrequency = boostFrequency
                                        )

                                        // Save and start boost
                                        onSave(updatedTask)
                                        com.example.caresync.scheduler.BoostModeScheduler.startBoostMode(
                                            context,
                                            updatedTask,
                                            boostDurationHours,
                                            boostFrequency
                                        )

                                        isBoostActive = true
                                        showBoostDialog = false
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF560154))
                                ) {
                                    Text("START", color = Color.White, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
            // ✅ Themed Calendar Picker Dialog
            if (showDatePicker) {
                val datePickerState = rememberDatePickerState(
                    initialSelectedDateMillis = selectedDueDate ?: System.currentTimeMillis()
                )

                DatePickerDialog(
                    onDismissRequest = { showDatePicker = false },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                selectedDueDate = datePickerState.selectedDateMillis
                                showDatePicker = false
                            }
                        ) {
                            Text("OK", color = Color(0xFF560154), fontWeight = FontWeight.Bold)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showDatePicker = false }) {
                            Text("Cancel", color = Color.Gray)
                        }
                    },
                    colors = DatePickerDefaults.colors(
                        containerColor = Color(0xFF1A1625),
                        titleContentColor = Color.White,
                        headlineContentColor = Color.White,
                        weekdayContentColor = Color(0xFFAAAAAA),
                        subheadContentColor = Color.White,
                        yearContentColor = Color.White,
                        currentYearContentColor = Color(0xFF560154),
                        selectedYearContainerColor = Color(0xFF560154),
                        selectedYearContentColor = Color.White,
                        dayContentColor = Color.White,
                        disabledDayContentColor = Color(0xFF555555),
                        selectedDayContainerColor = Color(0xFF560154),
                        selectedDayContentColor = Color.White,
                        todayContentColor = Color(0xFF560154),
                        todayDateBorderColor = Color(0xFF560154),
                        dayInSelectionRangeContentColor = Color.White,
                        dayInSelectionRangeContainerColor = Color(0xFF3E2A3D),
                    )
                ) {
                    DatePicker(
                        state = datePickerState,
                        colors = DatePickerDefaults.colors(
                            containerColor = Color(0xFF1A1625),
                            titleContentColor = Color.White,
                            headlineContentColor = Color.White,
                            weekdayContentColor = Color(0xFFAAAAAA),
                            subheadContentColor = Color.White,
                            yearContentColor = Color.White,
                            currentYearContentColor = Color(0xFF560154),
                            selectedYearContainerColor = Color(0xFF560154),
                            selectedYearContentColor = Color.White,
                            dayContentColor = Color.White,
                            selectedDayContainerColor = Color(0xFF560154),
                            selectedDayContentColor = Color.White,
                            todayContentColor = Color(0xFF560154),
                            todayDateBorderColor = Color(0xFF560154)
                        )
                    )
                }
            }
        }
    }
}
