@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.alyaqdhan.riyal.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.alyaqdhan.riyal.core.ScanHistory
import com.alyaqdhan.riyal.ui.compose.Face
import com.alyaqdhan.riyal.ui.compose.popIn
import com.alyaqdhan.riyal.ui.compose.pressBounce

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
                    .size(148.dp)
                    .popIn(),
            )
            Spacer(Modifier.height(16.dp))
            Text("Riyal", style = MaterialTheme.typography.displaySmall)
            Spacer(Modifier.height(6.dp))
            Text(
                "Your spending, sorted from your bank SMS —\nquietly, and only on this phone.",
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
                    Promise("Reads only when you ask", "No background listener, ever.")
                    Promise("Stays on your phone", "No internet permission — data can't leave.")
                    Promise("Only what matters", "Bank messages with money words. The rest is ignored.")
                    Promise("You're in control", "Anything unclear is yours to decide, every step logged.")
                }
            }
            Spacer(Modifier.height(20.dp))
            Card(Modifier.fillMaxWidth()) {
                Column(
                    Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text("How far back should it read?", style = MaterialTheme.typography.titleSmall)
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        ScanHistory.entries.forEachIndexed { index, choice ->
                            SegmentedButton(
                                selected = history == choice,
                                onClick = { history = choice },
                                shape = SegmentedButtonDefaults.itemShape(
                                    index = index,
                                    count = ScanHistory.entries.size,
                                ),
                            ) { Text(choice.label) }
                        }
                    }
                    Text(
                        historyExplanation(history),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = { onGrant(history) },
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier
                    .fillMaxWidth()
                    .pressBounce(),
            ) {
                Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Allow SMS & scan")
            }
            TextButton(onClick = { onSkip(history) }) {
                Text("Explore first")
            }
        }
    }
}

/** One sentence per choice, saying what it means for what you will see. */
private fun historyExplanation(choice: ScanHistory): String = when (choice) {
    ScanHistory.ALL ->
        "Every bank message still in your inbox, so your history is complete from the first scan."
    ScanHistory.YEAR -> "Only the last 12 months. Anything older stays unread."
    ScanHistory.QUARTER -> "Only the last 3 months. Anything older stays unread."
    ScanHistory.FROM_NOW ->
        "Nothing that already arrived is read. Riyal starts empty, and your accounts and " +
            "history appear as your banks text you from today on."
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
