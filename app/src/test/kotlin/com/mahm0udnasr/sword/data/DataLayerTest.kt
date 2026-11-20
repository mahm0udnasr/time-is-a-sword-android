package com.mahm0udnasr.sword.data

import com.mahm0udnasr.sword.core.loosen.LooseReason
import com.mahm0udnasr.sword.core.loosen.RuleWhy
import com.mahm0udnasr.sword.core.model.Config
import com.mahm0udnasr.sword.core.model.DayUsage
import com.mahm0udnasr.sword.core.model.Mode
import com.mahm0udnasr.sword.core.model.Rule
import com.mahm0udnasr.sword.core.quotes.QuoteFile
import com.mahm0udnasr.sword.core.rules.normalizeDomain
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule as JUnitRule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import com.mahm0udnasr.sword.core.model.Defaults
import java.io.File

/** Phase 2 acceptance: repositories, corruption, concurrency, seeding, commit pipeline, assets. */
class DataLayerTest {
    @get:JUnitRule
    val tmp = TemporaryFolder()

    private val scopes = mutableListOf<CoroutineScope>()
    private fun newScope() = CoroutineScope(Dispatchers.IO + Job()).also { scopes += it }
    private suspend fun close(s: CoroutineScope) = s.coroutineContext.job.cancelAndJoin()

    @After
    fun tearDown() = runBlocking { scopes.forEach { close(it) } }

    private val assets = File("src/main/assets")
    private val presets = File(assets, "presets.json").readText()

    @Test
    fun `first run - all 13 presets and Short videos, switched off, no groups, Salah First off`() = runBlocking {
        val repo = ConfigRepository(tmp.root, newScope(), firstRunConfig(presets))
        repo.awaitLoaded()
        val c = repo.value
        assertEquals(14, c.targets.size)
        assertTrue(c.targets.none { it.enabled })
        assertTrue(c.groups.isEmpty())
        assertFalse(c.prayer.enabled)
        assertEquals(listOf("com.instagram.android", "com.instagram.lite"), c.targets.first { it.id == "p_instagram" }.packages)
        assertTrue("Graph Messenger is in Telegram", "ir.ilmili.telegraph" in c.targets.first { it.id == "p_telegram" }.packages)
        assertTrue(c.targets.any { it.name == "Discord" })
    }

    @Test
    fun `every preset domain is already normalized and ids are unique`() {
        val raw = StoreJson.decodeFromString<List<Preset>>(presets)
        raw.flatMap { it.domains }.forEach { assertEquals(it, normalizeDomain(it)) }
        assertEquals(raw.size, raw.map { it.id }.toSet().size)
        assertEquals("a package belongs to one preset only", raw.flatMap { it.packages }.size, raw.flatMap { it.packages }.toSet().size)
    }

    @Test
    fun `writes persist across a restart`() = runBlocking {
        val s1 = newScope()
        val repo = ConfigRepository(tmp.root, s1, firstRunConfig(presets))
        repo.update { c -> c.copy(targets = c.targets.map { if (it.id == "p_youtube") it.copy(enabled = true) else it }) }
        close(s1)
        val again = ConfigRepository(tmp.root, newScope(), Config())
        again.awaitLoaded()
        assertTrue(again.value.targets.first { it.id == "p_youtube" }.enabled)
        assertEquals("the file wins over first-run defaults", 14, again.value.targets.size)
    }

    @Test
    fun `corrupt file - defaults used and a copy kept`() = runBlocking {
        File(tmp.root, "state.json").writeText("{\"usage\": {\"2026-11-19\": ") // truncated mid-write
        val repo = StateRepository(tmp.root, newScope())
        repo.update { it } // forces a read
        assertEquals(com.mahm0udnasr.sword.core.model.State(), repo.value)
        val copies = tmp.root.listFiles()!!.filter { it.name.startsWith("state.json.corrupt-") }
        assertEquals(1, copies.size)
        assertTrue(copies[0].readText().startsWith("{\"usage\""))
    }

