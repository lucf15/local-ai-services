pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { google(); mavenCentral() }
}
rootProject.name = "LocalAiServices"
include(":apps:local-ai", ":apps:sample", ":libs:sdk", ":libs:runtime", ":libs:ui")
