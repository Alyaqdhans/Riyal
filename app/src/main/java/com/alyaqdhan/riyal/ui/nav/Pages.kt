package com.alyaqdhan.riyal.ui.nav

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.LayoutRes
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import com.alyaqdhan.riyal.MainActivity
import com.alyaqdhan.riyal.R
import com.alyaqdhan.riyal.core.Dates
import com.alyaqdhan.riyal.core.Language
import com.alyaqdhan.riyal.core.ScanHistory
import com.alyaqdhan.riyal.core.Verbose
import com.alyaqdhan.riyal.riyalViewModel
import com.alyaqdhan.riyal.ui.MainViewModel
import com.alyaqdhan.riyal.ui.Words
import com.alyaqdhan.riyal.ui.compose.TimeSlice
import com.alyaqdhan.riyal.ui.screens.CategoriesScreen
import com.alyaqdhan.riyal.ui.screens.CategoryDetailScreen
import com.alyaqdhan.riyal.ui.screens.ListsScreen
import com.alyaqdhan.riyal.ui.screens.ChangelogScreen
import com.alyaqdhan.riyal.ui.screens.NeedsCategoryScreen
import com.alyaqdhan.riyal.ui.screens.OnboardingScreen
import com.alyaqdhan.riyal.ui.screens.ReviewScreen
import com.alyaqdhan.riyal.ui.screens.SetupScreen
import com.alyaqdhan.riyal.ui.screens.UpdatesScreen
import com.alyaqdhan.riyal.ui.theme.RiyalTheme
import java.time.YearMonth

/**
 * A page opened from one of the tabs. Each is an activity with a layout of its own in
 * res/layout, so going into one and coming back out is the phone's own transition -
 * whatever this phone does when one screen opens another - rather than one the app
 * draws. The layout holds a single ComposeView, and the page in it is Compose Material
 * 3 like the rest of the app.
 */
abstract class PageActivity : AppCompatActivity() {

    protected val vm: MainViewModel get() = riyalViewModel

    @get:LayoutRes
    protected abstract val layout: Int

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(Language.latinDigits(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // A page can be the first screen of a process the system brought back, so it
        // sets up the localised names itself instead of relying on the main screen.
        Words.apply(this)
        Dates.use(resources.configuration.locales[0])
        setContentView(layout)
        findViewById<ComposeView>(R.id.compose_view).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            // Named Page(), not Content(): inside apply {} the ComposeView is the
            // innermost receiver, and an unqualified Content() would resolve to its own.
            setContent { RiyalTheme { Page() } }
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
    protected abstract fun Page()
}

/** Opens a page on top of whatever is showing. */
fun Context.openPage(page: Class<out PageActivity>) {
    startActivity(Intent(this, page))
}

/** Inner page pushed from Home's "Needs review" section. */
class ReviewActivity : PageActivity() {
    override val layout get() = R.layout.activity_review

    @Composable
    override fun Page() {
        ReviewScreen(vm, onBack = { finish() })
    }
}

/** The grouped backlog, pushed from Home's "needs a category" card. */
class NeedsCategoryActivity : PageActivity() {
    override val layout get() = R.layout.activity_needs_category

    @Composable
    override fun Page() {
        NeedsCategoryScreen(vm, onBack = { finish() })
    }
}

/**
 * Teaching Riyal the user's banks. Reached straight after onboarding, from Home until it
 * has been done once, and from Settings whenever the user wants to go through it again.
 */
class SetupActivity : PageActivity() {
    override val layout get() = R.layout.activity_setup

    @Composable
    override fun Page() {
        SetupScreen(vm, onFinished = {
            // Straight after onboarding there is nothing underneath to go back to: the
            // app itself opens.
            if (intent.getBooleanExtra(EXTRA_FIRST_RUN, false)) {
                startActivity(Intent(this, MainActivity::class.java))
            }
            finish()
        })
    }

    companion object {
        const val EXTRA_FIRST_RUN = "firstRun"
    }
}

/** Settings › Updates: the status card, the channel and the changelog's way in. */
class UpdatesActivity : PageActivity() {
    override val layout get() = R.layout.activity_updates

    @Composable
    override fun Page() {
        UpdatesScreen(
            vm,
            onBack = { finish() },
            onOpenChangelog = { openPage(ChangelogActivity::class.java) },
        )
    }
}

class ChangelogActivity : PageActivity() {
    override val layout get() = R.layout.activity_changelog

    @Composable
    override fun Page() {
        ChangelogScreen(vm, onBack = { finish() })
    }
}

class CategoriesActivity : PageActivity() {
    override val layout get() = R.layout.activity_categories

    @Composable
    override fun Page() {
        CategoriesScreen(
            vm,
            onBack = { finish() },
            onOpenCategory = { id, slice -> openCategory(this, id, slice) },
            onOpenLists = { openPage(ListsActivity::class.java) },
        )
    }
}

/** Settings › Lists: keywords, senders, and what is ignored. */
class ListsActivity : PageActivity() {
    override val layout get() = R.layout.activity_lists

    @Composable
    override fun Page() {
        ListsScreen(vm, onBack = { finish() })
    }
}

/** One category's records. The id, the period and the account travel in the intent. */
class CategoryDetailActivity : PageActivity() {
    override val layout get() = R.layout.activity_category_detail

    @Composable
    override fun Page() {
        CategoryDetailScreen(
            vm,
            categoryId = intent.getStringExtra(ARG_CATEGORY_ID).orEmpty(),
            initialSlice = intent.extras.sliceArg(),
            initialAccountId = intent.getStringExtra(ARG_ACCOUNT_ID),
            onBack = { finish() },
        )
    }
}

/** The first screen of a first run: what Riyal reads, and how far back. */
class OnboardingActivity : PageActivity() {
    override val layout get() = R.layout.activity_onboarding

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
    override fun Page() {
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
     * asks about them. Without, there is nothing to set up yet, so the app it is.
     */
    private fun finishOnboarding(startScan: Boolean) {
        vm.prefs.onboardingDone = true
        startActivity(
            if (startScan) {
                Intent(this, SetupActivity::class.java).putExtra(SetupActivity.EXTRA_FIRST_RUN, true)
            } else {
                Intent(this, MainActivity::class.java)
            }
        )
        finish()
    }
}

const val ARG_CATEGORY_ID = "categoryId"
const val ARG_SLICE_START = "sliceStart"
const val ARG_SLICE_END = "sliceEnd"
const val ARG_SLICE_LABEL = "sliceLabel"
const val ARG_SLICE_MONTH = "sliceMonth"
const val ARG_ACCOUNT_ID = "accountId"

/**
 * Opens a category with the period the caller was looking at, and the account when it
 * was narrowed to one. Sending the id alone dropped them: tapping August's biggest
 * category answered for the current month, and a category opened from one bank's
 * Analysis listed every bank's records.
 */
fun openCategory(context: Context, categoryId: String, slice: TimeSlice, accountId: String? = null) {
    context.startActivity(
        Intent(context, CategoryDetailActivity::class.java)
            .putExtra(ARG_CATEGORY_ID, categoryId)
            .putExtra(ARG_ACCOUNT_ID, accountId)
            .putExtra(ARG_SLICE_START, slice.start)
            .putExtra(ARG_SLICE_END, slice.endExclusive)
            .putExtra(
                ARG_SLICE_LABEL,
                slice.labelRes?.let { context.getString(it, *slice.labelArgs.toTypedArray()) } ?: slice.label,
            )
            // Kept so the chevrons keep stepping whole calendar months rather than
            // drifting by the month's own length in days.
            .putExtra(ARG_SLICE_MONTH, slice.month?.toString()),
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
