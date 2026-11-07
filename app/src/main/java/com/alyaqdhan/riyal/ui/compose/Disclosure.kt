@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.alyaqdhan.riyal.ui.compose

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.alyaqdhan.riyal.R

/**
 * A details list: a row that opens what is folded under it and closes it again. The
 * chevron turns as the list slides down out from under the row, and back up, as Needs a
 * category's records first did. What matters only now and then - a merged place's names,
 * what Review dismissed, the empty categories, the rest of a budget, the records behind a
 * row of the backlog - waits in one of these, never behind a Show or Hide button.
 *
 * In a lazy list it is one item: what it folds is drawn whole when open, so it is for
 * lists of tens, not thousands.
 */
@Composable
fun Disclosure(
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    contentPadding: PaddingValues = PaddingValues(vertical = 8.dp),
    spacing: Dp = 8.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val motion = MaterialTheme.motionScheme
    Column(modifier) {
        DisclosureRow(title, expanded, onToggle, supporting, contentPadding)
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(motion.defaultSpatialSpec()) + fadeIn(motion.defaultEffectsSpec()),
            exit = shrinkVertically(motion.defaultSpatialSpec()) + fadeOut(motion.defaultEffectsSpec()),
        ) {
            Column(Modifier.padding(top = spacing), verticalArrangement = Arrangement.spacedBy(spacing), content = content)
        }
    }
}

/** The row itself: what is folded, how many, and a chevron that turns with it. */
@Composable
private fun DisclosureRow(
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    supporting: String?,
    contentPadding: PaddingValues,
) {
    val turn by animateFloatAsState(if (expanded) 180f else 0f, MaterialTheme.motionScheme.defaultSpatialSpec())
    val state = stringResource(if (expanded) R.string.state_expanded else R.string.state_collapsed)
    ListItem(
        onClick = onToggle,
        modifier = Modifier.semantics { stateDescription = state },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        contentPadding = contentPadding,
        supportingContent = supporting?.let { { Text(it) } },
        trailingContent = {
            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null, modifier = Modifier.rotate(turn))
        },
    ) { Text(title) }
}
