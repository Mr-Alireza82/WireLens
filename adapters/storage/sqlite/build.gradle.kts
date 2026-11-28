// Driven adapter: settings in an SQLite database (desktop). The Android equivalent will be adapters/storage/room.
plugins {
    id("wirelens.java-conventions")
}

dependencies {
    implementation(project(":presentation:screens:traffic"))
    implementation(libs.sqlite.jdbc)
}
