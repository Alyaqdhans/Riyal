@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.alyaqdhan.riyal.ui.compose

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.toShape
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
import kotlin.math.abs

/**
 * Every category gets an official Material Symbols vector drawable (the pre-made XML
 * files from fonts.google.com, rounded + filled style) and its own imperfect M3
 * Expressive shape, so no two badges in a list look alike. Color comes from
 * [Categories.colorFor].
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

    @DrawableRes
    fun iconFor(id: String): Int =
        BY_KEY[ALIAS[id] ?: id] ?: R.drawable.ic_cat_other

    @DrawableRes
    fun byKey(key: String): Int = BY_KEY[key] ?: R.drawable.ic_cat_other

    // Rotating set of jagged/imperfect shapes; stable per category via hash.
    private val SHAPES = listOf(
        MaterialShapes.Cookie9Sided,
        MaterialShapes.Clover8Leaf,
        MaterialShapes.Sunny,
        MaterialShapes.SoftBurst,
        MaterialShapes.Cookie7Sided,
        MaterialShapes.Flower,
        MaterialShapes.Cookie12Sided,
        MaterialShapes.Clover4Leaf,
    )

    fun shapeFor(id: String) = SHAPES[abs(id.hashCode()) % SHAPES.size]
}

/** Icon-on-jaggy-shape badge, tinted with the category's own color. */
@Composable
fun CategoryBadge(categoryId: String, modifier: Modifier = Modifier, size: Dp = 44.dp) {
    val color = Color(Categories.colorFor(categoryId))
    Box(
        modifier = modifier
            .size(size)
            .clip(CategoryVisuals.shapeFor(categoryId).toShape())
            .background(color.copy(alpha = 0.22f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painterResource(CategoryVisuals.iconFor(categoryId)),
            contentDescription = null,
            tint = color,
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
        tint = Color(Categories.colorFor(categoryId)),
        modifier = modifier.size(size),
    )
}
