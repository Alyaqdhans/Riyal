package com.alyaqdhan.riyal.data

import com.alyaqdhan.riyal.R
import com.alyaqdhan.riyal.core.Verbose
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.ensureActive
import org.json.JSONArray
import org.json.JSONObject

/**
 * Whether a published release is newer than what is installed, and which one to offer.
 *
 * Split from the network calls on purpose: the comparisons are where the bugs live and
 * they are pure Kotlin, so they are decided by [VersionTest] and [UpdatePickTest] rather
 * than by a phone with a release published against it.
 */
object Updates {

    /** Where releases are published. Public, so the API needs no token. */
    const val OWNER = "Alyaqdhans"
    const val REPO = "Riyal"

    /** GitHub rejects an API request that does not identify itself. */
    const val USER_AGENT = "Riyal-Android"

    data class Asset(val name: String, val url: String, val bytes: Long)

    data class Release(
        val tag: String,
        /** The notes, as GitHub stores them: markdown. */
        val notes: String,
        val name: String = tag,
        /** When it was published, or 0 when GitHub did not say. */
        val publishedAt: Long = 0L,
        /** The release's page on GitHub. */
        val htmlUrl: String = "",
        /** A beta: offered only to someone who asked for betas. */
        val prerelease: Boolean = false,
        val assets: List<Asset> = emptyList(),
    ) {
        /**
         * The file to install. A release could in principle carry a debug build beside
         * the real one, and the real one is the only one that can update the installed
         * app, so it wins.
         */
        val apk: Asset?
            get() = assets.filter { it.name.endsWith(".apk", ignoreCase = true) }.let { apks ->
                apks.firstOrNull { !it.name.contains("debug", ignoreCase = true) } ?: apks.firstOrNull()
            }

        val hasApk: Boolean get() = apk != null
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
        return compare(a, b) > 0
    }

    private fun compare(a: List<Int>, b: List<Int>): Int {
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x.compareTo(y)
        }
        return 0
    }

    /** One version written two ways - "V1.7.1" and "v1.7.1" - is one version. */
    fun sameVersion(a: String, b: String): Boolean {
        val x = parse(a) ?: return false
        val y = parse(b) ?: return false
        return compare(x, y) == 0
    }

    /**
     * The newest release on the chosen channel, by version number rather than by the
     * order GitHub lists them in. Betas count only when [includeBeta].
     */
    fun newest(releases: List<Release>, includeBeta: Boolean): Release? =
        releases
            .filter { includeBeta || !it.prerelease }
            .mapNotNull { r -> parse(r.tag)?.let { it to r } }
            .maxWithOrNull { x, y -> compare(x.first, y.first) }
            ?.second

    /**
     * The release to offer, or null: the newest one on the channel, when it is newer than
     * [current] and the user has not said to skip it.
     *
     * A skipped version stays skipped only for itself. When a later one is published it
     * is offered as usual - "not this one" is not "never again".
     */
    fun pick(
        releases: List<Release>,
        current: String,
        includeBeta: Boolean,
        skipped: Set<String>,
    ): Release? {
        val candidate = newest(releases, includeBeta) ?: return null
        if (!isNewer(candidate.tag, current)) return null
        if (skipped.any { sameVersion(it, candidate.tag) }) return null
        return candidate
    }
}

/**
 * The whole of the app's network code, deliberately in one file so the manifest's claim
 * about it can be checked by reading rather than believed.
 *
 * Two requests, both GETs to public GitHub addresses with nothing of the user's in the
 * URL, the headers or a body, because there is no body: the list of releases, and - only
 * when the user taps Update - the APK of the one they chose. The User-Agent names the
 * app, not the phone or the person holding it, because GitHub refuses a request that
 * does not identify itself.
 */
object UpdateApi {

    private const val TIMEOUT_MS = 10_000

