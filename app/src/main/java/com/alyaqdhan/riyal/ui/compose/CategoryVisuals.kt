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

    @DrawableRes
    fun iconFor(id: String): Int = when (id) {
        "food" -> R.drawable.ic_cat_food
        "groceries" -> R.drawable.ic_cat_groceries
        "transport" -> R.drawable.ic_cat_transport
        "bills" -> R.drawable.ic_cat_bills
        "shopping" -> R.drawable.ic_cat_shopping
        "health" -> R.drawable.ic_cat_health
        "entertainment" -> R.drawable.ic_cat_entertainment
        "travel" -> R.drawable.ic_cat_travel
        "education" -> R.drawable.ic_cat_education
        "fees" -> R.drawable.ic_cat_fees
        "cash" -> R.drawable.ic_cat_cash
        "transfer" -> R.drawable.ic_cat_transfer
        "salary" -> R.drawable.ic_cat_salary
        "income" -> R.drawable.ic_cat_income
        else -> R.drawable.ic_cat_other
    }

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
