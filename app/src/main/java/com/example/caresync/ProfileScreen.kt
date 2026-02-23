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
import androidx.compose.ui.platform.LocalConfiguration
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import com.example.caresync.data.ProfileDataStore
import com.example.caresync.utils.getDeviceType
import com.example.caresync.utils.DeviceType
import androidx.compose.ui.draw.clip


@Composable
fun ProfileScreen(navController: NavController) {
    // ✅ Get device type for responsive design
    val deviceType = getDeviceType()

    // ✅ Responsive values
    val topImageHeight = when (deviceType) {
        DeviceType.PHONE -> 250.dp
        DeviceType.TABLET -> 350.dp  // ← CHANGED: 250 × 1.5 = 375
    }

    val topPadding = when (deviceType) {
        DeviceType.PHONE -> 200.dp
        DeviceType.TABLET -> 300.dp  // ← CHANGED: 200 × 1.5 = 300
    }

    val horizontalPadding = when (deviceType) {
        DeviceType.PHONE -> 30.dp
        DeviceType.TABLET -> 45.dp  // ← CHANGED: 30 × 1.5 = 45
    }

    val spacingBetweenFields = when (deviceType) {
        DeviceType.PHONE -> 30.dp
        DeviceType.TABLET -> 25.dp  // ← CHANGED: 30 × 1.5 = 45
    }

    val fieldHeight = when (deviceType) {
        DeviceType.PHONE -> 65.dp
        DeviceType.TABLET -> 80.dp  // ← CHANGED: 65 × 1.5 = 97.5 (round to 98)
    }

    val fieldFontSize = when (deviceType) {
        DeviceType.PHONE -> 21.sp
        DeviceType.TABLET -> 28.sp  // ← CHANGED: 21 × 1.5 = 31.5 (round to 32)
    }

    val buttonHeight = when (deviceType) {
        DeviceType.PHONE -> 73.dp
        DeviceType.TABLET -> 100.dp  // ← CHANGED: 73 × 1.5 = 109.5 (round to 110)
    }

    val buttonFontSize = when (deviceType) {
        DeviceType.PHONE -> 22.sp
        DeviceType.TABLET -> 33.sp  // ← CHANGED: 22 × 1.5 = 33
    }

    val bottomButtonPadding = when (deviceType) {
        DeviceType.PHONE -> 70.dp
        DeviceType.TABLET -> 40.dp  // ← CHANGED: 70 × 1.5 = 105
    }

    // ✅ NEW: Heading spacing variables
    val headingTopPadding = when (deviceType) {
        DeviceType.PHONE -> 60.dp
        DeviceType.TABLET -> 60.dp  // ← CHANGED: 60 × 1.5 = 90
    }

    val headingBottomPadding = when (deviceType) {
        DeviceType.PHONE -> 1.dp
        DeviceType.TABLET -> 0.dp  // ← CHANGED: 1 × 1.5 = 1.5 (round to 2)
    }

    val headingFontSize1 = when (deviceType) {
        DeviceType.PHONE -> 48.sp
        DeviceType.TABLET -> 50.sp  // ← CHANGED: 48 × 1.5 = 72
    }

    val headingFontSize2 = when (deviceType) {
        DeviceType.PHONE -> 46.sp
        DeviceType.TABLET -> 54.sp  // ← CHANGED: 46 × 1.5 = 69
    }

    val headingLineSpacing = when (deviceType) {
        DeviceType.PHONE -> 8.dp
        DeviceType.TABLET -> 12.dp  // ← CHANGED: 8 × 1.5 = 12 ✓ Already correct
    }

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
        .zIndex(0f)
        .background(Color(0xFF0F0620))  // ✅ SOLID DARK BACKGROUND
    ) {
        // Top image slide-in
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(topImageHeight)
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
                    .padding(horizontal = horizontalPadding)
                    .padding(top = topPadding),
                verticalArrangement = Arrangement.spacedBy(spacingBetweenFields),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // ✅ NEW: Profile Page Heading
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            top = headingTopPadding,  // ← EDITABLE
                            bottom = headingBottomPadding  // ← EDITABLE
                        )
                ) {
                    // Line 1: "Create an"
                    Text(
                        text = "Create an",
                        fontSize = headingFontSize1,  // ← EDITABLE
                        color = Color.White,
                        fontWeight = FontWeight.Normal
                    )

                    Spacer(modifier = Modifier.height(headingLineSpacing))  // ← EDITABLE

                    // Line 2: "Profile!"
                    Text(
                        text = "Profile!",
                        fontSize = headingFontSize2,  // ← EDITABLE
                        color = Color(0xFFD4A5FF),  // Pink/Purple
                        fontWeight = FontWeight.Bold
                    )
                }

                // 1️⃣ Username
                Column(
                    modifier = Modifier.fillMaxWidth()
                ) {
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
                            if (input.length <= 25) {
                                username = input
                            }
                        },
                        placeholder = " Enter name",
                        iconId = R.drawable.textlogo1,
                        fieldHeight = fieldHeight,
                        fieldFontSize = fieldFontSize
                    )
                }

                // 2️⃣ Age
                Column(
                    modifier = Modifier.fillMaxWidth()
                ) {
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
                        placeholder = " Enter age",
                        iconId = R.drawable.textlogo2,
                        fieldHeight = fieldHeight,
                        fieldFontSize = fieldFontSize
                    )
                }

                // 3️⃣ Adaptive Intelligence Layer
                Column(
                    modifier = Modifier.fillMaxWidth()
                ) {
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
                            adaptiveLayerError = false
                        },
                        fieldHeight = fieldHeight,
                        fieldFontSize = fieldFontSize
                    )
                }
            }
        }

        // Animated Button
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = bottomButtonPadding)
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
                        usernameError = username.isBlank()
                        ageError = age.isBlank()
                        adaptiveLayerError = adaptiveLayerChoice.isBlank()

                        if (!usernameError && !ageError && !adaptiveLayerError) {
                            val adaptiveLayerEnabled = when (adaptiveLayerChoice) {
                                "ON" -> true
                                "OFF" -> false
                                else -> true
                            }

                            scope.launch {
                                profileDataStore.saveProfile(
                                    username,
                                    age,
                                    "User",
                                    adaptiveLayerEnabled
                                )
                            }

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
                        .height(buttonHeight)
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
                            fontSize = buttonFontSize,
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
    iconId: Int,
    fieldHeight: androidx.compose.ui.unit.Dp,
    fieldFontSize: androidx.compose.ui.unit.TextUnit
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = {
            Text(placeholder, color = Color(0xFFDCD7D7), fontSize = fieldFontSize)
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
            .height(fieldHeight)
            .padding(start = 2.dp),
        shape = RoundedCornerShape(22.dp),
        singleLine = true,
        textStyle = androidx.compose.ui.text.TextStyle(
            fontSize = fieldFontSize,
            textIndent = TextIndent(firstLine = 8.sp)
        )
    )
}

@Composable
fun AdaptiveLayerSelector(
    selectedOption: String,
    onOptionSelected: (String) -> Unit,
    fieldHeight: androidx.compose.ui.unit.Dp,
    fieldFontSize: androidx.compose.ui.unit.TextUnit
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
                    .height(fieldHeight)
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
                    fontSize = fieldFontSize,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                )
            }
        }
    }
}
