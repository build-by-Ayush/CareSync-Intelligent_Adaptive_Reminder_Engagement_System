package com.example.caresync.ui.components

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.util.Log
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
 * ✅ Get ONLY user-installed apps (Play Store apps)
 */
fun getInstalledApps(context: Context): List<AppInfo> {
    val pm = context.packageManager

    return try {
        val packages = pm.getInstalledApplications(PackageManager.GET_META_DATA)

        val apps = packages
            .filter { app ->
                // ✅ Only launchable apps
                pm.getLaunchIntentForPackage(app.packageName) != null &&
                        // ✅ Exclude the host app
                        app.packageName != context.packageName &&
                        // ✅ Exclude pure system apps, but ALLOW updated system apps (YouTube, Chrome)
                        (app.flags and ApplicationInfo.FLAG_SYSTEM == 0 ||
                                app.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP != 0)
            }
            .mapNotNull { app ->
                try {
                    val label = pm.getApplicationLabel(app).toString()
                    val icon = pm.getApplicationIcon(app.packageName)

                    if (label.isBlank()) return@mapNotNull null

                    AppInfo(
                        label = label,
                        packageName = app.packageName,
                        icon = icon
                    )
                } catch (e: Exception) {
                    null
                }
            }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }

        Log.d("APP_PICKER", "✅ Found ${apps.size} visible apps")
        apps
    } catch (e: Exception) {
        Log.e("APP_PICKER", "❌ Error: ${e.message}", e)
        emptyList()
    }
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
