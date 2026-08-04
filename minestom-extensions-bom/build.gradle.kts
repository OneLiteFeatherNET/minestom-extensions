plugins {
    `java-platform`
}

description = "Bill of Materials for the minestom-extensions modules"

// No allowDependencies() - this platform only declares constraints.
dependencies {
    constraints {
        api(project(":minestom-extensions"))
        api(project(":minestom-extensions-processor"))
    }
}
