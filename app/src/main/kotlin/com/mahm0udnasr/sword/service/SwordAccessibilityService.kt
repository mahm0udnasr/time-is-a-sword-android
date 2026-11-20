package com.mahm0udnasr.sword.service

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import androidx.core.content.ContextCompat
import com.mahm0udnasr.sword.container
import com.mahm0udnasr.sword.core.model.Config
import com.mahm0udnasr.sword.core.rules.findTargetByHost
import com.mahm0udnasr.sword.core.rules.findTargetByPackage
import com.mahm0udnasr.sword.core.rules.Locks
import com.mahm0udnasr.sword.core.rules.WindowInfo
import com.mahm0udnasr.sword.ui.overlay.OverlayController
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * The heart of the app (plan §8, step 3.1): knows what is in front and feeds
 * the tracker. Blocking arrives in Phase 4.
 *
 * Must never crash (Android switches off an accessibility service that keeps
 * crashing): every callback runs inside [guard].
 */
class SwordAccessibilityService : AccessibilityService() {

    private val main = Handler(Looper.getMainLooper())
    private var scope: CoroutineScope? = null
    private lateinit var screen: ScreenState
    private lateinit var resolver: ForegroundResolver
    private lateinit var urls: BrowserUrlReader
    private lateinit var tracker: Tracker
    private lateinit var enforcer: Enforcer
    private lateinit var overlay: OverlayController
    private lateinit var prayer: com.mahm0udnasr.sword.prayer.PrayerScheduler
    private lateinit var settingsGuard: Guard
    private lateinit var shorts: ShortsDetector
    private var autoTimeObserver: android.database.ContentObserver? = null

    /** Automatic date & time (D12), followed live. */
    @Volatile
    private var autoTimeOn = true
    private var current = Foreground()
    private var resolvePending = false
    private val resolveRunnable = Runnable { resolvePending = false; guard("resolve") { resolveNow() } }
    private var urlPending = false
    private val urlRunnable = Runnable { urlPending = false; guard("url") { resolveNow() } }

    /**
     * One more look once a short-video app's screen has settled (Phase 12). Opening the
     * Shorts/Reels tab sends a burst of events while the player is still loading; the
     * throttled read can land before it, and a playing video sends no more events, so
     * without this the player was sometimes missed (seen on the phone).
     */
    private val settleRunnable = Runnable { guard("settle") { resolveNow() } }

