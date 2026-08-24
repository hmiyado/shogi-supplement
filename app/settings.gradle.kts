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
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "shogi-supplement"
include(":kifu")
include(":analysis")
include(":application")
include(":contracts")
include(":engine:subprocess")
include(":shared")
include(":ui")
include(":androidApp")
include(":server:worker")
include(":webApp")
