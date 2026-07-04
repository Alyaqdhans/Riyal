// Top-level build file. AGP 9 ships built-in Kotlin, so no standalone Kotlin plugins here.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
