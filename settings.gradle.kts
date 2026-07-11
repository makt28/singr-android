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
        // The SingR core ships as a local gomobile AAR (libbox) under app/libs,
        // fetched from a SingR release by CI / dev setup (not committed).
        flatDir { dirs("app/libs") }
    }
}

rootProject.name = "singr-android"
include(":app")
