rootProject.name = "minestom-extensions"

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        maven {
            name = "OneLiteFeatherRepository"
            url = uri("https://repo.onelitefeather.dev/onelitefeather")
            if (System.getenv("CI") != null) {
                credentials {
                    username = System.getenv("ONELITEFEATHER_MAVEN_USERNAME")
                    password = System.getenv("ONELITEFEATHER_MAVEN_PASSWORD")
                }
            } else {
                credentials(PasswordCredentials::class)
                authentication {
                    create<BasicAuthentication>("basic")
                }
            }
        }
    }

    versionCatalogs {
        create("libs") {
            version("mycelium-bom", "1.7.1")
            version("dependency-getter", "v1.0.1")
            version("logback-classic", "1.4.5")
            version("slf4j2", "2.0.18")

            library("myclium-bom", "net.onelitefeather", "mycelium-bom").versionRef("mycelium-bom")
            library("dependency-getter", "com.github.Minestom", "DependencyGetter").versionRef("dependency-getter")
            library("slf4j2", "org.slf4j", "slf4j-api").versionRef("slf4j2")
            library("minestom", "net.minestom", "minestom").withoutVersion()
            library("logback-classic", "ch.qos.logback", "logback-classic").versionRef("logback-classic")

            library("junit.api", "org.junit.jupiter", "junit-jupiter-api").withoutVersion()
            library("junit.engine", "org.junit.jupiter", "junit-jupiter-engine").withoutVersion()
            library("junit.params", "org.junit.jupiter", "junit-jupiter-params").withoutVersion()
            library("junit.platform.launcher", "org.junit.platform", "junit-platform-launcher").withoutVersion()
        }

    }
}