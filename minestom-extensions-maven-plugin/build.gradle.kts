plugins {
    `java-library`
}

description = "Maven plugin feeding build-declared dependencies into extension.json"

dependencies {
    // Shared with the processor so both sides render the descriptor identically. It carries no
    // dependencies of its own.
    implementation(project(":minestom-extensions-processor"))

    compileOnly(libs.maven.plugin.api)
    compileOnly(libs.maven.core)
    compileOnly(libs.maven.plugin.annotations)

    testImplementation(platform(libs.myclium.bom))
    testImplementation(libs.junit.api)
    testImplementation(libs.junit.platform.launcher)
    testRuntimeOnly(libs.junit.engine)
    testImplementation(libs.maven.plugin.api)
    testImplementation(libs.maven.core)
    testImplementation(libs.maven.plugin.annotations)
}

// A Maven build would generate META-INF/maven/plugin.xml with maven-plugin-plugin. The Gradle
// equivalent (de.benediktritter.maven-plugin-development) calls
// ProjectDependency.getDependencyProject(), which Gradle 9 removed, and its latest release 0.4.3
// has not been updated — so the descriptor is maintained by hand in src/main/resources instead.
// PluginDescriptorTest checks it against the mojo through reflection, so the two cannot drift:
// every documented parameter must exist as a field, and every @Parameter field must be documented.
tasks.processResources {
    val pluginVersion = project.version.toString()
    inputs.property("pluginVersion", pluginVersion)
    filesMatching("META-INF/maven/plugin.xml") {
        // A plain token, not expand(): the descriptor is full of Maven's own ${project...}
        // placeholders that must survive into the published file verbatim.
        filter { line -> line.replace("@pluginVersion@", pluginVersion) }
    }
}

tasks {
    test {
        useJUnitPlatform()
    }
}
