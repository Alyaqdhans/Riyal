package com.alyaqdhan.riyal.ui.compose

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.alyaqdhan.riyal.R
import com.alyaqdhan.riyal.data.Categories
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.toArgb
import com.alyaqdhan.riyal.ui.theme.badgeColours
import com.alyaqdhan.riyal.ui.theme.legible

/**
 * Every category gets an official Material Symbols vector drawable (the pre-made XML
 * files from fonts.google.com, rounded + filled style) on a circle tinted with its own
 * colour from [Categories.colorFor].
 */
object CategoryVisuals {

    /**
     * Icon key -> drawable. The key is a name for the *art*, not for a category, which
     * is what lets two categories share one drawable and what a custom category
     * persists in [com.alyaqdhan.riyal.data.Category.icon].
     */
    private val BY_KEY: Map<String, Int> = mapOf(
        "food" to R.drawable.ic_cat_food,
        "groceries" to R.drawable.ic_cat_groceries,
        "transport" to R.drawable.ic_cat_transport,
        "telecom" to R.drawable.ic_cat_telecom,
        "bills" to R.drawable.ic_cat_bills,
        "utilities" to R.drawable.ic_cat_utilities,
        "rent" to R.drawable.ic_cat_rent,
        "home" to R.drawable.ic_cat_home,
        "shopping" to R.drawable.ic_cat_shopping,
        "health" to R.drawable.ic_cat_health,
        "personalcare" to R.drawable.ic_cat_personalcare,
        "entertainment" to R.drawable.ic_cat_entertainment,
        "subscriptions" to R.drawable.ic_cat_subscriptions,
        "travel" to R.drawable.ic_cat_travel,
        "education" to R.drawable.ic_cat_education,
        "insurance" to R.drawable.ic_cat_insurance,
        "loan" to R.drawable.ic_cat_loan,
        "charity" to R.drawable.ic_cat_charity,
        "giving" to R.drawable.ic_cat_giving,
        "government" to R.drawable.ic_cat_government,
        "fees" to R.drawable.ic_cat_fees,
        "cash" to R.drawable.ic_cat_cash,
        "transfer" to R.drawable.ic_cat_transfer,
        "salary" to R.drawable.ic_cat_salary,
        "business" to R.drawable.ic_cat_business,
        "investment" to R.drawable.ic_cat_investment,
        "reimbursement" to R.drawable.ic_cat_reimbursement,
        "cashback" to R.drawable.ic_cat_cashback,
        "refund" to R.drawable.ic_cat_refund,
        "gift" to R.drawable.ic_cat_gift,
        "income" to R.drawable.ic_cat_income,
        "other" to R.drawable.ic_cat_other,
    )

    /**
     * Built-in categories whose id is not its own icon key. Sharing is deliberate:
     * "rent" and "rental" (and "loan"/"borrowed") sit on opposite sides of the ledger,
     * so they are never offered in the same picker, and their colour and badge shape
     * differ anyway. "bills" narrowed to telecom when utilities moved out, so it points
     * at the router rather than the receipt it used to carry.
     */
    private val ALIAS = mapOf(
        "sending" to "transfer",
        "bills" to "telecom",
        "rental" to "rent",
        "borrowed" to "loan",
    )

    /** Every icon a custom category may choose, in the order the picker shows them. */
    val KEYS: List<String> = BY_KEY.keys.toList()

    /**
     * Art for a category id: built-ins resolve straight off their own id, and a user
     * category falls through to whichever icon it was given. An unrecognised id, or a
     * user category with no icon chosen, gets the generic one.
     */
    @DrawableRes
    fun iconFor(id: String): Int =
        BY_KEY[ALIAS[id] ?: id]
            ?: BY_KEY[Categories.byId(id).icon]
            ?: R.drawable.ic_cat_other

    @DrawableRes
    fun byKey(key: String): Int = BY_KEY[key] ?: R.drawable.ic_cat_other
}

/** A category's colour as this theme can show it: its own, shifted just enough to stand out. */
@Composable
fun categoryColor(categoryId: String): Color = legible(Color(Categories.colorFor(categoryId)))

/** A badge's circle and icon colours for [color] ([badgeColours]), on this theme's cards. */
@Composable
fun rememberBadgeColours(color: Int): Pair<Color, Color> {
    val card = MaterialTheme.colorScheme.surfaceContainerHighest
    return remember(color, card) {
        badgeColours(color, card.toArgb()).let { (circle, icon) -> Color(circle) to Color(icon) }
    }
}

/** The category's icon on a circle of its own colour: the badge at the head of a row. */
@Composable
fun CategoryBadge(categoryId: String, modifier: Modifier = Modifier, size: Dp = 44.dp) {
    val (circle, ink) = rememberBadgeColours(Categories.colorFor(categoryId))
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(circle),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painterResource(CategoryVisuals.iconFor(categoryId)),
            contentDescription = null,
            tint = ink,
            modifier = Modifier.size(size / 2),
        )
    }
}

/** Small category icon for chips and inline rows, tinted with the category color. */
@Composable
fun CategoryIcon(categoryId: String, modifier: Modifier = Modifier, size: Dp = 18.dp) {
    Icon(
        painterResource(CategoryVisuals.iconFor(categoryId)),
        contentDescription = null,
        tint = categoryColor(categoryId),
        modifier = modifier.size(size),
    )
}
