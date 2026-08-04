package net.onelitefeather.minestom.extensions.maven;

import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.handler.DefaultArtifactHandler;
import org.apache.maven.artifact.repository.MavenArtifactRepository;
import org.apache.maven.artifact.repository.layout.DefaultRepositoryLayout;
import org.apache.maven.model.Build;
import org.apache.maven.model.Model;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises the goal directly, without a Maven build around it.
 *
 * <p>Fields are injected by reflection because that is what Maven does at runtime, and pulling in
 * {@code maven-plugin-testing-harness} for six fields would cost more than it gives.
 */
class ExtensionDescriptorMojoTest {

    private static final String PROCESSOR_OUTPUT = """
            {
              "name": "Sample",
              "entrypoint": "com.example.SampleExtension",
              "version": "1.0.0"
            }""";

    @Test
    @DisplayName("a coordinate without a version takes it from the project dependencies")
    void versionComesFromTheProject(@TempDir Path dir) throws Exception {
        final Path classes = writeProcessorOutput(dir);
        final ExtensionDescriptorMojo mojo = mojo(dir, List.of("org.apache.commons:commons-lang3"));

        mojo.execute();

        final String json = Files.readString(classes.resolve("extension.json"), StandardCharsets.UTF_8);
        assertTrue(json.contains("org.apache.commons:commons-lang3:3.17.0"),
                () -> "the version was not resolved from the project:\n" + json);
    }

    @Test
    @DisplayName("removing the last coordinate restores the processor's descriptor")
    void removingCoordinatesRestoresTheDescriptor(@TempDir Path dir) throws Exception {
        final Path classes = writeProcessorOutput(dir);
        final Path descriptor = classes.resolve("extension.json");

        mojo(dir, List.of("org.apache.commons:commons-lang3")).execute();
        assertTrue(Files.readString(descriptor).contains("commons-lang3"), "setup failed");

        // Second run with nothing configured, and without the processor having run again — which is
        // what an incremental build looks like. The stale coordinate must not survive.
        mojo(dir, List.of()).execute();

        final String json = Files.readString(descriptor, StandardCharsets.UTF_8);
        assertAll(
                () -> assertFalse(json.contains("commons-lang3"),
                        () -> "the removed coordinate is still there:\n" + json),
                () -> assertFalse(json.contains("externalDependencies"),
                        () -> "the empty section should be gone entirely:\n" + json),
                () -> assertTrue(json.contains("\"name\": \"Sample\""),
                        () -> "the processor's own fields were lost:\n" + json));
    }

    @Test
    @DisplayName("running twice with the same configuration is idempotent")
    void repeatedRunsAreIdempotent(@TempDir Path dir) throws Exception {
        final Path descriptor = writeProcessorOutput(dir).resolve("extension.json");

        mojo(dir, List.of("org.apache.commons:commons-lang3")).execute();
        final String first = Files.readString(descriptor, StandardCharsets.UTF_8);
        mojo(dir, List.of("org.apache.commons:commons-lang3")).execute();

        assertTrue(first.equals(Files.readString(descriptor, StandardCharsets.UTF_8)),
                "the second run changed the descriptor");
    }

    @Test
    @DisplayName("an unknown coordinate without a version fails with an actionable message")
    void unknownCoordinateFails(@TempDir Path dir) throws Exception {
        writeProcessorOutput(dir);
        final ExtensionDescriptorMojo mojo = mojo(dir, List.of("com.unknown:missing"));

        final MojoFailureException failure = assertThrows(MojoFailureException.class, mojo::execute);
        assertTrue(failure.getMessage().contains("no such dependency"),
                () -> "unhelpful message: " + failure.getMessage());
    }

