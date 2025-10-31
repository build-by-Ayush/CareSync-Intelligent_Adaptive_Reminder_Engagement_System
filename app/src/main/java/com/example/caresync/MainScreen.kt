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
import kotlinx.coroutines.launch
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.LocalContext
import com.example.caresync.data.ProfileDataStore
import com.example.caresync.viewmodel.ReminderViewModel
import com.example.caresync.domain.ReminderSettings
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
    val vm: ReminderViewModel = viewModel()
    val reminders by vm.reminders.collectAsState(initial = emptyList())
    var showCreateDialog by remember { mutableStateOf(false) }

    // ✅ ADD THIS LINE - Missing variable
    var selectedReminder by remember { mutableStateOf<ReminderSettings?>(null) }

    val context = LocalContext.current

    Box(modifier = Modifier.fillMaxSize()) {
        Image(
            painter = painterResource(id = R.drawable.mainpage),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // Top bar code (your existing dashboard icon section)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 15.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = openDrawer) {
                    Image(
                        painter = painterResource(id = R.drawable.menuicon),
                        contentDescription = "Menu",
                        modifier = Modifier.size(32.dp)
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
                            modifier = Modifier.size(38.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(50.dp))

            // ✅ CORRECTED SECTION - Fixed all errors
            if (reminders.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 80.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Image(
                            painter = painterResource(id = R.drawable.notasklogo),
                            contentDescription = "No Task Logo",
                            modifier = Modifier
                                .size(200.dp)
                                .padding(bottom = 16.dp)
                        )
                        Text(
                            text = "No Reminders",
                            color = Color.White,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 80.dp)
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
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp)
                        )

                    }
                }
            }
        }

        // FAB
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
                containerColor = Color(0xFF750182)
            ) {
                Text("+", fontSize = 24.sp, color = Color.White)
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
