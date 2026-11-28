import java.time.Duration

// Shared Java setup for every module: Java 21 toolchain-free release flag, JUnit 5 and AssertJ.
plugins {
    `java-library`
}

val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

group = "io.github.mralireza82.wirelens"
version = "1.0.0"

// Nested projects share last names (e.g. "file", "tcp"), so jars are named after the full path:
// :adapters:storage:file -> adapters-storage-file-1.0.0.jar.
base.archivesName = path.removePrefix(":").replace(':', '-')

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release = 21
    options.compilerArgs.addAll(listOf("-Xlint:all,-serial,-processing", "-Werror"))
}

dependencies {
    testImplementation(platform(libs.findLibrary("junit-bom").get()))
    testImplementation(libs.findLibrary("junit-jupiter").get())
    testImplementation(libs.findLibrary("assertj").get())
    testRuntimeOnly(libs.findLibrary("junit-launcher").get())
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    // A test that blocks (e.g. on a socket) fails with its name after 60 s instead of hanging the build;
    // SEPARATE_THREAD lets JUnit abandon a thread stuck in non-interruptible I/O.
    systemProperty("junit.jupiter.execution.timeout.default", "60 s")
    systemProperty("junit.jupiter.execution.timeout.thread.mode.default", "SEPARATE_THREAD")
    timeout.set(Duration.ofMinutes(10))
    testLogging {
        events("failed", "skipped")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}
