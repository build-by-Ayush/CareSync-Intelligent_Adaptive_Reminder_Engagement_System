package com.example.caresync

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import kotlinx.coroutines.delay
import androidx.compose.foundation.clickable
import androidx.compose.material3.TextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.zIndex
import androidx.navigation.NavController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import com.example.caresync.data.ProfileDataStore


@Composable
fun ProfileScreen(navController: NavController) {
    var showTopImage by remember { mutableStateOf(false) }
    var usernameError by remember { mutableStateOf(false) }
    var ageError by remember { mutableStateOf(false) }
    var adaptiveLayerError by remember { mutableStateOf(false) }

    var username by remember { mutableStateOf("") }
    var age by remember { mutableStateOf("") }
    var adaptiveLayerChoice by remember { mutableStateOf("") }

    // Trigger animation after slight delay
    LaunchedEffect(Unit) {
        delay(300)
        showTopImage = true
    }

    Box(modifier = Modifier
        .fillMaxSize()
        .zIndex(0f)) {

        // Background Image stretched to fill
        Image(
            painter = painterResource(id = R.drawable.profilepage),
            contentDescription = "Background",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        // Top image slide-in
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(250.dp)
                .zIndex(1f)
                .align(Alignment.TopCenter)
        ) {
            AnimatedVisibility(
                visible = showTopImage,
                enter = slideInVertically(
                    initialOffsetY = { fullHeight -> -fullHeight },
                    animationSpec = tween(durationMillis = 1100)
                ) + fadeIn(
                    animationSpec = tween(durationMillis = 1100)
                )
            ) {
                Image(
                    painter = painterResource(id = R.drawable.clock2),
                    contentDescription = "Top Image",
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        val scrollState = rememberScrollState()

        Box(modifier = Modifier
            .fillMaxSize()
            .padding(top = 0.dp)
            .zIndex(2f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(horizontal = 30.dp)
                    .padding(top = 400.dp),
                verticalArrangement = Arrangement.spacedBy(30.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 1️⃣ Username
                Column(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // ✅ NEW: Title heading for Username (same style as Adaptive Intelligence)
                    Text(
                        text = if (usernameError) "Username is required" else "Profile Information",
                        color = if (usernameError) Color.Red else Color(0xFFB2A3E8),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
                    )

                    CustomTextField(
                        value = username,
                        onValueChange = { input ->
                            // ✅ Limit to 25 characters
                            if (input.length <= 25) {
                                username = input
                            }
                        },
                        placeholder = " Enter name",  // ✅ Updated placeholder
                        iconId = R.drawable.textlogo1
                    )
                }

                // 2️⃣ Age
                Column(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // ✅ NEW: Title heading for Age (same style as Adaptive Intelligence)
                    Text(
                        text = if (ageError) "Age is required" else "Age Information",
                        color = if (ageError) Color.Red else Color(0xFFB2A3E8),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
                    )

                    CustomTextField(
                        value = age,
                        onValueChange = { input ->
                            if (input.all { it.isDigit() }) {
                                age = input
                            }
                        },
                        placeholder = " Enter age",  // ✅ Updated placeholder
                        iconId = R.drawable.textlogo2
                    )
                }

                // 3️⃣ Adaptive Intelligence Layer
                Column(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // ✅ Warning on LEFT (like name and age)
                    Text(
                        text = if (adaptiveLayerError) "Please select a mode" else "Adaptive Intelligence",
                        color = if (adaptiveLayerError) Color.Red else Color(0xFFB2A3E8),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    AdaptiveLayerSelector(
                        selectedOption = adaptiveLayerChoice,
                        onOptionSelected = {
                            adaptiveLayerChoice = it
                            adaptiveLayerError = false  // Clear error when selected
                        }
                    )
                }
            }
        }

        // Animated Button
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 70.dp)
                .zIndex(3f),
            verticalArrangement = Arrangement.Bottom,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            var visible by remember { mutableStateOf(false) }

            LaunchedEffect(Unit) {
                delay(600)
                visible = true
            }

            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(animationSpec = tween(1600)) + slideInVertically(
                    initialOffsetY = { it / 2 },
                    animationSpec = tween(1600)
                )
            ) {
                val context = LocalContext.current
                val profileDataStore = remember { ProfileDataStore(context) }
                val scope = rememberCoroutineScope()

                Button(
                    onClick = {
                        // Reset errors first
                        usernameError = username.isBlank()
                        ageError = age.isBlank()
                        adaptiveLayerError = adaptiveLayerChoice.isBlank()

                        if (!usernameError && !ageError && !adaptiveLayerError) {
                            // Convert choice to boolean
                            val adaptiveLayerEnabled = when (adaptiveLayerChoice) {
                                "ON" -> true
                                "OFF" -> false
                                else -> true  // Default ON
                            }

                            // Save profile data with adaptive layer setting
                            scope.launch {
                                profileDataStore.saveProfile(
                                    username,
                                    age,
                                    "User",  // Purpose no longer used
                                    adaptiveLayerEnabled
                                )
                            }

                            // Navigate to main
                            navController.navigate("main") {
                                popUpTo("profile") { inclusive = true }
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Transparent
                    ),
                    contentPadding = PaddingValues(),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .height(73.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                brush = Brush.horizontalGradient(
                                    colors = listOf(
                                        Color(0xFFA8016D),
                                        Color(0xFF8E0177),
                                        Color(0xFF750182)
                                    )
                                ),
                                shape = RoundedCornerShape(20.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "LET'S GET STARTED",
                            fontSize = 22.sp,
                            color = Color.White,
                            fontWeight = FontWeight.Light
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CustomTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    iconId: Int
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = {
            Text(placeholder, color = Color(0xFFDCD7D7), fontSize = 21.sp)
        },
        leadingIcon = {
            Image(
                painter = painterResource(id = iconId),
                contentDescription = null,
                modifier = Modifier
                    .size(50.dp)
                    .padding(start = 10.dp)
            )
        },
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color(0xFF3E3951),
            unfocusedContainerColor = Color(0xFF3E3951),
            disabledContainerColor = Color(0xFF3E3951),
            focusedTextColor = Color(0xFFDCD7D7),
            unfocusedTextColor = Color(0xFFDCD7D7),
            disabledTextColor = Color(0xFFDCD7D7),
            focusedPlaceholderColor = Color(0xFFDCD7D7),
            unfocusedPlaceholderColor = Color(0xFFDCD7D7),
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .height(65.dp)
            .padding(start = 2.dp),
        shape = RoundedCornerShape(22.dp),
        singleLine = true,
        textStyle = androidx.compose.ui.text.TextStyle(
            fontSize = 21.sp,
            textIndent = TextIndent(firstLine = 8.sp)
        )
    )
}

@Composable
fun AdaptiveLayerSelector(
    selectedOption: String,
    onOptionSelected: (String) -> Unit
) {
    val options = listOf("ON", "OFF")

    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        options.forEach { option ->
            val isSelected = selectedOption == option
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(65.dp)
                    .background(
                        color = if (isSelected) Color(0xFFB2A3E8) else Color(0xFF3E3951),
                        shape = RoundedCornerShape(22.dp)
                    )
                    .clickable { onOptionSelected(option) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (option == "ON") "ON" else "OFF",
                    color = if (isSelected) Color(0xFF3B3A3A) else Color(0xFFDCD7D7),
                    fontSize = 21.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                )
            }
        }
    }
}
