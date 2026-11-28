// The Windows app: Main.java, Windows-only bits and packaging. Everything is wired by di/dagger.
//   .\gradlew.bat :apps:windows:run                    start WireLens
//   .\gradlew.bat :apps:windows:run --args="--demo"    start with simulated payment traffic
plugins {
    id("wirelens.java-conventions")
    application
    alias(libs.plugins.javafx)
}

javafx {
    version = libs.versions.javafx.get()
    modules("javafx.controls")
}

dependencies {
    implementation(project(":di:dagger"))
    implementation(project(":adapters:gui:javafx"))
}

application {
    mainClass = "io.github.mralireza82.wirelens.app.windows.Main"
    applicationName = "wirelens"
    // JavaFX and the SQLite driver load native libraries; allowing it up front avoids JDK 24+ warnings.
    applicationDefaultJvmArgs = listOf("-Xmx1g", "--enable-native-access=ALL-UNNAMED,javafx.graphics")
}

tasks.named<JavaExec>("run") {
    systemProperties(System.getProperties().filterKeys { (it as String).startsWith("wirelens.") }
        .mapKeys { it.key as String })
}
