package com.alyaqdhan.riyal

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.painterResource
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
 * fragments with native transitions, while the bottom bar is a Compose Material 3
 * NavigationBar driven by the same NavController.
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
            bottomBar.isVisible = destination.id != R.id.onboardingFragment
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
    NavigationBar {
        NavigationBarItem(
            selected = selected == R.id.homeFragment,
            onClick = { onSelect(R.id.homeFragment) },
            icon = { Icon(Icons.Filled.Home, contentDescription = null) },
            label = { Text("Home") },
        )
        NavigationBarItem(
            selected = selected == R.id.transactionsFragment,
            onClick = { onSelect(R.id.transactionsFragment) },
            icon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = null) },
            label = { Text("Activity") },
        )
        NavigationBarItem(
            selected = selected == R.id.analysisFragment,
            onClick = { onSelect(R.id.analysisFragment) },
            icon = { Icon(painterResource(R.drawable.ic_pie), contentDescription = null) },
            label = { Text("Analysis") },
        )
        NavigationBarItem(
            selected = selected == R.id.reviewFragment,
            onClick = { onSelect(R.id.reviewFragment) },
            icon = {
                BadgedBox(badge = { if (reviewCount > 0) Badge { Text("$reviewCount") } }) {
                    Icon(painterResource(R.drawable.ic_inbox), contentDescription = null)
                }
            },
            label = { Text("Review") },
        )
        NavigationBarItem(
            selected = selected == R.id.settingsFragment,
            onClick = { onSelect(R.id.settingsFragment) },
            icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
            label = { Text("Settings") },
        )
    }
}
