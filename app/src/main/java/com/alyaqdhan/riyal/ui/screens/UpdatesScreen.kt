@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)

package com.alyaqdhan.riyal.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.nonInteractiveScrollbar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.alyaqdhan.riyal.R
import com.alyaqdhan.riyal.core.Dates
import com.alyaqdhan.riyal.data.Updates
import com.alyaqdhan.riyal.ui.MainViewModel
import com.alyaqdhan.riyal.ui.MainViewModel.UpdateState
import com.alyaqdhan.riyal.ui.compose.FlatWheel
import com.alyaqdhan.riyal.ui.compose.ReleaseNotes
import com.alyaqdhan.riyal.ui.compose.appVersion
import com.alyaqdhan.riyal.ui.compose.megabytes
import com.alyaqdhan.riyal.ui.compose.relativeTime
import com.alyaqdhan.riyal.ui.theme.onSuccessContainer
import com.alyaqdhan.riyal.ui.theme.successContainer
import java.time.Instant
import java.time.ZoneId

/**
 * Updates, laid out the way ytdlnis lays its own: a status card that says where things
 * stand and holds the one button that matters, then the few settings, then the
 * changelog.
 */
@Composable
fun UpdatesScreen(vm: MainViewModel, onBack: () -> Unit, onOpenChangelog: () -> Unit) {
    val context = LocalContext.current
    val version = appVersion(context)
    val state by vm.updateState.collectAsState()
    val lastCheck by vm.lastUpdateCheck.collectAsState()
    val checkOnStart by vm.checkOnStart.collectAsState()
    val includeBeta by vm.includeBeta.collectAsState()
    val skipped by vm.skippedUpdates.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.upd_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
        ) {
            StatusCard(
                state = state,
                version = version,
                lastCheck = lastCheck,
                includeBeta = includeBeta,
                onCheck = { vm.checkForUpdate(version, force = true) },
                onUpdate = { vm.openUpdatePrompt(it) },
                modifier = Modifier.padding(16.dp),
            )
            ListItem(
                checked = checkOnStart,
                onCheckedChange = { vm.setCheckOnStart(it) },
                supportingContent = { Text(stringResource(R.string.upd_check_on_start_detail)) },
                leadingContent = { Icon(painterResource(R.drawable.ic_sym_system_update), contentDescription = null) },
                trailingContent = { Switch(checked = checkOnStart, onCheckedChange = null) },
            ) { Text(stringResource(R.string.upd_check_on_start)) }
            ListItem(
                checked = includeBeta,
                onCheckedChange = { vm.setIncludeBeta(it, version) },
                supportingContent = { Text(stringResource(R.string.upd_beta_detail)) },
                leadingContent = { Icon(painterResource(R.drawable.ic_sym_science), contentDescription = null) },
                trailingContent = { Switch(checked = includeBeta, onCheckedChange = null) },
            ) { Text(stringResource(R.string.upd_beta)) }
            ListItem(
                onClick = onOpenChangelog,
                supportingContent = { Text(stringResource(R.string.upd_changelog_detail)) },
                leadingContent = { Icon(painterResource(R.drawable.ic_sym_history), contentDescription = null) },
                trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) },
            ) { Text(stringResource(R.string.upd_changelog)) }
            if (skipped.isNotEmpty()) {
                ListItem(
                    supportingContent = { Text(skipped.sorted().joinToString(", ")) },
                    trailingContent = {
                        TextButton(onClick = { vm.clearSkippedUpdates() }, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.action_clear)) }
                    },
                ) { Text(stringResource(R.string.upd_skipped)) }
            }
            Text(
                stringResource(R.string.upd_privacy),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )
        }
    }
}

/**
 * Where updates stand, coloured by it: green when current, the primary colour when a
 * release is waiting, red when GitHub could not be reached.
 */
