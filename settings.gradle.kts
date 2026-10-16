pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

// Explicit: KMP modules embed the project name in the dex, and a floating name breaks
// P.CASH's reproducible F-Droid build.
rootProject.name = "ledger-kit"

include(":ledgerkit")
