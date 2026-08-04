plugins {
    base
}

/**
 * Shared metadata for every publishable module. Only `name`, `description` and `artifactId`
 * differ per module - everything below is declared exactly once and reused.
 */
val projectUrl = "https://github.com/OneLiteFeatherNET/minestom-extensions"

allprojects {
    group = rootProject.group

    // gradle.properties carries "version = 2.0.0 # x-release-please-version".
    // Strip the release-please marker comment for every project.
    version = (version as String).substringBefore('#').trim()
}

subprojects {
    apply(plugin = "maven-publish")

    // --- Java modules (java-library). The java-platform BOM never matches this block,
    // --- so it correctly never gets a sources-/javadoc-jar or a toolchain.
    plugins.withId("java-library") {
        extensions.configure<JavaPluginExtension> {
            withSourcesJar()
            withJavadocJar()

            toolchain {
                languageVersion.set(JavaLanguageVersion.of(25))
            }
        }
    }

    extensions.configure<PublishingExtension> {
        // --- One publication per module, created from whichever component exists.
        // --- Deferred to afterEvaluate because java-gradle-plugin supplies its own `pluginMaven`
        // --- for the very same artifact; creating ours as well would publish the identical GAV
        // --- twice, which a release repository rejects on the second upload.
        plugins.withId("java-library") {
            afterEvaluate {
                // Checking for the plugin, not for the `pluginMaven` publication: that publication
                // is itself created in an afterEvaluate, and ours runs first, so it would not be
                // visible yet.
                if (!pluginManager.hasPlugin("java-gradle-plugin")) {
                    publications.create<MavenPublication>("maven") {
                        from(components["java"])
                    }
                }
            }
        }
        plugins.withId("java-platform") {
            publications.create<MavenPublication>("maven") {
                from(components["javaPlatform"])
            }
        }

        // --- Central POM boilerplate, applied to whatever publication a module declares.
        publications.withType<MavenPublication>().configureEach {
            groupId = project.group.toString()
            version = project.version.toString()

            // Gradle plugin markers are a fixed coordinate derived from the plugin id - that is how
            // `plugins { id(...) }` resolves them - so they must keep the name Gradle gave them.
            if (!name.endsWith("PluginMarkerMaven")) {
                artifactId = project.name
            }

            pom {
                name.set(project.name)
                description.set(provider { project.description })
                url.set(projectUrl)

                licenses {
                    license {
                        name.set("Apache 2.0")
                        url.set("$projectUrl/blob/main/LICENSE")
                    }
                }

                developers {
                    developer {
                        id.set("Minestom Contributors")
                    }
                }

                issueManagement {
                    system.set("GitHub")
                    url.set("$projectUrl/issues")
                }

                scm {
                    connection.set("scm:git:git://github.com/OneLiteFeatherNET/minestom-extensions.git")
                    developerConnection.set("scm:git:git@github.com:OneLiteFeatherNET/minestom-extensions.git")
                    url.set(projectUrl)
                    tag.set(project.version.toString())
                }

                ciManagement {
                    system.set("Github Actions")
                    url.set("$projectUrl/actions")
                }
            }
        }

        // --- Central publishing target.
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
                url = if (project.version.toString().contains("SNAPSHOT")) snapshotsRepoUrl else releasesRepoUrl
            }
        }
    }
}
