package com.alyaqdhan.riyal.ui.compose

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.Spanned
import android.text.style.ClickableSpan
import android.text.util.Linkify
import android.util.TypedValue
import android.widget.TextView
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.alyaqdhan.riyal.R
import com.alyaqdhan.riyal.data.Updates
import com.alyaqdhan.riyal.ui.MainViewModel
import com.alyaqdhan.riyal.ui.MainViewModel.DownloadState
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.Markwon
import io.noties.markwon.MarkwonConfiguration
import io.noties.markwon.linkify.LinkifyPlugin
import io.noties.markwon.movement.MovementMethodPlugin
import java.util.Locale
import kotlin.math.roundToInt

/**
 * A release body as GitHub stores it - markdown - drawn by Markwon, the renderer ytdlnis
 * draws its own with, in its own look: headings at twice, one and a half and 1.17 times
 * the text, the first two with a rule under them, a blank line between blocks, bullets set
 * in, and links in the accent colour. A bare address (the "Full Changelog" line) is a link
 * too. The text itself is the app's body size and line height.
 *
 * The TextView only draws. A tap on a link opens it, and every other touch - a drag above
 * all - goes to whatever the notes scroll in: a TextView that handled its own links would
 * keep the drags, and the list would not move under a finger on the notes.
 */
@Composable
fun ReleaseNotes(markdown: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val markwon = remember(context) { notesMarkwon(context) }
    val notes = remember(markwon, markdown) { markwon.toMarkdown(markdown) }
    val textColor = LocalContentColor.current.toArgb()
    val linkColor = MaterialTheme.colorScheme.primary.toArgb()
    val style = MaterialTheme.typography.bodyMedium
    val lineHeight = with(LocalDensity.current) { style.lineHeight.roundToPx() }
    val shown = remember { NotesView() }
    AndroidView(
        factory = { TextView(it).also { view -> shown.view = view } },
        update = { view ->
            view.includeFontPadding = false
            view.setTextSize(TypedValue.COMPLEX_UNIT_SP, style.fontSize.value)
            view.letterSpacing = style.letterSpacing.value / style.fontSize.value
            view.setLineHeight(lineHeight)
            view.setTextColor(textColor)
            view.setLinkTextColor(linkColor)
            markwon.setParsedMarkdown(view, notes)
        },
        onRelease = { shown.view = null },
        modifier = modifier
            .fillMaxWidth()
            .pointerInput(shown) {
                detectTapGestures { at -> shown.view?.let { view -> view.linkAt(at)?.onClick(view) } }
            },
    )
}

/** The notes' TextView, kept so a tap can be matched to the link under it. */
private class NotesView {
    var view: TextView? = null
}

/**
 * Markwon as ytdlnis builds it, with links opening in the browser, bare web addresses
 * turned into links, and no movement method: Markwon would otherwise give the TextView
 * one that keeps every touch.
 */
private fun notesMarkwon(context: Context): Markwon = Markwon.builder(context)
    .usePlugin(MovementMethodPlugin.none())
    .usePlugin(LinkifyPlugin.create(Linkify.WEB_URLS))
    .usePlugin(object : AbstractMarkwonPlugin() {
        override fun configureConfiguration(builder: MarkwonConfiguration.Builder) {
            builder.linkResolver { view, link ->
                runCatching {
                    view.context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse(link)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
            }
        }
    })
    .build()

/** The link under [at], a point in the view's own pixels, or null when there is none. */
private fun TextView.linkAt(at: Offset): ClickableSpan? {
    val layout = layout ?: return null
    val spanned = text as? Spanned ?: return null
    val x = at.x - totalPaddingLeft + scrollX
    val y = at.y.toInt() - totalPaddingTop + scrollY
    if (y < 0 || y > layout.height) return null
    val line = layout.getLineForVertical(y)
    if (x < layout.getLineLeft(line) || x > layout.getLineRight(line)) return null
    val offset = layout.getOffsetForHorizontal(line, x)
    return spanned.getSpans(offset, offset, ClickableSpan::class.java).firstOrNull()
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

    val pop = rememberDialogPop()
    AlertDialog(
        onDismissRequest = { if (state !is DownloadState.Downloading) pop.leave { vm.dismissUpdatePrompt() } },
        modifier = Modifier.popIn(pop),
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
                is DownloadState.Downloading -> TextButton(onClick = {}, enabled = false, shapes = ButtonDefaults.shapes()) {
                    Text(stringResource(R.string.upd_percent, (state.fraction * 100).roundToInt()))
                }
                is DownloadState.Ready -> TextButton(onClick = {
                    when (vm.install(state.file)) {
                        MainViewModel.InstallResult.NEEDS_PERMISSION -> {
                            needsPermission = true
                            vm.openInstallPermission()
                        }
                        MainViewModel.InstallResult.STARTED -> pop.leave { vm.dismissUpdatePrompt() }
                        MainViewModel.InstallResult.FAILED -> openOnGitHub()
                    }
                }, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.upd_install)) }
                is DownloadState.Failed -> TextButton(onClick = { vm.startDownload(release) }, shapes = ButtonDefaults.shapes()) {
                    Text(stringResource(R.string.upd_retry))
                }
                else -> if (release.hasApk) {
                    TextButton(onClick = { vm.startDownload(release) }, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.upd_update)) }
                } else {
                    TextButton(onClick = { openOnGitHub() }, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.upd_open_github)) }
                }
            }
        },
        dismissButton = {
            Row {
                if (state is DownloadState.Downloading) {
                    TextButton(onClick = { vm.cancelDownload() }, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.action_cancel)) }
                } else {
                    TextButton(onClick = { pop.leave { vm.skipUpdate(release) } }, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.upd_skip)) }
                    TextButton(onClick = { pop.leave { vm.dismissUpdatePrompt() } }, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.upd_later)) }
                }
            }
        },
    )
}
