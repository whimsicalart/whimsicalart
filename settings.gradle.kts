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

rootProject.name = "WhimsicalArt"
include(":app")
include(":core:designsystem")
include(":core:common")
include(":core:domain")
include(":core:data")
include(":feature:editor")
include(":feature:filters")
include(":feature:beauty")
include(":feature:gallery")
include(":feature:camera")
include(":feature:stickers")
include(":feature:collage")
include(":feature:settings")
