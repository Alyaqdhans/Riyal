package com.alyaqdhan.riyal.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File

/**
 * Hands a downloaded release to Android's own installer.
 *
 * Nothing installs itself: Android shows its install screen and the user confirms it,
 * and Android is also what refuses an APK signed with a different key than the copy
 * already installed. The app only needs leave to *ask* - the "install unknown apps"
 * permission - which the user grants once, in system settings.
 */
object ApkInstaller {

    /** Where downloaded releases go: app-private cache, shared only through the provider. */
    fun updatesDir(context: Context): File =
        File(context.cacheDir, "updates").apply { mkdirs() }

    /** Whether the user has let this app open the installer. */
    fun canInstall(context: Context): Boolean = context.packageManager.canRequestPackageInstalls()

    /** The system page where that permission is granted, opened on this app. */
    fun openInstallPermission(context: Context) {
        context.startActivity(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + context.packageName))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    /** Opens the installer on [apk]. False when no installer could be opened. */
    fun install(context: Context, apk: File): Boolean = try {
        val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", apk)
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        true
    } catch (e: Exception) {
        false
    }

    /** Leftover downloads from earlier releases; the installed one no longer needs them. */
    fun clearOld(context: Context) {
        updatesDir(context).listFiles()?.forEach { it.delete() }
    }
}
