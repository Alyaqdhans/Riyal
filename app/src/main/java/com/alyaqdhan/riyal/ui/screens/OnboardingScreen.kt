@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.alyaqdhan.riyal.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.alyaqdhan.riyal.R
import com.alyaqdhan.riyal.core.ScanHistory
import com.alyaqdhan.riyal.ui.compose.Face

/**
 * First-run pitch. The whole point of the app in four promises, then the user -
 * not the app, decides whether SMS reading is allowed, and how much of the inbox
 * that permission covers.
 *
 * The history question belongs here rather than in Settings because it can only be
 * answered once: "from today" is a moment, and by the time you have found Settings
 * that moment has passed.
 */
@Composable
fun OnboardingScreen(
    onGrant: (ScanHistory) -> Unit,
    onSkip: (ScanHistory) -> Unit,
) {
    var history by remember { mutableStateOf(ScanHistory.ALL) }
    Scaffold { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Face(
                mood = 1f,
                modifier = Modifier
                    .size(148.dp),
            )
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
                Column(
                    Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Promise(
                        stringResource(R.string.onboarding_promise_ask_title),
                        stringResource(R.string.onboarding_promise_ask_detail),
                    )
                    Promise(
                        stringResource(R.string.onboarding_promise_stays_title),
                        stringResource(R.string.onboarding_promise_stays_detail),
                    )
                    Promise(
                        stringResource(R.string.onboarding_promise_matters_title),
                        stringResource(R.string.onboarding_promise_matters_detail),
                    )
                    Promise(
                        stringResource(R.string.onboarding_promise_control_title),
                        stringResource(R.string.onboarding_promise_control_detail),
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
            Card(Modifier.fillMaxWidth()) {
                Column(
                    Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        stringResource(R.string.onboarding_history_question),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        ScanHistory.entries.forEachIndexed { index, choice ->
                            SegmentedButton(
                                selected = history == choice,
                                onClick = { history = choice },
                                shape = SegmentedButtonDefaults.itemShape(
                                    index = index,
                                    count = ScanHistory.entries.size,
                                ),
                                // An equal share each. Left to size themselves the four
                                // segments took the width of their own labels, so "All"
                                // was a third of "3 months" and the long ones wrapped on
                                // to a second line, leaving the row visibly ragged.
                                modifier = Modifier.weight(1f),
                                // Four equal segments leave no room for the selection
                                // tick as well as the word; the filled segment already
                                // says which one is chosen.
                                icon = {},
                                // The default padding is generous for a segment that has
                                // a quarter of the width to work with.
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
                            ) {
                                Text(
                                    stringResource(choice.label),
                                    // maxLines alone still breaks at the space and then
                                    // shows only the first line - "3 months" became "3".
                                    softWrap = false,
                                    maxLines = 1,
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            }
                        }
                    }
                    Text(
                        stringResource(history.explanation),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = { onGrant(history) },
                modifier = Modifier
                    .fillMaxWidth(),
            ) {
                Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.onboarding_allow))
            }
            TextButton(onClick = { onSkip(history) }) {
                Text(stringResource(R.string.onboarding_explore))
            }
        }
    }
}

@Composable
private fun Promise(title: String, detail: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(
            Icons.Filled.Check,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
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
