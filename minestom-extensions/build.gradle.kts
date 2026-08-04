plugins {
    `java-library`
}

description = "Extensions for minestom, added externally as a library"

dependencies {
    implementation(platform(libs.myclium.bom))
    compileOnly(libs.minestom)
    implementation(libs.dependency.getter)
    implementation(libs.slf4j2)

    testImplementation(platform(libs.myclium.bom))
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
