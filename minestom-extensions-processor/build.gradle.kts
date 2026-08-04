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

tasks {
    test {
        useJUnitPlatform()
    }
}
