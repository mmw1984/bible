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

dependencyResolutionManagement {
    // Modules must never declare their own repositories; the catalog is the single source.
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "bible-native"

// Dependency direction: app -> feature/* -> core/*. Features never depend on each other.
include(":app")

include(":core:common")
include(":core:database")
include(":core:datastore")
include(":core:design-system")
include(":core:legacy-migration")
include(":core:model")
include(":core:navigation")
include(":core:network")

include(":feature:aichat")
include(":feature:devotion")
include(":feature:library")
include(":feature:reader")
include(":feature:search")
include(":feature:settings")