    @Test
    fun `concurrent updates never lose a write`() = runBlocking {
        val repo = StateRepository(tmp.root, newScope())
        (1..200).map {
            async(Dispatchers.IO) {
                repo.update { s ->
                    val d = s.usage["d"] ?: DayUsage()
                    s.copy(usage = mapOf("d" to d.copy(pausesUsed = d.pausesUsed + 1)))
                }
            }
        }.awaitAll()
        assertEquals(200, repo.value.usage["d"]!!.pausesUsed)
    }

    @Test
    fun `old schema is migrated on read`() = runBlocking {
        File(tmp.root, "config.json").writeText("{\"schemaVersion\":0,\"targets\":[]}")
        val repo = ConfigRepository(tmp.root, newScope(), Config())
        repo.awaitLoaded()
        assertEquals(Defaults.SCHEMA_VERSION, repo.value.schemaVersion)
        assertEquals(Defaults.SCHEMA_VERSION, ConfigMigrations.migrate(Config(schemaVersion = 0)).schemaVersion)
        // 1 → 2 adds the Short-videos target, switched off (Phase 12).
        val shorts = repo.value.targets.single { it.id == com.mahm0udnasr.sword.core.shorts.SHORTS_TARGET_ID }
        assertFalse(shorts.enabled)
    }

    @Test
    fun `unknown fields from a newer version are ignored`() = runBlocking {
        File(tmp.root, "config.json").writeText("{\"schemaVersion\":2,\"futureThing\":true,\"targets\":[]}")
        val repo = ConfigRepository(tmp.root, newScope(), firstRunConfig(presets))
        repo.awaitLoaded()
        assertEquals(0, repo.value.targets.size)
        assertTrue(tmp.root.listFiles()!!.none { it.name.contains("corrupt") })
    }

    // --- commit pipeline
    @Test
    fun `stricter applies now, looser waits for friction, cancel changes nothing`() = runBlocking {
        val repo = ConfigRepository(tmp.root, newScope(), firstRunConfig(presets))
        val commit = CommitPipeline(repo)
        repo.awaitLoaded()

        // Switching YouTube on (limit 20) is stricter → applied at once.
        val on = repo.value.copy(
            targets = repo.value.targets.map { if (it.id == "p_youtube") it.copy(enabled = true, rules = listOf(Rule.default(Mode.LIMIT))) else it },
        )
        assertEquals(CommitPipeline.Result.Applied, commit.save(on))
        assertTrue(repo.value.targets.first { it.id == "p_youtube" }.enabled)
        assertEquals(CommitPipeline.Result.NoChange, commit.save(on))

        // Raise the limit (looser) and switch Instagram on (stricter) in one save.
        val mixed = repo.value.copy(
            targets = repo.value.targets.map {
                when (it.id) {
                    "p_youtube" -> it.copy(rules = listOf(Rule.default(Mode.LIMIT).copy(limitMinutes = 45)))
                    "p_instagram" -> it.copy(enabled = true)
                    else -> it
                }
            },
        )
        val r = commit.save(mixed) as CommitPipeline.Result.NeedsFriction
        assertTrue("stricter part applied", repo.value.targets.first { it.id == "p_instagram" }.enabled)
        assertEquals("looser part not yet", 20, repo.value.targets.first { it.id == "p_youtube" }.rules[0].limitMinutes)
        assertEquals(LooseReason.Rule((0..6).toList(), RuleWhy.LimitRaised(20, 45)), r.pending.reasons.single().reason)

        // Cancel = drop the pending ops: nothing more changes.
        assertEquals(20, repo.value.targets.first { it.id == "p_youtube" }.rules[0].limitMinutes)

        // Friction passed → applied.
        commit.applyConfirmed(r.pending)
        assertEquals(45, repo.value.targets.first { it.id == "p_youtube" }.rules[0].limitMinutes)
    }

    // --- assets (step 2.6)
    @Test
    fun `reminders load from the assets copied from the extension`() {
        val rem = Reminders { name -> File(assets, name).readText() }
        val q = rem.all(QuoteFile.REMINDERS)
        val s = rem.all(QuoteFile.SALAH)
        assertEquals(109, q.size)
        assertEquals(10, s.size)
        assertEquals("q0", q[0].id)
        assertEquals("s0", s[0].id)
        val a = rem.pick(QuoteFile.REMINDERS, null)!!
        assertNotEquals(a.id, rem.pick(QuoteFile.REMINDERS, a.id)!!.id)
    }
}
