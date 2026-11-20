package com.mahm0udnasr.sword.ui

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Mosque
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.mahm0udnasr.sword.R
import com.mahm0udnasr.sword.container
import com.mahm0udnasr.sword.core.model.Lang
import com.mahm0udnasr.sword.ui.components.LocalSnackbar
import com.mahm0udnasr.sword.ui.friction.Committer
import com.mahm0udnasr.sword.ui.groups.GroupEditorScreen
import com.mahm0udnasr.sword.ui.groups.GroupsScreen
import com.mahm0udnasr.sword.ui.overview.OverviewScreen
import com.mahm0udnasr.sword.ui.salah.SalahScreen
import com.mahm0udnasr.sword.ui.settings.SettingsScreen
import com.mahm0udnasr.sword.ui.targets.TargetEditorScreen
import com.mahm0udnasr.sword.ui.targets.TargetsScreen
import com.mahm0udnasr.sword.ui.theme.SwordTheme
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Per-app language (step 5.2): Arabic by default, Western digits in both languages. */
object AppLocale {
    fun tag(lang: Lang) = if (lang == Lang.AR) "ar-u-nu-latn" else "en"

    fun apply(lang: Lang) {
        val want = LocaleListCompat.forLanguageTags(tag(lang))
        if (AppCompatDelegate.getApplicationLocales().toLanguageTags() != want.toLanguageTags()) {
            AppCompatDelegate.setApplicationLocales(want)
        }
    }
}

private data class Tab(val route: String, val label: Int, val icon: ImageVector)

private val TABS = listOf(
    Tab("overview", R.string.x_nav_overview, Icons.Outlined.Dashboard),
    Tab("targets", R.string.nav_targets_short, Icons.Outlined.Apps),
    Tab("groups", R.string.x_nav_groups, Icons.Outlined.Layers),
    Tab("salah", R.string.x_nav_prayer, Icons.Outlined.Mosque),
    Tab("settings", R.string.nav_settings, Icons.Outlined.Settings),
)

/** The app (step 5.3): bottom bar with Overview, Apps & sites, Groups, Salah First, Settings. */
class MainActivity : AppCompatActivity() {
    /** The screen a widget asked for (Widgets.EXTRA_ROUTE), consumed by the NavHost. */
    private val route = androidx.compose.runtime.mutableStateOf<String?>(null)

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.getStringExtra(com.mahm0udnasr.sword.widget.Widgets.EXTRA_ROUTE)?.let { route.value = it }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val c = container
        val committer = Committer(c) { this }
        if (savedInstanceState == null) route.value = intent.getStringExtra(com.mahm0udnasr.sword.widget.Widgets.EXTRA_ROUTE)
        // A language switch recreates this screen once the new language is in place: redraw the
        // widgets now, in that language (the service's refresh can run a moment too early).
        if (savedInstanceState != null) com.mahm0udnasr.sword.widget.Widgets.request(this, force = true)

        // The saved language is the source of truth; switching recreates the activity.
        lifecycleScope.launch {
            c.config.awaitLoaded()
            c.config.flow.map { it.appearance.lang }.distinctUntilChanged().collect { AppLocale.apply(it) }
        }

        setContent {
            val cfg by c.config.flow.collectAsStateWithLifecycle()
            val snackbar = remember { SnackbarHostState() }
            val dark = com.mahm0udnasr.sword.ui.theme.isDark(cfg.appearance.theme)
            // Status/navigation bar icons follow the app's theme, not the phone's.
            androidx.compose.runtime.LaunchedEffect(dark) {
                val style = if (dark) {
                    androidx.activity.SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                } else {
                    androidx.activity.SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
                }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }
            // First run: the wizard (Phase 9), once the state file is read (no flash for returning users).
            val st by c.state.flow.collectAsStateWithLifecycle()
            var loaded by remember { androidx.compose.runtime.mutableStateOf(false) }
            androidx.compose.runtime.LaunchedEffect(Unit) {
                c.state.awaitLoaded()
                loaded = true
            }
            SwordTheme(cfg.appearance.theme) {
                if (!loaded) return@SwordTheme
                if (!st.onboardingDone) {
                    com.mahm0udnasr.sword.ui.onboarding.OnboardingScreen(c, committer, onDone = {})
                    committer.Host()
                    return@SwordTheme
                }
                CompositionLocalProvider(LocalSnackbar provides snackbar) {
                    val nav = rememberNavController()
                    // Opened from a widget: go to its screen ("overview" / "salah").
                    androidx.compose.runtime.LaunchedEffect(route.value) {
                        val r = route.value ?: return@LaunchedEffect
                        route.value = null
                        nav.navigate(r) {
                            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                    val entry by nav.currentBackStackEntryAsState()
                    val route = entry?.destination?.route
                    Scaffold(
                        snackbarHost = { SnackbarHost(snackbar) },
                        bottomBar = {
                            NavigationBar {
                                TABS.forEach { tab ->
                                    val selected = route == tab.route || route?.startsWith(tab.route.removeSuffix("s") + "/") == true || route?.startsWith(tab.route + "/") == true
                                    NavigationBarItem(
                                        selected = selected,
                                        // Large fonts: only the selected tab's label, the rest would be cut off.
                                        alwaysShowLabel = !com.mahm0udnasr.sword.ui.components.largeFont(),
                                        onClick = {
                                            nav.navigate(tab.route) {
                                                popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                                launchSingleTop = true
                                                restoreState = true
                                            }
                                        },
                                        icon = { Icon(tab.icon, null) },
                                        label = { Text(stringResource(tab.label), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                    )
                                }
                            }
                        },
                    ) { pad ->
                        NavHost(nav, startDestination = "overview", modifier = Modifier.padding(pad)) {
                            composable("overview") { OverviewScreen(c, committer) }
                            composable("targets") { TargetsScreen(c, committer) { id -> nav.navigate("target/$id") } }
                            composable("target/{id}") { e -> TargetEditorScreen(c, committer, e.arguments?.getString("id").orEmpty()) { nav.popBackStack() } }
                            composable("groups") { GroupsScreen(c, committer) { id -> nav.navigate("group/$id") } }
                            composable("group/{id}") { e -> GroupEditorScreen(c, committer, e.arguments?.getString("id").orEmpty()) { nav.popBackStack() } }
                            composable("salah") { SalahScreen(c, committer) }
                            composable("settings") { SettingsScreen(c, committer) { nav.navigate("settings/about") } }
                            composable("settings/about") { com.mahm0udnasr.sword.ui.about.AboutScreen(cfg.appearance.lang) }
                        }
                    }
                    committer.Host()
                }
            }
        }
    }
}