    /**
     * Every published release, newest first as GitHub lists them, or null for every way
     * this can fail: no network, a rate limit, a body that is not what was expected. A
     * failed check is not news, so each of those is a line in the log and nothing else.
     */
    fun releases(): List<Updates.Release>? {
        val url = "https://api.github.com/repos/${Updates.OWNER}/${Updates.REPO}/releases?per_page=30"
        Verbose.scan(R.string.log_asking_github, Updates.OWNER, Updates.REPO)
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
                    when (code) {
                        404 -> R.string.log_github_404
                        403, 429 -> R.string.log_github_rate_limited
                        else -> R.string.log_github_other_code
                    },
                    code,
                )
                return null
            }
            val array = JSONArray(connection.inputStream.bufferedReader().use { it.readText() })
            val releases = (0 until array.length())
                .mapNotNull { i -> array.optJSONObject(i)?.let(::releaseOf) }
                // A draft is not published; GitHub only lists them to the owner anyway.
                .filter { it.tag.isNotBlank() }
            if (releases.isEmpty()) {
                Verbose.fail(R.string.log_github_no_tag)
                return null
            }
            Verbose.ok(R.string.log_github_releases, releases.size, releases.first().tag)
            releases
        } catch (e: Exception) {
            // Offline, DNS, a timeout, a body that would not parse. All the same thing
            // to the user: no answer this time, ask again next time.
            Verbose.info(R.string.log_github_unreachable, e.javaClass.simpleName)
            null
        } finally {
            connection.disconnect()
        }
    }

    private fun releaseOf(o: JSONObject): Updates.Release? {
        if (o.optBoolean("draft", false)) return null
        val tag = o.optString("tag_name").trim()
        val assets = o.optJSONArray("assets")?.let { a ->
            (0 until a.length()).mapNotNull { i ->
                val asset = a.optJSONObject(i) ?: return@mapNotNull null
                val link = asset.optString("browser_download_url").takeIf { it.isNotBlank() }
                    ?: return@mapNotNull null
                Updates.Asset(asset.optString("name"), link, asset.optLong("size"))
            }
        }.orEmpty()
        return Updates.Release(
            tag = tag,
            notes = o.optString("body").trim(),
            name = o.optString("name").trim().ifBlank { tag },
            publishedAt = runCatching { Instant.parse(o.optString("published_at")).toEpochMilli() }
                .getOrDefault(0L),
            htmlUrl = o.optString("html_url"),
            prerelease = o.optBoolean("prerelease", false),
            assets = assets,
        )
    }

    /**
     * Downloads [asset] into [dest], reporting progress as a fraction once the size is
     * known. Cancelling the calling coroutine stops it between two reads and removes the
     * half-written file, so a cancelled download never sits waiting to be installed.
     */
    suspend fun download(asset: Updates.Asset, dest: File, onProgress: (Float) -> Unit): Boolean {
        Verbose.info(R.string.log_download_started, asset.name)
        val partial = File(dest.parentFile, dest.name + ".part")
        val connection = (URL(asset.url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS * 3
            // GitHub answers with a redirect to its file storage, on the same scheme.
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", Updates.USER_AGENT)
        }
        try {
            if (connection.responseCode != 200) {
                Verbose.fail(R.string.log_download_failed, "HTTP ${connection.responseCode}")
                return false
            }
            val total = connection.contentLengthLong.takeIf { it > 0 } ?: asset.bytes
            var done = 0L
            connection.inputStream.use { input ->
                partial.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        coroutineContext.ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        done += read
                        if (total > 0) onProgress((done.toFloat() / total).coerceIn(0f, 1f))
                    }
                }
            }
            if (dest.exists()) dest.delete()
            if (!partial.renameTo(dest)) {
                Verbose.fail(R.string.log_download_failed, "rename")
                return false
            }
            Verbose.ok(R.string.log_download_done, asset.name)
            return true
        } catch (e: Exception) {
            Verbose.fail(R.string.log_download_failed, e.javaClass.simpleName)
            if (e is kotlinx.coroutines.CancellationException) throw e
            return false
        } finally {
            connection.disconnect()
            if (partial.exists()) partial.delete()
            Verbose.flush()
        }
    }
}
