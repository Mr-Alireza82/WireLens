// Driving adapter: the JavaFX user interface (MVVM).
plugins {
    id("wirelens.java-conventions")
    alias(libs.plugins.javafx)
}

javafx {
    version = libs.versions.javafx.get()
    modules("javafx.controls")
}

dependencies {
    api(project(":application"))
    implementation(libs.atlantafx)
    implementation(libs.ikonli.javafx)
    implementation(libs.ikonli.feather)
}
