package com.mahm0udnasr.sword.block

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.mahm0udnasr.sword.R
import com.mahm0udnasr.sword.container
import com.mahm0udnasr.sword.core.quotes.Quote
import com.mahm0udnasr.sword.core.quotes.QuoteFile
import com.mahm0udnasr.sword.core.rules.BlockReason
import com.mahm0udnasr.sword.core.rules.EvalContext
import com.mahm0udnasr.sword.core.rules.evaluate
import com.mahm0udnasr.sword.core.rules.webRulesActive
import com.mahm0udnasr.sword.ui.theme.Amiri
import com.mahm0udnasr.sword.ui.theme.LocalSwordColors
import com.mahm0udnasr.sword.ui.theme.PlexArabic
import com.mahm0udnasr.sword.ui.theme.SwordTheme
import com.mahm0udnasr.sword.ui.format.tr
import com.mahm0udnasr.sword.ui.format.formatClock
import com.mahm0udnasr.sword.ui.format.prayerName
import com.mahm0udnasr.sword.ui.friction.Committer
import com.mahm0udnasr.sword.ui.friction.runPauseFlow
import androidx.compose.foundation.clickable
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.ui.text.style.TextDecoration
import android.net.Uri
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.time.ZoneId

/**
 * The block screen (port of the extension's blocked page, step 4.3): one reminder and its source,
 * nothing else — like the extension. Android-only reasons (unsupported browser,
 * and later clock and guard) add one short line, because the user needs to
 * know what to do. Back always goes Home, never back to the blocked app.
 *
 * Closes by itself as soon as the block no longer holds (pause granted, window
 * starts, reset, rule loosened). Phase 6 adds the pause link and the relaunch.
 */
class BlockActivity : androidx.appcompat.app.AppCompatActivity() {

    private var reason = BlockReason.BLOCKED
    private var targetId: String? = null
    private var pkg: String? = null
    private var host: String? = null

