// ArchUnit rules that keep the layers honest: the build fails if a dependency points the wrong way.
plugins {
    id("wirelens.java-conventions")
}

dependencies {
    testImplementation(project(":shared:kernel"))
    testImplementation(project(":shared:design-tokens"))
    testImplementation(project(":contexts:relay"))
    testImplementation(project(":contexts:inspection"))
    testImplementation(project(":presentation:layout"))
    testImplementation(project(":presentation:screens:traffic"))
    testImplementation(project(":adapters:connection:tcp"))
    testImplementation(project(":adapters:gui:javafx"))
    testImplementation(project(":adapters:storage:file"))
    testImplementation(project(":adapters:storage:sqlite"))
    testImplementation(project(":di:dagger"))
    testImplementation(project(":apps:windows"))
    testImplementation(libs.archunit)
}
