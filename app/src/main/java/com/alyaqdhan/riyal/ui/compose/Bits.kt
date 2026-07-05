@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.alyaqdhan.riyal.ui.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alyaqdhan.riyal.core.LogLine
import com.alyaqdhan.riyal.core.Money
import com.alyaqdhan.riyal.data.Categories
import com.alyaqdhan.riyal.data.Direction
import com.alyaqdhan.riyal.data.Txn
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

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        modifier = modifier.padding(top = 4.dp, bottom = 2.dp),
    )
}

/** One transaction row; tap to re-categorize. */
@Composable
fun TxnRow(txn: Txn, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val category = Categories.byId(txn.categoryId)
    val expense = txn.direction == Direction.EXPENSE
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
                    txn.merchant ?: category.name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${txn.sender} · ${rowTimeFmt.format(Instant.ofEpochMilli(txn.atMillis).atZone(ZoneId.systemDefault()))}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (txn.categorySource == "auto" && txn.confidence < 70) {
                    Text(
                        "parser was ${txn.confidence}% sure, tap to fix",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    Money.formatSigned(txn.amountMinor, txn.currency, expense),
                    style = MaterialTheme.typography.titleSmall,
                    // Money direction is semantic: out = danger red, in = success green.
                    color = if (expense) MaterialTheme.colorScheme.error else successColor(),
                )
                Text(
                    category.name,
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
