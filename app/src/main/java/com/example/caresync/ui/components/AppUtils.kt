package com.example.caresync.ui.components

import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.painter.Painter

/**
 * Data class representing an installed application
 */
data class AppInfo(
    val label: String,
    val packageName: String,
    val icon: Drawable
)

/**
 * Get list of all installed launcher apps
 */
fun getInstalledApps(context: Context): List<AppInfo> {
    val packageManager = context.packageManager
    val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
        addCategory(Intent.CATEGORY_LAUNCHER)
    }

    val apps = packageManager.queryIntentActivities(mainIntent, 0)
        .map { resolveInfo ->
            AppInfo(
                label = resolveInfo.loadLabel(packageManager).toString(),
                packageName = resolveInfo.activityInfo.packageName,
                icon = resolveInfo.loadIcon(packageManager)
            )
        }
        .distinctBy { it.packageName }  // ✅ ADD: Remove duplicate packages
        .sortedBy { it.label.lowercase() }

    android.util.Log.d("APP_PICKER", "✅ Found ${apps.size} apps")  // ✅ ADD: Debug log
    return apps
}


/**
 * Convert Android Drawable to Compose Painter for displaying icons
 */
@Composable
fun rememberDrawablePainter(drawable: Drawable): Painter {
    return remember(drawable) {
        object : Painter() {
            override val intrinsicSize: Size
                get() = Size(
                    drawable.intrinsicWidth.toFloat(),
                    drawable.intrinsicHeight.toFloat()
                )

            override fun DrawScope.onDraw() {
                drawIntoCanvas { canvas ->
                    drawable.setBounds(0, 0, size.width.toInt(), size.height.toInt())
                    drawable.draw(canvas.nativeCanvas)
                }
            }
        }
    }
}
