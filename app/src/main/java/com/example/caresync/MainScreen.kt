package com.example.caresync

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalConfiguration
import kotlinx.coroutines.launch
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.LocalContext
import com.example.caresync.data.ProfileDataStore
import com.example.caresync.viewmodel.ReminderViewModel
import com.example.caresync.domain.ReminderSettings
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.navigation.NavHostController
import com.example.caresync.ui.components.AppInfo
import com.example.caresync.ui.components.getInstalledApps
import com.example.caresync.ui.components.rememberDrawablePainter
import com.example.caresync.ui.components.HoursPickerUI
import com.example.caresync.ui.components.DaysPickerUI
import com.example.caresync.ui.components.WeekdaysPickerUI
import com.example.caresync.ui.components.AppPickerDialog
import com.example.caresync.ui.components.TaskSettingBottomSheet
import com.example.caresync.ui.components.ReminderCard
import androidx.compose.ui.platform.LocalContext
import com.example.caresync.ui.components.ProfileDrawerContent
import com.example.caresync.utils.getDeviceType  // ✅ NEW: Import shared device detection
import com.example.caresync.utils.DeviceType       // ✅ NEW: Import device type enum


@Composable
fun AppWithDrawer(navController: NavHostController)  {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    val context = LocalContext.current
    val profileDataStore = remember { ProfileDataStore(context) }
    val profileData by profileDataStore.profileData.collectAsState(initial = Triple("", "", ""))

    val username = profileData.first
    val age = profileData.second
    val purpose = profileData.third

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            // ✅ NEW BEAUTIFUL PROFILE DRAWER
            ProfileDrawerContent(
                username = username,
                age = age,
                purpose = purpose,
                onLogout = {
                    // Navigate back to welcome screen
                    navController.navigate("welcome") {
                        popUpTo("main") { inclusive = true }
                    }
                }
            )
        }
    ) {
        MainScreen(
            username = username,
            age = age,
            purpose = purpose,
            navController = navController,  // ← PASS IT HERE
            openDrawer = {
                scope.launch { drawerState.open() }
            }
        )
    }
}


