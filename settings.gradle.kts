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
    // shared: things every context uses that are not a feature
    ":shared:kernel",
    ":shared:design-tokens",
    // bounded contexts: the features (domain + application)
    ":contexts:relay",
    ":contexts:inspection",
    // platform-neutral presentation: breakpoints, layout rules, screen state
    ":presentation:layout",
    ":presentation:screens:traffic",
    // adapters, grouped by technology (a technology may serve several platforms)
    ":adapters:connection:tcp",
    ":adapters:connection:udp",
    ":adapters:gui:javafx",
    ":adapters:storage:file",
    ":adapters:storage:sqlite",
    // dependency injection: picks the adapters for a platform
    ":di:dagger",
    // runnable apps: one composition root per platform
    ":apps:windows",
    ":apps:linux",
    // build-time quality gates
    ":quality:architecture-tests",
)
// Android modules (adapters/gui/compose, adapters/storage/room, di/hilt, apps/android) need the Android Gradle
// plugin and are kept as documented folders until the Android app is started, so the desktop build stays green.

// Optional: build against a local checkout of base-conversion instead of the JitPack artifact,
// e.g. ./gradlew build -PbaseConversion.localPath=../base-conversion
providers.gradleProperty("baseConversion.localPath").orNull?.let { localPath ->
    includeBuild(localPath) {
        dependencySubstitution {
            substitute(module("com.github.Mr-Alireza82:Base-Conversion")).using(project(":"))
        }
    }
}
