package com.alyaqdhan.riyal.data

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
