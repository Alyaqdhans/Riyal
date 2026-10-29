@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.alyaqdhan.riyal

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Looper
import android.view.View
import android.view.animation.PathInterpolator
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.fragment.app.Fragment
import com.alyaqdhan.riyal.core.Dates
import com.alyaqdhan.riyal.core.Language
import com.alyaqdhan.riyal.core.Verbose
import com.alyaqdhan.riyal.ui.MainViewModel
import com.alyaqdhan.riyal.ui.Words
import com.alyaqdhan.riyal.ui.compose.ManualTxnDialog
import com.alyaqdhan.riyal.ui.compose.UpdateDialogHost
import com.alyaqdhan.riyal.ui.nav.AccountsFragment
import com.alyaqdhan.riyal.ui.nav.AnalysisFragment
import com.alyaqdhan.riyal.ui.nav.HomeFragment
import com.alyaqdhan.riyal.ui.nav.OnboardingActivity
import com.alyaqdhan.riyal.ui.nav.ScreenFragment
import com.alyaqdhan.riyal.ui.nav.SettingsFragment
import com.alyaqdhan.riyal.ui.nav.TransactionsFragment
import com.alyaqdhan.riyal.ui.theme.RiyalTheme
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * The five tabs and the floating toolbar that switches between them. The tabs are
 * fragments ([Tab]) that slide in place under the toolbar; every page opened from one
 * of them is an activity of its own (ui/nav/Pages.kt), so the phone opens and closes it
 * with its own transition.
 */
class MainActivity : AppCompatActivity() {

