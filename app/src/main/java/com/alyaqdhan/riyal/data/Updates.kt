package com.alyaqdhan.riyal.data

import com.alyaqdhan.riyal.core.Verbose
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject

/**
 * Whether a published release is newer than what is installed, and what it contains.
 *
 * Split from the network call on purpose: the comparison is where the bugs live and it
 * is pure Kotlin, so it is decided by [VersionTest] rather than by a phone with a
 * release published against it.
 */
object Updates {

    /** Where releases are published. Public, so the API needs no token. */
    const val OWNER = "Alyaqdhans"
    const val REPO = "Riyal"

    /** GitHub rejects an API request that does not identify itself. */
    const val USER_AGENT = "Riyal-Android"

    data class Release(
        val tag: String,
        /** The release notes, shown as-is. */
        val notes: String,
        /** Direct link to the .apk asset, or null when the release carries none. */
        val apkUrl: String?,
        val apkName: String?,
        val apkBytes: Long,
    ) {
        val hasApk: Boolean get() = apkUrl != null
    }

    /**
     * A tag as the numbers it is made of: "v1.51" is [1, 51].
     *
     * A leading v goes, and so does anything after a dash, which is how the debug build
     * carries "1.51-debug" through the same comparison as everything else. Each segment
     * contributes its leading digits, so a tag nobody expected does not throw.
     *
     * Returns null when there is no number in it at all. That is not zero: a tag that
     * cannot be read must not compare as older or newer than anything, it must be
     * ignored, and null is the only answer that cannot be mistaken for a version.
     */
    fun parse(tag: String): List<Int>? {
        val cleaned = tag.trim().removePrefix("v").removePrefix("V")
            .substringBefore('-')
            .substringBefore('+')
        val parts = cleaned.split('.')
            .map { part -> part.takeWhile(Char::isDigit) }
        if (parts.isEmpty() || parts.all { it.isEmpty() }) return null
        return parts.map { it.toIntOrNull() ?: 0 }
    }

    /**
     * Is [latest] a version after [current]?
     *
     * Pairwise on the numbers, missing places counting as zero, which is the only
     * reading that gets 1.10 right: as text "1.10" sorts before "1.9", and as a decimal
     * 1.10 is less than 1.9. Both are wrong about which one came second.
     *
     * A tag that cannot be read is not an update. Neither is an equal version, and
     * neither is going backwards - a release deleted on GitHub should not offer to
     * install the one before it.
     */
    fun isNewer(latest: String, current: String): Boolean {
        val a = parse(latest) ?: return false
        val b = parse(current) ?: return false
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }
}

/**
 * The whole of the app's network code, deliberately in one file so the manifest's claim
 * about it can be checked by reading rather than believed.
 *
 * One request, one direction: a GET to a public endpoint, with nothing of the user's in
 * the URL, the headers or a body, because there is no body. It sends the app's name as
 * its User-Agent because GitHub refuses a request that does not identify itself, and
 * that string names the app, not the phone or the person holding it.
 */
object UpdateApi {

    private const val TIMEOUT_MS = 10_000

    /**
     * The latest published release, or null for every way this can fail: no network, a
     * repository with no releases yet, a rate limit, a body that is not what was
     * expected. A failed update check is not news, so each of those is a line in the
     * log and nothing else - see [com.alyaqdhan.riyal.ui.MainViewModel.checkForUpdate].
     */
    fun latestRelease(): Updates.Release? {
        val url = "https://api.github.com/repos/${Updates.OWNER}/${Updates.REPO}/releases/latest"
        Verbose.scan("asking GitHub for the latest release of ${Updates.OWNER}/${Updates.REPO}")
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            setRequestProperty("User-Agent", Updates.USER_AGENT)
            setRequestProperty("Accept", "application/vnd.github+json")
        }
        return try {
            val code = connection.responseCode
            if (code != 200) {
                Verbose.info(
                    "GitHub answered $code" + when (code) {
                        404 -> ": no release has been published yet, nothing to update to"
                        403, 429 -> ": too many requests from this network, it will ask again tomorrow"
                        else -> ", so there is nothing to compare against this time"
                    }
                )
                return null
            }
            val root = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            val tag = root.optString("tag_name").takeIf { it.isNotBlank() } ?: run {
                Verbose.fail("GitHub's answer carried no tag name, ignoring it")
                return null
            }
            val apk = root.optJSONArray("assets").let { assets ->
                (0 until (assets?.length() ?: 0))
                    .map { assets!!.getJSONObject(it) }
                    .firstOrNull { it.optString("name").endsWith(".apk", ignoreCase = true) }
            }
            Updates.Release(
                tag = tag,
                notes = root.optString("body").trim(),
                apkUrl = apk?.optString("browser_download_url")?.takeIf { it.isNotBlank() },
                apkName = apk?.optString("name"),
                apkBytes = apk?.optLong("size") ?: 0L,
            ).also {
                Verbose.ok(
                    "GitHub's latest release is ${it.tag}" +
                        if (it.hasApk) ", carrying ${it.apkName}" else ", with no APK attached"
                )
            }
        } catch (e: Exception) {
            // Offline, DNS, a timeout, a body that would not parse. All the same thing
            // to the user: no answer this time, ask again tomorrow.
            Verbose.info("could not reach GitHub (${e.javaClass.simpleName}), will try again later")
            null
        } finally {
            connection.disconnect()
        }
    }
}
