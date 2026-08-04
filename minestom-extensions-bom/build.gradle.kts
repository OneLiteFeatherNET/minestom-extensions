plugins {
    `java-platform`
}

description = "Bill of Materials for the minestom-extensions modules"

// No allowDependencies() - this platform only declares constraints.
dependencies {
    constraints {
        api(project(":minestom-extensions"))
        api(project(":minestom-extensions-processor"))
        // The build plugins are pinned here too. It is what lets a Maven user put the plugin
        // version under dependencyManagement instead of repeating it, and it keeps all four
        // artifacts moving as one version.
        api(project(":minestom-extensions-gradle-plugin"))
        api(project(":minestom-extensions-maven-plugin"))
    }
}
