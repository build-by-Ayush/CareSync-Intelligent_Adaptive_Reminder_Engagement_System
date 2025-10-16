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
    var showTopImage by remember { mutableStateOf(false)
    }
    var usernameError by remember { mutableStateOf(false) }
    var ageError by remember { mutableStateOf(false) }
    var purposeError by remember { mutableStateOf(false) }

    var username by remember { mutableStateOf("") }
    var age by remember { mutableStateOf("") }
    var purpose by remember { mutableStateOf("") }

    // Trigger animation after slight delay
    LaunchedEffect(Unit) {
        delay(300)
        showTopImage = true
    }

    Box(modifier = Modifier
        .fillMaxSize()
        .zIndex(0f)) {

        // ✅ Background Image stretched to fill
        Image(
            painter = painterResource(id = R.drawable.profilepage), // your bg image
            contentDescription = "Background",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )


        // ✅ Top image slide-in
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(250.dp)
                .zIndex(1f) // higher than background
                .align(Alignment.TopCenter)
        ) {
            AnimatedVisibility(
                visible = showTopImage,
                enter = slideInVertically(
                    initialOffsetY = { fullHeight -> -fullHeight },
                    animationSpec = tween(durationMillis = 1100) // ← controls slide speed
                ) + fadeIn(
                    animationSpec = tween(durationMillis = 1100) // ← controls fade speed
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
            .padding(top = 0.dp) // make space for top image
            .zIndex(2f)             // highest priority for interaction
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()  // ✅ Full height, not just fillMaxWidth
                    .verticalScroll(scrollState)
                    .padding(horizontal = 30.dp)
                    .padding(top = 400.dp), // below the header image
                verticalArrangement = Arrangement.spacedBy(30.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 1️⃣ Username
                Column(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // 🔴 Error Text (visible or invisible placeholder)
                    Text(
                        text = if (usernameError) "            Username is required" else "",
                        color = Color.Red,
                        fontSize = 14.sp,
                        modifier = Modifier
                            .height(20.dp) // fixed space for error
                            .padding(start = 150.dp)
                    )

                    // ✅ Your input field
                    CustomTextField(
                        value = username,
                        onValueChange = { username = it },
                        placeholder = "  Username",
                        iconId = R.drawable.textlogo1
                    )
                }


                // 2️⃣ Age
                Column(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = if (ageError) "                       Age is required" else "",
                        color = Color.Red,
                        fontSize = 14.sp,
                        modifier = Modifier
                            .height(20.dp)
                            .padding(start = 150.dp)
                    )

                    CustomTextField(
                        value = age,
                        onValueChange = { input ->
                            if (input.all { it.isDigit() }) {   // allow only digits
                                age = input
                            }
                        },
                        placeholder = "  Age",
                        iconId = R.drawable.textlogo2
                    )
                }


                // 3 Purpose
                Column(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = if (purposeError) "            Please select a purpose" else "",
                        color = Color.Red,
                        fontSize = 14.sp,
                        modifier = Modifier
                            .height(20.dp)
                            .padding(start = 150.dp)
                    )

                    PurposeSelector(
                        selectedOption = purpose,
                        onOptionSelected = { purpose = it }
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
                delay(600)  // optional delay for animation
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
                        purposeError = purpose.isBlank()

                        if (!usernameError && !ageError && !purposeError) {
                            // ✅ Save profile data
                            scope.launch {
                                profileDataStore.saveProfile(username, age, purpose)
                            }

                            // Navigate to main directly (no need to pass args anymore)
                            navController.navigate("main") {
                                popUpTo("profile") { inclusive = true } // removes profile from backstack
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
            Text(placeholder, color = Color(0xFFDCD7D7) , fontSize = 21.sp)
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
fun PurposeSelector(
    selectedOption: String,
    onOptionSelected: (String) -> Unit
) {
    val options = listOf("Study", "Workout")

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
                        color = if (selectedOption == option) Color(0xFFB2A3E8) else Color(0xFF3E3951),
                        shape = RoundedCornerShape(22.dp)
                    )
                    .clickable { onOptionSelected(option) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = option,
                    color = if (isSelected) Color(0xFF3B3A3A) else Color(0xFFDCD7D7),
                    fontSize = 21.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                )
            }
        }
    }
}


