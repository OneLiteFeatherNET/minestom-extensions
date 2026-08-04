plugins {
    `java-library`
}

description = "Annotation processor generating extension.json for minestom extensions"

// This module is intentionally dependency-free at runtime: it uses only the JDK's
// javax.annotation.processing / javax.lang.model APIs. No Gson, no Minestom, no auto-service.
// The processor registers itself through META-INF/services.
dependencies {
    // Compile testing uses the JDK's own javax.tools.ToolProvider.getSystemJavaCompiler(),
    // so no external compile-testing library is required.
    testImplementation(platform(libs.myclium.bom))
    testImplementation(libs.junit.api)
    testImplementation(libs.junit.platform.launcher)
    // Only used to parse the generated extension.json back, so the tests assert on the parsed
    // structure instead of on brittle string equality. Never a dependency of the processor itself.
    testImplementation(libs.gson)
    testRuntimeOnly(libs.junit.engine)
}

// The descriptor contract lives in minestom-extensions, where a test deserializes it into the real
// DiscoveredExtension. Copying it in (rather than duplicating it) is what keeps the two ends from
// drifting apart: this module asserts the processor reproduces the file, the other asserts Gson
// still reads every field of it. A plain file reference, not a project dependency - this module
// must stay independent of minestom-extensions.
val descriptorContract by tasks.registering(Copy::class) {
    description = "Copies the shared extension.json contract from the minestom-extensions module"
    from(rootProject.layout.projectDirectory
            .file("minestom-extensions/src/test/resources/extension-descriptor-contract.json"))
    into(layout.buildDirectory.dir("generated/test-resources/contract"))
}

sourceSets {
    test {
        resources.srcDir(descriptorContract)
    }
}

tasks {
    test {
        useJUnitPlatform()
    }
}
