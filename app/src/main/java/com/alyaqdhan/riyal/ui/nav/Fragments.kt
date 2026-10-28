package com.alyaqdhan.riyal.ui.nav

import android.Manifest
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.navOptions
import com.alyaqdhan.riyal.MainActivity
import com.alyaqdhan.riyal.R
import com.alyaqdhan.riyal.core.ScanHistory
import com.alyaqdhan.riyal.core.Verbose
import com.alyaqdhan.riyal.ui.MainViewModel
import androidx.core.os.bundleOf
import com.alyaqdhan.riyal.ui.screens.AccountsScreen
import com.alyaqdhan.riyal.ui.screens.AnalysisScreen
import com.alyaqdhan.riyal.ui.screens.CategoriesScreen
import com.alyaqdhan.riyal.ui.screens.ChangelogScreen
import com.alyaqdhan.riyal.ui.screens.CategoryDetailScreen
import com.alyaqdhan.riyal.ui.screens.HomeScreen
import com.alyaqdhan.riyal.ui.screens.NeedsCategoryScreen
import com.alyaqdhan.riyal.ui.screens.OnboardingScreen
import com.alyaqdhan.riyal.ui.screens.ReviewScreen
import com.alyaqdhan.riyal.ui.screens.SettingsScreen
import com.alyaqdhan.riyal.ui.screens.SetupScreen
import com.alyaqdhan.riyal.ui.screens.TransactionsScreen
import com.alyaqdhan.riyal.ui.screens.UpdatesScreen
import com.alyaqdhan.riyal.ui.compose.TimeSlice
import com.alyaqdhan.riyal.ui.theme.RiyalTheme
import java.time.LocalDate
import java.time.YearMonth

/**
 * Destinations of res/navigation/nav_graph.xml, the XML routing layer. Each fragment
 * renders its screen with Compose Material 3 (material3 1.5.0-alpha23).
 *
 * No enter or exit transitions, on any destination. A page that fades or slides in
 * makes every tap wait for the motion to finish before the content can be read, and a
 * finance app is opened to read a number - so a destination simply appears.
 */
abstract class ScreenFragment : Fragment() {

    protected val vm: MainViewModel by activityViewModels()

    final override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = ComposeView(requireContext()).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        // Named Screen(), not Content(): inside apply {} the ComposeView is the innermost
        // receiver, so an unqualified Content() would resolve to ComposeView.Content() and
        // recurse into the composition forever (StackOverflowError on launch).
        setContent { RiyalTheme { Screen() } }
    }

    override fun onResume() {
        super.onResume()
        // Permission can change behind our back in system settings; re-check on every return.
        vm.refreshPermission()
    }

    @Composable
    protected abstract fun Screen()

    /** Switches to a top-level screen the way the navigation bar does. */
    protected fun openTab(destination: Int) {
        (activity as? MainActivity)?.openTab(destination)
    }
}

class HomeFragment : ScreenFragment() {

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
            onOpenReview = { findNavController().navigate(R.id.reviewFragment) },
            onOpenNeedsCategory = { findNavController().navigate(R.id.needsCategoryFragment) },
            onOpenSetup = { findNavController().navigate(R.id.setupFragment) },
            // Accounts, Transactions and Analysis are tabs rather than pushed pages, so
            // these go through the same switch the bar uses: navigating to one directly
            // would stack a second copy on top of Home and leave Back going nowhere.
            onOpenAccounts = { openTab(R.id.accountsFragment) },
            onOpenTransactions = { openTab(R.id.transactionsFragment) },
            onOpenAnalysis = { openTab(R.id.analysisFragment) },
        )
    }
}

/**
 * Pages pushed on top of a tab rather than being one. They hide the bottom bar and
 * carry a back arrow, which is what makes them read as going *into* something instead
 * of switching between peers.
 */
abstract class InnerFragment : ScreenFragment() {

    protected fun back() {
        findNavController().navigateUp()
    }
}

/** Inner page pushed from Home's "Needs review" section, not a bottom tab. */
class ReviewFragment : InnerFragment() {

    @Composable
    override fun Screen() {
        ReviewScreen(vm, onBack = { back() })
    }
}

/** The bank accounts: one of the five tabs. */
class AccountsFragment : ScreenFragment() {

    @Composable
    override fun Screen() {
        AccountsScreen(vm, onBack = null)
    }
}

/** The grouped backlog, pushed from Home's "needs a category" card. */
class NeedsCategoryFragment : InnerFragment() {

    @Composable
    override fun Screen() {
        NeedsCategoryScreen(vm, onBack = { back() })
    }
}

/**
 * Teaching Riyal the user's banks. Reached straight after onboarding, from Home until it
 * has been done once, and from Settings whenever the user wants to go through it again.
 */
class SetupFragment : InnerFragment() {

    @Composable
    override fun Screen() {
        SetupScreen(vm, onFinished = {
            val nav = findNavController()
            if (nav.previousBackStackEntry != null) {
                nav.popBackStack()
            } else {
                // Arrived from onboarding, which is gone from the stack: Home becomes the root.
                nav.navigate(R.id.homeFragment, null, navOptions { popUpTo(R.id.setupFragment) { inclusive = true } })
            }
        })
    }
}

/** Settings › Updates: the status card, the channel and the changelog's way in. */
class UpdatesFragment : InnerFragment() {

