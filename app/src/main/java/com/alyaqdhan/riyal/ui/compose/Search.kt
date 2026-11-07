@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.alyaqdhan.riyal.ui.compose

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSearchBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.alyaqdhan.riyal.R

/**
 * A top bar's title that a search grows out of. Closed it is the title, and the bar shows
 * a search icon ([SearchAction]); tapped, Material's search field - a pill - grows out of
 * the icon's end across the title, with the keyboard up, and its own close button empties
 * it and folds it back. Nothing takes a row of the page until a search is wanted.
 *
 * The field is [SearchBarDefaults.InputField] on the search bar's own pill and colours,
 * but not inside [androidx.compose.material3.SearchBar]: that one keeps the keyboard down
 * for a full-screen search to open over it, and this search filters the page in place.
 */
@Composable
fun SearchTitle(
    title: String,
    open: Boolean,
    field: TextFieldState,
    placeholder: String,
    onClose: () -> Unit,
) {
    val motion = MaterialTheme.motionScheme
    AnimatedContent(
        targetState = open,
        contentAlignment = Alignment.CenterEnd,
        transitionSpec = {
            if (targetState) {
                (fadeIn(motion.defaultEffectsSpec()) +
                    expandHorizontally(motion.defaultSpatialSpec(), expandFrom = Alignment.End)) togetherWith
                    fadeOut(motion.fastEffectsSpec())
            } else {
                fadeIn(motion.defaultEffectsSpec()) togetherWith
                    (fadeOut(motion.fastEffectsSpec()) +
                        shrinkHorizontally(motion.defaultSpatialSpec(), shrinkTowards = Alignment.End))
            }
        },
        label = "search",
    ) { searching ->
        if (searching) {
            SearchPill(field, placeholder, onClose)
        } else {
            Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** The icon a [SearchTitle] grows out of, among the bar's actions while it is closed. */
@Composable
fun SearchAction(label: String, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(Icons.Filled.Search, contentDescription = label)
    }
}

@Composable
private fun SearchPill(field: TextFieldState, placeholder: String, onClose: () -> Unit) {
    val bar = rememberSearchBarState()
    val focus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    // Opened to type into, so the keyboard comes up with it.
    LaunchedEffect(Unit) { focus.requestFocus() }
    Surface(
        shape = SearchBarDefaults.inputFieldShape,
        color = SearchBarDefaults.colors().containerColor,
        modifier = Modifier.fillMaxWidth().padding(end = 8.dp),
    ) {
        SearchBarDefaults.InputField(
            textFieldState = field,
            searchBarState = bar,
            onSearch = { focusManager.clearFocus() },
            modifier = Modifier.focusRequester(focus),
            // Body text, not the bar title's that the slot it sits in would hand it.
            textStyle = MaterialTheme.typography.bodyLarge,
            placeholder = { Text(placeholder, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                IconButton(onClick = onClose) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_close))
                }
            },
        )
    }
}
