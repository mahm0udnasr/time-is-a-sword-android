package com.mahm0udnasr.sword.ui.friction

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.mahm0udnasr.sword.R
import com.mahm0udnasr.sword.core.model.Defaults
import com.mahm0udnasr.sword.core.quotes.Quote
import com.mahm0udnasr.sword.core.text.matchesReminder
import com.mahm0udnasr.sword.core.text.typingProgress
import com.mahm0udnasr.sword.ui.format.tr
import com.mahm0udnasr.sword.ui.theme.Amiri
import com.mahm0udnasr.sword.ui.theme.LocalSwordColors
import kotlinx.coroutines.delay

/** What the friction step is about. [reasons] are already human text. */
data class FrictionRequest(
    val title: String,
    val reasons: List<String>,
    val note: String? = null,
    val confirmLabel: String? = null,
)

/**
 * The anti-cheat step before any looser change (port of options/friction.js, step 5.8):
 *  1. a 30 s countdown that runs **only while the app is resumed** (leaving and
 *     coming back does not skip it),
 *  2. a reminder typed exactly (matchesReminder: tashkeel, tatweel, alef forms,
 *     extra spaces don't matter), with live feedback,
 *  3. no paste: no text toolbar, autocorrect off, and bulk inserts rejected (see [acceptEdit]).
 * Confirm is enabled only when both are done.
 */
@Composable
fun FrictionDialog(request: FrictionRequest, quote: Quote, onResult: (Boolean) -> Unit) {
    val t = LocalSwordColors.current
    var remaining by remember { mutableIntStateOf(Defaults.FRICTION_COUNTDOWN_SEC) }
    var typed by remember { mutableStateOf(TextFieldValue("")) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    LaunchedEffect(Unit) {
        while (remaining > 0) {
            delay(1000)
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) remaining--
        }
    }

    val matched = matchesReminder(typed.text, quote.text)
    val canConfirm = matched && remaining <= 0
    val poem = quote.type == "poetry"

    Dialog(
        onDismissRequest = { onResult(false) },
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false, decorFitsSystemWindows = false),
    ) {
        Surface(Modifier.fillMaxSize(), color = t.bg) {
            Column(
                Modifier.safeDrawingPadding().imePadding().verticalScroll(rememberScrollState()).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(request.title, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = t.text)
                if (request.reasons.isNotEmpty()) {
                    Column(
                        Modifier.fillMaxWidth().background(t.dangerSoft, RoundedCornerShape(12.dp)).padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        request.reasons.forEach { Text("• $it", color = t.text, fontSize = 14.sp) }
                    }
                }
                request.note?.let { Text(it, color = t.muted, fontSize = 13.sp) }

                // The reminder (always Arabic, right-to-left).
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    Column(
                        Modifier.fillMaxWidth().background(t.surface, RoundedCornerShape(14.dp)).padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        // Poetry as verse with kashida (step 10.1); the typing check ignores tatweel.
                        com.mahm0udnasr.sword.ui.reminder.ReminderText(
                            quote,
                            androidx.compose.ui.text.TextStyle(fontFamily = Amiri, fontSize = 22.sp, lineHeight = 40.sp, color = t.text, textAlign = TextAlign.Center),
                        )
                        if (quote.source.isNotEmpty()) Text(quote.source, fontFamily = Amiri, fontSize = 15.sp, color = t.gold, textAlign = TextAlign.Center)
                    }
                }

                Text(stringResource(if (poem) R.string.x_fr_typeVerse else R.string.x_fr_typeQuote), color = t.muted, fontSize = 13.sp)
                CompositionLocalProvider(LocalTextToolbar provides NoTextToolbar, LocalLayoutDirection provides LayoutDirection.Rtl) {
                    OutlinedTextField(
                        value = typed,
                        onValueChange = { new -> if (acceptEdit(typed.text, new.text)) typed = new else typed = typed.copy(selection = TextRange(typed.text.length)) },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 110.dp),
                        textStyle = androidx.compose.ui.text.TextStyle(fontFamily = Amiri, fontSize = 20.sp, color = t.text),
                        keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, keyboardType = KeyboardType.Text),
                        placeholder = { Text(stringResource(R.string.fr_noPaste), color = t.muted) },
                    )
                }
                val status = when {
                    typed.text.isBlank() -> null to t.muted
                    matched -> stringResource(if (remaining > 0) R.string.x_fr_matchesWait else R.string.x_fr_matches) to t.accent
                    else -> {
                        val p = typingProgress(typed.text, quote.text)
                        if (p.wrongWordIndex != null) {
                            tr(R.string.x_fr_wordDiffers, "n" to p.wrongWordIndex!! + 1, "w" to p.expected) to t.danger
                        } else {
                            tr(R.string.x_fr_progress, "a" to p.correctWords, "b" to p.totalWords) to t.muted
                        }
                    }
                }
                status.first?.let { Text(it, color = status.second, fontSize = 13.sp) }

                Spacer(Modifier)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (remaining > 0) Text(tr(R.string.x_fr_wait, "n" to remaining), color = t.muted, fontSize = 14.sp, modifier = Modifier.weight(1f))
                    else Spacer(Modifier.weight(1f))
                    OutlinedButton(onClick = { onResult(false) }) { Text(stringResource(R.string.x_cancel)) }
                    Button(
                        onClick = { if (canConfirm) onResult(true) },
                        enabled = canConfirm,
                        colors = ButtonDefaults.buttonColors(containerColor = t.danger, contentColor = t.surface),
                    ) { Text(request.confirmLabel ?: stringResource(R.string.x_fr_confirm)) }
                }
            }
        }
    }
}

/**
 * Paste guard for the typing box. One edit may insert at most one word and at
 * most 20 characters: enough for typing and for glide-typing a whole word, but
 * a clipboard chip, autofill or a pasted sentence is rejected. Deletions and
 * replacements by shorter text are always fine.
 */
internal fun acceptEdit(old: String, new: String): Boolean {
    var p = 0
    while (p < old.length && p < new.length && old[p] == new[p]) p++
    var s = 0
    while (s < old.length - p && s < new.length - p && old[old.length - 1 - s] == new[new.length - 1 - s]) s++
    val inserted = new.substring(p, new.length - s)
    if (inserted.length > MAX_INSERT_CHARS) return false
    return inserted.trim().split(Regex("\\s+")).count { it.isNotEmpty() } <= 1
}

private const val MAX_INSERT_CHARS = 20

/** No cut/copy/paste/select-all menu at all. */
private object NoTextToolbar : TextToolbar {
    override val status: TextToolbarStatus get() = TextToolbarStatus.Hidden
    override fun hide() {}
    override fun showMenu(rect: Rect, onCopyRequested: (() -> Unit)?, onPasteRequested: (() -> Unit)?, onCutRequested: (() -> Unit)?, onSelectAllRequested: (() -> Unit)?) {}
}
