@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.alyaqdhan.riyal.ui.compose

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSearchBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.alyaqdhan.riyal.R

/** The search button's size, which the field grows out of. */
private val ButtonSize = 48.dp

/**
 * A title - a top bar's, or a sheet's - with a search button at its end that grows into
 * the search field.
 *
 * Closed, the bar shows its title and, while [searchable], a search icon. Tapped, the icon
 * stays where it is and the button stretches from there across the title into Material's
 * search field - a pill on the search bar's own colours, its fill coming in as it grows -
 * with the keyboard up and a close button at its start. Closing empties the search and
 * draws the field back into the icon, as Back does while it is open. Nothing takes a row
 * of the page until a search is wanted.
 *
 * The field is laid out at its full width from the first frame, behind a window that grows
 * out of the icon, so nothing in it slides: the window uncovers it. It is
 * [SearchBarDefaults.InputField], not [androidx.compose.material3.SearchBar], which keeps the
 * keyboard down for a full-screen search to open over it; this search filters the page.
 */
@Composable
fun SearchTitle(
    title: String,
    searchable: Boolean,
    open: Boolean,
    field: TextFieldState,
    placeholder: String,
    onOpen: () -> Unit,
    onClose: () -> Unit,
    /** The title's own style, where no top bar gives it one (a sheet's title row). */
    titleStyle: TextStyle = LocalTextStyle.current,
) {
    val motion = MaterialTheme.motionScheme
    val focusManager = LocalFocusManager.current
    // A tab stays built while another shows, so a search left open in it must neither take
    // the Back meant for the tab in view nor keep the keyboard up over it. Its words stay.
    val inView = LocalTabInView.current
    BackHandler(enabled = open && inView) { onClose() }
    LaunchedEffect(open, inView) { if (!open || !inView) focusManager.clearFocus() }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val full = maxWidth
        val reveal by animateDpAsState(if (open) full else ButtonSize, motion.defaultSpatialSpec(), label = "search")
        val grown = ((reveal - ButtonSize) / (full - ButtonSize)).coerceIn(0f, 1f)
        Text(
            title,
            style = titleStyle,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(end = if (searchable) ButtonSize else 0.dp)
                .graphicsLayer { alpha = 1f - grown },
        )
        if (searchable || open || grown > 0f) {
            val fill = SearchBarDefaults.colors().containerColor
            Box(
                Modifier
                    .align(Alignment.CenterEnd)
                    .width(reveal)
                    .height(SearchBarDefaults.InputFieldHeight)
                    .clip(SearchBarDefaults.inputFieldShape)
                    .drawBehind { drawRect(fill.copy(alpha = fill.alpha * grown)) },
            ) {
                Row(
                    Modifier
                        .align(Alignment.CenterEnd)
                        .wrapContentWidth(Alignment.End, unbounded = true)
                        .width(full)
                        .fillMaxHeight(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (open || grown > 0f) {
                        SearchField(
                            field,
                            placeholder,
                            onClose = {
                                focusManager.clearFocus()
                                onClose()
                            },
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                    // The button the field grew out of, still where it was.
                    IconButton(onClick = { if (open) focusManager.clearFocus() else onOpen() }) {
                        Icon(Icons.Filled.Search, contentDescription = placeholder)
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchField(field: TextFieldState, placeholder: String, onClose: () -> Unit, modifier: Modifier) {
    val bar = rememberSearchBarState()
    val focus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    // Opened to type into, so the keyboard comes up with it.
    LaunchedEffect(Unit) { focus.requestFocus() }
    SearchBarDefaults.InputField(
        textFieldState = field,
        searchBarState = bar,
        onSearch = { focusManager.clearFocus() },
        modifier = modifier.focusRequester(focus),
        // Body text, not the bar title's that the slot it sits in would hand it.
        textStyle = MaterialTheme.typography.bodyLarge,
        placeholder = { Text(placeholder, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingIcon = {
            IconButton(onClick = onClose) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_close))
            }
        },
    )
}
