pluginManagement {
    includeBuild("build-logic")
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
        // Only the author's base-conversion library comes from JitPack.
        exclusiveContent {
            forRepository { maven("https://jitpack.io") }
            filter { includeGroup("com.github.Mr-Alireza82") }
        }
    }
}

rootProject.name = "wirelens"

include(
    ":domain",
    ":application",
    ":adapter-tcp",
    ":adapter-storage",
    ":adapter-javafx",
    ":bootstrap",
)

// Optional: build against a local checkout of base-conversion instead of the JitPack artifact,
// e.g. ./gradlew build -PbaseConversion.localPath=../base-conversion
providers.gradleProperty("baseConversion.localPath").orNull?.let { localPath ->
    includeBuild(localPath) {
        dependencySubstitution {
            substitute(module("com.github.Mr-Alireza82:Base-Conversion")).using(project(":"))
        }
    }
}
