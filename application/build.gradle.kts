// Use cases, ports and payload presentation. Depends only on the domain and the base-conversion library.
plugins {
    id("wirelens.java-conventions")
}

dependencies {
    api(project(":domain"))
    implementation(libs.base.conversion)
}
