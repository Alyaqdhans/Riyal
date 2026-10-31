@file:OptIn(ExperimentalMaterial3Api::class)

package com.alyaqdhan.riyal.ui.compose

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** One choice among a few: an icon to find it by, what it is, and what it covers. */
data class Preset(
    @param:DrawableRes val icon: Int,
    val title: String,
    /** What it covers, in dates where it has them: "1 Oct to 31 Oct 2026", "Since 28 Jul 2026". */
    val detail: String,
    val selected: Boolean,
    val onClick: () -> Unit,
)

/**
 * Choices as tiles, two to a row: an icon, the name, and the dates each one covers, so
 * "Last 3 months" says which three without having to work it out. The one in use is
 * filled in. A plain list of words gave none of that, and every choice looked the same.
 */
@Composable
fun PresetGrid(presets: List<Preset>, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        presets.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { PresetTile(it, Modifier.weight(1f)) }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun PresetTile(preset: Preset, modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    Card(
        onClick = preset.onClick,
        modifier = modifier.semantics { selected = preset.selected },
        colors = if (preset.selected) {
            CardDefaults.cardColors(containerColor = scheme.primaryContainer, contentColor = scheme.onPrimaryContainer)
        } else {
            CardDefaults.cardColors(containerColor = scheme.surfaceContainerHigh, contentColor = scheme.onSurface)
        },
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Icon(
                painterResource(preset.icon),
                contentDescription = null,
                tint = if (preset.selected) scheme.onPrimaryContainer else scheme.primary,
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.height(10.dp))
            Text(
                preset.title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                preset.detail,
                style = MaterialTheme.typography.bodySmall,
                color = if (preset.selected) scheme.onPrimaryContainer else scheme.onSurfaceVariant,
                // Two lines kept whether or not they are filled, so every tile is the
                // same size and the grid reads as a grid.
                minLines = 2,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** [PresetGrid] in a bottom sheet under its title, for a choice opened from elsewhere. */
@Composable
fun PresetSheet(title: String, presets: List<Preset>, onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden),
    ) {
        val scroll = rememberScrollState()
        ScrollThumb(scroll) {
        Column(
            Modifier
                .verticalScroll(scroll)
                .padding(horizontal = 16.dp)
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(start = 8.dp, bottom = 16.dp),
            )
            PresetGrid(presets)
        }
        }
    }
}
