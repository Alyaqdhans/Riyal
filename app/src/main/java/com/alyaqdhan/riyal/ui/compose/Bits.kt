@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.alyaqdhan.riyal.ui.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.draw.clip
import androidx.compose.material3.ListItem
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.annotation.PluralsRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alyaqdhan.riyal.R
import com.alyaqdhan.riyal.core.Dates
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable

private val rowTimeFmt: DateTimeFormatter get() = Dates.pattern("dd MMM · h:mm a")
// For a list already broken into days: the header above the row states the date, so
// repeating it in the row is one more thing to read that says nothing new - and it was
// the segment that pushed the account name into an ellipsis on every single row.
private val rowClockFmt: DateTimeFormatter get() = Dates.pattern("h:mm a")
private val dayFmt: DateTimeFormatter get() = Dates.pattern("EEEE, dd MMM uuuu")

@Composable
fun dayLabel(date: LocalDate): String {
    val today = LocalDate.now()
    return when (date) {
        today -> stringResource(R.string.day_today)
        today.minusDays(1) -> stringResource(R.string.day_yesterday)
        else -> dayFmt.format(date)
    }
}

fun localDateOf(millis: Long): LocalDate =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()

/**
 * The floating toolbar: its height and the gap it floats above the system bar by,
 * Material's own figures for both.
 */
val BottomBarHeight: Dp = FloatingToolbarDefaults.ContainerSize + FloatingToolbarDefaults.ScreenOffset

/**
 * The room a scrolling tab must leave at its end so its last row can be read rather than
 * sitting under the floating toolbar, which hovers over the content instead of taking a
 * row of its own.
 *
 * Only the toolbar and a little clearance: the system's navigation bar under it is
 * already in the padding every Scaffold gives its content. For the five tabs only - a
 * page opened from one has no toolbar.
 */
val toolbarSpace: Dp
    @Composable get() = BottomBarHeight + 8.dp

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
 * A masked counterparty name, shortened for display only.
 *
 * Omani banks blank the middle of a name: "SAIFXXXXXXXXXXHMED", and sometimes all of
 * it after the first few letters. Rendered whole, the X's fill the row and the end of
 * the name - the half that identifies it - is what gets ellipsed away. Collapsing the
 * run puts both readable ends back on screen: "SAIF…HMED".
 *
 * Display only. The stored merchant string is untouched, because rules and the
 * backlog's grouping key on it and a name shortened in storage would key differently
 * from the same name in a message.
 *
 * Four is the shortest run worth collapsing: it saves nothing below that, and real
 * names do carry two or three capitals in a row.
 */
private val maskedRun = Regex("X{4,}")

