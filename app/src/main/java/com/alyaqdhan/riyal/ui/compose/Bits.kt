@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.alyaqdhan.riyal.ui.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alyaqdhan.riyal.core.LogLine
import com.alyaqdhan.riyal.core.Money
import com.alyaqdhan.riyal.data.Account
import com.alyaqdhan.riyal.data.Categories
import com.alyaqdhan.riyal.data.Txn
import com.alyaqdhan.riyal.data.TxnType
import com.alyaqdhan.riyal.ui.theme.successColor
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable

private val rowTimeFmt = DateTimeFormatter.ofPattern("dd MMM · h:mm a")
// For a list already broken into days: the header above the row states the date, so
// repeating it in the row is one more thing to read that says nothing new - and it was
// the segment that pushed the account name into an ellipsis on every single row.
private val rowClockFmt = DateTimeFormatter.ofPattern("h:mm a")
private val dayFmt = DateTimeFormatter.ofPattern("EEEE, dd MMM uuuu")

fun dayLabel(date: LocalDate): String {
    val today = LocalDate.now()
    return when (date) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> dayFmt.format(date)
    }
}

fun localDateOf(millis: Long): LocalDate =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()

/**
 * The pill itself: its height, the gap it floats above the system bar by, and enough
 * clearance that the last row is readable rather than touching it.
 *
 * This is only half the answer - see [toolbarSpace].
 */
private val ToolbarPill = 96.dp

/**
 * The room a scrolling page must leave at its end so its last row can be read rather
 * than sitting under the floating toolbar, which hovers over the content instead of
 * taking a row of its own.
 *
 * The toolbar sits above the navigation bar, so the space it occupies is the pill plus
 * whatever the system bar takes - which is 24dp of gesture pill on one phone and three
 * times that with button navigation. A fixed number was right for exactly one of those
 * and cut the last row off on the other, so it is measured rather than assumed.
 *
 * For the four tab screens only - a pushed page hides the toolbar, so it needs nothing
 * more than ordinary padding at its end.
 */
val toolbarSpace: Dp
    @Composable get() = ToolbarPill +
        WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

@Composable
fun ToolbarSpacer() {
    Spacer(Modifier.height(toolbarSpace))
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        modifier = modifier.padding(top = 4.dp, bottom = 2.dp),
    )
}

/**
 * One transaction row; tap to re-categorize.
 *
 * A transfer is drawn deliberately differently: no red, no green, no leading sign.
 * Those colours mean "this made you poorer / richer", and a transfer did neither - it
 * reads as neutral movement between two of your own accounts, with both ends named.
 */
