// Composition root: wires adapters to the application core and starts the UI.
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
    implementation(project(":application"))
    implementation(project(":adapter-tcp"))
    implementation(project(":adapter-storage"))
    implementation(project(":adapter-javafx"))
}

application {
    mainClass = "io.github.mralireza82.wirelens.bootstrap.WireLens"
    applicationName = "wirelens"
    applicationDefaultJvmArgs = listOf("-Xmx768m")
}
