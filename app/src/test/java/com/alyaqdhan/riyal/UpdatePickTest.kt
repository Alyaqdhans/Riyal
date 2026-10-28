package com.alyaqdhan.riyal

import com.alyaqdhan.riyal.data.Updates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdatePickTest {

    private fun release(tag: String, beta: Boolean = false, vararg apks: String) = Updates.Release(
        tag = tag,
        notes = "",
        prerelease = beta,
        assets = apks.map { Updates.Asset(it, "https://example.invalid/$it", 33_000_000L) },
    )

    // The real list, as GitHub returned it on the day this was written: tags in two cases.
    private val published = listOf(
        release("V1.7.1", apks = arrayOf("Riyal-v1.7.1.apk")),
        release("v1.7.0", apks = arrayOf("Riyal-v1.7.0.apk")),
        release("v1.6.1", apks = arrayOf("riyal-v1.6.1.apk")),
        release("V1.6.0", apks = arrayOf("riyal-v1.6.0.apk")),
    )

    @Test
    fun `nothing is offered to someone already on the newest`() {
        assertNull(Updates.pick(published, "1.7.1", includeBeta = false, skipped = emptySet()))
    }

    @Test
    fun `an older install is offered the newest, whatever case its tag is in`() {
        assertEquals("V1.7.1", Updates.pick(published, "1.6.1", includeBeta = false, skipped = emptySet())?.tag)
    }

    @Test
    fun `the newest is decided by version, not by the order github lists them in`() {
        val shuffled = published.reversed()
        assertEquals("V1.7.1", Updates.newest(shuffled, includeBeta = false)?.tag)
    }

    @Test
    fun `a beta is only offered to someone who asked for betas`() {
        val withBeta = listOf(release("v1.8.0-beta1", beta = true, "Riyal-v1.8.0-beta1.apk")) + published
        assertNull(Updates.pick(withBeta, "1.7.1", includeBeta = false, skipped = emptySet()))
        assertEquals(
            "v1.8.0-beta1",
            Updates.pick(withBeta, "1.7.1", includeBeta = true, skipped = emptySet())?.tag,
        )
    }

    @Test
    fun `a skipped version is not offered, written either way`() {
        assertNull(Updates.pick(published, "1.7.0", includeBeta = false, skipped = setOf("v1.7.1")))
    }

    @Test
    fun `skipping one version does not skip the next`() {
        val later = listOf(release("v1.7.2", apks = arrayOf("Riyal-v1.7.2.apk"))) + published
        assertEquals(
            "v1.7.2",
            Updates.pick(later, "1.7.0", includeBeta = false, skipped = setOf("V1.7.1"))?.tag,
        )
    }

    @Test
    fun `the debug build is reported as the version it is`() {
        assertNull(Updates.pick(published, "1.7.1-debug", includeBeta = false, skipped = emptySet()))
    }

    @Test
    fun `the release apk is chosen over a debug one`() {
        val r = release("v1.8.0", false, "Riyal-v1.8.0-debug.apk", "Riyal-v1.8.0.apk")
        assertEquals("Riyal-v1.8.0.apk", r.apk?.name)
    }

    @Test
    fun `a release with no apk says so`() {
        assertFalse(release("v1.8.0").hasApk)
        assertTrue(release("v1.8.0", false, "Riyal-v1.8.0.apk").hasApk)
    }

    @Test
    fun `one version written two ways is one version`() {
        assertTrue(Updates.sameVersion("V1.7.1", "v1.7.1"))
        assertFalse(Updates.sameVersion("v1.7.1", "v1.7.10"))
    }
}
