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

rootProject.name = "OpenCab"

include(
    ":app",
    ":core:designsystem",
    ":core:model",
    ":core:protocol",
    ":core:data",
    ":feature:connection",
    ":feature:throttle",
    ":feature:roster",
    ":feature:settings",
)