    /** True while the pause flow runs: don't close underneath it. */
    private var pausing = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        readExtras(intent)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = goHome()
        })

        val c = container
        // One reminder per opening; never the same as last time (port of pickQuote()).
        val file = if (reason == BlockReason.PRAYER) QuoteFile.SALAH else QuoteFile.REMINDERS
        val last = if (file == QuoteFile.SALAH) c.state.value.lastSalahId else c.state.value.lastQuoteId
        val quote = savedInstanceState?.getString(KEY_QUOTE)?.let { id -> c.reminders.all(file).firstOrNull { it.id == id } }
            ?: c.reminders.pick(file, last)
        if (savedInstanceState == null && quote != null) {
            lifecycleScope.launch {
                c.state.update { if (file == QuoteFile.SALAH) it.copy(lastSalahId = quote.id) else it.copy(lastQuoteId = quote.id) }
            }
        }
        shownQuoteId = quote?.id

        // Leave as soon as it is allowed again (no buttons, like the extension).
        lifecycleScope.launch {
            combine(c.config.flow, c.state.flow) { _, _ -> Unit }.collect { if (!pausing && !stillBlocked()) leave() }
        }
        lifecycleScope.launch {
            while (true) {
                // Every 15 s, and right when a prayer lock ends (that is not a data change).
                val lockEnd = c.prayerClock.active()?.end?.let { it - System.currentTimeMillis() + 300 }
                delay(minOf(15_000L, lockEnd ?: 15_000L).coerceAtLeast(300))
                if (!pausing && !stillBlocked()) leave()
            }
        }
        // Clock: no data changes when automatic time comes back on, so follow the setting itself.
        val autoTime = com.mahm0udnasr.sword.system.AutoTime.observe(this) { if (!pausing && !stillBlocked()) leave() }
        lifecycle.addObserver(object : androidx.lifecycle.DefaultLifecycleObserver {
            override fun onDestroy(owner: androidx.lifecycle.LifecycleOwner) = contentResolver.unregisterContentObserver(autoTime)
        })
        val committer = Committer(c) { this }

        setContent {
            val cfg by c.config.flow.collectAsState()
            val st by c.state.flow.collectAsState()
            var message by remember { mutableStateOf<String?>(null) }
            // "Pause for 5 min · N left": only for the target's own rules, while pauses are left,
            // never during a prayer lock (§8.4).
            var tick by remember { mutableStateOf(0) }
            LaunchedEffect(Unit) {
                while (true) {
                    delay(5_000)
                    tick++
                }
            }
            val lock = (st to tick).let { _ -> c.prayerClock.active() }
            val pause = c.pauses.availability(targetId).takeIf { reason.pausable && it.ok && lock == null }
            val prayerLine = if (reason == BlockReason.PRAYER && lock != null) {
                tr(R.string.x_blocked_prayerNow, "name" to stringResource(prayerName(lock.prayer))) + " · " + com.mahm0udnasr.sword.ui.format.ltr(formatClock(lock.at))
            } else {
                null
            }
            SwordTheme(cfg.appearance.theme) {
                BlockScreen(
                    quote, reason, prayerLine,
                    actionLabel = when (reason) {
                        BlockReason.CLOCK -> stringResource(R.string.block_clockOpen)
                        BlockReason.GUARD -> stringResource(R.string.block_guardOpen)
                        else -> null
                    },
                    onAction = ::runAction,
                    pauseLabel = pause?.let { tr(R.string.x_pp_pauseBtn, "n" to it.minutes, "left" to it.left) },
                    message = message,
                    onPause = {
                        val id = targetId ?: return@BlockScreen
                        lifecycleScope.launch {
                            pausing = true
                            val out = runPauseFlow(this@BlockActivity, c, committer, id)
                            message = out.message
                            if (out.ok) {
                                relaunch()
                                leave()
                            } else {
                                pausing = false
                            }
                        }
                    },
                )
                committer.Host()
            }
        }
    }

    private var shownQuoteId: String? = null

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        shownQuoteId?.let { outState.putString(KEY_QUOTE, it) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        readExtras(intent)
    }

    private fun readExtras(i: Intent) {
        reason = runCatching { BlockReason.valueOf(i.getStringExtra(EXTRA_REASON) ?: "") }.getOrDefault(BlockReason.BLOCKED)
        targetId = i.getStringExtra(EXTRA_TARGET)
        pkg = i.getStringExtra(EXTRA_PKG)
        host = i.getStringExtra(EXTRA_HOST)
    }

    /** Clock: Android's Date & time page. Guard: this app, where protection can be switched off properly. */
    private fun runAction() {
        val i = when (reason) {
            BlockReason.CLOCK -> Intent(android.provider.Settings.ACTION_DATE_SETTINGS)
            BlockReason.GUARD -> Intent(this, com.mahm0udnasr.sword.ui.MainActivity::class.java)
            else -> return
        }
        runCatching { startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        leave()
    }

    /** Does the block still hold? */
    private fun stillBlocked(): Boolean {
        val c = container
        val cfg = c.config.value
        // During a prayer lock everything stays closed, whatever opened this screen (like the extension).
        if (c.prayerClock.active() != null) return true
        return when (reason) {
            BlockReason.BROWSER -> webRulesActive(cfg)
            BlockReason.BLOCKED, BlockReason.LIMIT, BlockReason.SCHEDULE, BlockReason.SHORTS -> {
                val t = cfg.targets.firstOrNull { it.id == targetId } ?: return false
                val s = c.state.value
                evaluate(t, EvalContext(System.currentTimeMillis(), ZoneId.systemDefault(), usage = s.usage, targets = cfg.targets, groups = cfg.groups, pauses = s.pauses)).blocked
            }
            BlockReason.PRAYER -> false
            BlockReason.CLOCK -> !com.mahm0udnasr.sword.system.AutoTime.trusted(this, cfg.protection.requireAutoTime)
            BlockReason.GUARD -> com.mahm0udnasr.sword.core.guard.guardActive(cfg.protection, c.state.value.guardSuspendedUntil, System.currentTimeMillis())
        }
    }

    private var leaving = false

    private fun leave() {
        if (leaving) return
        leaving = true
        finish()
    }

    /**
     * After a pause: back to what was blocked (§8.4) — the website in the browser
     * it came from, or the app.
     */
    private fun relaunch() {
        val p = pkg ?: return
        val i = if (host != null) {
            Intent(Intent.ACTION_VIEW, Uri.parse("https://$host")).setPackage(p)
        } else {
            packageManager.getLaunchIntentForPackage(p)
        } ?: return
        runCatching { startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    private fun goHome() {
        startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        leave()
    }

    companion object {
        private const val EXTRA_REASON = "reason"
        private const val EXTRA_TARGET = "target"
        private const val EXTRA_PKG = "pkg"
        private const val EXTRA_HOST = "host"
        private const val EXTRA_UNTIL = "until"
        private const val KEY_QUOTE = "quote"

        fun intent(ctx: Context, reason: BlockReason, targetId: String?, pkg: String, host: String?, until: Long?): Intent =
            Intent(ctx, BlockActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
                .putExtra(EXTRA_REASON, reason.name)
                .putExtra(EXTRA_TARGET, targetId)
                .putExtra(EXTRA_PKG, pkg)
                .putExtra(EXTRA_HOST, host)
                .putExtra(EXTRA_UNTIL, until ?: 0L)
    }
}

@Composable
private fun BlockScreen(
    quote: Quote?,
    reason: BlockReason,
    prayerLine: String?,
    actionLabel: String?,
    onAction: () -> Unit,
    pauseLabel: String?,
    message: String?,
    onPause: () -> Unit,
) {
    val t = LocalSwordColors.current
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    Box(Modifier.fillMaxSize().background(t.bg).safeDrawingPadding(), contentAlignment = Alignment.Center) {
        Column(
            Modifier.widthIn(max = 720.dp).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            val note = when (reason) {
                BlockReason.BROWSER -> stringResource(R.string.block_browser)
                BlockReason.CLOCK -> stringResource(R.string.block_clock)
                BlockReason.GUARD -> stringResource(R.string.block_guard)
                BlockReason.SHORTS -> stringResource(R.string.shorts_closed)
                // "حان وقت صلاة الظهر · 11:40" (the extension's prayer line).
                BlockReason.PRAYER -> prayerLine
                else -> null
            }
            if (note != null) {
                Text(
                    note,
                    color = t.accent,
                    fontFamily = PlexArabic,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.background(t.accentSoft, RoundedCornerShape(999.dp)).padding(horizontal = 16.dp, vertical = 6.dp),
                )
            }
            // Reminders are Arabic: always right-to-left, whatever the interface language.
            AnimatedVisibility(visible = shown && quote != null, enter = fadeIn(tween(600))) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        if (quote != null) {
                            // Poetry as verse with kashida, like the extension's blocked page (step 10.1).
                            com.mahm0udnasr.sword.ui.reminder.ReminderText(
                                quote,
                                androidx.compose.ui.text.TextStyle(
                                    fontFamily = Amiri,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = if (quote.type == "poetry") 26.sp else 30.sp,
                                    lineHeight = if (quote.type == "poetry") 50.sp else 57.sp,
                                    color = t.text,
                                    textAlign = TextAlign.Center,
                                ),
                            )
                        }
                        if (!quote?.source.isNullOrEmpty()) {
                            Spacer(Modifier.height(20.dp))
                            Text(quote?.source.orEmpty(), fontFamily = Amiri, fontSize = 19.sp, color = t.gold, textAlign = TextAlign.Center)
                        }
                    }
                }
            }
            // Clock / guard: the one thing to do next.
            if (actionLabel != null) {
                androidx.compose.material3.OutlinedButton(onClick = onAction) {
                    Text(actionLabel, fontFamily = PlexArabic)
                }
            }
            // A quiet way out, not a call to action (the extension's .pause-link).
            if (pauseLabel != null) {
                Text(
                    pauseLabel, color = t.muted, fontFamily = PlexArabic, fontSize = 14.sp,
                    textDecoration = TextDecoration.Underline,
                    modifier = Modifier.minimumInteractiveComponentSize().clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onPause).padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
            message?.let { Text(it, color = t.muted, fontFamily = PlexArabic, fontSize = 13.sp, textAlign = TextAlign.Center) }
        }
    }
}
