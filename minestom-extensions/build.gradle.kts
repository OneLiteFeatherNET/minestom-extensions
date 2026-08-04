plugins {
    `java-library`
}

description = "Extensions for minestom, added externally as a library"

dependencies {
    compileOnly(libs.minestom)
    // Runtime resolution of an extension's externalDependencies. maven-resolver-provider supplies
    // the RepositorySystem plus the POM-aware descriptor reader; the connector and transports are
    // what actually fetch artifacts, and Aether does nothing without them being registered.
    implementation(libs.maven.resolver.provider)
    implementation(libs.maven.resolver.connector.basic)
    implementation(libs.maven.resolver.transport.http)
    implementation(libs.maven.resolver.transport.file)
    implementation(libs.slf4j2)

    testImplementation(libs.minestom)
    testImplementation(libs.junit.api)
    testImplementation(libs.junit.platform.launcher)
    testRuntimeOnly(libs.junit.engine)
    testImplementation(libs.logback.classic)
}

tasks {
    test {
        useJUnitPlatform()
        jvmArgs("-Dminestom.inside-test=true")
    }
}
