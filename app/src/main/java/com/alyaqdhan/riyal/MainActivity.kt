@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.alyaqdhan.riyal

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.isVisible
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.navOptions
import com.alyaqdhan.riyal.ui.MainViewModel
import com.alyaqdhan.riyal.ui.theme.RiyalTheme
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Single-activity shell: an XML nav graph (res/navigation/nav_graph.xml) routes the
 * fragments with native transitions, while navigation is an M3 Expressive
 * HorizontalFloatingToolbar (a vibrant pill floating over the content) driven by the
 * same NavController.
 */
class MainActivity : AppCompatActivity() {

    private val vm: MainViewModel by viewModels()
    private val currentDestination = MutableStateFlow(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val navHost = supportFragmentManager.findFragmentById(R.id.nav_host) as NavHostFragment
        val navController = navHost.navController
        val graph = navController.navInflater.inflate(R.navigation.nav_graph)
        graph.setStartDestination(
            if (vm.prefs.onboardingDone) R.id.homeFragment else R.id.onboardingFragment,
        )
        navController.graph = graph

        val bottomBar = findViewById<ComposeView>(R.id.bottom_bar)
        navController.addOnDestinationChangedListener { _, destination, _ ->
            currentDestination.value = destination.id
            // Hidden on onboarding and on inner pages (review) for a focused push feel.
            bottomBar.isVisible =
                destination.id != R.id.onboardingFragment && destination.id != R.id.reviewFragment
        }
        vm.autoScanOnLaunch()

        bottomBar.setContent {
            RiyalTheme {
                val selected by currentDestination.collectAsState()
                val reviewCount by vm.pendingReviewCount.collectAsState()
                RiyalNavBar(
                    selected = selected,
                    reviewCount = reviewCount,
                    onSelect = { destId -> switchTab(navController, destId) },
                )
            }
        }
    }

    private fun switchTab(navController: NavController, destId: Int) {
        if (navController.currentDestination?.id == destId) return
        navController.navigate(
            destId,
            null,
            navOptions {
                launchSingleTop = true
                restoreState = true
                popUpTo(R.id.homeFragment) { saveState = true }
            },
        )
    }
}

@Composable
private fun RiyalNavBar(selected: Int, reviewCount: Int, onSelect: (Int) -> Unit) {
    HorizontalFloatingToolbar(
        expanded = true,
        colors = FloatingToolbarDefaults.vibrantFloatingToolbarColors(),
        modifier = Modifier
            .navigationBarsPadding()
            .padding(bottom = 12.dp),
    ) {
        NavToggle(
            checked = selected == R.id.homeFragment,
            onCheck = { onSelect(R.id.homeFragment) },
        ) {
            // Review lives inside Home; a plain dot (no number) marks pending items,
            // the count itself is on the Home "Needs review" card.
            BadgedBox(badge = { if (reviewCount > 0) Badge() }) {
                Icon(Icons.Filled.Home, contentDescription = "Home")
            }
        }
        NavToggle(
            checked = selected == R.id.transactionsFragment,
            onCheck = { onSelect(R.id.transactionsFragment) },
        ) { Icon(Icons.AutoMirrored.Filled.List, contentDescription = "Activity") }
        NavToggle(
            checked = selected == R.id.analysisFragment,
            onCheck = { onSelect(R.id.analysisFragment) },
        ) { Icon(painterResource(R.drawable.ic_pie), contentDescription = "Analysis") }
        NavToggle(
            checked = selected == R.id.settingsFragment,
            onCheck = { onSelect(R.id.settingsFragment) },
        ) { Icon(Icons.Filled.Settings, contentDescription = "Settings") }
    }
}

@Composable
private fun RowScope.NavToggle(
    checked: Boolean,
    onCheck: () -> Unit,
    icon: @Composable () -> Unit,
) {
    // Expressive shape morph: round when idle, squarish when selected or pressed,
    // instead of sitting in one perfect circle forever.
    FilledIconToggleButton(
        checked = checked,
        onCheckedChange = { if (it) onCheck() },
        shapes = IconButtonDefaults.toggleableShapes(),
        content = icon,
    )
}
