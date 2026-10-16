pluginManagement {
    repositories {
        google()
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        // Only the artifact under test resolves from mavenLocal, so a stale local cache
        // cannot mask a publication bug.
        mavenLocal {
            content {
                includeGroup("com.github.piratecash.ledger-kit")
            }
        }
        google()
        mavenCentral()
    }
}

rootProject.name = "ledger-kit-consumer-smoke"
