package com.mahm0udnasr.sword.ui.overlay

import android.accessibilityservice.AccessibilityService
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.mahm0udnasr.sword.R
import com.mahm0udnasr.sword.core.model.Theme
import com.mahm0udnasr.sword.core.rules.BlockKind
import com.mahm0udnasr.sword.ui.theme.LocalSwordColors
import com.mahm0udnasr.sword.ui.theme.PlexArabic
import com.mahm0udnasr.sword.ui.theme.SwordTheme
import com.mahm0udnasr.sword.ui.format.withLang
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * The small "N minutes left" card on top of the app in use (port of
 * content/overlay.js, step 4.5): a TYPE_ACCESSIBILITY_OVERLAY (Spike C),
 * never focusable, touches outside it go to the app; a tap closes it.
 * Shows for a few seconds with a short vibration.
 */
class OverlayController(private val service: AccessibilityService) {
    private val main = Handler(Looper.getMainLooper())
    private var view: View? = null
    private var owner: OverlayOwner? = null

    fun showWarning(targetName: String, kind: BlockKind, blockAt: Long, theme: Theme, lang: com.mahm0udnasr.sword.core.model.Lang) {
        hide()
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.TOP; y = 120 }
        val o = OverlayOwner().also { it.start() }
        // The service has no activity: give the card a context in the app's chosen language.
        val v = ComposeView(service.withLang(lang)).apply {
            o.attach(this)
            setContent {
                SwordTheme(theme) { WarningCard(targetName, kind, blockAt) { hide() } }
            }
        }
        if (runCatching { service.getSystemService(WindowManager::class.java).addView(v, lp) }.isSuccess) {
            view = v
            owner = o
            vibrate()
            main.postDelayed({ hide() }, SHOW_MS)
        } else {
            o.stop()
        }
    }

    fun hide() {
        main.removeCallbacksAndMessages(null)
        view?.let { runCatching { service.getSystemService(WindowManager::class.java).removeView(it) } }
        owner?.stop()
        view = null
        owner = null
    }

    private fun vibrate() = runCatching {
        val v: Vibrator = if (Build.VERSION.SDK_INT >= 31) {
            service.getSystemService(VibratorManager::class.java).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            service.getSystemService(Vibrator::class.java)
        }
        v.vibrate(VibrationEffect.createOneShot(120, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    private companion object {
        const val SHOW_MS = 6_000L
    }
}

private val HM = DateTimeFormatter.ofPattern("HH:mm")

@Composable
private fun WarningCard(targetName: String, kind: BlockKind, blockAt: Long, onClose: () -> Unit) {
    val t = LocalSwordColors.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(250)
            now = System.currentTimeMillis()
        }
    }
    val ms = (blockAt - now).coerceAtLeast(0)
    val minutes = Math.ceil(ms / 60_000.0).toInt()
    val title = if (minutes > 1) {
        stringResource(R.string.warn_minutes, minutes)
    } else {
        val s = Math.round(ms / 1000.0).toInt()
        stringResource(R.string.warn_seconds, String.format(java.util.Locale.ROOT, "%d:%02d", s / 60, s % 60))
    }
    // Keep the name left-to-right inside Arabic text (Unicode isolate).
    val name = "⁦$targetName⁩"
    val sub = if (kind == BlockKind.SCHEDULE) {
        stringResource(R.string.warn_until, name, Instant.ofEpochMilli(blockAt).atZone(ZoneId.systemDefault()).format(HM))
    } else {
        stringResource(R.string.warn_limit, name)
    }
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier.padding(horizontal = 16.dp).widthIn(max = 420.dp).fillMaxWidth()
                .clip(RoundedCornerShape(16.dp)).background(t.surface)
                .border(1.dp, t.accentSoft, RoundedCornerShape(16.dp))
                .clickable(onClick = onClose),
        ) {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(t.accentSoft), contentAlignment = Alignment.Center) {
                    Text("⏳", fontSize = 16.sp)
                }
                Column {
                    Text(title, color = t.text, fontFamily = PlexArabic, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text(sub, color = t.muted, fontFamily = PlexArabic, fontSize = 13.sp)
                }
            }
            // Drains towards the moment the app closes.
            val total = remember { (blockAt - System.currentTimeMillis()).coerceAtLeast(1) }
            Box(Modifier.fillMaxWidth().height(3.dp).background(t.accentSoft)) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Box(Modifier.fillMaxWidth((ms.toFloat() / total).coerceIn(0f, 1f)).height(3.dp).background(t.accent))
                }
            }
        }
    }
}

/** Lifecycle + SavedState owner so a ComposeView works outside an Activity (Spike C). */
class OverlayOwner : LifecycleOwner, SavedStateRegistryOwner {
    private val registry = LifecycleRegistry(this)
    private val ssrc = SavedStateRegistryController.create(this)
    override val lifecycle: Lifecycle get() = registry
    override val savedStateRegistry: SavedStateRegistry get() = ssrc.savedStateRegistry

    fun start() {
        ssrc.performAttach()
        ssrc.performRestore(null)
        registry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    fun attach(v: View) {
        v.setViewTreeLifecycleOwner(this)
        v.setViewTreeSavedStateRegistryOwner(this)
    }

    fun stop() {
        if (registry.currentState != Lifecycle.State.INITIALIZED && registry.currentState != Lifecycle.State.DESTROYED) {
            registry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        }
    }
}
