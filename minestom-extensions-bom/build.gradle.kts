plugins {
    `java-platform`
}

description = "Bill of Materials for the minestom-extensions modules"

// No allowDependencies() - this platform only declares constraints.
dependencies {
    constraints {
        api(project(":minestom-extensions"))
        api(project(":minestom-extensions-processor"))
        // The build plugin is pinned here too, so it moves as one version with the rest.
        api(project(":minestom-extensions-gradle-plugin"))
    }
}
