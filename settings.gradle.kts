pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // LazyColumnScrollbar is published only through JitPack, and nothing else may come
        // from there.
        maven("https://jitpack.io") {
            content { includeGroupByRegex("com\\.github\\.nanihadesuka(\\..*)?") }
        }
    }
}

rootProject.name = "Riyal"
include(":app")
