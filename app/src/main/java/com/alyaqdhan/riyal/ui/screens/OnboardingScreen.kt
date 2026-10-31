@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.alyaqdhan.riyal.ui.screens

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.alyaqdhan.riyal.R
import com.alyaqdhan.riyal.core.Dates
import com.alyaqdhan.riyal.core.ScanHistory
import com.alyaqdhan.riyal.ui.compose.CurrencySign
import com.alyaqdhan.riyal.ui.compose.Preset
import com.alyaqdhan.riyal.ui.compose.PresetGrid
import com.alyaqdhan.riyal.ui.compose.ScrollThumb
import java.time.LocalDate

/** The room the two floating answers take at the foot of the page. */
private val ANSWERS_CLEARANCE = 104.dp

/**
 * First-run welcome. What the app is, the four promises it keeps, and then the user -
 * not the app - decides whether SMS reading is allowed, and how much of the inbox that
 * permission covers.
 *
 * The history question belongs here rather than in Settings because it can only be
 * answered once: "from today" is a moment, and by the time you have found Settings that
 * moment has passed.
 *
 * The two answers float over the page as buttons, as the Add button floats over the
 * tabs, rather than sitting in a bar of their own along the bottom.
 */
@Composable
fun OnboardingScreen(
    onGrant: (ScanHistory) -> Unit,
    onSkip: (ScanHistory) -> Unit,
) {
    var history by rememberSaveable { mutableStateOf(ScanHistory.ALL) }
    Scaffold { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            val scroll = rememberScrollState()
            ScrollThumb(scroll, Modifier.fillMaxSize(), bottom = ANSWERS_CLEARANCE) {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(scroll)
                    .padding(horizontal = 20.dp)
                    .padding(top = 32.dp, bottom = ANSWERS_CLEARANCE),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                BrandMark()
                Spacer(Modifier.height(16.dp))
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.displaySmall)
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(R.string.onboarding_pitch),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(24.dp))
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Promise(
                            R.drawable.ic_sym_lock,
                            stringResource(R.string.onboarding_promise_ask_title),
                            stringResource(R.string.onboarding_promise_ask_detail),
                        )
                        Promise(
                            R.drawable.ic_sym_sms,
                            stringResource(R.string.onboarding_promise_stays_title),
                            stringResource(R.string.onboarding_promise_stays_detail),
                        )
                        Promise(
                            R.drawable.ic_sym_payments,
                            stringResource(R.string.onboarding_promise_matters_title),
                            stringResource(R.string.onboarding_promise_matters_detail),
                        )
                        Promise(
                            R.drawable.ic_sym_check_circle,
                            stringResource(R.string.onboarding_promise_control_title),
                            stringResource(R.string.onboarding_promise_control_detail),
                        )
                    }
                }
                Spacer(Modifier.height(24.dp))
                Text(
                    stringResource(R.string.onboarding_history_question),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.fillMaxWidth().padding(start = 4.dp, bottom = 12.dp),
                )
                PresetGrid(
                    ScanHistory.entries.map { choice ->
                        Preset(
                            icon = iconOf(choice),
                            title = stringResource(choice.label),
                            detail = detailOf(choice),
                            selected = history == choice,
                        ) { history = choice }
                    }
                )
                Text(
                    stringResource(history.explanation),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(start = 4.dp, top = 12.dp),
                )
            }
            }

            Row(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ExtendedFloatingActionButton(
                    onClick = { onSkip(history) },
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.primary,
                    icon = { Icon(painterResource(R.drawable.ic_nav_home), contentDescription = null) },
                    text = { Text(stringResource(R.string.onboarding_explore)) },
                )
                ExtendedFloatingActionButton(
                    onClick = { onGrant(history) },
                    icon = { Icon(painterResource(R.drawable.ic_sym_lock), contentDescription = null) },
                    text = { Text(stringResource(R.string.onboarding_allow)) },
                )
            }
        }
    }
}

/** The app's mark, as on its icon: the rial's sign on the app's colour. */
@Composable
private fun BrandMark() {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        modifier = Modifier.size(96.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            CurrencySign("OMR", height = 30.dp, tint = MaterialTheme.colorScheme.onPrimary)
        }
    }
}

@Composable
private fun Promise(@DrawableRes icon: Int, title: String, detail: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Icon(
            painterResource(icon),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp),
        )
        Column {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(
                detail,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun iconOf(choice: ScanHistory): Int = when (choice) {
    ScanHistory.ALL -> R.drawable.ic_sym_all_inclusive
    ScanHistory.YEAR -> R.drawable.ic_sym_event
    ScanHistory.QUARTER -> R.drawable.ic_sym_date_range
    ScanHistory.FROM_NOW -> R.drawable.ic_sym_today
}

/** Since when each choice reads, as a date rather than a sum to do. */
@Composable
private fun detailOf(choice: ScanHistory): String = when (choice) {
    ScanHistory.ALL -> stringResource(R.string.range_everything_detail)
    ScanHistory.FROM_NOW -> stringResource(R.string.history_from_now_detail)
    else -> stringResource(
        R.string.range_since,
        Dates.pattern("d MMM uuuu").format(LocalDate.now().minusMonths(choice.months.toLong())),
    )
}
