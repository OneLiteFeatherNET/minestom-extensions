plugins {
    `java-library`
    `maven-publish`
}

group = "net.onelitefeather.minestom"
version = (version as String).substringBefore('#').trim()
description = "Extensions for minestom, added externally as a library"


dependencies {
    implementation(platform(libs.myclium.bom))
    compileOnly(libs.minestom)
    implementation(libs.dependency.getter)

    testImplementation(platform(libs.myclium.bom))
    testImplementation(libs.minestom)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.logback.classic)
}

java {
    withSourcesJar()
    withJavadocJar()

    sourceCompatibility = JavaVersion.VERSION_25
    targetCompatibility = JavaVersion.VERSION_25
}

tasks {
    test {
        useJUnitPlatform()
    }
}

publishing {
    publications.create<MavenPublication>("maven") {
        groupId = "net.onelitefeather"
        artifactId = "minestom-extensions"
        version = project.version.toString()

        from(project.components["java"])

        pom {
            name.set("minestom-extensions")
            description.set("Extensions for minestom, added externally as a library")
            url.set("https://github.com/OneLiteFeatherNET/minestom-extensions")

            licenses {
                license {
                    name.set("Apache 2.0")
                    url.set("https://github.com/OneLiteFeatherNET/minestom-extensions/blob/main/LICENSE")
                }
            }

            developers {
                developer {
                    id.set("Minestom Contributors")
                }
            }

            issueManagement {
                system.set("GitHub")
                url.set("https://github.com/OneLiteFeatherNET/minestom-extensions/issues")
            }

            scm {
                connection.set("scm:git:git://github.com/OneLiteFeatherNET/minestom-extensions.git")
                developerConnection.set("scm:git:git@github.com:OneLiteFeatherNET/minestom-extensions.git")
                url.set("https://github.com/OneLiteFeatherNET/minestom-extensions")
                tag.set(version)
            }

            ciManagement {
                system.set("Github Actions")
                url.set("https://github.com/OneLiteFeatherNET/minestom-extensions/actions")
            }
        }
    }
    repositories {
        maven {
            authentication {
                credentials(PasswordCredentials::class) {
                    username = System.getenv("ONELITEFEATHER_MAVEN_USERNAME")
                    password = System.getenv("ONELITEFEATHER_MAVEN_PASSWORD")
                }
            }

            name = "OneLiteFeatherRepository"
            val releasesRepoUrl = uri("https://repo.onelitefeather.dev/releases")
            val snapshotsRepoUrl = uri("https://repo.onelitefeather.dev/snapshots")
            url = if (version.toString().contains("SNAPSHOT")) snapshotsRepoUrl else releasesRepoUrl
        }
    }
}
