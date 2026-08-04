package net.onelitefeather.minestom.extensions.gradle;

import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Runs real builds with both the annotation processor and this plugin attached, and asserts on the
 * {@code extension.json} that ends up in the jar.
 *
 * <p>The point of these is the seam between the two: the processor writes the descriptor during
 * {@code compileJava}, the plugin rewrites it afterwards, and the jar has to pick up the rewritten
 * one. Nothing but an end-to-end build proves that ordering holds.
 */
class MinestomExtensionPluginTest {

    @Test
    @DisplayName("a build-declared library ends up in extension.json inside the jar")
    void recordsExtensionLibrary(@TempDir Path dir) throws Exception {
        writeProject(dir, """
                dependencies {
                    extensionLibrary("com.google.guava:guava:33.4.0-jre")
                }
                """);

        final BuildResult result = build(dir, "jar");

        assertEquals(TaskOutcome.SUCCESS, outcomeOf(result));

        final String json = descriptorFromJar(dir);
        assertAll(
                () -> assertTrue(json.contains("\"com.google.guava:guava:33.4.0-jre\""),
                        () -> "coordinate missing from descriptor:\n" + json),
                // The repository comes from the project's own repositories block.
                () -> assertTrue(json.contains("https://repo1.maven.org/maven2/"),
                        () -> "repository missing from descriptor:\n" + json),
                // Everything the annotation stated has to survive the rewrite.
                () -> assertTrue(json.contains("\"name\": \"Sample\""), json),
                () -> assertTrue(json.contains("\"entrypoint\": \"com.example.SampleExtension\""), json));
    }

    @Test
    @DisplayName("what the annotation already declared is kept alongside the build's libraries")
    void annotationAndBuildAreMerged(@TempDir Path dir) throws Exception {
        writeProject(dir, """
                dependencies {
                    extensionLibrary("com.google.guava:guava:33.4.0-jre")
                }
                """, """
                        externalDependencies = {
                                @ExternalDependency("org.apache.commons:commons-lang3:3.17.0")
                        },
                """);

        build(dir, "jar");

        final String json = descriptorFromJar(dir);
        assertAll(
                () -> assertTrue(json.contains("org.apache.commons:commons-lang3:3.17.0"),
                        () -> "the annotation's coordinate was dropped:\n" + json),
                () -> assertTrue(json.contains("com.google.guava:guava:33.4.0-jre"),
                        () -> "the build's coordinate is missing:\n" + json));
    }

    @Test
    @DisplayName("the descriptor is untouched when the build declares no libraries")
    void noLibrariesLeavesDescriptorAlone(@TempDir Path dir) throws Exception {
        writeProject(dir, "");

        build(dir, "jar");

        final String json = descriptorFromJar(dir);
        assertAll(
                () -> assertTrue(json.contains("\"name\": \"Sample\""), json),
                () -> assertTrue(!json.contains("externalDependencies"),
                        () -> "nothing was declared, so the field should be absent:\n" + json));
    }

    @Test
    @DisplayName("a library without a version fails with an actionable message")
    void versionlessLibraryFails(@TempDir Path dir) throws Exception {
        writeProject(dir, """
                dependencies {
                    extensionLibrary("com.google.guava:guava")
                }
                """);

        final BuildResult result = GradleRunner.create()
                .withProjectDir(dir.toFile())
                .withPluginClasspath()
                .withArguments("jar", "--stacktrace")
                .buildAndFail();

        assertTrue(result.getOutput().contains("needs an explicit group and version"),
                () -> "unhelpful failure:\n" + result.getOutput());
    }

    @Test
    @DisplayName("the task is up to date on a second run")
    void secondRunIsUpToDate(@TempDir Path dir) throws Exception {
        writeProject(dir, """
                dependencies {
                    extensionLibrary("com.google.guava:guava:33.4.0-jre")
                }
                """);

        build(dir, "jar");
        final BuildResult second = build(dir, "jar");

        assertEquals(TaskOutcome.UP_TO_DATE, outcomeOf(second));
    }

