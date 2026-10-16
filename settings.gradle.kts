pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradle()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "ZaiNative"
include(":app")