    @Test
    @DisplayName("the project version overrides the version from the annotation")
    void projectVersionOverridesTheAnnotation(@TempDir Path dir) throws Exception {
        final Path descriptor = writeProcessorOutput(dir).resolve("extension.json");
        final ExtensionDescriptorMojo mojo = mojo(dir, List.of());
        set(mojo, "useProjectVersion", true);

        mojo.execute();

        final String json = Files.readString(descriptor, StandardCharsets.UTF_8);
        assertAll(
                () -> assertTrue(json.contains("\"version\": \"9.9.9\""),
                        () -> "the project version was not written:\n" + json),
                () -> assertFalse(json.contains("1.0.0"),
                        () -> "the annotation's version is still there:\n" + json));
    }

    @Test
    @DisplayName("useProjectVersion=false keeps the version from the annotation")
    void annotationVersionIsKeptWhenOptedOut(@TempDir Path dir) throws Exception {
        final Path descriptor = writeProcessorOutput(dir).resolve("extension.json");
        final ExtensionDescriptorMojo mojo = mojo(dir, List.of());
        set(mojo, "useProjectVersion", false);

        mojo.execute();

        assertTrue(Files.readString(descriptor, StandardCharsets.UTF_8).contains("\"version\": \"1.0.0\""),
                "the annotation's version should have been left alone");
    }

    @Test
    @DisplayName("a malformed coordinate fails")
    void malformedCoordinateFails(@TempDir Path dir) throws Exception {
        writeProcessorOutput(dir);
        final ExtensionDescriptorMojo mojo = mojo(dir, List.of("not-a-coordinate"));

        assertThrows(MojoFailureException.class, mojo::execute);
    }

    @Test
    @DisplayName("external dependencies without any repository fail rather than ship a broken descriptor")
    void missingRepositoryFails(@TempDir Path dir) throws Exception {
        writeProcessorOutput(dir);
        final ExtensionDescriptorMojo mojo = mojo(dir, List.of("org.apache.commons:commons-lang3"));
        set(mojo, "inheritProjectRepositories", false);

        final MojoFailureException failure = assertThrows(MojoFailureException.class, mojo::execute);
        assertTrue(failure.getMessage().contains("no repository"),
                () -> "unhelpful message: " + failure.getMessage());
    }

    private static Path writeProcessorOutput(Path dir) throws Exception {
        final Path classes = dir.resolve("target/classes");
        Files.createDirectories(classes);
        Files.writeString(classes.resolve("extension.json"), PROCESSOR_OUTPUT, StandardCharsets.UTF_8);
        return classes;
    }

    private static ExtensionDescriptorMojo mojo(Path dir, List<String> coordinates) throws Exception {
        final Build build = new Build();
        build.setDirectory(dir.resolve("target").toString());
        build.setOutputDirectory(dir.resolve("target/classes").toString());

        final Model model = new Model();
        model.setBuild(build);
        model.setVersion("9.9.9");
        final MavenProject project = new MavenProject(model);
        project.setArtifacts(Set.of(artifact("org.apache.commons", "commons-lang3", "3.17.0")));
        project.setRemoteArtifactRepositories(List.of(new MavenArtifactRepository(
                "central", "https://repo1.maven.org/maven2/", new DefaultRepositoryLayout(), null, null)));

        final ExtensionDescriptorMojo mojo = new ExtensionDescriptorMojo();
        set(mojo, "project", project);
        set(mojo, "outputDirectory", dir.resolve("target/classes").toFile());
        set(mojo, "externalDependencies", coordinates);
        set(mojo, "repositories", new java.util.LinkedHashMap<String, String>());
        set(mojo, "inheritProjectRepositories", true);
        set(mojo, "skip", false);
        return mojo;
    }

    private static org.apache.maven.artifact.Artifact artifact(String group, String name, String version) {
        return new DefaultArtifact(group, name, version, "provided", "jar", null,
                new DefaultArtifactHandler("jar"));
    }

    private static void set(Object target, String field, Object value) throws Exception {
        final Field declared = target.getClass().getDeclaredField(field);
        declared.setAccessible(true);
        declared.set(target, value);
    }
}
