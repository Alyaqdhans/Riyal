package com.alyaqdhan.riyal.ui.nav

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.LayoutRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import com.alyaqdhan.riyal.MainActivity
import com.alyaqdhan.riyal.R
import com.alyaqdhan.riyal.Tab
import com.alyaqdhan.riyal.core.Verbose
import com.alyaqdhan.riyal.riyalViewModel
import com.alyaqdhan.riyal.ui.MainViewModel
import com.alyaqdhan.riyal.ui.compose.LocalTabInView
import com.alyaqdhan.riyal.ui.compose.LocalTabShows
import com.alyaqdhan.riyal.ui.screens.AccountsScreen
import com.alyaqdhan.riyal.ui.screens.AnalysisScreen
import com.alyaqdhan.riyal.ui.screens.HomeScreen
import com.alyaqdhan.riyal.ui.screens.SettingsScreen
import com.alyaqdhan.riyal.ui.screens.TransactionsScreen
import com.alyaqdhan.riyal.ui.theme.RiyalTheme
import java.time.LocalDate

/**
 * The five tabs ([Tab]), each inflating its own layout from res/layout and rendering its
 * screen with Compose Material 3 inside it. They slide in place under the floating
 * toolbar; anything opened from one of them is a page of its own ([PageActivity]),
 * which the phone opens with its own transition.
 */
abstract class ScreenFragment(@LayoutRes layout: Int) : Fragment(layout) {

    protected val vm: MainViewModel get() = requireContext().riyalViewModel

    private val shows = mutableIntStateOf(0)
    private val inView = mutableStateOf(false)

    /** Called each time a switch brings this tab into view ([LocalTabShows], [LocalTabInView]). */
    internal fun onShown() {
        shows.intValue++
        inView.value = true
    }

    /** Called when a switch takes this tab out of view. */
    internal fun onHidden() {
        inView.value = false
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // Every tab stays built once made; only the one showing is visible, and the rest
        // stay laid out, unseen, so switching to them is instant (MainActivity.openTab).
        val showing = (activity as? MainActivity)?.isShowing(this) != false
        if (!showing) view.visibility = View.INVISIBLE
        inView.value = showing
        view.findViewById<ComposeView>(R.id.compose_view).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            // Named Screen(), not Content(): inside apply {} the ComposeView is the innermost
            // receiver, so an unqualified Content() would resolve to ComposeView.Content() and
            // recurse into the composition forever (StackOverflowError on launch).
            setContent {
                RiyalTheme {
                    CompositionLocalProvider(
                        LocalTabShows provides shows.intValue,
                        LocalTabInView provides inView.value,
                    ) { Screen() }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Permission can change behind our back in system settings; re-check on every return.
        vm.refreshPermission()
        // Back from the "install unknown apps" switch with it on: the update installs.
        vm.resumeInstall()
    }

    @Composable
    protected abstract fun Screen()

    /** Switches to a top-level screen the way the toolbar does. */
    internal fun openTab(tab: Tab) {
        (activity as? MainActivity)?.openTab(tab)
    }

    protected fun open(page: Class<out PageActivity>) {
        requireContext().openPage(page)
    }
}

class HomeFragment : ScreenFragment(R.layout.fragment_home) {

    private val requestSms = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        Verbose.info(
            if (granted) R.string.log_perm_granted_home else R.string.log_perm_denied_home
        )
        Verbose.flush()
        vm.refreshPermission()
        // Quiet: this lands the user on Home, which shows the result itself. Asking for
        // the sheet here left it queued for whenever Settings was next opened.
        if (granted) vm.startScan(showSheet = false)
    }

    @Composable
    override fun Screen() {
        HomeScreen(
            vm,
            onRequestPermission = { requestSms.launch(Manifest.permission.READ_SMS) },
            onOpenReview = { open(ReviewActivity::class.java) },
            onOpenNeedsCategory = { open(NeedsCategoryActivity::class.java) },
            onOpenSetup = { open(SetupActivity::class.java) },
            // Accounts, Transactions and Analysis are tabs rather than pages, so these go
            // through the same switch the toolbar uses.
            onOpenAccounts = { openTab(Tab.ACCOUNTS) },
            onOpenTransactions = { openTab(Tab.TRANSACTIONS) },
            onOpenAnalysis = { openTab(Tab.ANALYSIS) },
        )
    }
}

/** The bank accounts: one of the five tabs. */
class AccountsFragment : ScreenFragment(R.layout.fragment_accounts) {

    @Composable
    override fun Screen() {
        AccountsScreen(vm, onBack = null)
    }
}

private val BACKUP_TYPES = arrayOf("application/json", "text/plain", "application/octet-stream")

class TransactionsFragment : ScreenFragment(R.layout.fragment_transactions) {
    @Composable
    override fun Screen() {
        TransactionsScreen(vm)
    }
}

class AnalysisFragment : ScreenFragment(R.layout.fragment_analysis) {
    @Composable
    override fun Screen() {
        AnalysisScreen(
            vm,
            onOpenCategory = { id, slice, account -> openCategory(requireContext(), id, slice, account) },
        )
    }
}

class SettingsFragment : ScreenFragment(R.layout.fragment_settings) {

    // The same document picker Activity uses: exporting is one action with two doors,
    // not two features.
    private val exportCsv = registerForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) {
            vm.exportCsv(uri)
        } else {
            Verbose.info(R.string.log_export_cancelled)
            Verbose.flush()
        }
    }

    private val saveBackup = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            vm.backup(uri)
        } else {
            Verbose.info(R.string.log_backup_cancelled)
            Verbose.flush()
        }
    }

    private val openBackup = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            vm.openImport(uri)
        } else {
            Verbose.info(R.string.log_restore_cancelled)
            Verbose.flush()
        }
    }

    @Composable
    override fun Screen() {
        SettingsScreen(
            vm,
            onOpenAccounts = { openTab(Tab.ACCOUNTS) },
            onOpenCategories = { open(CategoriesActivity::class.java) },
            onOpenLists = { open(ListsActivity::class.java) },
            onOpenUpdates = { open(UpdatesActivity::class.java) },
            onOpenSetup = { open(SetupActivity::class.java) },
            onWipe = {
                // Nothing is left to go back to: the app starts over from the welcome, in a
                // task of its own, as on the day it was installed.
                val app = requireContext().applicationContext
                vm.wipeAll {
                    app.startActivity(
                        Intent(app, OnboardingActivity::class.java)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    )
                }
            },
            onExport = { exportCsv.launch("riyal-transactions.csv") },
            onBackup = { saveBackup.launch("riyal-backup-" + LocalDate.now() + ".json") },
            onRestore = { openBackup.launch(BACKUP_TYPES) },
        )
    }
}
