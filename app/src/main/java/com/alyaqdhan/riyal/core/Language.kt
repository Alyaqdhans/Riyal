package com.alyaqdhan.riyal.core

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

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
}
