package com.example.caresync.utils

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration

// ✅ SINGLE SOURCE OF TRUTH - Define once, use everywhere
enum class DeviceType {
    PHONE,
    TABLET
}

// ✅ Reusable helper for any screen
@Composable
fun getDeviceType(): DeviceType {
    val screenWidth = LocalConfiguration.current.screenWidthDp
    return when {
        screenWidth >= 600 -> DeviceType.TABLET
        else -> DeviceType.PHONE
    }
}
