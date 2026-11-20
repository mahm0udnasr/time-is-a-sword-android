package com.mahm0udnasr.sword.ui.targets

import android.content.Context
import com.mahm0udnasr.sword.container
import com.mahm0udnasr.sword.core.model.Lang
import com.mahm0udnasr.sword.core.shorts.ShortsRules
import com.mahm0udnasr.sword.core.shorts.ShortsSection

/** The short-video sections of `assets/shorts.json`, read once, for the UI (Phase 12). */
object ShortsCatalog {
    @Volatile
    private var rules: ShortsRules? = null

    fun rules(ctx: Context): ShortsRules = rules ?: ShortsRules.parse(
        ctx.applicationContext.assets.open("shorts.json").use { it.readBytes().decodeToString() },
    ).also { rules = it }

    fun sections(ctx: Context): List<ShortsSection> = rules(ctx).sections

    /** The section's name in the app's language. */
    fun name(ctx: Context, id: String): String {
        val s = rules(ctx).byId(id) ?: return id
        val lang = if (ctx.container.config.value.appearance.lang == Lang.AR) "ar" else "en"
        return s.name[lang] ?: s.name.values.firstOrNull() ?: id
    }
}