    private val packagesChanged = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) = guard("packages") { refreshPackageLists() }
    }

    /** Time, zone or date changed: prayer windows move (step 7.5). */
    private val timeChanged = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) = guard("time") {
            DebugBus.event("TIME ${i.action?.substringAfterLast('.')}")
            applicationContext.container.prayerClock.invalidate()
            prayer.reschedule()
            enforceNow()
            com.mahm0udnasr.sword.widget.Widgets.request(this@SwordAccessibilityService, force = true)
        }
    }

    override fun onServiceConnected() = guard("connect") {
        val c = applicationContext.container
        val crashGuard = CoroutineExceptionHandler { _, t ->
            Log.e(TAG, "coroutine error", t)
            runCatching { DebugBus.event("ERROR coroutine: ${t.javaClass.simpleName} ${t.message}") }
        }
        val s = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate + crashGuard).also { scope = it }
        screen = ScreenState(this).also { it.start() }
        resolver = ForegroundResolver(this)
        urls = BrowserUrlReader(this, assets.open("browsers.json").use { it.readBytes().decodeToString() })
        refreshPackageLists()
        ContextCompat.registerReceiver(
            this, packagesChanged,
            IntentFilter().apply {
                addAction(Intent.ACTION_PACKAGE_ADDED)
                addAction(Intent.ACTION_PACKAGE_REMOVED)
                addAction(Intent.ACTION_PACKAGE_REPLACED)
                addDataScheme("package")
            },
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        overlay = OverlayController(this)
        settingsGuard = Guard(this, assets.open("guard.json").use { it.readBytes().decodeToString() })
        shorts = ShortsDetector(assets.open("shorts.json").use { it.readBytes().decodeToString() })
        autoTimeOn = com.mahm0udnasr.sword.system.AutoTime.isOn(this)
        autoTimeObserver = com.mahm0udnasr.sword.system.AutoTime.observe(this) { on ->
            guard("autotime") {
                autoTimeOn = on
                DebugBus.event("AUTO TIME $on")
                enforceNow()
            }
        }
        enforcer = Enforcer(this) { resolver.launchers }
        // Short videos (Phase 12, Q9): the small card after Back; then look again.
        enforcer.onSoft = { d ->
            val cfg = c.config.value
            val ctx = com.mahm0udnasr.sword.widget.Widgets.localized(this)
            overlay.showNotice(ctx.getString(com.mahm0udnasr.sword.R.string.shorts_closed), ctx.getString(com.mahm0udnasr.sword.R.string.shorts_closedHint), cfg.appearance.theme, cfg.appearance.lang)
        }
        enforcer.recheck = { main.post { guard("recheck") { resolveNow() } } }
        // Salah First (step 7.6): every app not on the allowlist, every website.
        // Clock (D12): automatic date & time off → limit/schedule targets closed.
        enforcer.locks = {
            val lock = c.prayerClock.active()
            Locks(
                prayerLocked = lock != null,
                prayerAllows = c.prayerAllowlist::allows,
                clockTrusted = !c.config.value.protection.requireAutoTime || autoTimeOn,
            )
        }
        prayer = com.mahm0udnasr.sword.prayer.PrayerScheduler(this, c.prayerClock, s, onBoundary = {
            DebugBus.event("PRAYER boundary lock=${c.prayerClock.active()?.prayer ?: "-"}")
            updateStatus(DebugBus.lastFlush.value?.state?.session?.targetId, DebugBus.lastFlush.value?.verdict)
            com.mahm0udnasr.sword.widget.Widgets.request(this, force = true)
            enforceNow()
            refreshPrayerTimes()
        })
        ContextCompat.registerReceiver(
            this, timeChanged,
            IntentFilter().apply {
                addAction(Intent.ACTION_TIME_CHANGED)
                addAction(Intent.ACTION_TIMEZONE_CHANGED)
                addAction(Intent.ACTION_DATE_CHANGED)
            },
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        tracker = Tracker(c.config, c.state, s).apply {
            onFlushed = { r ->
                val before = DebugBus.lastFlush.value?.state?.session?.targetId
                val after = r.state.session?.targetId
                if (before != after) DebugBus.event("SESSION $before → $after (inUse=$inUse, verdict=${r.verdict?.javaClass?.simpleName})")
                DebugBus.lastFlush.value = r
                updateStatus(r.state.session?.targetId, r.verdict)
                com.mahm0udnasr.sword.widget.Widgets.request(this@SwordAccessibilityService)
                // Budget ran out, window ended, pause ended, rules changed: judge the screen again (4.4).
                enforceNow()
            }
            onWarning = { w ->
                DebugBus.event("WARNING ${w.targetId} ${(w.secondsLeft / 60).toInt()} min left (${w.kind})")
                val cfg = c.config.value
                val name = cfg.targets.firstOrNull { it.id == w.targetId }?.name ?: w.targetId
                if (screen.state.value.active) overlay.showWarning(name, w.kind, w.blockAt, cfg.appearance.theme, cfg.appearance.lang)
            }
            onRanOut = { id -> DebugBus.event("RAN OUT $id") }
            onScheduleWake = { at -> WakeAlarm.schedule(this@SwordAccessibilityService, at) }
        }
        s.launch {
            tracker.start()
            screen.state.collect { sc ->
                DebugBus.screen.value = sc
                DebugBus.event("SCREEN interactive=${sc.interactive} unlocked=${sc.unlocked}")
                if (sc.active) {
                    resolveNow()
                    refreshPrayerTimes()
                }
                tracker.onScreen(sc.active)
                updateInUse()
            }
        }
        s.launch {
            c.config.flow.collect {
                guard("config") {
                    refreshPrayerTimes()
                    val at = prayer.reschedule()
                    com.mahm0udnasr.sword.widget.Widgets.request(this@SwordAccessibilityService, force = true)
                    DebugBus.event("PRAYER next boundary ${at?.let { java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneId.systemDefault()).toLocalTime() } ?: "-"}")
                    resolveNow()
                }
            }
        }
        DebugBus.requestDump = { main.postDelayed({ guard("dump") { dumpActiveWindow() } }, 5000) }
        WakeAlarm.onWake = {
            main.post {
                s.launch {
                    DebugBus.event("ALARM")
                    guard("alarm") { prayer.reschedule() }
                    tracker.flushNow() // also judges the screen again
                }
            }
        }
        DebugBus.running.value = true
        DebugBus.event("CONNECTED")
        com.mahm0udnasr.sword.system.HealthCheck.clear(this)
    }

    override fun onAccessibilityEvent(e: AccessibilityEvent?) = guard("event") {
        if (e == null || scope == null) return@guard
        val evPkg = e.packageName?.toString()
        if (shorts.watches(evPkg)) {
            main.removeCallbacks(settleRunnable)
            main.postDelayed(settleRunnable, SETTLE_MS)
        }
        when (e.eventType) {
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                // Single-page sites change URL without a new window (§8.2 step 5).
                // Throttled, not debounced: a page that never stops changing
                // (video, animations) must not postpone the read forever.
                // Settings pages change inside one window too (App list → App info): the guard re-reads them.
                // Short-video players open inside the same window too (YouTube Shorts tab, a reel from the feed).
                if ((current.browser || settingsGuard.watches(current.pkg) || shorts.watches(current.pkg)) && e.packageName?.toString() == current.pkg && !urlPending) {
                    urlPending = true
                    main.postDelayed(urlRunnable, URL_THROTTLE_MS)
                }
            }
            else -> if (!resolvePending) {
                // Throttle event storms: at most one resolution per RESOLVE_THROTTLE_MS.
                resolvePending = true
                main.postDelayed(resolveRunnable, RESOLVE_THROTTLE_MS)
            }
        }
    }

    private fun resolveNow() {
        if (scope == null) return
        screen.refresh()
        val w = resolver.resolve(this)
        if (w.pkg == null) return // a short gap between two apps (Spike A): keep what we had
        var host: String? = null
        var path: String? = null
        var source: String? = null
        val browser = !w.ignored && urls.isBrowser(w.pkg)
        if (browser) {
            when (val r = urls.read(this, w.pkg, w.title)) {
                is UrlRead.Host -> { host = r.host; path = r.path; source = r.source }
                UrlRead.Typing -> if (current.pkg == w.pkg) { host = current.host; path = current.path; source = "typing" }
                UrlRead.None -> {}
            }
        }
        // Short videos (Phase 12): a short-video page, a whole short-video app, or the player inside an app.
        val section = when {
            w.ignored -> null
            browser -> shorts.forWeb(host, path)
            else -> shorts.forApp(this, w.pkg)
        }
        val (primary, also) = targetsFor(w.pkg, host, w.ignored, browser, section?.id)
        val fg = Foreground(
            w.pkg, w.visible, w.title, w.ignored, browser, host, source, primary,
            path = path, section = section?.id, sectionInApp = section != null && !section.wholeApp && !browser, alsoTargetId = also,
        )
        if (fg != current) {
            if (fg.pkg != current.pkg || fg.host != current.host || fg.targetId != current.targetId || fg.section != current.section) {
                DebugBus.event("FG ${fg.pkg}${fg.host?.let { " · $it" } ?: ""}${fg.section?.let { " [$it]" } ?: ""}${fg.targetId?.let { " → $it" } ?: ""}${fg.alsoTargetId?.let { " + $it" } ?: ""} [${fg.hostSource ?: "-"}] visible=${fg.visible} title=${fg.title}")
            }
            current = fg
            DebugBus.foreground.value = fg
        }
        updateInUse()
        enforceNow()
    }

    /** A typed city's times: fetch from Aladhan when something is missing (throttled), then reschedule. */
    private fun refreshPrayerTimes() {
        val s = scope ?: return
        s.launch {
            val changed = runCatching { applicationContext.container.prayerFetcher.refreshIfDue() }.getOrDefault(false)
            if (changed) guard("prayer-fetched") {
                DebugBus.event("PRAYER times fetched error=${applicationContext.container.state.value.prayerCache?.error}")
                prayer.reschedule()
                enforceNow()
            }
        }
    }

    /** Judge every visible application window (split screen too) and block if needed. */
    private fun enforceNow() {
        if (scope == null || !screen.state.value.active) return
        val c = applicationContext.container
        val ignored = resolver.ignoredPackages
        val now = System.currentTimeMillis()
        val guardOn = com.mahm0udnasr.sword.core.guard.guardActive(c.config.value.protection, c.state.value.guardSuspendedUntil, now)
        val windows = current.visible.ifEmpty { listOfNotNull(current.pkg) }.map { pkg ->
            val browser = pkg !in ignored && urls.isBrowser(pkg)
            val host = when {
                !browser -> null
                pkg == current.pkg -> current.host
                else -> (urls.read(this, pkg, null) as? UrlRead.Host)?.host
            }
            val page = if (guardOn && pkg !in ignored) settingsGuard.check(this, pkg) else null
            if (page != null) DebugBus.event("GUARD $page in $pkg")
            // The section of the window in front is known; others (split screen): whole short-video apps only.
            val (section, inApp) = when {
                pkg == current.pkg -> current.section to current.sectionInApp
                pkg !in ignored && !browser -> shorts.rules.let { r -> com.mahm0udnasr.sword.core.shorts.matchWholeApp(r, pkg)?.id } to false
                else -> null to false
            }
            WindowInfo(pkg, host, browser, unsupportedBrowser = pkg in urls.unsupported, ignored = pkg in ignored, guarded = page != null, section = section, sectionInApp = inApp)
        }
        enforcer.check(windows, c.config.value, c.state.value, now, java.time.ZoneId.systemDefault())
    }

    /**
     * The enabled target in front: the website's target in a browser, else the app's.
     * In short videos (Phase 12) the Short-videos target is timed, and the app's or
     * site's own target too (Q8): returns (primary, also).
     */
    private fun targetsFor(pkg: String, host: String?, ignored: Boolean, browser: Boolean, section: String?): Pair<String?, String?> {
        if (ignored) return null to null
        val cfg: Config = applicationContext.container.config.value
        val t = if (browser) findTargetByHost(host, cfg.targets)?.target else findTargetByPackage(pkg, cfg.targets)
        val own = t?.takeIf { it.enabled }?.id
        val st = cfg.targets.firstOrNull { it.id == com.mahm0udnasr.sword.core.shorts.SHORTS_TARGET_ID && it.enabled && section != null && section in it.sections }
        return if (st != null) st.id to own else own to null
    }

    private fun updateInUse() {
        val active = screen.state.value.active
        tracker.setInUse(if (active) current.targetId else null, if (active) current.alsoTargetId else null)
    }

    private fun refreshPackageLists() {
        resolver.refreshIgnored()
        urls.refreshUnsupported(resolver.ignoredPackages)
        DebugBus.unsupportedBrowsers.value = urls.unsupported
        scope?.launch(Dispatchers.IO) { runCatching { urls.refreshWebApks() } }
        scope?.launch(Dispatchers.IO) { runCatching { applicationContext.container.prayerAllowlist.refresh() } }
    }

    /** Debug: log every node of the active app window (view ids + texts), for URL bars and guard texts. */
    private fun dumpActiveWindow() {
        val apps = windows.filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }
        val w = apps.firstOrNull { it.isActive } ?: apps.firstOrNull() ?: return
        val sb = StringBuilder("pkg=${w.root?.packageName} title=${w.title}\n")
        var n = 0
        fun walk(node: AccessibilityNodeInfo?, depth: Int) {
            if (node == null || depth > 60 || n > 1500) return
            n++
            if (node.viewIdResourceName != null || !node.text.isNullOrBlank() || !node.contentDescription.isNullOrBlank()) {
                sb.append("${"·".repeat(minOf(depth, 12))}${node.viewIdResourceName ?: "-"} | ${node.className} | '${node.text ?: ""}' | '${node.contentDescription ?: ""}'${if (node.isFocused) " FOCUSED" else ""}\n")
            }
            for (i in 0 until node.childCount) walk(node.getChild(i), depth + 1)
        }
        walk(w.root, 0)
        DebugBus.dump.value = sb.toString()
        sb.lines().chunked(40).forEach { Log.i(TAG, "DUMP\n" + it.joinToString("\n")) }
        DebugBus.event("DUMP ${w.root?.packageName} ($n nodes)")
    }

    override fun onInterrupt() {}

    override fun onUnbind(intent: Intent?): Boolean {
        shutdown()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        shutdown()
        super.onDestroy()
    }

    private fun shutdown() = guard("shutdown") {
        if (scope == null) return@guard
        main.removeCallbacksAndMessages(null)
        resolvePending = false
        urlPending = false
        runCatching { unregisterReceiver(packagesChanged) }
        runCatching { unregisterReceiver(timeChanged) }
        com.mahm0udnasr.sword.system.StatusNotification.clear(this)
        autoTimeObserver?.let { o -> runCatching { contentResolver.unregisterContentObserver(o) } }
        autoTimeObserver = null
        prayer.stop()
        tracker.stop()
        enforcer.stop()
        overlay.hide()
        screen.stop()
        scope?.cancel()
        scope = null
        DebugBus.requestDump = null
        WakeAlarm.onWake = null
        WakeAlarm.schedule(this, null)
        DebugBus.running.value = false
        DebugBus.event("DISCONNECTED")
    }

    /** The persistent status notification (step 10.2). */
    private fun updateStatus(targetId: String?, verdict: com.mahm0udnasr.sword.core.rules.Verdict?) = guard("status") {
        val c = applicationContext.container
        val lock = c.prayerClock.active()
        com.mahm0udnasr.sword.system.StatusNotification.update(
            this, c.config.value, targetId, verdict,
            lock?.let { com.mahm0udnasr.sword.system.StatusNotification.prayerRes(it.prayer) to it.end },
        )
    }

    /** Never let an exception escape a callback. */
    private inline fun guard(where: String, block: () -> Unit) {
        try {
            block()
        } catch (t: Throwable) {
            Log.e(TAG, "error in $where", t)
            runCatching { DebugBus.event("ERROR $where: ${t.javaClass.simpleName} ${t.message}") }
        }
    }

    private companion object {
        const val TAG = "SwordService"
        const val RESOLVE_THROTTLE_MS = 120L
        const val URL_THROTTLE_MS = 400L
        const val SETTLE_MS = 700L
    }
}
