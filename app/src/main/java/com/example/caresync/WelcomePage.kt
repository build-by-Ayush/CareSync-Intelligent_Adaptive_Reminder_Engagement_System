package com.example.caresync

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.example.caresync.data.ProfileDataStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import com.example.caresync.utils.getDeviceType
import com.example.caresync.utils.DeviceType
import androidx.compose.foundation.Image
import androidx.compose.ui.draw.clip

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val profileDataStore = ProfileDataStore(this)

        lifecycleScope.launch {
            val completed = profileDataStore.isProfileCompleted.first()

            setContent {
                val navController = rememberNavController()
                AppNavigation(
                    navController = navController,
                    startDestination = if (completed) "main" else "welcome"
                )
            }
        }
    }
}

@Composable
fun WelcomePage(navController: NavController) {
    val deviceType = getDeviceType()

    // ✅ EDITABLE: Header image height
    val headerImageHeight = when (deviceType) {
        DeviceType.PHONE -> 250.dp
        DeviceType.TABLET -> 300.dp  // ← CHANGED: 250 × 1.5 = 375
    }

    // ✅ EDITABLE: Bottom padding for button
    val bottomPadding = when (deviceType) {
        DeviceType.PHONE -> 122.dp
        DeviceType.TABLET -> 183.dp  // ← CHANGED: 122 × 1.5 = 183
    }

    // ✅ EDITABLE: Button height
    val buttonHeight = when (deviceType) {
        DeviceType.PHONE -> 73.dp
        DeviceType.TABLET -> 100.dp  // ← CHANGED: 73 × 1.5 = 109.5 (round to 110)
    }

    // ✅ EDITABLE: Button font size
    val buttonFontSize = when (deviceType) {
        DeviceType.PHONE -> 22.sp
        DeviceType.TABLET -> 33.sp  // ← CHANGED: 22 × 1.5 = 33
    }

    // Logo size
    val logoSize = when (deviceType) {
        DeviceType.PHONE -> 75.dp
        DeviceType.TABLET -> 112.5.dp  // ← CHANGED: 75 × 1.5 = 112.5 (round to 113)
    }

    // Tagline font size
    val taglineFontSize = when (deviceType) {
        DeviceType.PHONE -> 42.sp
        DeviceType.TABLET -> 55.sp  // ← CHANGED: 42 × 1.5 = 63
    }

    // Spacing between logo+title and tagline
    val spacingBetweenSections = when (deviceType) {
        DeviceType.PHONE -> 45.dp
        DeviceType.TABLET -> 50.dp  // ← CHANGED: 45 × 1.5 = 67.5 (round to 68)
    }

    // Spacing between white line and button
    val lineToButtonSpacing = when (deviceType) {
        DeviceType.PHONE -> 32.dp
        DeviceType.TABLET -> 48.dp  // ← KEPT: Already correct (32 × 1.5 = 48) ✓
    }

    // Bottom margin for button & line
    val bottomMarginFromScreen = when (deviceType) {
        DeviceType.PHONE -> 70.dp
        DeviceType.TABLET -> 105.dp  // ← CHANGED: 70 × 1.5 = 105
    }

    // Logo spacing (horizontal)
    val logoHorizontalOffset = when (deviceType) {
        DeviceType.PHONE -> (-50).dp
        DeviceType.TABLET -> (-75).dp  // ← CHANGED: (-50) × 1.5 = (-75)
    }

    // Spacer before Logo+Title+Tagline section
    val centerSectionTopSpacer = when (deviceType) {
        DeviceType.PHONE -> 370.dp
        DeviceType.TABLET -> 450.dp  // ← CHANGED: 370 × 1.5 = 555
    }

    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(300)
        visible = true
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // ✅ SOLID DARK BACKGROUND
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0F0620))
        )

        // ✅ TOP 50%: Empty space for clock animation
        var showImage by remember { mutableStateOf(false) }

        LaunchedEffect(Unit) {
            delay(300)
            showImage = true
        }

        AnimatedVisibility(
            visible = showImage,
            enter = slideInVertically(
                initialOffsetY = { fullHeight -> -fullHeight },
                animationSpec = tween(durationMillis = 1100)
            ) + fadeIn(
                animationSpec = tween(durationMillis = 1100)
            ),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .fillMaxHeight(0.4f)
        ) {
            Image(
                painter = painterResource(id = R.drawable.clock1),
                contentDescription = "Top Header Image",
                contentScale = ContentScale.FillWidth,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(headerImageHeight)
            )
        }

        // ✅ CENTER: Logo + Title + Tagline
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp),
            verticalArrangement = Arrangement.Top,  // ✅ CHANGED
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ✅ SPACER TO MOVE ENTIRE CENTER SECTION DOWN
            Spacer(modifier = Modifier.height(centerSectionTopSpacer))  // ← EDIT THIS

            // Logo + Title Row - CENTERED
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                // ✅ SPACER FOR HORIZONTAL OFFSET
                Spacer(modifier = Modifier.width(logoHorizontalOffset))  // ← EDIT THIS

                // Logo
                Image(
                    painter = painterResource(id = R.drawable.taglogo),
                    contentDescription = "CareSync Logo",
                    modifier = Modifier
                        .size(logoSize)
                        .clip(RoundedCornerShape(8.dp))
                )

                Spacer(modifier = Modifier.width(1.dp))

                // Title: CareSync
                Text(
                    "CareSync",
                    fontSize = if (deviceType == DeviceType.PHONE) 40.sp else 52.sp,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(spacingBetweenSections))

            // Tagline
            Text(
                "Reminders That\nUnderstand\nYou",
                fontSize = taglineFontSize,
                color = Color(0xFFD4A5FF),
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                lineHeight = (taglineFontSize.value + 8).sp
            )
        }

        // ✅ BOTTOM: White line + Button
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = bottomMarginFromScreen),  // ✅ CHANGED: Use new variable
            verticalArrangement = Arrangement.Bottom,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
        AnimatedVisibility(
                visible = visible,
                enter = fadeIn(animationSpec = tween(1600)) + slideInVertically(
                    initialOffsetY = { it / 2 },
                    animationSpec = tween(1600)
                )
            ) {
                Button(
                    onClick = {
                        navController.navigate("profile") {
                            popUpTo("welcome") { inclusive = true }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Transparent
                    ),
                    contentPadding = PaddingValues(),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth(0.72f)
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
                            "LET'S BEGIN",
                            fontSize = buttonFontSize,
                            color = Color.White,
                            fontWeight = FontWeight.Light
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(lineToButtonSpacing))  // ✅ ADJUST lineToButtonSpacing - INCREASE TO MOVE LINE DOWN

            // White separator line
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Color.White.copy(alpha = 0.3f))
            )
        }
    }
}
