plugins {
    `java-gradle-plugin`
}

description = "Gradle plugin feeding build-declared dependencies into extension.json"

dependencies {
    // ExtensionDescriptor reads and renders the file; sharing it with the processor is what keeps
    // the formatting and the ASCII escaping identical no matter which side writes. It carries no
    // dependencies of its own, so the plugin adds nothing to a consumer's buildscript classpath.
    implementation(project(":minestom-extensions-processor"))

    testImplementation(libs.junit.api)
    testImplementation(libs.junit.platform.launcher)
    testRuntimeOnly(libs.junit.engine)
    testImplementation(gradleTestKit())
}

gradlePlugin {
    plugins {
        create("minestomExtension") {
            id = "net.onelitefeather.minestom-extensions"
            implementationClass = "net.onelitefeather.minestom.extensions.gradle.MinestomExtensionPlugin"
            displayName = "Minestom extension descriptor"
            description = "Adds the dependencies declared in the build to the generated extension.json"
        }
    }
}

// The functional test runs a real build with the real annotation processor attached, so it needs
// the processor jar. Handing over the path beats publishing to mavenLocal from a test.
val processorJar = tasks.register<Sync>("processorJarForTest") {
    from(project(":minestom-extensions-processor").tasks.named("jar"))
    into(layout.buildDirectory.dir("test-processor"))
}

tasks {
    test {
        useJUnitPlatform()
        dependsOn(processorJar)
        systemProperty("processor.jar.dir",
            layout.buildDirectory.dir("test-processor").get().asFile.absolutePath)
        // TestKit builds a throwaway project per test; without this it inherits the outer daemon's
        // memory settings and the suite gets noticeably slower.
        maxHeapSize = "1g"
    }
}
