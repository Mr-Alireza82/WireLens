// Driven adapter: capture files (export/import) and user preferences.
plugins {
    id("wirelens.java-conventions")
}

dependencies {
    implementation(project(":application"))
    implementation(libs.base.conversion)
}
