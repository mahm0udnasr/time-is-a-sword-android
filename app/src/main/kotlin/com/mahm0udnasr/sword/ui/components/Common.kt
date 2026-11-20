package com.mahm0udnasr.sword.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mahm0udnasr.sword.R
import com.mahm0udnasr.sword.ui.theme.LocalSwordColors

/** Snackbars ("toasts" in the extension) from anywhere. */
val LocalSnackbar = staticCompositionLocalOf { SnackbarHostState() }

/** Page title with an ⓘ that shows the page description (the extension's tooltip). */
@Composable
fun PageHeader(title: String, info: String? = null) {
    val t = LocalSwordColors.current
    var open by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = t.text, modifier = Modifier.weight(1f, fill = false))
            if (info != null) {
                IconButton(onClick = { open = !open }) {
                    Icon(Icons.Outlined.Info, stringResource(R.string.x_info_label), tint = if (open) t.accent else t.muted)
                }
            }
        }
        if (open && info != null) {
            Text(info, color = t.muted, fontSize = 13.sp, modifier = Modifier.padding(bottom = 8.dp))
        }
    }
}

@Composable
fun SwordCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val t = LocalSwordColors.current
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(t.surface)
            .border(1.dp, t.accentSoft, RoundedCornerShape(16.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = content,
    )
}

@Composable
fun CardTitle(text: String, end: @Composable (() -> Unit)? = null) {
    val t = LocalSwordColors.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(text, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, color = t.text, modifier = Modifier.weight(1f))
        end?.invoke()
    }
}

/** Used / limit bar: accent, amber from 75 %, red when full (like the extension). */
@Composable
fun UsageBar(used: Double, limit: Double, modifier: Modifier = Modifier) {
    val t = LocalSwordColors.current
    val ratio = if (limit > 0) (used / limit).coerceIn(0.0, 1.0).toFloat() else 1f
    val color = when {
        ratio >= 1f -> t.danger
        ratio >= 0.75f -> t.gold
        else -> t.accent
    }
    Box(
        modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(t.accentSoft)
            .semantics { progressBarRangeInfo = ProgressBarRangeInfo(ratio, 0f..1f) },
    ) {
        Box(Modifier.fillMaxWidth(ratio).height(6.dp).clip(RoundedCornerShape(3.dp)).background(color))
    }
}

@Composable
fun EmptyState(icon: ImageVector, text: String) {
    val t = LocalSwordColors.current
    Column(Modifier.fillMaxWidth().padding(vertical = 28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(icon, null, tint = t.muted, modifier = Modifier.size(36.dp))
        Text(text, color = t.muted, fontSize = 14.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

@Composable
fun Badge(text: String, danger: Boolean = false) {
    val t = LocalSwordColors.current
    Text(
        text, fontSize = 11.sp, fontWeight = FontWeight.Medium,
        color = if (danger) t.danger else t.accent,
        modifier = Modifier.clip(RoundedCornerShape(999.dp)).background(if (danger) t.dangerSoft else t.accentSoft).padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

/** Something that must stay left-to-right (domains, package names). */
@Composable
fun Ltr(content: @Composable () -> Unit) {
    androidx.compose.runtime.CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr, content = content)
}

@Composable
fun rememberFlag(initial: Boolean = false) = remember { mutableStateOf(initial) }

/** The user's font size is large (above 130%): layouts that put several texts side by side stack them instead (step 10.5). */
@Composable
fun largeFont(): Boolean = androidx.compose.ui.platform.LocalDensity.current.fontScale > 1.3f
