// Driven adapter: the TCP relay engine (virtual threads, blocking sockets).
plugins {
    id("wirelens.java-conventions")
}

dependencies {
    implementation(project(":application"))
}
