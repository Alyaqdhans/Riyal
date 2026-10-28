@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.alyaqdhan.riyal

import android.content.Context
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.annotation.DrawableRes
import androidx.annotation.IdRes
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.isVisible
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.navOptions
import com.alyaqdhan.riyal.core.Dates
import com.alyaqdhan.riyal.core.Language
import com.alyaqdhan.riyal.core.Verbose
import com.alyaqdhan.riyal.ui.MainViewModel
import com.alyaqdhan.riyal.ui.Words
import com.alyaqdhan.riyal.ui.compose.ManualTxnDialog
import com.alyaqdhan.riyal.ui.compose.UpdateDialogHost
import com.alyaqdhan.riyal.ui.theme.RiyalTheme
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Single-activity shell: an XML nav graph (res/navigation/nav_graph.xml) routes the
 * fragments, and a Material 3 navigation bar along the bottom switches between the five
 * top-level screens - the arrangement every finance app people already use has, so
 * nothing about getting around has to be learned.
 */
class MainActivity : AppCompatActivity() {

    private val vm: MainViewModel by viewModels()
    private val currentDestination = MutableStateFlow(0)

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(Language.latinDigits(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        Words.apply(this)
        Verbose.attach(this)
        Dates.use(resources.configuration.locales[0])
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
            // Only the five top-level screens carry the bar; a page pushed on top of one
            // has a back arrow instead.
            bottomBar.isVisible = Tab.entries.any { it.destination == destination.id }
        }
        vm.autoScanOnLaunch()
        // Once a day at most, only when "check on start" is on, and silent unless it
        // finds something. An empty version string parses to nothing, which is never
        // newer than anything, so a phone that cannot report its own version is simply
        // never offered an update.
        vm.checkForUpdate(
            try {
                packageManager.getPackageInfo(packageName, 0).versionName ?: ""
            } catch (e: Exception) {
                ""
            },
            onStart = true,
        )

        bottomBar.setContent {
            RiyalTheme {
                val selected by currentDestination.collectAsState()
                val reviewCount by vm.pendingReviewCount.collectAsState()
                val update by vm.updateState.collectAsState()
                RiyalNavBar(
                    selected = selected,
                    badges = buildSet {
                        if (reviewCount > 0) add(Tab.HOME)
                        if (update is MainViewModel.UpdateState.Available) add(Tab.SETTINGS)
                    },
                    onSelect = { destId -> switchTab(navController, destId) },
                )

                // A new release opens its dialog over whatever screen is showing.
                UpdateDialogHost(vm)

                // Home and Transactions both have an Add button, so the dialog is hosted
                // once, here, rather than in each of them.
                val showAdd by vm.manualAddVisible.collectAsState()
                val accounts by vm.accounts.collectAsState()
                val categoryUse by vm.categoryUse.collectAsState()
                if (showAdd) {
                    ManualTxnDialog(
                        title = getString(R.string.action_add_transaction),
                        atMillis = System.currentTimeMillis(),
                        defaultCurrency = vm.prefs.defaultCurrency,
                        accounts = accounts,
                        onSave = { amountMinor, currency, type, merchant, categoryId, from, to ->
                            vm.addManual(amountMinor, currency, type, merchant, categoryId, from, to)
                            vm.dismissManualAdd()
                        },
                        onDismiss = { vm.dismissManualAdd() },
                        categoryUse = categoryUse,
                    )
                }
            }
        }
    }

    /**
     * Switches to a top-level screen from somewhere other than the bar - Home's accounts
     * card, Settings' accounts row - the same move the bar makes, so the screen is never
     * stacked on top of the one it came from.
     */
    fun openTab(@IdRes destination: Int) {
        val navHost = supportFragmentManager.findFragmentById(R.id.nav_host) as NavHostFragment
        switchTab(navHost.navController, destination)
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

/** The five top-level screens, in the bar's order. */
private enum class Tab(
    @param:IdRes val destination: Int,
    @param:DrawableRes val icon: Int,
    @param:DrawableRes val selectedIcon: Int,
    @param:StringRes val label: Int,
) {
    HOME(R.id.homeFragment, R.drawable.ic_nav_home, R.drawable.ic_nav_home_fill, R.string.tab_home_a11y),
    TRANSACTIONS(R.id.transactionsFragment, R.drawable.ic_nav_records, R.drawable.ic_nav_records_fill, R.string.tab_transactions),
    ANALYSIS(R.id.analysisFragment, R.drawable.ic_nav_analysis, R.drawable.ic_nav_analysis_fill, R.string.tab_analysis),
    ACCOUNTS(R.id.accountsFragment, R.drawable.ic_nav_accounts, R.drawable.ic_nav_accounts_fill, R.string.acct_title),
    SETTINGS(R.id.settingsFragment, R.drawable.ic_nav_settings, R.drawable.ic_nav_settings_fill, R.string.tab_settings),
}

/**
 * Material 3's own short navigation bar: icon over label, the filled icon on the screen
 * you are on, and a dot where something is waiting - a review on Home, an update in
 * Settings.
 */
@Composable
private fun RiyalNavBar(
    selected: Int,
    badges: Set<Tab>,
    onSelect: (Int) -> Unit,
) {
    ShortNavigationBar {
        Tab.entries.forEach { tab ->
            val isSelected = selected == tab.destination
            ShortNavigationBarItem(
                selected = isSelected,
                onClick = { onSelect(tab.destination) },
                icon = {
                    BadgedBox(badge = { if (tab in badges) Badge() }) {
                        Icon(
                            painterResource(if (isSelected) tab.selectedIcon else tab.icon),
                            contentDescription = null,
                        )
                    }
                },
                label = { Text(stringResource(tab.label)) },
            )
        }
    }
}