@Composable
fun MainScreen(
    username: String,
    age: String,
    purpose: String,
    navController: NavHostController,
    openDrawer: () -> Unit
) {
    // ✅ NEW: Get device type for responsive design
    val deviceType = getDeviceType()

    // ✅ NEW: Responsive values
    val topPadding = when (deviceType) {
        DeviceType.PHONE -> 15.dp
        DeviceType.TABLET -> 25.dp
    }

    val horizontalPadding = when (deviceType) {
        DeviceType.PHONE -> 16.dp
        DeviceType.TABLET -> 30.dp
    }

    val spacerHeight = when (deviceType) {
        DeviceType.PHONE -> 50.dp
        DeviceType.TABLET -> 80.dp
    }

    val iconSize = when (deviceType) {
        DeviceType.PHONE -> 32.dp
        DeviceType.TABLET -> 48.dp
    }

    val dashboardIconSize = when (deviceType) {
        DeviceType.PHONE -> 38.dp
        DeviceType.TABLET -> 56.dp
    }

    val cardPaddingHorizontal = when (deviceType) {
        DeviceType.PHONE -> 18.dp
        DeviceType.TABLET -> 32.dp
    }

    val cardPaddingVertical = when (deviceType) {
        DeviceType.PHONE -> 12.dp
        DeviceType.TABLET -> 18.dp
    }

    val bottomPadding = when (deviceType) {
        DeviceType.PHONE -> 80.dp
        DeviceType.TABLET -> 80.dp
    }

    val noTaskImageSize = when (deviceType) {
        DeviceType.PHONE -> 200.dp
        DeviceType.TABLET -> 320.dp
    }

    val noTaskFontSize = when (deviceType) {
        DeviceType.PHONE -> 22.sp
        DeviceType.TABLET -> 32.sp
    }

    val fabSize = when (deviceType) {
        DeviceType.PHONE -> 56.dp
        DeviceType.TABLET -> 72.dp
    }

    val fabFontSize = when (deviceType) {
        DeviceType.PHONE -> 24.sp
        DeviceType.TABLET -> 32.sp
    }

    val gradientStripHeight = when (deviceType) {
        DeviceType.PHONE -> 0.11f
        DeviceType.TABLET -> 0.12f  // ← Adjust this value as needed for tablets
    }

    val vm: ReminderViewModel = viewModel()

    // ✅ NEW: Sort by creation date (newest first) - stable, doesn't reorder on toggle
    val reminders by vm.reminders.collectAsState(initial = emptyList()).let { remindersFlow ->
        remember(remindersFlow.value) {
            derivedStateOf {
                remindersFlow.value.sortedWith(compareBy({ !it.enabled }, { -it.createdAt }))
            }
        }
    }

    var showCreateDialog by remember { mutableStateOf(false) }

    // ✅ ADD THIS LINE - Missing variable
    var selectedReminder by remember { mutableStateOf<ReminderSettings?>(null) }

    val context = LocalContext.current

    Box(modifier = Modifier.fillMaxSize()) {
        // ✅ SOLID DARK BACKGROUND
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0F0620))
        )

        // ✅ TOP GRADIENT STRIP - 3-color gradient
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(gradientStripHeight)  // ← Uses responsive value
                .background(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            Color(0xFF63014E),  // Left: Pink-Purple
                            Color(0xFF560154),  // Middle: Dark Purple
                            Color(0xFF4C0158)   // Right: Dark Purple-Blue
                        )
                    )
                )
                .align(Alignment.TopCenter)
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontalPadding)  // ✅ CHANGED: Now responsive
        ) {
            // Top bar code (your existing dashboard icon section)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = topPadding),  // ✅ CHANGED: Now responsive
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = openDrawer) {
                    Image(
                        painter = painterResource(id = R.drawable.menuicon),
                        contentDescription = "Menu",
                        modifier = Modifier.size(iconSize)  // ✅ CHANGED: Now responsive
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clickable { navController.navigate("analytics") },
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.dashboard),
                            contentDescription = "Dashboard",
                            modifier = Modifier.size(dashboardIconSize)  // ✅ CHANGED: Now responsive
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(spacerHeight))  // ✅ CHANGED: Now responsive

            // ✅ CORRECTED SECTION - Fixed all errors
            if (reminders.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = bottomPadding),  // ✅ CHANGED: Now responsive
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Image(
                            painter = painterResource(id = R.drawable.notasklogo),
                            contentDescription = "No Task Logo",
                            modifier = Modifier
                                .size(noTaskImageSize)  // ✅ CHANGED: Now responsive
                                .padding(bottom = 16.dp)
                        )
                        Text(
                            text = "No Reminders",
                            color = Color.White,
                            fontSize = noTaskFontSize,  // ✅ CHANGED: Now responsive
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = bottomPadding)  // ✅ CHANGED: Now responsive
                ) {
                    itemsIndexed(
                        items = reminders,
                        key = { _, reminder -> reminder.id }
                    ) { _, reminder ->  // ✅ Changed parameter names
                        ReminderCard(
                            reminder = reminder,
                            onToggle = { enabled ->
                                vm.toggleReminder(reminder.id, enabled, context)
                            },
                            onClick = {
                                selectedReminder = reminder
                                vm.load(reminder.id)
                                showCreateDialog = true
                            },
                            onDelete = {
                                // ✅ NEW: Delete functionality
                                vm.update { reminder }  // Load into edit state
                                vm.delete(context)      // Delete from database
                            },
                            modifier = Modifier.padding(
                                horizontal = cardPaddingHorizontal,  // ✅ CHANGED: Now responsive
                                vertical = cardPaddingVertical       // ✅ CHANGED: Now responsive
                            )
                        )

                    }
                }
            }
        }

        // FAB - ✅ CHANGED: Now responsive
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            contentAlignment = Alignment.BottomEnd
        ) {
            FloatingActionButton(
                onClick = {
                    selectedReminder = null  // ✅ Reset selected reminder
                    vm.load(null)
                    showCreateDialog = true
                },
                containerColor = Color(0xFF750182),
                modifier = Modifier.size(fabSize)  // ✅ NEW: Responsive FAB size
            ) {
                Text("+", fontSize = fabFontSize, color = Color.White)
            }
        }

        // Bottom Sheet
        if (showCreateDialog) {
            TaskSettingBottomSheet(
                task = selectedReminder,
                onDismiss = {
                    showCreateDialog = false
                },
                onSave = { updatedTask ->
                    vm.update { updatedTask }
                    vm.save(context)
                    showCreateDialog = false
                },
                reminderViewModel = vm
            )
        }
    }
}
