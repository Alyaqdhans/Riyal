package com.alyaqdhan.riyal.core

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import java.util.Locale

object Language {

    enum class Choice(val tag: String?) {
        SYSTEM(null),
        ENGLISH("en"),
        ARABIC("ar"),
    }

    fun current(): Choice {
        val locales = AppCompatDelegate.getApplicationLocales()
        if (locales.isEmpty) return Choice.SYSTEM
        return when (locales[0]?.language) {
            "ar" -> Choice.ARABIC
            "en" -> Choice.ENGLISH
            else -> Choice.SYSTEM
        }
    }

    fun set(choice: Choice) {
        AppCompatDelegate.setApplicationLocales(
            choice.tag?.let { LocaleListCompat.forLanguageTags(it) }
                ?: LocaleListCompat.getEmptyLocaleList()
        )
    }

    fun latinDigits(context: Context): Context {
        val locales = context.resources.configuration.locales
        if (locales.isEmpty) return context
        val pinned = ArrayList<Locale>(locales.size())
        var changed = false
        for (i in 0 until locales.size()) {
            val locale = locales[i]
            if (locale.language == "ar" && locale.getUnicodeLocaleType("nu") == null) {
                pinned += Locale.Builder().setLocale(locale)
                    .setUnicodeLocaleKeyword("nu", "latn").build()
                changed = true
            } else {
                pinned += locale
            }
        }
        if (!changed) return context
        val config = Configuration(context.resources.configuration)
        config.setLocales(LocaleList(*pinned.toTypedArray()))
        return context.createConfigurationContext(config)
    }
}
