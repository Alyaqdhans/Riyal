package com.alyaqdhan.riyal.ui.nav

import android.Manifest
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
import com.alyaqdhan.riyal.R
import com.alyaqdhan.riyal.core.ScanHistory
import com.alyaqdhan.riyal.core.Verbose
import com.alyaqdhan.riyal.ui.MainViewModel
import androidx.core.os.bundleOf
import com.alyaqdhan.riyal.ui.screens.AccountsScreen
import com.alyaqdhan.riyal.ui.screens.AnalysisScreen
import com.alyaqdhan.riyal.ui.screens.CategoriesScreen
import com.alyaqdhan.riyal.ui.screens.CategoryDetailScreen
import com.alyaqdhan.riyal.ui.screens.HomeScreen
import com.alyaqdhan.riyal.ui.screens.OnboardingScreen
import com.alyaqdhan.riyal.ui.screens.ReviewScreen
import com.alyaqdhan.riyal.ui.screens.SettingsScreen
import com.alyaqdhan.riyal.ui.screens.TransactionsScreen
import com.alyaqdhan.riyal.ui.theme.RiyalTheme
import com.google.android.material.transition.MaterialFadeThrough
import com.google.android.material.transition.MaterialSharedAxis

/**
 * Destinations of res/navigation/nav_graph.xml, the XML routing layer. Each fragment
 * enters/exits with native Material motion transitions and renders its screen with
 * Compose Material 3 Expressive (material3 1.5.0-alpha23).
 */
abstract class ScreenFragment : Fragment() {

    protected val vm: MainViewModel by activityViewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enterTransition = MaterialFadeThrough()
        exitTransition = MaterialFadeThrough()
        reenterTransition = MaterialFadeThrough()
        returnTransition = MaterialFadeThrough()
    }

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
}

class HomeFragment : ScreenFragment() {

    private val requestSms = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        Verbose.info(
            if (granted) "you granted READ_SMS from Home"
            else "you declined READ_SMS, nothing will be read",
        )
        Verbose.flush()
        vm.refreshPermission()
        if (granted) vm.startScan()
    }

    @Composable
    override fun Screen() {
        HomeScreen(
            vm,
            onRequestPermission = { requestSms.launch(Manifest.permission.READ_SMS) },
            onOpenReview = { findNavController().navigate(R.id.reviewFragment) },
            onOpenAccounts = { findNavController().navigate(R.id.accountsFragment) },
        )
    }
}

/**
 * Pages pushed on top of a tab rather than being one: they slide in along the X axis
 * and hide the bottom toolbar, which is what makes them feel like going *into*
 * something instead of switching between peers.
 */
abstract class InnerFragment : ScreenFragment() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enterTransition = MaterialSharedAxis(MaterialSharedAxis.X, true)
        returnTransition = MaterialSharedAxis(MaterialSharedAxis.X, false)
    }

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

/** The bank account manager, reached from Settings and from Home's setup prompt. */
class AccountsFragment : InnerFragment() {

    @Composable
    override fun Screen() {
        AccountsScreen(vm, onBack = { back() })
    }
}

class CategoriesFragment : InnerFragment() {

    @Composable
    override fun Screen() {
        CategoriesScreen(
            vm,
            onBack = { back() },
            onOpenCategory = { openCategory(findNavController(), it) },
        )
    }
}

/** One category's records. The id travels in the arguments bundle. */
class CategoryDetailFragment : InnerFragment() {

    @Composable
    override fun Screen() {
        CategoryDetailScreen(
            vm,
            categoryId = arguments?.getString(ARG_CATEGORY_ID).orEmpty(),
            onBack = { back() },
        )
    }
}

const val ARG_CATEGORY_ID = "categoryId"

fun openCategory(navController: androidx.navigation.NavController, categoryId: String) {
    navController.navigate(R.id.categoryDetailFragment, bundleOf(ARG_CATEGORY_ID to categoryId))
}

class TransactionsFragment : ScreenFragment() {

    private val exportCsv = registerForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) {
            vm.exportCsv(uri)
        } else {
            Verbose.info("CSV export cancelled by you, nothing was written")
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
        AnalysisScreen(vm, onOpenCategory = { openCategory(findNavController(), it) })
    }
}

class SettingsFragment : ScreenFragment() {
    @Composable
    override fun Screen() {
        SettingsScreen(
            vm,
            onOpenAccounts = { findNavController().navigate(R.id.accountsFragment) },
            onOpenCategories = { findNavController().navigate(R.id.categoriesFragment) },
        )
    }
}

class OnboardingFragment : ScreenFragment() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Slide the onboarding away with a shared-axis push into the app.
        exitTransition = MaterialSharedAxis(MaterialSharedAxis.X, true)
    }

    private val requestSms = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        Verbose.info(
            if (granted) "you granted READ_SMS during onboarding"
            else "you declined READ_SMS, Riyal stays fully inert until you allow it",
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
        Verbose.info(
            "you chose to read " + when (choice) {
                ScanHistory.ALL -> "the whole inbox"
                ScanHistory.FROM_NOW -> "nothing older than today; history starts here"
                else -> "the last ${choice.months} month(s)"
            }
        )
        Verbose.flush()
    }

    private fun finishOnboarding(startScan: Boolean) {
        vm.prefs.onboardingDone = true
        findNavController().navigate(
            R.id.homeFragment,
            null,
            navOptions { popUpTo(R.id.onboardingFragment) { inclusive = true } },
        )
        if (startScan) vm.startScan()
    }
}