fun unmasked(name: String): String = maskedRun.replace(name.trim(), "…")

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

    val time = (if (showDate) rowTimeFmt else rowClockFmt)
        .format(Instant.ofEpochMilli(txn.atMillis).atZone(ZoneId.systemDefault()))
    val detail = if (transfer) {
        val unassigned = stringResource(R.string.row_unassigned)
        stringResource(
            R.string.row_transfer_route,
            accountName(txn.fromAccountId) ?: unassigned,
            accountName(txn.toAccountId) ?: unassigned,
            time,
        )
    } else {
        // The category is said in words only when the headline is a shop: when there is
        // no shop the headline already is the category.
        listOfNotNull(
            category.name.takeIf { txn.merchant != null },
            (accountName(txn.accountId) ?: txn.sender).takeIf { showAccount },
            time,
        ).joinToString(" · ")
    }

    ListItem(
        onClick = onClick,
        modifier = modifier,
        leadingContent = { CategoryBadge(category.id, size = 40.dp) },
        supportingContent = {
            Text(detail, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        trailingContent = {
            Column(horizontalAlignment = Alignment.End) {
                MoneyText(
                    txn.amountMinor,
                    txn.currency,
                    sign = when {
                        transfer -> MoneySign.NONE
                        expense -> MoneySign.OUT
                        else -> MoneySign.IN
                    },
                    style = MaterialTheme.typography.titleSmall,
                    // Money direction is semantic: out = danger red, in = success green,
                    // moved between your own accounts = neither.
                    color = when {
                        transfer -> MaterialTheme.colorScheme.onSurfaceVariant
                        expense -> MaterialTheme.colorScheme.error
                        else -> successColor()
                    },
                )
                // Bought abroad: what the shop charged, under what it counts as.
                if (txn.isConverted) {
                    MoneyText(
                        txn.originalAmountMinor!!,
                        txn.originalCurrency!!,
                        sign = MoneySign.NONE,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                // "not counted" is why a transfer's amount is neither red nor green.
                if (transfer) {
                    Text(
                        stringResource(R.string.row_not_counted),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
    ) {
        Text(
            if (transfer) stringResource(R.string.label_transfer)
            else txn.merchant?.let(::unmasked) ?: category.name,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Nothing to show yet: an icon on a tonal circle, what is missing, and why. */
@Composable
fun EmptyState(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int = R.drawable.ic_sym_inbox,
) {
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painterResource(icon),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(32.dp),
            )
        }
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
        LogLine.Kind.SKIP -> MaterialTheme.colorScheme.onSurfaceVariant
        LogLine.Kind.SCAN -> MaterialTheme.colorScheme.tertiary
        LogLine.Kind.INFO -> MaterialTheme.colorScheme.onSurface
    }
    Text(
        bidi("[${line.time}]") + " " + line.text,
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

fun bidi(amount: String): String = "\u2066" + amount + "\u2069"

@Composable
fun counted(@PluralsRes plural: Int, n: Int): String = pluralStringResource(plural, n, n)

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
            contentDescription = stringResource(R.string.help_about, title),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    if (open) {
        val pop = rememberDialogPop()
        AlertDialog(
            onDismissRequest = { pop.leave { open = false } },
            modifier = Modifier.popIn(pop),
            title = { Text(title) },
            text = { Text(help) },
            confirmButton = {
                TextButton(onClick = { pop.leave { open = false } }, shapes = ButtonDefaults.shapes()) {
                    Text(stringResource(R.string.action_got_it))
                }
            },
        )
    }
}

/**
 * The version name this build carries, as a person reads it.
 *
 * Shared because two screens say it now: Settings, where the About row lives, and Home,
 * where the card about a newer release has to name the one you are on. A phone that
 * cannot report its own version is a case that never happens in practice and must not be
 * a crash, so it reads as "1.0" and the comparison in Updates.isNewer treats it as the
 * oldest thing there is.
 */
fun appVersion(context: android.content.Context): String = try {
    context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0"
} catch (e: Exception) {
    "1.0"
}

private val relativeDayFmt: DateTimeFormatter get() = Dates.pattern("d MMM uuuu")

/** "2 hours ago", or the date once that stops being a useful way to say it. */
@Composable
fun relativeTime(millis: Long): String {
    val minutes = (System.currentTimeMillis() - millis) / 60_000
    return when {
        minutes < 1 -> stringResource(R.string.set_just_now)
        minutes < 60 -> stringResource(
            R.string.set_ago,
            counted(R.plurals.count_minutes, minutes.toInt()),
        )
        minutes < 60 * 24 -> stringResource(
            R.string.set_ago,
            counted(R.plurals.count_hours, (minutes / 60).toInt()),
        )
        minutes < 60 * 24 * 7 -> stringResource(
            R.string.set_ago,
            counted(R.plurals.count_days, (minutes / (60 * 24)).toInt()),
        )
        else -> stringResource(
            R.string.set_on_date,
            relativeDayFmt.format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault())),
        )
    }
}

/**
 * Deleting what an editor is open on: at the end of its form, the whole width, in the
 * error colour, well away from Cancel and Save, so it is never the button a thumb meant
 * for one of them.
 */
@Composable
fun DeleteButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val error = MaterialTheme.colorScheme.error
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().padding(top = 8.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = error),
        border = BorderStroke(1.dp, error.copy(alpha = 0.5f)),
        shapes = ButtonDefaults.shapes(),
    ) {
        Icon(
            painterResource(R.drawable.ic_sym_delete),
            contentDescription = null,
            modifier = Modifier.size(ButtonDefaults.IconSize),
        )
        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
        Text(stringResource(R.string.action_delete))
    }
}

/**
 * A screen's own action - Add account, New category - as Material 3 Expressive's medium
 * button: taller than a plain one, led by its icon, and its corners square off while it
 * is pressed and spring back after, so the press is felt.
 */
@Composable
fun ActionButton(
    text: String,
    @DrawableRes icon: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tonal: Boolean = false,
) {
    val height = ButtonDefaults.MediumContainerHeight
    val content: @Composable RowScope.() -> Unit = {
        Icon(
            painterResource(icon),
            contentDescription = null,
            modifier = Modifier.size(ButtonDefaults.iconSizeFor(height)),
        )
        Spacer(Modifier.width(ButtonDefaults.iconSpacingFor(height)))
        Text(text, style = ButtonDefaults.textStyleFor(height))
    }
    val shapes = ButtonDefaults.shapesFor(height)
    val padding = ButtonDefaults.contentPaddingFor(height, hasStartIcon = true)
    if (tonal) {
        FilledTonalButton(onClick, shapes, modifier.heightIn(min = height), contentPadding = padding, content = content)
    } else {
        Button(onClick, shapes, modifier.heightIn(min = height), contentPadding = padding, content = content)
    }
}
