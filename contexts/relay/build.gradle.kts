// Bounded context "relay": proxy, connections and hold mode (domain + application, ports in and out).
plugins {
    id("wirelens.java-conventions")
}

dependencies {
    api(project(":shared:kernel"))
}
