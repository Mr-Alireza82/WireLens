// Driven adapter: a UDP relay. Empty for now; it will implement the same relay ports as adapters/connection/tcp.
plugins {
    id("wirelens.java-conventions")
}

dependencies {
    implementation(project(":contexts:relay"))
}
