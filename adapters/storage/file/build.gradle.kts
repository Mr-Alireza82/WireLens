// Driven adapter: file formats. JSON Lines captures (export + import), text reports and raw .bin payloads.
plugins {
    id("wirelens.java-conventions")
}

dependencies {
    implementation(project(":contexts:inspection"))
    implementation(libs.base.conversion)
}
