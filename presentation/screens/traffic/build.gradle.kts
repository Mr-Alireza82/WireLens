// The traffic screen, independent of any GUI toolkit: its layout spec per size class and its remembered settings.
plugins {
    id("wirelens.java-conventions")
}

dependencies {
    api(project(":presentation:layout"))
    api(project(":contexts:inspection"))
}
