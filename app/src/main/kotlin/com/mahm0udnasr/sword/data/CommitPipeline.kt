package com.mahm0udnasr.sword.data

import com.mahm0udnasr.sword.core.loosen.ConfigOp
import com.mahm0udnasr.sword.core.loosen.LooseItem
import com.mahm0udnasr.sword.core.loosen.applyOps
import com.mahm0udnasr.sword.core.loosen.describeLoosening
import com.mahm0udnasr.sword.core.loosen.diffState
import com.mahm0udnasr.sword.core.model.Config

/**
 * The one way the UI changes the config (port of options/commit.js +
 * background/changes.js, step 2.5):
 *
 *   save(draft) → diff → stricter/neutral ops apply now
 *               → looser ops come back as [Pending] for the friction step
 *   friction passed → applyConfirmed(pending); cancelled → just drop it
 *
 * Ops are per entity (a target, a group, a settings block), so a target edit
 * that is partly looser waits as a whole.
 */
class CommitPipeline(private val config: ConfigRepository) {

    /** Looser changes waiting for the friction step. [reasons] are shown before it. */
    data class Pending(val ops: List<ConfigOp>, val reasons: List<LooseItem>)

    sealed interface Result {
        /** Nothing differed. */
        data object NoChange : Result

        /** Everything applied (nothing was looser). */
        data object Applied : Result

        /** Stricter parts applied; [pending] needs the friction step. */
        data class NeedsFriction(val pending: Pending) : Result
    }

    suspend fun save(draft: Config): Result {
        config.awaitLoaded()
        val ops = diffState(config.value, draft)
        if (ops.isEmpty()) return Result.NoChange
        val (looser, now) = ops.partition { it.isLooser }
        if (now.isNotEmpty()) config.update { applyOps(it, now) }
        if (looser.isEmpty()) return Result.Applied
        return Result.NeedsFriction(Pending(looser, describeLoosening(looser)))
    }

    /** Apply looser ops after the friction step was passed. They take effect at once. */
    suspend fun applyConfirmed(pending: Pending) {
        config.update { applyOps(it, pending.ops) }
    }
}
