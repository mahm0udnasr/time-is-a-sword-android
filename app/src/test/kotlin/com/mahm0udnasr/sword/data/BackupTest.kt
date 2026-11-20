package com.mahm0udnasr.sword.data

import com.mahm0udnasr.sword.core.loosen.diffState
import com.mahm0udnasr.sword.core.model.Config
import com.mahm0udnasr.sword.core.model.Mode
import com.mahm0udnasr.sword.core.model.Rule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate

class BackupTest {
    private val cfg = firstRunConfig(File("src/main/assets/presets.json").readText()).let { c ->
        c.copy(targets = c.targets.map { if (it.id == "p_instagram") it.copy(enabled = true, rules = listOf(Rule.default(Mode.BLOCK))) else it })
    }

    @Test
    fun `export then import gives the same config`() {
        assertEquals(cfg, Backup.parse(Backup.export(cfg)))
    }

    @Test
    fun `modes are written like the extension`() {
        assertTrue(Backup.export(cfg).contains("\"mode\": \"block\""))
    }

    @Test
    fun `file name has the date`() = assertEquals("time-is-a-sword-backup-2026-11-20.json", Backup.fileName(LocalDate.of(2026, 11, 20)))

    @Test
    fun `not a backup is refused`() {
        assertNull(Backup.parse("hello"))
        assertNull(Backup.parse("{}"))
        assertNull(Backup.parse("[1,2]"))
    }

    @Test
    fun `importing a looser backup needs friction for the looser parts only`() {
        val looser = cfg.copy(targets = cfg.targets.map { if (it.id == "p_instagram") it.copy(enabled = false) else it })
        val imported = Backup.parse(Backup.export(looser))!!
        val ops = diffState(cfg, imported)
        assertTrue("switching Instagram off is looser", ops.any { it.reasons.isNotEmpty() })
        assertEquals(Config::class, imported::class)
    }
}
