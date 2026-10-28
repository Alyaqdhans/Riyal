package com.alyaqdhan.riyal.ui.nav

import android.Manifest
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.LayoutRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import com.alyaqdhan.riyal.MainActivity
import com.alyaqdhan.riyal.R
import com.alyaqdhan.riyal.core.Verbose
import com.alyaqdhan.riyal.riyalViewModel
import com.alyaqdhan.riyal.ui.MainViewModel
import com.alyaqdhan.riyal.ui.screens.AccountsScreen
import com.alyaqdhan.riyal.ui.screens.AnalysisScreen
import com.alyaqdhan.riyal.ui.screens.HomeScreen
import com.alyaqdhan.riyal.ui.screens.SettingsScreen
import com.alyaqdhan.riyal.ui.screens.TransactionsScreen
import com.alyaqdhan.riyal.ui.theme.RiyalTheme
import java.time.LocalDate

/**
 * The five tabs of res/navigation/nav_graph.xml, each inflating its own layout from
 * res/layout and rendering its screen with Compose Material 3 inside it. They switch in
 * place under the floating toolbar; anything opened from one of them is a page of its
 * own ([PageActivity]), which the phone opens with its own transition.
 */
abstract class ScreenFragment(@LayoutRes layout: Int) : Fragment(layout) {

    protected val vm: MainViewModel get() = requireContext().riyalViewModel

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.findViewById<ComposeView>(R.id.compose_view).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            // Named Screen(), not Content(): inside apply {} the ComposeView is the innermost
            // receiver, so an unqualified Content() would resolve to ComposeView.Content() and
            // recurse into the composition forever (StackOverflowError on launch).
            setContent { RiyalTheme { Screen() } }
        }
    }

    override fun onResume() {
        super.onResume()
        // Permission can change behind our back in system settings; re-check on every return.
        vm.refreshPermission()
    }

    @Composable
    protected abstract fun Screen()

    /** Switches to a top-level screen the way the toolbar does. */
    protected fun openTab(destination: Int) {
        (activity as? MainActivity)?.openTab(destination)
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
            onOpenAccounts = { openTab(R.id.accountsFragment) },
            onOpenTransactions = { openTab(R.id.transactionsFragment) },
            onOpenAnalysis = { openTab(R.id.analysisFragment) },
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

    private val exportCsv = registerForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) {
            vm.exportCsv(uri)
        } else {
            Verbose.info(R.string.log_export_cancelled)
            Verbose.flush()
        }
    }

    @Composable
    override fun Screen() {
        TransactionsScreen(vm, onExport = { exportCsv.launch("riyal-transactions.csv") })
    }
}

class AnalysisFragment : ScreenFragment(R.layout.fragment_analysis) {
    @Composable
    override fun Screen() {
        AnalysisScreen(
            vm,
            onOpenCategory = { id, slice -> openCategory(requireContext(), id, slice) },
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
            vm.restore(uri)
        } else {
            Verbose.info(R.string.log_restore_cancelled)
            Verbose.flush()
        }
    }

    @Composable
    override fun Screen() {
        SettingsScreen(
            vm,
            onOpenAccounts = { openTab(R.id.accountsFragment) },
            onOpenCategories = { open(CategoriesActivity::class.java) },
            onOpenUpdates = { open(UpdatesActivity::class.java) },
            onOpenSetup = { open(SetupActivity::class.java) },
            onExport = { exportCsv.launch("riyal-transactions.csv") },
            onBackup = { saveBackup.launch("riyal-backup-" + LocalDate.now() + ".json") },
            onRestore = { openBackup.launch(BACKUP_TYPES) },
        )
    }
}
