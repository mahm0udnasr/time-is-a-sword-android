package com.mahm0udnasr.sword.ui.about

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Gavel
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Work
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mahm0udnasr.sword.BuildConfig
import com.mahm0udnasr.sword.R
import com.mahm0udnasr.sword.core.model.Lang
import com.mahm0udnasr.sword.ui.components.CardTitle
import com.mahm0udnasr.sword.ui.components.PageHeader
import com.mahm0udnasr.sword.ui.components.SwordCard
import com.mahm0udnasr.sword.ui.debug.DebugActivity
import com.mahm0udnasr.sword.ui.format.tr
import com.mahm0udnasr.sword.ui.theme.LocalSwordColors

/** The Android app's repository (Phase 11.6 creates it). */
const val REPO = "https://github.com/mahm0udnasr/time-is-a-sword-android"

/** Port of AUTHOR in options/about.js. */
private object Author {
    val name = mapOf(Lang.AR to "محمود نصر", Lang.EN to "Mahmoud Nasr")
    val bio = mapOf(
        Lang.AR to "طالب نظم معلومات الأعمال (BIS)، أنتقل من تطوير الويب إلى تعلم الآلة والذكاء الاصطناعي",
        Lang.EN to "BIS student, transitioning from web development to machine learning & AI.",
    )
    val location = mapOf(Lang.AR to "مصر", Lang.EN to "Egypt")
}

private sealed interface LinkIcon {
    data class Vector(val v: ImageVector) : LinkIcon
    data class Brand(val path: String) : LinkIcon
}

/** [label (null = the "Website" string), url, icon], same order as the extension. */
private val LINKS = listOf(
    Triple(null, "https://mahm0udnasr.vercel.app/", LinkIcon.Vector(Icons.Outlined.Language)),
    Triple("GitHub", "https://github.com/mahm0udnasr", LinkIcon.Brand(BrandPaths.GITHUB)),
    Triple("LinkedIn", "https://linkedin.com/in/mahm0udnasr", LinkIcon.Vector(Icons.Outlined.Work)),
    Triple("Telegram", "https://t.me/mahm0udnasr", LinkIcon.Brand(BrandPaths.TELEGRAM)),
    Triple("Kaggle", "https://kaggle.com/mahm0udnasr", LinkIcon.Vector(Icons.Outlined.EmojiEvents)),
    Triple("Facebook", "https://facebook.com/mahm0udnasr1/", LinkIcon.Brand(BrandPaths.FACEBOOK)),
)

private fun brandVector(path: String): ImageVector =
    ImageVector.Builder(defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
        .addPath(pathData = PathParser().parsePathString(path).toNodes(), fill = SolidColor(Color.Black))
        .build()

/** About (port of options/about.js, step 10.3) + recovery steps (step 8.8). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AboutScreen(lang: Lang) {
    val t = LocalSwordColors.current
    val ctx = LocalContext.current
    val taps = remember { mutableIntStateOf(0) }
    fun open(url: String) = runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }

    Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PageHeader(stringResource(R.string.settings_about))

        SwordCard {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Image(
                    painterResource(R.drawable.author), Author.name.getValue(lang),
                    modifier = Modifier.size(88.dp).clip(CircleShape),
                )
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.x_about_madeBy), color = t.muted, fontSize = 12.sp)
                    Text(Author.name.getValue(lang), color = t.text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                    Text(Author.bio.getValue(lang), color = t.text, fontSize = 13.sp, lineHeight = 20.sp)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.LocationOn, null, tint = t.muted, modifier = Modifier.size(15.dp))
                        Text(Author.location.getValue(lang), color = t.muted, fontSize = 12.sp)
                    }
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                LINKS.forEach { (label, url, icon) ->
                    OutlinedButton(onClick = { open(url) }) {
                        val v = when (icon) {
                            is LinkIcon.Vector -> icon.v
                            is LinkIcon.Brand -> remember(icon.path) { brandVector(icon.path) }
                        }
                        Icon(v, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(6.dp))
                        Text(label ?: stringResource(R.string.x_about_website), fontSize = 13.sp)
                    }
                }
            }
        }

        SwordCard {
            // The app icon next to its name, like the extension's about card.
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                // The foreground keeps room for launcher masks; zoom it here so the drawing reads clearly.
                androidx.compose.foundation.layout.Box(Modifier.size(64.dp).clip(RoundedCornerShape(16.dp))) {
                    Image(painterResource(R.drawable.ic_launcher_background), null, Modifier.size(64.dp))
                    Image(
                        painterResource(R.drawable.ic_launcher_foreground), null,
                        Modifier.size(64.dp).graphicsLayer(scaleX = 1.5f, scaleY = 1.5f),
                    )
                }
                Text(stringResource(R.string.x_app_name), color = t.text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            }
            // Tap the version 7 times: the hidden Debug screen (step 3.6).
            Text(
                tr(R.string.x_about_version, "v" to BuildConfig.VERSION_NAME) + " · " + stringResource(R.string.x_about_license),
                color = t.muted, fontSize = 13.sp,
                modifier = Modifier.clickable {
                    if (++taps.intValue >= 7) {
                        taps.intValue = 0
                        ctx.startActivity(Intent(ctx, DebugActivity::class.java))
                    }
                },
            )
            Text(stringResource(R.string.about_descApp), color = t.text, fontSize = 14.sp, lineHeight = 22.sp)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { open(REPO) }) { Icon(Icons.Outlined.Code, null, Modifier.size(16.dp)); Spacer(Modifier.size(6.dp)); Text(stringResource(R.string.x_about_source), fontSize = 13.sp) }
                OutlinedButton(onClick = { open("$REPO/issues") }) { Icon(Icons.Outlined.BugReport, null, Modifier.size(16.dp)); Spacer(Modifier.size(6.dp)); Text(stringResource(R.string.x_about_issue), fontSize = 13.sp) }
                OutlinedButton(onClick = { open("$REPO/blob/main/LICENSE") }) { Icon(Icons.Outlined.Gavel, null, Modifier.size(16.dp)); Spacer(Modifier.size(6.dp)); Text("MIT", fontSize = 13.sp) }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Outlined.FavoriteBorder, null, tint = t.muted, modifier = Modifier.size(14.dp))
                Text(stringResource(R.string.x_about_footer), color = t.muted, fontSize = 12.sp)
            }
            Text(stringResource(R.string.about_credits), color = t.muted, fontSize = 11.sp, lineHeight = 17.sp)
        }

        RecoveryCard()
        Spacer(Modifier.height(24.dp))
    }
}

/** Recovery (plan §10.4, step 8.8): never hidden. Safe Mode only here; the README also has the computer (adb) way. */
@Composable
private fun RecoveryCard() {
    val t = LocalSwordColors.current
    SwordCard {
        CardTitle(stringResource(R.string.recovery_title))
        Text(stringResource(R.string.recovery_text), color = t.text, fontSize = 13.sp, lineHeight = 20.sp)
    }
}
