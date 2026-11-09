package com.alyaqdhan.riyal

import com.alyaqdhan.riyal.data.Updates
import org.junit.Assert.assertEquals
import org.junit.Test

/** A release body carries English first and each other language after `<!-- lang:xx -->`. */
class ReleaseNotesLanguageTest {

    private val both = Updates.Release(
        tag = "V1.0.1",
        notes = "# What's Changed\n- A fix\n\n<!-- lang:ar -->\n# ما الجديد\n- إصلاح",
    )

    @Test
    fun `the app in Arabic shows the Arabic part`() {
        assertEquals("# ما الجديد\n- إصلاح", both.notesIn("ar"))
    }

    @Test
    fun `the app in English shows the English part, without the marker`() {
        assertEquals("# What's Changed\n- A fix", both.notesIn("en"))
    }

    @Test
    fun `a language the release was not written in shows English`() {
        assertEquals("# What's Changed\n- A fix", both.notesIn("fr"))
    }

    @Test
    fun `a release written only in English shows it in every language`() {
        val english = Updates.Release(tag = "V1.0.0", notes = "# What's Changed\n- A fix")
        assertEquals(english.notes, english.notesIn("ar"))
    }

    @Test
    fun `an empty Arabic part falls back to English`() {
        val empty = Updates.Release(tag = "V1.0.2", notes = "# What's Changed\n- A fix\n<!-- lang:ar -->\n  ")
        assertEquals("# What's Changed\n- A fix", empty.notesIn("ar"))
    }

    @Test
    fun `parts in several languages each end where the next begins`() {
        val three = Updates.Release(
            tag = "V1.0.3",
            notes = "English\n<!-- lang:ar -->\nعربي\n<!--lang: fr-->\nFrançais",
        )
        assertEquals("عربي", three.notesIn("ar"))
        assertEquals("Français", three.notesIn("fr"))
    }
}
