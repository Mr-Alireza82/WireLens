// Bounded context "inspection": rendering bytes, filters, capture import/export ports and statistics.
// Downstream of relay: it reads the traffic events relay publishes.
plugins {
    id("wirelens.java-conventions")
}

dependencies {
    api(project(":contexts:relay"))
    implementation(libs.base.conversion)
}