    @Test
    @DisplayName("removing a library removes it from the descriptor on the next build")
    void removingALibraryUpdatesTheDescriptor(@TempDir Path dir) throws Exception {
        writeProject(dir, """
                dependencies {
                    extensionLibrary("com.google.guava:guava:33.4.0-jre")
                }
                """);
        build(dir, "jar");
        assertTrue(descriptorFromJar(dir).contains("guava"), "setup failed, guava was never recorded");

        // Only the build script changes; compileJava stays up to date and does not rewrite the
        // descriptor. An in-place merge would silently keep the stale coordinate here.
        writeProject(dir, "");
        build(dir, "jar");

        final String json = descriptorFromJar(dir);
        assertTrue(!json.contains("guava"),
                () -> "the removed library is still in the descriptor:\n" + json);
    }

    @Test
    @DisplayName("the jar carries exactly one extension.json")
    void jarHasASingleDescriptor(@TempDir Path dir) throws Exception {
        writeProject(dir, """
                dependencies {
                    extensionLibrary("com.google.guava:guava:33.4.0-jre")
                }
                """);
        build(dir, "jar");

        try (ZipFile zip = new ZipFile(dir.resolve("build/libs/sample.jar").toFile())) {
            final long count = zip.stream()
                    .filter(entry -> entry.getName().equals("extension.json"))
                    .count();
            assertEquals(1, count, "the processor's copy and the enriched one both got packaged");
        }
    }

    private static TaskOutcome outcomeOf(BuildResult result) {
        final var task = result.task(":" + MinestomExtensionPlugin.TASK_NAME);
        assertNotNull(task, () -> "the plugin's task did not run:\n" + result.getOutput());
        return task.getOutcome();
    }

    private static BuildResult build(Path dir, String task) {
        return GradleRunner.create()
                .withProjectDir(dir.toFile())
                .withPluginClasspath()
                .withArguments(task, "--stacktrace")
                .build();
    }

    private static String descriptorFromJar(Path dir) throws IOException {
        final Path jar = dir.resolve("build/libs/sample.jar");
        assertTrue(Files.exists(jar), () -> "no jar was built at " + jar);
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            final var entry = zip.getEntry("extension.json");
            assertNotNull(entry, "the jar has no extension.json");
            try (var in = zip.getInputStream(entry)) {
                return new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
        }
    }

    private static void writeProject(Path dir, String extraBuildScript) throws IOException {
        writeProject(dir, extraBuildScript, "");
    }

    private static void writeProject(Path dir, String extraBuildScript, String extraAnnotation)
            throws IOException {
        final String processorDir = System.getProperty("processor.jar.dir");
        assertNotNull(processorDir, "processor.jar.dir was not handed over by the build");
        final File[] jars = new File(processorDir).listFiles((d, n) -> n.endsWith(".jar"));
        assertTrue(jars != null && jars.length > 0, "no processor jar in " + processorDir);

        Files.writeString(dir.resolve("settings.gradle.kts"), "rootProject.name = \"sample\"\n");

        Files.writeString(dir.resolve("build.gradle.kts"), """
                plugins {
                    java
                    id("net.onelitefeather.minestom-extensions")
                }

                repositories {
                    maven {
                        name = "central"
                        url = uri("https://repo1.maven.org/maven2/")
                    }
                }

                dependencies {
                    compileOnly(files("%s"))
                    annotationProcessor(files("%s"))
                }

                %s
                """.formatted(
                jars[0].getAbsolutePath().replace("\\", "/"),
                jars[0].getAbsolutePath().replace("\\", "/"),
                extraBuildScript));

        final Path source = dir.resolve("src/main/java/com/example");
        Files.createDirectories(source);
        Files.writeString(source.resolve("SampleExtension.java"), """
                package com.example;

                import net.onelitefeather.minestom.extensions.processor.ExtensionInfo;
                import net.onelitefeather.minestom.extensions.processor.ExternalDependency;
                import net.onelitefeather.minestom.extensions.processor.Repository;

                @ExtensionInfo(
                        name = "Sample",
                        version = "1.0.0",
                %s
                        authors = {"Tester"}
                )
                public class SampleExtension {
                }
                """.formatted(extraAnnotation));
    }
}
