package com.alyaqdhan.riyal.ui.compose

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.alyaqdhan.riyal.R
import com.alyaqdhan.riyal.data.Updates
import com.alyaqdhan.riyal.ui.MainViewModel
import com.alyaqdhan.riyal.ui.MainViewModel.DownloadState
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownColor
import com.mikepenz.markdown.m3.markdownTypography
import com.mikepenz.markdown.model.markdownAnimations
import java.util.Locale
import kotlin.math.roundToInt

/**
 * A release body as GitHub stores it - markdown - set in the app's own type scale.
 * Headings are sized for a dialog, not a document, and nothing animates in.
 */
@Composable
fun ReleaseNotes(markdown: String, modifier: Modifier = Modifier) {
    val type = MaterialTheme.typography
    Markdown(
        content = markdown,
        colors = markdownColor(),
        typography = markdownTypography(
            h1 = type.titleMedium,
            h2 = type.titleSmall,
            h3 = type.titleSmall,
            h4 = type.labelLarge,
            h5 = type.labelLarge,
            h6 = type.labelLarge,
            text = type.bodyMedium,
            paragraph = type.bodyMedium,
            ordered = type.bodyMedium,
            bullet = type.bodyMedium,
            list = type.bodyMedium,
        ),
        modifier = modifier,
        animations = markdownAnimations { this },
    )
}

/** "33.2 MB", for saying how large a download is before it starts. */
fun megabytes(bytes: Long): String = String.format(Locale.ROOT, "%.1f MB", bytes / 1_048_576.0)

/**
 * The new-release dialog, after ytdlnis's: the notes, then Update. The button becomes
 * the download's progress, then Install, which hands the file to Android's installer.
 * "Skip this version" means that version only; "Later" leaves it waiting in Settings.
 *
 * Hosted once by the activity, so it opens over whichever screen is showing.
 */
@Composable
fun UpdateDialogHost(vm: MainViewModel) {
    val release = vm.updatePrompt.collectAsState().value ?: return
    val download by vm.download.collectAsState()
    val context = LocalContext.current
    val installed = appVersion(context)
    // Only a download of this release belongs in this dialog.
    val state = download.takeIf {
        when (it) {
            is DownloadState.Downloading -> Updates.sameVersion(it.tag, release.tag)
            is DownloadState.Ready -> Updates.sameVersion(it.tag, release.tag)
            is DownloadState.Failed -> Updates.sameVersion(it.tag, release.tag)
            DownloadState.Idle -> false
        }
    }
    var needsPermission by remember { mutableStateOf(false) }

    fun openOnGitHub() {
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(release.htmlUrl)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    AlertDialog(
        onDismissRequest = { if (state !is DownloadState.Downloading) vm.dismissUpdatePrompt() },
        icon = { Icon(painterResource(R.drawable.ic_sym_system_update), contentDescription = null) },
        title = { Text(stringResource(R.string.upd_dialog_title, release.tag)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    listOfNotNull(
                        stringResource(R.string.upd_you_have, installed),
                        release.apk?.bytes?.takeIf { it > 0 }?.let(::megabytes),
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Column(
                    Modifier
                        .heightIn(max = 300.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    ReleaseNotes(release.notes.ifBlank { stringResource(R.string.upd_no_notes) })
                }
                when (state) {
                    is DownloadState.Downloading -> {
                        FlatBar(progress = { state.fraction }, modifier = Modifier.fillMaxWidth())
                        Text(
                            stringResource(R.string.upd_downloading, (state.fraction * 100).roundToInt()),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    is DownloadState.Failed -> Text(
                        stringResource(R.string.upd_download_failed),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    is DownloadState.Ready -> if (needsPermission) {
                        Text(
                            stringResource(R.string.upd_allow_installs),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    else -> if (!release.hasApk) {
                        Text(
                            stringResource(R.string.upd_no_apk),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
        confirmButton = {
            when (state) {
                is DownloadState.Downloading -> TextButton(onClick = {}, enabled = false) {
                    Text(stringResource(R.string.upd_percent, (state.fraction * 100).roundToInt()))
                }
                is DownloadState.Ready -> TextButton(onClick = {
                    when (vm.install(state.file)) {
                        MainViewModel.InstallResult.NEEDS_PERMISSION -> {
                            needsPermission = true
                            vm.openInstallPermission()
                        }
                        MainViewModel.InstallResult.STARTED -> vm.dismissUpdatePrompt()
                        MainViewModel.InstallResult.FAILED -> openOnGitHub()
                    }
                }) { Text(stringResource(R.string.upd_install)) }
                is DownloadState.Failed -> TextButton(onClick = { vm.startDownload(release) }) {
                    Text(stringResource(R.string.upd_retry))
                }
                else -> if (release.hasApk) {
                    TextButton(onClick = { vm.startDownload(release) }) { Text(stringResource(R.string.upd_update)) }
                } else {
                    TextButton(onClick = { openOnGitHub() }) { Text(stringResource(R.string.upd_open_github)) }
                }
            }
        },
        dismissButton = {
            Row {
                if (state is DownloadState.Downloading) {
                    TextButton(onClick = { vm.cancelDownload() }) { Text(stringResource(R.string.action_cancel)) }
                } else {
                    TextButton(onClick = { vm.skipUpdate(release) }) { Text(stringResource(R.string.upd_skip)) }
                    TextButton(onClick = { vm.dismissUpdatePrompt() }) { Text(stringResource(R.string.upd_later)) }
                }
            }
        },
    )
}
