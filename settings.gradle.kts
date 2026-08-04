rootProject.name = "minestom-extensions"

include("minestom-extensions")
include("minestom-extensions-processor")
include("minestom-extensions-gradle-plugin")
include("minestom-extensions-maven-plugin")
include("minestom-extensions-bom")

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
            version("logback-classic", "1.4.5")
            version("slf4j2", "2.0.18")
            version("gson", "2.13.2")
            // maven-resolver-provider carries the Maven-flavoured ArtifactDescriptorReader that
            // understands POMs; the resolver modules must match the version it depends on, so keep
            // these two in lockstep when bumping.
            version("maven-resolver-provider", "3.9.12")
            version("maven-resolver", "1.9.25")
            version("maven-plugin-api", "3.9.16")
            version("maven-plugin-annotations", "3.15.2")

            library("myclium-bom", "net.onelitefeather", "mycelium-bom").versionRef("mycelium-bom")
            library("maven-resolver-provider", "org.apache.maven", "maven-resolver-provider").versionRef("maven-resolver-provider")
            library("maven-resolver-connector-basic", "org.apache.maven.resolver", "maven-resolver-connector-basic").versionRef("maven-resolver")
            library("maven-resolver-transport-http", "org.apache.maven.resolver", "maven-resolver-transport-http").versionRef("maven-resolver")
            library("maven-resolver-transport-file", "org.apache.maven.resolver", "maven-resolver-transport-file").versionRef("maven-resolver")
            library("maven-plugin-api", "org.apache.maven", "maven-plugin-api").versionRef("maven-plugin-api")
            library("maven-core", "org.apache.maven", "maven-core").versionRef("maven-plugin-api")
            library("maven-plugin-annotations", "org.apache.maven.plugin-tools", "maven-plugin-annotations").versionRef("maven-plugin-annotations")
            library("slf4j2", "org.slf4j", "slf4j-api").versionRef("slf4j2")
            library("minestom", "net.minestom", "minestom").withoutVersion()
            library("logback-classic", "ch.qos.logback", "logback-classic").versionRef("logback-classic")
            library("gson", "com.google.code.gson", "gson").versionRef("gson")

            library("junit.api", "org.junit.jupiter", "junit-jupiter-api").withoutVersion()
            library("junit.engine", "org.junit.jupiter", "junit-jupiter-engine").withoutVersion()
            library("junit.params", "org.junit.jupiter", "junit-jupiter-params").withoutVersion()
            library("junit.platform.launcher", "org.junit.platform", "junit-platform-launcher").withoutVersion()
        }

    }
}