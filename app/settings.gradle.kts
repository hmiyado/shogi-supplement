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
include(":shared")
include(":ui")
include(":androidApp")

// サーバー解析ワーカー（Cloud Run/Ktor）。androidApp/iosAppのどちらからも依存されない
// 独立モジュール。app/server/worker に配置。
include(":server:worker")
