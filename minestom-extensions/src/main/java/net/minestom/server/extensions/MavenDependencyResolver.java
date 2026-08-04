package net.minestom.server.extensions;

import org.apache.maven.repository.internal.MavenRepositorySystemUtils;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.connector.basic.BasicRepositoryConnectorFactory;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.impl.DefaultServiceLocator;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.repository.RepositoryPolicy;
import org.eclipse.aether.resolution.DependencyRequest;
import org.eclipse.aether.resolution.DependencyResolutionException;
import org.eclipse.aether.spi.connector.RepositoryConnectorFactory;
import org.eclipse.aether.spi.connector.transport.TransporterFactory;
import org.eclipse.aether.transport.file.FileTransporterFactory;
import org.eclipse.aether.transport.http.HttpTransporterFactory;
import org.eclipse.aether.util.artifact.JavaScopes;
import org.eclipse.aether.util.graph.visitor.PreorderNodeListGenerator;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Resolves the {@code externalDependencies} of an extension from Maven repositories, downloading
 * each artifact and its transitive dependencies into the extension libraries folder.
 *
 * <p>Backed by Maven Artifact Resolver (Aether) — the same resolver Maven itself uses, and the same
 * approach Paper takes for its plugin library loader.
 *
 * <p>The wiring below looks old-fashioned on purpose. {@code RepositorySystemSupplier} is the
 * modern entry point, but it builds a resolver with Aether's generic descriptor reader rather than
 * the Maven-flavoured one, and that reader does not interpret POMs: resolution then silently
 * returns the requested artifact with <em>no</em> transitive dependencies at all. Going through
 * {@code MavenRepositorySystemUtils} is what pulls in the POM-aware reader. It is deprecated but
 * not replaced for this use case.
 */
final class MavenDependencyResolver {

    private final RepositorySystem system;
    private final DefaultRepositorySystemSession session;

    /**
     * @param localRepository directory that caches downloaded artifacts
     */
    MavenDependencyResolver(@NotNull File localRepository) {
        final DefaultServiceLocator locator = MavenRepositorySystemUtils.newServiceLocator();
        locator.addService(RepositoryConnectorFactory.class, BasicRepositoryConnectorFactory.class);
        // Both transports are registered: http(s) for remote repositories, file for local ones.
        // Aether cannot fetch anything from a repository whose scheme has no transport.
        locator.addService(TransporterFactory.class, HttpTransporterFactory.class);
        locator.addService(TransporterFactory.class, FileTransporterFactory.class);
        locator.setErrorHandler(new DefaultServiceLocator.ErrorHandler() {
            @Override
            public void serviceCreationFailed(Class<?> type, Class<?> impl, Throwable exception) {
                ExtensionManager.LOGGER.error("Could not create the dependency resolver service {}",
                        type.getName(), exception);
            }
        });

        this.system = locator.getService(RepositorySystem.class);

        this.session = MavenRepositorySystemUtils.newSession();
        this.session.setSystemProperties(System.getProperties());
        // A corrupted download is worse than a failed one: it would surface much later as an
        // unexplainable linkage error inside the extension.
        this.session.setChecksumPolicy(RepositoryPolicy.CHECKSUM_POLICY_FAIL);
        this.session.setLocalRepositoryManager(
                system.newLocalRepositoryManager(session, new LocalRepository(localRepository)));
    }

    /**
     * Resolves one Maven coordinate along with its transitive runtime dependencies.
     *
     * @param coordinate   the artifact, as {@code group:artifact:version}
     * @param repositories the repositories to search, in order
     * @return the location of the resolved artifact and of every transitive dependency, the
     *         requested artifact first
     * @throws DependencyResolutionException if the artifact or any of its dependencies is unavailable
     * @throws MalformedURLException         if a resolved file cannot be expressed as a URL
     */
    @NotNull
    List<URL> resolve(@NotNull String coordinate, @NotNull List<RemoteRepository> repositories)
            throws DependencyResolutionException, MalformedURLException {
        final Artifact artifact = new DefaultArtifact(coordinate);
        final CollectRequest collect =
                new CollectRequest(new Dependency(artifact, JavaScopes.RUNTIME), repositories);

        final DependencyNode root =
                system.resolveDependencies(session, new DependencyRequest(collect, null)).getRoot();

        // Preorder keeps the requested artifact first and each dependency ahead of its own
        // dependencies, which is the order the extension classloader should see them in.
        final PreorderNodeListGenerator generator = new PreorderNodeListGenerator();
        root.accept(generator);

        // A diamond in the graph would otherwise add the same jar to the classloader twice.
        final Set<URL> locations = new LinkedHashSet<>();
        for (Artifact resolved : generator.getArtifacts(false)) {
            final File file = resolved.getFile();
            if (file == null) {
                ExtensionManager.LOGGER.warn("Dependency {} resolved without a file, skipping it.",
                        resolved);
                continue;
            }
            locations.add(file.toURI().toURL());
        }
        return List.copyOf(locations);
    }

    /**
     * Builds the Aether repositories for an extension's declared repository list.
     *
     * @param repositories the repositories declared in {@code extension.json}
     * @return the same repositories in Aether's representation
     * @throws IllegalStateException if a declared repository has no name or no url
     */
    @NotNull
    static List<RemoteRepository> toRemoteRepositories(
            DiscoveredExtension.ExternalDependencies.@NotNull Repository @NotNull [] repositories) {
        final List<RemoteRepository> result = new ArrayList<>(repositories.length);
        for (var repository : repositories) {
            if (repository.name == null || repository.name.isEmpty()) {
                throw new IllegalStateException("Missing 'name' element in repository object.");
            }

            if (repository.url == null || repository.url.isEmpty()) {
                throw new IllegalStateException("Missing 'url' element in repository object.");
            }

            result.add(new RemoteRepository.Builder(repository.name, "default", repository.url).build());
        }
        return result;
    }
}