    private val vm: MainViewModel get() = riyalViewModel
    private val currentTab = MutableStateFlow(Tab.HOME)
    private val backToHome = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() = openTab(Tab.HOME)
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(Language.latinDigits(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Before the fragments come back, so each knows whether it is the one showing.
        savedInstanceState?.getString(KEY_TAB)?.let { name ->
            Tab.entries.firstOrNull { it.name == name }?.let { currentTab.value = it }
        }
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

        // A screen rebuilt in place gets its tabs back from the fragment manager.
        if (supportFragmentManager.findFragmentByTag(currentTab.value.name) == null) addTab(currentTab.value)
        // Back from another tab goes Home, and back from Home leaves.
        onBackPressedDispatcher.addCallback(this, backToHome)
        backToHome.isEnabled = currentTab.value != Tab.HOME
        // The other tabs are built soon after the app opens, one whenever the main thread
        // has nothing else to do, so even the first switch to each is instant. Not at once:
        // Home's figures are rolling in for the first moment.
        window.decorView.postDelayed({
            Looper.myQueue().addIdleHandler {
                if (isDestroyed) return@addIdleHandler false
                if (supportFragmentManager.isStateSaved) return@addIdleHandler true
                val next = Tab.entries.firstOrNull { supportFragmentManager.findFragmentByTag(it.name) == null }
                    ?: return@addIdleHandler false
                addTab(next)
                true
            }
        }, PREWARM_DELAY_MS)
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
                val selected by currentTab.collectAsState()
                val update by vm.updateState.collectAsState()
                RiyalToolbar(
                    selected = selected,
                    // Only an update earns a dot. Home is where anything needing an answer
                    // is listed anyway, and it is the tab the app opens on.
                    badges = buildSet {
                        if (update is MainViewModel.UpdateState.Available) add(Tab.SETTINGS)
                    },
                    onSelect = ::openTab,
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

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(KEY_TAB, currentTab.value.name)
    }

    /** Whether [fragment] is the tab on screen; the others stay laid out but unseen. */
    internal fun isShowing(fragment: Fragment): Boolean = fragment.tag == currentTab.value.name

    /**
     * Switches tabs, from the toolbar or from inside a screen (Home's accounts card,
     * Settings' accounts row). Every tab stays built once made, so a switch only slides
     * two views that are already drawn: the toolbar's highlight and the slide start on
     * the same frame, and each tab keeps its scroll.
     */
    internal fun openTab(tab: Tab) {
        val from = currentTab.value
        if (tab == from || supportFragmentManager.isStateSaved) return
        val incoming = supportFragmentManager.findFragmentByTag(tab.name) ?: addTab(tab)
        val outgoing = supportFragmentManager.findFragmentByTag(from.name)
        currentTab.value = tab
        backToHome.isEnabled = tab != Tab.HOME
        slide(outgoing?.view, incoming.view ?: return, forward = tab.ordinal > from.ordinal)
        (incoming as? ScreenFragment)?.onShown()
    }

    private fun addTab(tab: Tab): Fragment {
        val fragment = tab.make()
        supportFragmentManager.beginTransaction()
            .setReorderingAllowed(true)
            .add(R.id.tabs, fragment, tab.name)
            .commitNow()
        return fragment
    }

    /**
     * The whole width of the screen, the way pages of one thing move: a tab further
     * along the toolbar comes in from its end and the one it replaces leaves by the
     * start, and the other way going back. The end is the left in Arabic, as the
     * toolbar's order is.
     */
    private fun slide(outgoing: View?, incoming: View, forward: Boolean) {
        val width = findViewById<View>(R.id.tabs).width.toFloat()
        val towardsEnd = if (forward != (window.decorView.layoutDirection == View.LAYOUT_DIRECTION_RTL)) 1f else -1f
        // A tab passed over in a quick run of switches is put away where it stands.
        supportFragmentManager.fragments.mapNotNull { it.view }
            .filter { it !== outgoing && it !== incoming }
            .forEach {
                it.animate().cancel()
                it.translationX = 0f
                it.visibility = View.INVISIBLE
            }
        incoming.animate().cancel()
        // One still on its way out comes back from where it is.
        if (incoming.visibility != View.VISIBLE) incoming.translationX = towardsEnd * width
        incoming.visibility = View.VISIBLE
        incoming.animate().translationX(0f).setDuration(SLIDE_MS).setInterpolator(EASING).withLayer().start()
        outgoing?.let { view ->
            view.animate().cancel()
            view.animate().translationX(-towardsEnd * width).setDuration(SLIDE_MS).setInterpolator(EASING).withLayer()
                .withEndAction {
                    view.visibility = View.INVISIBLE
                    view.translationX = 0f
                }
                .start()
        }
    }

    private companion object {
        const val KEY_TAB = "tab"
        const val SLIDE_MS = 250L
        const val PREWARM_DELAY_MS = 2_000L

        /** Material's standard easing: quick to start, settling gently, no overshoot. */
        val EASING = PathInterpolator(0.2f, 0f, 0f, 1f)
    }
}

/** The five top-level screens, in the toolbar's order. */
internal enum class Tab(
    val make: () -> ScreenFragment,
    @param:DrawableRes val icon: Int,
    @param:DrawableRes val selectedIcon: Int,
    @param:StringRes val label: Int,
) {
    HOME(::HomeFragment, R.drawable.ic_nav_home, R.drawable.ic_nav_home_fill, R.string.tab_home_a11y),
    TRANSACTIONS(::TransactionsFragment, R.drawable.ic_nav_records, R.drawable.ic_nav_records_fill, R.string.tab_transactions),
    ANALYSIS(::AnalysisFragment, R.drawable.ic_nav_analysis, R.drawable.ic_nav_analysis_fill, R.string.tab_analysis),
    ACCOUNTS(::AccountsFragment, R.drawable.ic_nav_accounts, R.drawable.ic_nav_accounts_fill, R.string.acct_title),
    SETTINGS(::SettingsFragment, R.drawable.ic_nav_settings, R.drawable.ic_nav_settings_fill, R.string.tab_settings),
}

/**
 * Material 3 Expressive's floating toolbar with its paired button, as the component's
 * own guidance places it: a vibrant pill centred over the content, the Add button
 * attached at its end. Each tab is a toggle - filled where you are - with its name on a
 * long press, and a dot on Settings when an update is waiting.
 */
@Composable
private fun RiyalToolbar(
    selected: Tab,
    badges: Set<Tab>,
    onSelect: (Tab) -> Unit,
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
            val isSelected = selected == tab
            TooltipBox(
                positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
                tooltip = { PlainTooltip { Text(stringResource(tab.label)) } },
                state = rememberTooltipState(),
            ) {
                // The tab you are on widens into a pill on a spring as its fill fades
                // in, while the one you left narrows back: the indicator moves rather
                // than blinking from one tab to the next.
                val motion = MaterialTheme.motionScheme
                val width by animateDpAsState(
                    IconButtonDefaults.smallContainerSize(
                        if (isSelected) IconButtonDefaults.IconButtonWidthOption.Wide
                        else IconButtonDefaults.IconButtonWidthOption.Uniform
                    ).width,
                    motion.fastSpatialSpec(),
                )
                val fill by animateColorAsState(
                    if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                    motion.fastEffectsSpec(),
                )
                val ink by animateColorAsState(
                    if (isSelected) MaterialTheme.colorScheme.onPrimary else LocalContentColor.current,
                    motion.fastEffectsSpec(),
                )
                FilledIconToggleButton(
                    checked = isSelected,
                    onCheckedChange = { onSelect(tab) },
                    // Squares off under the finger and springs back round, as the
                    // app's buttons do; the tab you are on stays fully rounded.
                    shapes = IconButtonDefaults.toggleableShapes(checkedShape = CircleShape),
                    modifier = Modifier.width(width),
                    colors = IconButtonDefaults.filledIconToggleButtonColors(
                        containerColor = fill,
                        contentColor = ink,
                        checkedContainerColor = fill,
                        checkedContentColor = ink,
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
