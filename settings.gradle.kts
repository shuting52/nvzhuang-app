pluginManagement {
    repositories { maven { url = uri("/home/hatch/.m2-local") }; google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { maven { url = uri("/home/hatch/.m2-local") }; google(); mavenCentral() }
}
rootProject.name = "Nvzhuang"
include(":app")
