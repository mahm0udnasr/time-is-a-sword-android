package com.mahm0udnasr.sword.ui.friction

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.mahm0udnasr.sword.AppContainer
import com.mahm0udnasr.sword.R
import com.mahm0udnasr.sword.core.model.Config
import com.mahm0udnasr.sword.core.quotes.Quote
import com.mahm0udnasr.sword.core.quotes.QuoteFile
import com.mahm0udnasr.sword.data.CommitPipeline
import com.mahm0udnasr.sword.ui.components.InstalledApps
import com.mahm0udnasr.sword.ui.format.looseText
import kotlinx.coroutines.CompletableDeferred

/**
 * The one way screens change the config (port of options/commit.js):
 * stricter/neutral parts apply at once; if anything is looser, the friction
 * step runs first and the looser parts apply only if it is passed.
 */
class Committer(private val c: AppContainer, private val ctx: () -> Context) {
    private data class Open(val request: FrictionRequest, val quote: Quote, val result: CompletableDeferred<Boolean>)

    private var open by mutableStateOf<Open?>(null)

    enum class Outcome { NO_CHANGE, APPLIED, CANCELLED }

    /** @param title shown on the friction step, e.g. "Switch off Instagram?" */
    suspend fun commit(draft: Config, title: String? = null): Outcome {
        return when (val r = c.commit.save(draft)) {
            CommitPipeline.Result.NoChange -> Outcome.NO_CHANGE
            CommitPipeline.Result.Applied -> Outcome.APPLIED
            is CommitPipeline.Result.NeedsFriction -> {
                val context = ctx()
                val reasons = r.pending.reasons.map { context.looseText(it) { pkg -> InstalledApps.label(context, pkg) } }
                val ok = friction(
                    FrictionRequest(
                        title = title ?: context.getString(R.string.x_fr_defaultTitle),
                        reasons = reasons,
                        note = context.getString(R.string.x_fr_note),
                        confirmLabel = context.getString(R.string.x_fr_apply),
                    ),
                )
                if (ok) {
                    c.commit.applyConfirmed(r.pending)
                    Outcome.APPLIED
                } else {
                    Outcome.CANCELLED
                }
            }
        }
    }

    /** Run the friction step on its own (pauses use it too, Phase 6). */
    suspend fun friction(request: FrictionRequest): Boolean {
        val quote = c.reminders.pick(QuoteFile.REMINDERS, c.state.value.lastQuoteId) ?: FALLBACK
        c.state.update { it.copy(lastQuoteId = quote.id) }
        val d = CompletableDeferred<Boolean>()
        open = Open(request, quote, d)
        return try {
            d.await()
        } finally {
            open = null
        }
    }

    /** Place once at the root of the UI. */
    @Composable
    fun Host() {
        val o = open ?: return
        FrictionDialog(o.request, o.quote) { o.result.complete(it) }
    }

    private companion object {
        /** Used only if the reminders list is empty (same as the extension). */
        val FALLBACK = Quote("fallback", "الوقت سيف، فإن قطعته وإلا قطعك", "منقول عن الإمام الشافعي", "athar")
    }
}