@Composable
private fun StatusCard(
    state: UpdateState,
    version: String,
    lastCheck: Long,
    includeBeta: Boolean,
    onCheck: () -> Unit,
    onUpdate: (Updates.Release) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val (container, content) = when (state) {
        is UpdateState.Available -> scheme.primaryContainer to scheme.onPrimaryContainer
        is UpdateState.UpToDate -> successContainer() to onSuccessContainer()
        UpdateState.Unreachable -> scheme.errorContainer to scheme.onErrorContainer
        else -> scheme.surfaceContainerHigh to scheme.onSurfaceVariant
    }
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = container, contentColor = content),
    ) {
        Row(
            Modifier.padding(20.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) {
                when (state) {
                    UpdateState.Checking -> FlatWheel(Modifier.size(28.dp), color = content)
                    is UpdateState.Available -> StatusIcon(R.drawable.ic_sym_new_releases, content)
                    is UpdateState.UpToDate -> StatusIcon(R.drawable.ic_sym_check_circle, content)
                    UpdateState.Unreachable -> StatusIcon(R.drawable.ic_sym_error, content)
                    UpdateState.Unknown -> StatusIcon(R.drawable.ic_sym_system_update, content)
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    if (state is UpdateState.Available) {
                        stringResource(R.string.upd_version_arrow, version, state.release.tag)
                    } else {
                        stringResource(R.string.upd_app_version, version)
                    },
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    stringResource(if (includeBeta) R.string.upd_channel_beta else R.string.upd_channel_stable),
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    when (state) {
                        UpdateState.Checking -> stringResource(R.string.upd_status_checking)
                        is UpdateState.Available -> stringResource(R.string.upd_status_available)
                        UpdateState.Unreachable -> stringResource(R.string.upd_status_unreachable)
                        is UpdateState.UpToDate -> if (lastCheck > 0L) {
                            stringResource(R.string.upd_status_latest) + " · " +
                                stringResource(R.string.upd_checked_ago, relativeTime(lastCheck))
                        } else {
                            stringResource(R.string.upd_status_latest)
                        }
                        UpdateState.Unknown -> stringResource(R.string.upd_status_unknown)
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 16.dp)) {
            Spacer(Modifier.weight(1f))
            Button(
                onClick = { if (state is UpdateState.Available) onUpdate(state.release) else onCheck() },
                enabled = state != UpdateState.Checking,
                shapes = ButtonDefaults.shapes(),
            ) {
                Text(
                    stringResource(
                        when (state) {
                            is UpdateState.Available -> R.string.upd_update
                            UpdateState.Unreachable -> R.string.upd_try_again
                            else -> R.string.upd_check_now
                        }
                    )
                )
            }
        }
    }
}

@Composable
private fun StatusIcon(res: Int, tint: Color) {
    Icon(painterResource(res), contentDescription = null, tint = tint, modifier = Modifier.size(28.dp))
}

/**
 * Every release, newest first, each with its date, its notes rendered as written and
 * the APK it shipped: the changelog ytdlnis keeps under its update settings.
 */
@Composable
fun ChangelogScreen(vm: MainViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val installed = appVersion(context)
    val releases by vm.releases.collectAsState()
    val failed by vm.releasesFailed.collectAsState()
    val listState = rememberLazyListState()
    LaunchedEffect(Unit) { if (vm.releases.value == null) vm.loadReleases() }

    fun open(url: String) {
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.upd_changelog)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        val list = releases
        when {
            list == null && failed -> Column(
                Modifier.padding(padding).fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(stringResource(R.string.upd_changelog_failed), style = MaterialTheme.typography.bodyLarge)
                Button(onClick = { vm.loadReleases() }, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.upd_try_again)) }
            }
            list == null -> Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                FlatWheel()
            }
            // Material's own scrollbar: every release's notes make a long page, and the
            // thumb says how far down it you are. It starts under the top bar, so the
            // list does too.
            else -> LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = padding.calculateTopPadding())
                    .then(listState.scrollIndicatorState?.let { Modifier.nonInteractiveScrollbar(it, Orientation.Vertical) } ?: Modifier),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(list, key = { it.tag + it.publishedAt }) { release ->
                    ReleaseCard(
                        release = release,
                        installed = Updates.sameVersion(release.tag, installed),
                        onOpen = ::open,
                    )
                }
            }
        }
    }
}

@Composable
private fun ReleaseCard(release: Updates.Release, installed: Boolean, onOpen: (String) -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(release.tag, style = MaterialTheme.typography.titleMedium)
                if (release.prerelease) {
                    Text(
                        stringResource(R.string.upd_beta_badge),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
                if (installed) {
                    Text(
                        stringResource(R.string.upd_installed_badge),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Spacer(Modifier.weight(1f))
                if (release.publishedAt > 0L) {
                    Text(
                        Dates.pattern("d MMM uuuu").format(
                            Instant.ofEpochMilli(release.publishedAt).atZone(ZoneId.systemDefault())
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (release.notes.isNotBlank()) ReleaseNotes(release.notes)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                release.apk?.let { apk ->
                    AssistChip(
                        onClick = { onOpen(apk.url) },
                        label = { Text(listOfNotNull(apk.name, apk.bytes.takeIf { it > 0 }?.let(::megabytes)).joinToString(" · ")) },
                        leadingIcon = {
                            Icon(painterResource(R.drawable.ic_sym_download), contentDescription = null, modifier = Modifier.size(18.dp))
                        },
                    )
                }
                if (release.htmlUrl.isNotBlank()) {
                    AssistChip(
                        onClick = { onOpen(release.htmlUrl) },
                        label = { Text(stringResource(R.string.upd_view_github)) },
                        leadingIcon = {
                            Icon(painterResource(R.drawable.ic_sym_open_in_new), contentDescription = null, modifier = Modifier.size(18.dp))
                        },
                    )
                }
            }
        }
    }
}
