// Driven adapter: the TCP relay engine (java.net sockets; runs on Windows, Linux and Android).
plugins {
    id("wirelens.java-conventions")
}

dependencies {
    implementation(project(":contexts:relay"))
}
