// Driving adapter: the JavaFX user interface (MVVM) for Windows and Linux.
plugins {
    id("wirelens.java-conventions")
    alias(libs.plugins.javafx)
}

javafx {
    version = libs.versions.javafx.get()
    modules("javafx.controls")
}

dependencies {
    api(project(":presentation:screens:traffic"))
    implementation(project(":shared:design-tokens"))
    implementation(libs.atlantafx)
    implementation(libs.ikonli.javafx)
    implementation(libs.ikonli.feather)
}
