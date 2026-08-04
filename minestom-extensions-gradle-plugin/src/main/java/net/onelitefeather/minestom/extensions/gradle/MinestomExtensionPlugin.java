package net.onelitefeather.minestom.extensions.gradle;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.repositories.MavenArtifactRepository;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.bundling.Jar;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.language.jvm.tasks.ProcessResources;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * Lets a build declare the libraries an extension loads at runtime, and writes them into the
 * generated {@code extension.json}.
 *
 * <p>Without this plugin the coordinates have to be repeated in {@code @ExtensionInfo}, next to the
 * same versions the build already knows - two places to update, one of which silently goes stale.
 *
 * <pre>{@code
 * plugins {
 *     java
 *     id("net.onelitefeather.minestom-extensions")
 * }
 *
 * dependencies {
 *     extensionLibrary("com.google.guava:guava:33.4.0-jre")
 * }
 * }</pre>
 *
 * <p>The {@code extensionLibrary} configuration is not on any compile or runtime classpath. It only
 * records what the extension resolves for itself at startup, which is exactly the point: those jars
 * must not be bundled or leak into the consumer's classpath.
 */
public class MinestomExtensionPlugin implements Plugin<Project> {

    /** Name of the configuration holding the runtime-resolved libraries. */
    public static final String CONFIGURATION_NAME = "extensionLibrary";

    /** Name of the task that rewrites the descriptor. */
    public static final String TASK_NAME = "extensionDescriptor";

    /** Name of the generated descriptor. */
    static final String DESCRIPTOR_NAME = "extension.json";

    /** Name of the {@code minestomExtension} block. */
    public static final String SPEC_NAME = "minestomExtension";

    @Override
    public void apply(Project project) {
        project.getPluginManager().apply(JavaPlugin.class);

        final MinestomExtensionSpec spec =
                project.getExtensions().create(SPEC_NAME, MinestomExtensionSpec.class);
        spec.getInheritProjectRepositories().convention(true);

        final Configuration libraries = project.getConfigurations().create(CONFIGURATION_NAME, it -> {
            it.setDescription("Libraries the extension resolves at runtime, recorded in extension.json");
            // Declarable only: these never belong on a classpath, they are metadata for the server.
            it.setCanBeResolved(false);
            it.setCanBeConsumed(false);
            it.setVisible(false);
        });

        final var descriptorTask = project.getTasks().register(TASK_NAME, ExtensionDescriptorTask.class, task -> {
            task.setGroup("build");
            task.setDescription("Adds the build-declared dependencies to extension.json");

            task.getDescriptor().fileProvider(project.provider(() ->
                    new java.io.File(mainOutputDir(project), DESCRIPTOR_NAME)));
            task.getOutput().set(project.getLayout().getBuildDirectory()
                    .file("generated/minestom-extension/" + DESCRIPTOR_NAME));

            task.getArtifacts().set(project.provider(() -> libraries.getDependencies().stream()
                    .map(dependency -> {
                        if (dependency.getGroup() == null || dependency.getVersion() == null) {
                            throw new org.gradle.api.GradleException(
                                    "The " + CONFIGURATION_NAME + " dependency '" + dependency
                                            + "' needs an explicit group and version: the server resolves "
                                            + "it from Maven coordinates, with no access to this build's "
                                            + "version catalog or platforms.");
                        }
                        return dependency.getGroup() + ":" + dependency.getName() + ":" + dependency.getVersion();
                    })
                    .toList()));

            task.getRepositories().set(project.provider(() -> repositoriesFor(project, spec)));
        });

        // The processor writes the descriptor during compileJava, so the task has to run after it.
        descriptorTask.configure(task -> task.dependsOn(
                project.getTasks().withType(JavaCompile.class),
                project.getTasks().withType(ProcessResources.class)));

        project.getTasks().withType(Jar.class).configureEach(jar -> {
            jar.from(descriptorTask);
            // Both copies would otherwise land in the jar. Dropping it via eachFile rather than
            // exclude() is deliberate: an exclude on the jar spec applies to every source including
            // the one just added above, which would leave the jar with no descriptor at all.
            jar.eachFile(details -> {
                if (DESCRIPTOR_NAME.equals(details.getSourcePath())
                        && !details.getFile().toPath().startsWith(
                                generatedDir(project).toPath())) {
                    details.exclude();
                }
            });
        });
    }

    private static java.io.File generatedDir(Project project) {
        return project.getLayout().getBuildDirectory()
                .dir("generated/minestom-extension").get().getAsFile();
    }

    /** Collects the repositories to record, project ones first, explicit ones last. */
    private static Map<String, String> repositoriesFor(Project project, MinestomExtensionSpec spec) {
        final Map<String, String> repositories = new LinkedHashMap<>();

        if (Boolean.TRUE.equals(spec.getInheritProjectRepositories().get())) {
            for (var repository : project.getRepositories()) {
                if (!(repository instanceof MavenArtifactRepository maven)) {
                    continue;
                }
                final String url = maven.getUrl().toString();
                // A local path is meaningless on the server, and mavenLocal() would point at the
                // build machine's home directory.
                if (!url.startsWith("http://") && !url.startsWith("https://")) {
                    continue;
                }
                repositories.put(maven.getName(), url);
            }
        }

        repositories.putAll(spec.getRepositories().get());
        // Sorted so the descriptor does not churn when Gradle varies repository iteration order.
        return new TreeMap<>(repositories);
    }

    private static java.io.File mainOutputDir(Project project) {
        final SourceSetContainer sourceSets =
                project.getExtensions().getByType(JavaPluginExtension.class).getSourceSets();
        return sourceSets.getByName(SourceSet.MAIN_SOURCE_SET_NAME)
                .getOutput().getClassesDirs().getFiles().iterator().next();
    }
}
