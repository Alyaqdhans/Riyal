@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.alyaqdhan.riyal

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.annotation.DrawableRes
import androidx.annotation.IdRes
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
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
import com.alyaqdhan.riyal.ui.nav.OnboardingActivity
import com.alyaqdhan.riyal.ui.nav.TabMotion
import com.alyaqdhan.riyal.ui.theme.RiyalTheme
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * The five tabs and the floating toolbar that switches between them. The tabs are
 * fragments of res/navigation/nav_graph.xml, swapped in place under the toolbar; every
 * page opened from one of them is an activity of its own (ui/nav/Pages.kt), so the
 * phone opens and closes it with its own transition.
 */
class MainActivity : AppCompatActivity() {

    private val vm: MainViewModel get() = riyalViewModel
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

        // A first run starts on the welcome, a page of its own; the tabs open once it,
        // and the setup after it, are done.
        if (!vm.prefs.onboardingDone) {
            startActivity(Intent(this, OnboardingActivity::class.java))
            finish()
            return
        }
        setContentView(R.layout.activity_main)

        val navHost = supportFragmentManager.findFragmentById(R.id.nav_host) as NavHostFragment
        val navController = navHost.navController
        // Called as a switch begins and before its fragments move, so this is where the
        // slide learns which way to go. The first call is the tab the screen opens on, which
        // does not slide in from anywhere.
        var lastTab: Int? = null
        navController.addOnDestinationChangedListener { _, destination, _ ->
            val from = Tab.entries.indexOfFirst { it.destination == lastTab }
            val to = Tab.entries.indexOfFirst { it.destination == destination.id }
            TabMotion.armed = from >= 0 && to >= 0 && from != to
            TabMotion.forward = to > from
            lastTab = destination.id
            currentDestination.value = destination.id
        }
        vm.autoScanOnLaunch(fresh = savedInstanceState == null)
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

        findViewById<ComposeView>(R.id.bottom_bar).setContent {
            RiyalTheme {
                val selected by currentDestination.collectAsState()
                val update by vm.updateState.collectAsState()
                RiyalToolbar(
                    selected = selected,
                    // Only an update earns a dot. Home is where anything needing an answer
                    // is listed anyway, and it is the tab the app opens on.
                    badges = buildSet {
                        if (update is MainViewModel.UpdateState.Available) add(Tab.SETTINGS)
                    },
                    onSelect = { destId -> switchTab(navController, destId) },
                    onAdd = { vm.requestManualAdd() },
                )

                // A new release opens its dialog over whichever tab is showing.
                UpdateDialogHost(vm)

                // The toolbar's Add button opens this from any tab, so the dialog is
                // hosted once, here, beside it.
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
     * Switches to a top-level screen from somewhere other than the toolbar - Home's
     * accounts card, Settings' accounts row - the same move the toolbar makes, so the
     * screen is never stacked on top of the one it came from.
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

/** The five top-level screens, in the toolbar's order. */
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
 * Material 3 Expressive's floating toolbar with its paired button, as the component's
 * own guidance places it: a vibrant pill centred over the content, the Add button
 * attached at its end. Each tab is a toggle - filled where you are - with its name on a
 * long press, and a dot on Settings when an update is waiting.
 */
@Composable
private fun RiyalToolbar(
    selected: Int,
    badges: Set<Tab>,
    onSelect: (Int) -> Unit,
    onAdd: () -> Unit,
) {
    HorizontalFloatingToolbar(
        expanded = true,
        floatingActionButton = {
            FloatingToolbarDefaults.VibrantFloatingActionButton(onClick = onAdd) {
                Icon(
                    painterResource(R.drawable.ic_sym_add),
                    contentDescription = stringResource(R.string.action_add_transaction),
                )
            }
        },
        modifier = Modifier
            .navigationBarsPadding()
            .padding(bottom = FloatingToolbarDefaults.ScreenOffset),
        colors = FloatingToolbarDefaults.vibrantFloatingToolbarColors(),
    ) {
        Tab.entries.forEach { tab ->
            val isSelected = selected == tab.destination
            TooltipBox(
                positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
                tooltip = { PlainTooltip { Text(stringResource(tab.label)) } },
                state = rememberTooltipState(),
            ) {
                FilledIconToggleButton(
                    checked = isSelected,
                    onCheckedChange = { onSelect(tab.destination) },
                    colors = IconButtonDefaults.filledIconToggleButtonColors(
                        containerColor = Color.Transparent,
                        contentColor = LocalContentColor.current,
                        checkedContainerColor = MaterialTheme.colorScheme.primary,
                        checkedContentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                ) {
                    BadgedBox(badge = { if (tab in badges) Badge() }) {
                        Icon(
                            painterResource(if (isSelected) tab.selectedIcon else tab.icon),
                            contentDescription = stringResource(tab.label),
                        )
                    }
                }
            }
        }
    }
}
