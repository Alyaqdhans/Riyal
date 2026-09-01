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

private val rowTimeFmt = DateTimeFormatter.ofPattern("dd MMM · h:mm a")
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
) {
    val category = Categories.byId(txn.categoryId)
    val transfer = txn.type == TxnType.TRANSFER
    val expense = txn.type == TxnType.EXPENSE

    fun accountName(id: String?): String? =
        id?.let { wanted -> accounts.firstOrNull { it.id == wanted }?.displayName }

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
                val time = rowTimeFmt.format(Instant.ofEpochMilli(txn.atMillis).atZone(ZoneId.systemDefault()))
                Text(
                    if (transfer) {
                        val from = accountName(txn.fromAccountId) ?: "unassigned"
                        val to = accountName(txn.toAccountId) ?: "unassigned"
                        "$from → $to · $time"
                    } else {
                        val account = accountName(txn.accountId)
                        if (account != null) "$account · $time" else "${txn.sender} · $time"
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
                Text(
                    if (transfer) "not counted" else category.name,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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
