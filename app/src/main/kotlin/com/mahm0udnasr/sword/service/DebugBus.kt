package com.mahm0udnasr.sword.service

import com.mahm0udnasr.sword.core.track.FlushResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * What the service sees right now.
 * @property hostSource "urlbar", "webapk", "typing" (previous host kept) or null
 * @property targetId the enabled target this maps to, or null
 */
data class Foreground(
    val pkg: String? = null,
    val visible: List<String> = emptyList(),
    val title: String? = null,
    val ignored: Boolean = false,
    val browser: Boolean = false,
    val host: String? = null,
    val hostSource: String? = null,
    val targetId: String? = null,
)

/**
 * In-memory live data for the hidden Debug screen (replaces the extension's
 * slDebug(), step 3.6). Same process as the service, so plain StateFlows.
 */
object DebugBus {
    val running = MutableStateFlow(false)
    val foreground = MutableStateFlow(Foreground())
    val screen = MutableStateFlow(ScreenState.Screen(interactive = false, unlocked = false))
    val lastFlush = MutableStateFlow<FlushResult?>(null)
    val dump = MutableStateFlow("")
    val unsupportedBrowsers = MutableStateFlow<Set<String>>(emptySet())

    private val _events = MutableStateFlow<List<String>>(emptyList())
    val events: StateFlow<List<String>> = _events.asStateFlow()

    private val hms = DateTimeFormatter.ofPattern("HH:mm:ss")

    fun event(text: String) {
        val line = "${LocalTime.now().format(hms)}  $text"
        if (com.mahm0udnasr.sword.BuildConfig.DEBUG) runCatching { android.util.Log.i("SwordDebug", text) }
        _events.update { (listOf(line) + it).take(50) }
    }

    /** Set by the service; the Debug screen calls it. */
    @Volatile
    var requestDump: (() -> Unit)? = null
}
