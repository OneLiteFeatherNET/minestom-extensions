package net.minestom.server.extensions;

import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.DependencyResolutionException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the runtime resolution of an extension's {@code externalDependencies}.
 *
 * <p>Everything is served from a Maven repository laid out in a temporary directory and reached
 * over {@code file://}, so the suite neither touches the network nor depends on Maven Central's
 * availability or terms of service.
 *
 * <p>The transitive case is the one that matters. A resolver that returns only the requested
 * artifact still looks like it works — the extension loads, and the failure surfaces much later as
 * a {@code NoClassDefFoundError} from inside the extension. That is exactly what the previous
 * implementation degraded into, so it is asserted explicitly here.
 */
class MavenDependencyResolverTest {

    @Test
    @DisplayName("a transitive dependency is resolved along with the requested artifact")
    void resolvesTransitiveDependencies(@TempDir Path dir) throws Exception {
        final Path repo = repositoryWithTransitiveChain(dir.resolve("repo"));
        final MavenDependencyResolver resolver =
                new MavenDependencyResolver(dir.resolve("cache").toFile());

        final List<URL> resolved =
                resolver.resolve("com.example:lib-a:1.0", List.of(fileRepository(repo)));

        assertEquals(2, resolved.size(), () -> "expected lib-a and its transitive lib-b, got " + resolved);
        assertAll(
                // Preorder: the requested artifact comes first, its dependency after it.
                () -> assertTrue(resolved.get(0).toString().endsWith("lib-a-1.0.jar"), resolved::toString),
                () -> assertTrue(resolved.get(1).toString().endsWith("lib-b-1.0.jar"), resolved::toString));
    }

    @Test
    @DisplayName("resolved artifacts are downloaded into the local repository")
    void downloadsIntoLocalRepository(@TempDir Path dir) throws Exception {
        final Path repo = repositoryWithTransitiveChain(dir.resolve("repo"));
        final Path cache = dir.resolve("cache");

        new MavenDependencyResolver(cache.toFile())
                .resolve("com.example:lib-a:1.0", List.of(fileRepository(repo)));

        assertAll(
                () -> assertTrue(Files.exists(cache.resolve("com/example/lib-a/1.0/lib-a-1.0.jar")),
                        "lib-a was not cached"),
                () -> assertTrue(Files.exists(cache.resolve("com/example/lib-b/1.0/lib-b-1.0.jar")),
                        "the transitive lib-b was not cached"));
    }

    @Test
    @DisplayName("an unavailable artifact fails instead of resolving to nothing")
    void missingArtifactFails(@TempDir Path dir) throws Exception {
        final Path repo = repositoryWithTransitiveChain(dir.resolve("repo"));
        final MavenDependencyResolver resolver =
                new MavenDependencyResolver(dir.resolve("cache").toFile());

        assertThrows(DependencyResolutionException.class,
                () -> resolver.resolve("com.example:does-not-exist:9.9", List.of(fileRepository(repo))));
    }

    @Test
    @DisplayName("a repository without a name or url is rejected")
    void invalidRepositoryIsRejected() {
        final var noName = new DiscoveredExtension.ExternalDependencies.Repository();
        noName.url = "https://example.invalid/repo";
        final var noUrl = new DiscoveredExtension.ExternalDependencies.Repository();
        noUrl.name = "example";

        assertAll(
                () -> assertThrows(IllegalStateException.class, () -> MavenDependencyResolver
                        .toRemoteRepositories(new DiscoveredExtension.ExternalDependencies.Repository[]{noName})),
                () -> assertThrows(IllegalStateException.class, () -> MavenDependencyResolver
                        .toRemoteRepositories(new DiscoveredExtension.ExternalDependencies.Repository[]{noUrl})));
    }

    @Test
    @DisplayName("declared repositories are translated into Aether repositories in order")
    void repositoriesAreTranslated() {
        final var first = new DiscoveredExtension.ExternalDependencies.Repository();
        first.name = "central";
        first.url = "https://repo1.maven.org/maven2/";
        final var second = new DiscoveredExtension.ExternalDependencies.Repository();
        second.name = "onelitefeather";
        second.url = "https://repo.onelitefeather.dev/releases";

        final List<RemoteRepository> repositories = MavenDependencyResolver.toRemoteRepositories(
                new DiscoveredExtension.ExternalDependencies.Repository[]{first, second});

        assertAll(
                () -> assertEquals(2, repositories.size()),
                () -> assertEquals("central", repositories.get(0).getId()),
                () -> assertEquals("https://repo1.maven.org/maven2/", repositories.get(0).getUrl()),
                () -> assertEquals("onelitefeather", repositories.get(1).getId()),
                () -> assertEquals("https://repo.onelitefeather.dev/releases", repositories.get(1).getUrl()));
    }

    private static RemoteRepository fileRepository(Path repo) {
        return new RemoteRepository.Builder("test", "default", repo.toUri().toString()).build();
    }

    /** Lays out {@code lib-a:1.0} depending on {@code lib-b:1.0} as a real Maven repository. */
    private static Path repositoryWithTransitiveChain(Path repo) throws Exception {
        deploy(repo, "lib-b", """
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>com.example</groupId>
                  <artifactId>lib-b</artifactId>
                  <version>1.0</version>
                </project>
                """);
        deploy(repo, "lib-a", """
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>com.example</groupId>
                  <artifactId>lib-a</artifactId>
                  <version>1.0</version>
                  <dependencies>
                    <dependency>
                      <groupId>com.example</groupId>
                      <artifactId>lib-b</artifactId>
                      <version>1.0</version>
                    </dependency>
                  </dependencies>
                </project>
                """);
        return repo;
    }

    /** Writes a pom and a (minimal but valid) jar, each with the checksums Aether verifies. */
    private static void deploy(Path repo, String artifactId, String pom) throws Exception {
        final Path dir = repo.resolve("com/example/" + artifactId + "/1.0");
        Files.createDirectories(dir);

        writeWithChecksums(dir.resolve(artifactId + "-1.0.pom"), pom.getBytes(StandardCharsets.UTF_8));
        writeWithChecksums(dir.resolve(artifactId + "-1.0.jar"), emptyJar(artifactId));
    }

    private static byte[] emptyJar(String artifactId) throws IOException {
        final var bytes = new java.io.ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            zip.putNextEntry(new ZipEntry("com/example/" + artifactId + "/Marker.class"));
            zip.write(new byte[]{(byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBE});
            zip.closeEntry();
        }
        return bytes.toByteArray();
    }

    /**
     * The resolver runs with {@code CHECKSUM_POLICY_FAIL}, so every artifact needs matching
     * checksums next to it — writing them here also keeps that policy under test.
     */
    private static void writeWithChecksums(Path file, byte[] content) throws Exception {
        try (OutputStream out = Files.newOutputStream(file)) {
            out.write(content);
        }
        for (String algorithm : List.of("SHA-1", "MD5")) {
            final byte[] digest = MessageDigest.getInstance(algorithm).digest(content);
            final String hex = String.format("%0" + (digest.length * 2) + "x", new BigInteger(1, digest));
            final String suffix = algorithm.equals("SHA-1") ? ".sha1" : ".md5";
            Files.writeString(file.resolveSibling(file.getFileName() + suffix), hex);
        }
    }
}
