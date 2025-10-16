package com.example.caresync

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.animation.core.tween
import androidx.compose.runtime.*
import kotlinx.coroutines.delay
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.ui.text.font.FontWeight
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import androidx.lifecycle.lifecycleScope
import com.example.caresync.data.ProfileDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import android.content.pm.PackageManager  // ← ADD
import android.os.Build  // ← ADD (if not already there)
import androidx.core.app.ActivityCompat  // ← ADD


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val profileDataStore = ProfileDataStore(this)

        // ✅ REQUEST NOTIFICATION PERMISSION (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    100
                )
            }
        }

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
    // Animation visibility trigger
    var visible by remember { mutableStateOf(false) }

    // Delay to start the animation
    LaunchedEffect(Unit) {
        delay(300)  // optional: slight delay before animation
        visible = true
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Background image
        Image(
            painter = painterResource(id = R.drawable.welcomepage), // Make sure image is in res/drawable
            contentDescription = "Welcome Background",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        // Trigger state for animation
        var showImage by remember { mutableStateOf(false) }

        LaunchedEffect(Unit) {
            delay(300) // Delay before showing the image
            showImage = true
        }

        // Slide-in image
        AnimatedVisibility(
            visible = showImage,
            enter = slideInVertically(
                initialOffsetY = { fullHeight -> -fullHeight },
                animationSpec = tween(durationMillis = 1100) // ← controls slide speed
            ) + fadeIn(
                animationSpec = tween(durationMillis = 1100) // ← controls fade speed
            ),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Image(
                painter = painterResource(id = R.drawable.clock1), // replace with your image name
                contentDescription = "Top Header Image",
                contentScale = ContentScale.FillWidth,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(250.dp)
            )
        }

        // Animated Button
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 122.dp),
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
                        .height(73.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                brush = Brush.horizontalGradient(
                                    colors = listOf(
                                        Color(0xFFA8016D),  // left
                                        Color(0xFF8E0177),  // middle
                                        Color(0xFF750182)   // right
                                    )
                                ),
                                shape = RoundedCornerShape(20.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "LET'S BEGIN",
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
