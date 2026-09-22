package com.alyaqdhan.riyal.core

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

/**
 * Which language the app speaks.
 *
 * The choice is kept by the platform rather than by [Prefs], which is the one thing to
 * understand here: on Android 13 and up it is a real per-app setting, so it also appears
 * in the phone's own Settings > Apps > Riyal > Language and can be changed from there
 * without Riyal knowing. Storing a second copy in our own preferences would give two
 * answers that drift apart, so this reads the platform's answer every time instead.
 *
 * Below 13 appcompat backports the same thing into its own storage and re-applies it at
 * startup, which is why the manifest declares AppLocalesMetadataHolderService with
 * autoStoreLocales. Both halves look identical from here.
 *
 * Nothing about parsing changes with this. The app reads Arabic bank messages whatever
 * it is set to, because the keywords the parser matches are the bank's words, not the
 * interface's - see SmsParser.
 */
object Language {

    /** A language the app is actually translated into, plus following the phone. */
    enum class Choice(val tag: String?) {
        /** Whatever the phone is set to, falling back to English. */
        SYSTEM(null),
        ENGLISH("en"),
        ARABIC("ar"),
    }

    /**
     * What is set right now.
     *
     * An empty locale list means nothing has been chosen and the phone decides. A list
     * whose first entry is neither of ours can happen if the platform picker offers
     * something unexpected; that reads as SYSTEM, because it is not a language this app
     * has words in and English is what it will actually render.
     */
    fun current(): Choice {
        val locales = AppCompatDelegate.getApplicationLocales()
        if (locales.isEmpty) return Choice.SYSTEM
        return when (locales[0]?.language) {
            "ar" -> Choice.ARABIC
            "en" -> Choice.ENGLISH
            else -> Choice.SYSTEM
        }
    }

    /**
     * Switches language.
     *
     * The activity is recreated by the framework as a result, which is how every string
     * on screen is re-read. That recreation is also why this is the whole of the work:
     * nothing has to be told to refresh, and no state in the ViewModel is touched,
     * because the ViewModel outlives the activity.
     */
    fun set(choice: Choice) {
        AppCompatDelegate.setApplicationLocales(
            choice.tag?.let { LocaleListCompat.forLanguageTags(it) }
                ?: LocaleListCompat.getEmptyLocaleList()
        )
    }
}
