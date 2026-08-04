rootProject.name = "minestom-extensions"

include("minestom-extensions")
include("minestom-extensions-processor")
include("minestom-extensions-gradle-plugin")
include("minestom-extensions-maven-plugin")
include("minestom-extensions-bom")

dependencyResolutionManagement {
    repositories {
        // Everything resolves from Central. The OneLiteFeather repository used to be needed here
        // for mycelium-bom alone; without it, building this project requires no credentials.
        // Publishing still targets repo.onelitefeather.dev - see the root build.
        mavenCentral()
    }

    versionCatalogs {
        create("libs") {
            // Minestom and JUnit used to inherit their versions from mycelium-bom. That platform is
            // not resolvable without OneLiteFeather credentials (404 on the public path, 401 behind
            // auth), and Gradle records it as a real runtime dependency rather than a constraint -
            // which made this library unresolvable for anyone outside the organisation.
            version("minestom", "2026.07.22-26.2")
            version("junit", "6.1.2")
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

            library("maven-resolver-provider", "org.apache.maven", "maven-resolver-provider").versionRef("maven-resolver-provider")
            library("maven-resolver-connector-basic", "org.apache.maven.resolver", "maven-resolver-connector-basic").versionRef("maven-resolver")
            library("maven-resolver-transport-http", "org.apache.maven.resolver", "maven-resolver-transport-http").versionRef("maven-resolver")
            library("maven-resolver-transport-file", "org.apache.maven.resolver", "maven-resolver-transport-file").versionRef("maven-resolver")
            library("maven-plugin-api", "org.apache.maven", "maven-plugin-api").versionRef("maven-plugin-api")
            library("maven-core", "org.apache.maven", "maven-core").versionRef("maven-plugin-api")
            library("maven-plugin-annotations", "org.apache.maven.plugin-tools", "maven-plugin-annotations").versionRef("maven-plugin-annotations")
            library("slf4j2", "org.slf4j", "slf4j-api").versionRef("slf4j2")
            library("minestom", "net.minestom", "minestom").versionRef("minestom")
            library("logback-classic", "ch.qos.logback", "logback-classic").versionRef("logback-classic")
            library("gson", "com.google.code.gson", "gson").versionRef("gson")

            library("junit.api", "org.junit.jupiter", "junit-jupiter-api").versionRef("junit")
            library("junit.engine", "org.junit.jupiter", "junit-jupiter-engine").versionRef("junit")
            library("junit.params", "org.junit.jupiter", "junit-jupiter-params").versionRef("junit")
            library("junit.platform.launcher", "org.junit.platform", "junit-platform-launcher").versionRef("junit")
        }

    }
}