@Composable
fun TxnRow(
    txn: Txn,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accounts: List<Account> = emptyList(),
    /** False where a day header above the row already gives the date. */
    showDate: Boolean = true,
    /** False where the whole list has been filtered to one account already. */
    showAccount: Boolean = true,
) {
    val category = Categories.byId(txn.categoryId)
    val transfer = txn.type == TxnType.TRANSFER
    val expense = txn.type == TxnType.EXPENSE

    // TxnRow already drops the date when a day header states it and the account when a
    // filter has narrowed to one. A bank name on every row of a one-bank inbox is the
    // same fact stated for the same reason, so it goes the same way.
    val oneBank = remember(accounts) {
        accounts.mapNotNull { it.bankName.trim().takeIf(String::isNotEmpty) }.distinct().size <= 1
    }

    fun accountName(id: String?): String? = id?.let { wanted ->
        accounts.firstOrNull { it.id == wanted }?.let { if (oneBank) it.shortName else it.displayName }
    }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier
            .fillMaxWidth()
            .pressBounce(0.97f),
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CategoryBadge(category.id)
            Column(Modifier.weight(1f)) {
                Text(
                    if (transfer) "Transfer" else txn.merchant ?: category.name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val time = (if (showDate) rowTimeFmt else rowClockFmt)
                    .format(Instant.ofEpochMilli(txn.atMillis).atZone(ZoneId.systemDefault()))
                Text(
                    if (transfer) {
                        val from = accountName(txn.fromAccountId) ?: "unassigned"
                        val to = accountName(txn.toAccountId) ?: "unassigned"
                        "$from → $to · $time"
                    } else {
                        val account = accountName(txn.accountId) ?: txn.sender
                        if (showAccount) "$account · $time" else time
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!transfer && txn.categorySource == "auto" && txn.confidence < 70) {
                    Text(
                        "parser was ${txn.confidence}% sure, tap to fix",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    if (transfer) Money.format(txn.amountMinor, txn.currency)
                    else Money.formatSigned(txn.amountMinor, txn.currency, expense),
                    style = MaterialTheme.typography.titleSmall,
                    // Money direction is semantic: out = danger red, in = success green,
                    // moved between your own accounts = neither.
                    color = when {
                        transfer -> MaterialTheme.colorScheme.onSurfaceVariant
                        expense -> MaterialTheme.colorScheme.error
                        else -> successColor()
                    },
                )
                // Only the transfer note. The category was already stated by the badge
                // at the head of the row, and saying it again in words cost about a
                // third of the width - which is why the merchant and the account line
                // were both ellipsed at once, leaving two Oman Oil stations looking
                // identical. "not counted" is not a repeat of anything: it is why a
                // transfer's amount is neither red nor green.
                if (transfer) {
                    Text(
                        "not counted",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** Friendly empty state with a mascot face. */
@Composable
fun EmptyState(
    style: FaceStyle,
    title: String,
    subtitle: String? = null,
    mood: Float = 0.2f,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Face(
            mood = mood,
            style = style,
            modifier = Modifier
                .size(108.dp)
                .popIn(),
        )
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        if (subtitle != null) {
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** Monospace verbose-log line, colored by what happened. */
@Composable
fun LogRow(line: LogLine) {
    val color = when (line.kind) {
        LogLine.Kind.OK -> MaterialTheme.colorScheme.primary
        LogLine.Kind.FAIL -> MaterialTheme.colorScheme.error
        LogLine.Kind.SKIP -> MaterialTheme.colorScheme.outline
        LogLine.Kind.SCAN -> MaterialTheme.colorScheme.tertiary
        LogLine.Kind.INFO -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Text(
        "[${line.time}] ${line.text}",
        fontFamily = FontFamily.Monospace,
        fontSize = 11.sp,
        lineHeight = 15.sp,
        color = color,
    )
}

/** Small stat pill used in scan summaries. */
@Composable
fun SummaryPill(text: String, container: Color, content: Color) {
    Surface(shape = RoundedCornerShape(50), color = container) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            color = content,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

/**
 * "1 record(s)" is not English, and it appears wherever a count does. One place to
 * decide it, so a screen counting things never has to think about the s.
 */
fun countOf(n: Int, noun: String): String = "$n $noun" + if (n == 1) "" else "s"

// ─────────────────────────── a screen's own explanation ───────────────────────────

/**
 * What a screen would have said about itself, behind the (i) in its title bar.
 *
 * Settings learned this first: an explanation is read once and a screen is used many
 * times, so a paragraph that sits on the page at rest is read once and then scrolled
 * past forever after, having pushed the actual work down the screen every time. The
 * same paragraph one tap away costs nothing to the people who already know.
 *
 * The dialog, not a tooltip, because the text is a paragraph and a tooltip that needs
 * scrolling is worse than no tooltip.
 */
@Composable
fun HelpAction(title: String, help: String) {
    var open by rememberSaveable { mutableStateOf(false) }
    IconButton(onClick = { open = true }) {
        Icon(
            Icons.Outlined.Info,
            contentDescription = "About $title",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(title) },
            text = { Text(help) },
            confirmButton = { TextButton(onClick = { open = false }) { Text("Got it") } },
        )
    }
}

/**
 * The same text on the page, for someone who asked in Settings to be told rather than
 * to go looking. Off by default: a screen that explains itself at rest has to be read
 * before it can be used.
 */
@Composable
fun HelpNote(help: String, visible: Boolean, modifier: Modifier = Modifier) {
    if (!visible) return
    Text(
        help,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(vertical = 4.dp),
    )
}
