package com.mahm0udnasr.sword.ui.components

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.mahm0udnasr.sword.ui.theme.LocalSwordColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

data class InstalledApp(val pkg: String, val label: String)

/** Apps with a launcher icon (needs the MAIN/LAUNCHER <queries> entry), labels and icons cached. */
object InstalledApps {
    private val icons = ConcurrentHashMap<String, ImageBitmap>()
    private val labels = ConcurrentHashMap<String, String>()

    suspend fun list(ctx: Context): List<InstalledApp> = withContext(Dispatchers.IO) {
        val pm = ctx.packageManager
        pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), PackageManager.MATCH_ALL)
            .map { it.activityInfo.packageName }
            .filter { it != ctx.packageName && !it.startsWith("org.chromium.webapk.") }
            .distinct()
            .map { InstalledApp(it, label(ctx, it)) }
            .sortedBy { it.label.lowercase() }
    }

    fun isInstalled(ctx: Context, pkg: String): Boolean =
        runCatching { ctx.packageManager.getApplicationInfo(pkg, 0); true }.getOrDefault(false)

    /** The app's name, or the package name if it is not installed. */
    fun label(ctx: Context, pkg: String): String = labels.getOrPut(pkg) {
        runCatching { ctx.packageManager.run { getApplicationLabel(getApplicationInfo(pkg, 0)).toString() } }.getOrDefault(pkg)
    }

    suspend fun icon(ctx: Context, pkg: String): ImageBitmap? = icons[pkg] ?: withContext(Dispatchers.IO) {
        runCatching { ctx.packageManager.getApplicationIcon(pkg).toBitmap(96, 96).asImageBitmap() }.getOrNull()?.also { icons[pkg] = it }
    }
}

/** An app's icon, or a letter tile for a website / missing app (like the extension's avatar). */
@Composable
fun AppAvatar(pkg: String?, fallback: String, size: Dp = 40.dp) {
    val ctx = LocalContext.current
    val t = LocalSwordColors.current
    var bmp by remember(pkg) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(pkg) { bmp = pkg?.let { InstalledApps.icon(ctx, it) } }
    val b = bmp
    if (b != null) {
        Image(b, null, Modifier.size(size).clip(RoundedCornerShape(size / 4)))
    } else {
        Box(Modifier.size(size).clip(RoundedCornerShape(size / 4)).background(t.accentSoft), contentAlignment = Alignment.Center) {
            Text(
                fallback.removePrefix("web.").removePrefix("m.").take(1).uppercase(),
                color = t.accent, fontWeight = FontWeight.Bold, fontSize = (size.value * 0.42f).sp,
            )
        }
    }
}