    @Composable
    override fun Screen() {
        UpdatesScreen(
            vm,
            onBack = { back() },
            onOpenChangelog = { findNavController().navigate(R.id.changelogFragment) },
        )
    }
}

class ChangelogFragment : InnerFragment() {

    @Composable
    override fun Screen() {
        ChangelogScreen(vm, onBack = { back() })
    }
}

class CategoriesFragment : InnerFragment() {

    @Composable
    override fun Screen() {
        CategoriesScreen(
            vm,
            onBack = { back() },
            onOpenCategory = { id, slice -> openCategory(findNavController(), requireContext(), id, slice) },
        )
    }
}

/** One category's records. The id and the period travel in the arguments bundle. */
class CategoryDetailFragment : InnerFragment() {

    @Composable
    override fun Screen() {
        CategoryDetailScreen(
            vm,
            categoryId = arguments?.getString(ARG_CATEGORY_ID).orEmpty(),
            initialSlice = arguments.sliceArg(),
            onBack = { back() },
        )
    }
}

const val ARG_CATEGORY_ID = "categoryId"
const val ARG_SLICE_START = "sliceStart"
const val ARG_SLICE_END = "sliceEnd"
const val ARG_SLICE_LABEL = "sliceLabel"
const val ARG_SLICE_MONTH = "sliceMonth"

private val BACKUP_TYPES = arrayOf("application/json", "text/plain", "application/octet-stream")

/**
 * Opens a category with the period the caller was looking at. Sending the id alone
 * dropped it: tapping August's biggest category answered for the current month, and
 * the reader had to walk back to the month they had already chosen.
 */
fun openCategory(
    navController: androidx.navigation.NavController,
    context: Context,
    categoryId: String,
    slice: TimeSlice,
) {
    navController.navigate(
        R.id.categoryDetailFragment,
        bundleOf(
            ARG_CATEGORY_ID to categoryId,
            ARG_SLICE_START to slice.start,
            ARG_SLICE_END to slice.endExclusive,
            ARG_SLICE_LABEL to (
                slice.labelRes?.let { context.getString(it, *slice.labelArgs.toTypedArray()) }
                    ?: slice.label
                ),
            // Kept so the chevrons keep stepping whole calendar months rather than
            // drifting by the month's own length in days.
            ARG_SLICE_MONTH to slice.month?.toString(),
        ),
    )
}

private fun Bundle?.sliceArg(): TimeSlice? {
    val args = this ?: return null
    val start = args.getLong(ARG_SLICE_START, 0L)
    val end = args.getLong(ARG_SLICE_END, 0L)
    if (start <= 0L || end <= start) return null
    return TimeSlice(
        start = start,
        endExclusive = end,
        label = args.getString(ARG_SLICE_LABEL).orEmpty(),
        month = args.getString(ARG_SLICE_MONTH)?.let {
            runCatching { YearMonth.parse(it) }.getOrNull()
        },
    )
}

class TransactionsFragment : ScreenFragment() {

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

class AnalysisFragment : ScreenFragment() {
    @Composable
    override fun Screen() {
        AnalysisScreen(
            vm,
            onOpenCategory = { id, slice -> openCategory(findNavController(), requireContext(), id, slice) },
        )
    }
}

class SettingsFragment : ScreenFragment() {

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
            onOpenCategories = { findNavController().navigate(R.id.categoriesFragment) },
            onOpenUpdates = { findNavController().navigate(R.id.updatesFragment) },
            onOpenSetup = { findNavController().navigate(R.id.setupFragment) },
            onExport = { exportCsv.launch("riyal-transactions.csv") },
            onBackup = { saveBackup.launch("riyal-backup-" + LocalDate.now() + ".json") },
            onRestore = { openBackup.launch(BACKUP_TYPES) },
        )
    }
}

class OnboardingFragment : ScreenFragment() {

    private val requestSms = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        Verbose.info(
            if (granted) R.string.log_perm_granted_onboarding
            else R.string.log_perm_denied_onboarding
        )
        Verbose.flush()
        vm.refreshPermission()
        finishOnboarding(startScan = granted)
    }

    @Composable
    override fun Screen() {
        OnboardingScreen(
            onGrant = {
                // Recorded before the permission dialog, because "from today" has to
                // mean the moment you chose it, not the moment you tapped Allow.
                chooseHistory(it)
                requestSms.launch(Manifest.permission.READ_SMS)
            },
            onSkip = {
                chooseHistory(it)
                finishOnboarding(startScan = false)
            },
        )
    }

    private fun chooseHistory(choice: ScanHistory) {
        vm.prefs.applyScanHistory(choice)
        when (choice) {
            ScanHistory.ALL -> Verbose.info(R.string.log_history_all)
            ScanHistory.FROM_NOW -> Verbose.info(R.string.log_history_from_now)
            else -> Verbose.info(
                R.string.log_history_months,
                Verbose.count(R.plurals.count_months, choice.months),
            )
        }
        Verbose.flush()
    }

    /**
     * With messages to read, onboarding hands over to the setup, which reads them and
     * asks about them. Without, there is nothing to set up yet, so Home it is.
     */
    private fun finishOnboarding(startScan: Boolean) {
        vm.prefs.onboardingDone = true
        findNavController().navigate(
            if (startScan) R.id.setupFragment else R.id.homeFragment,
            null,
            navOptions { popUpTo(R.id.onboardingFragment) { inclusive = true } },
        )
    }
}
