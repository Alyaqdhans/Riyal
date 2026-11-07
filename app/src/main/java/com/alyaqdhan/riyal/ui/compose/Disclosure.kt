@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.alyaqdhan.riyal.ui.compose

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.ui.unit.dp
import com.alyaqdhan.riyal.R

/**
 * A details row: a list item that opens what is folded under it and closes it again, its
 * chevron turning to say which way. What matters only now and then - a merged place's
 * names, what Review dismissed, the empty categories, the rest of a budget, the records
 * behind a row of the backlog - waits under one of these, never behind a Show or Hide
 * button beside a title. The caller draws what it folds, right under it, while [expanded].
 */
@Composable
fun DisclosureRow(
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    contentPadding: PaddingValues = PaddingValues(vertical = 8.dp),
) {
    val turn by animateFloatAsState(if (expanded) 180f else 0f, MaterialTheme.motionScheme.defaultSpatialSpec())
    val state = stringResource(if (expanded) R.string.state_expanded else R.string.state_collapsed)
    ListItem(
        onClick = onToggle,
        modifier = modifier.semantics { stateDescription = state },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        contentPadding = contentPadding,
        supportingContent = supporting?.let { { Text(it) } },
        trailingContent = {
            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null, modifier = Modifier.rotate(turn))
        },
    ) { Text(title) }
}
