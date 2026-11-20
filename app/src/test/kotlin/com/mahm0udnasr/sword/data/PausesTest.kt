package com.mahm0udnasr.sword.data

import com.mahm0udnasr.sword.core.model.Config
import com.mahm0udnasr.sword.core.model.makeTarget
import com.mahm0udnasr.sword.core.track.PauseResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Phase 6: pauses granted atomically in the state store (3 a day, 5 min each). */
class PausesTest {
    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun `three pauses a day then refused, and a switched-off target cannot be paused`() = runBlocking {
        val scope = CoroutineScope(Dispatchers.IO + Job())
        val ig = makeTarget("ig", "Instagram", packages = listOf("com.instagram.android"))
        val off = makeTarget("x", "X", enabled = false)
        val config = ConfigRepository(tmp.root, scope, Config(targets = listOf(ig, off)))
        val state = StateRepository(tmp.root, scope)
        config.awaitLoaded(); state.awaitLoaded()
        val p = Pauses(config, state)
        val now = System.currentTimeMillis()

        assertEquals(PauseResult.Code.NOT_ACTIVE, p.availability("x").code)
        assertTrue(p.availability("ig").ok)
        val lefts = (1..3).map { (p.grant("ig", now) as PauseResult.Granted).left }
        assertEquals(listOf(2, 1, 0), lefts)
        assertEquals(now + 5 * 60_000L, state.value.pauses["ig"]?.until)
        assertFalse(p.availability("ig").ok)
        assertEquals(PauseResult.Code.NONE_LEFT, p.availability("ig").code)
        assertEquals(PauseResult.Refused(PauseResult.Code.NONE_LEFT), p.grant("ig", now))
        assertEquals(0, p.left(now))
        scope.coroutineContext.job.cancelAndJoin()
    }
}
