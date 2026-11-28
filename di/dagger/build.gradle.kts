// Dependency injection for desktop platforms (Windows, Linux): chooses the TCP relay, SQLite settings, the file
// formats and the JavaFX UI, and builds the application services. Android will use di/hilt instead.
plugins {
    id("wirelens.java-conventions")
}

dependencies {
    api(project(":presentation:screens:traffic"))
    api(project(":adapters:gui:javafx"))
    api(libs.dagger)
    annotationProcessor(libs.dagger.compiler)
    implementation(project(":adapters:connection:tcp"))
    implementation(project(":adapters:storage:file"))
    implementation(project(":adapters:storage:sqlite"))
}
