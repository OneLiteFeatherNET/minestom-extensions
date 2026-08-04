package net.onelitefeather.minestom.extensions.processor;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.NestingKind;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import javax.tools.Diagnostic;
import javax.tools.FileObject;
import javax.tools.StandardLocation;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Generates the {@code extension.json} descriptor of a Minestom extension from a single
 * {@link ExtensionInfo} annotated class, in the spirit of the {@code plugin.yml} generators known
 * from the Bukkit ecosystem.
 *
 * <h2>Where the file ends up</h2>
 * The descriptor is written to {@link StandardLocation#CLASS_OUTPUT} with an empty package name,
 * which puts it at the root of the compiled output directory and therefore at the root of the
 * resulting jar. That is exactly where {@code ExtensionManager} looks for it: it opens the jar as a
 * zip and reads the entry named {@code extension.json}.
 *
 * <h2>Compiler options</h2>
 * <table border="1">
 *     <caption>Supported {@code -A} options</caption>
 *     <tr><th>Option</th><th>Effect</th></tr>
 *     <tr>
 *         <td>{@code minestom.extension.version}</td>
 *         <td>Overrides {@link ExtensionInfo#version()}. The common setup: leave the annotation
 *         element empty and pass the build's project version.</td>
 *     </tr>
 *     <tr>
 *         <td>{@code minestom.extension.name}</td>
 *         <td>Overrides {@link ExtensionInfo#name()}. The override is validated against the same
 *         name pattern as the annotation element.</td>
 *     </tr>
 * </table>
 *
 * <p>With Gradle both are passed like this:
 *
 * <pre>{@code
 * tasks.withType<JavaCompile>().configureEach {
 *     options.compilerArgs.add("-Aminestom.extension.version=${project.version}")
 * }
 * }</pre>
 *
 * <h2>Diagnostics</h2>
 * Errors abort the build; warnings do not. The processor reports an error when more than one class
 * is annotated, when the name does not match {@code [A-Za-z][_A-Za-z0-9]+}, when the annotated type
 * cannot be instantiated reflectively by {@code ExtensionManager}, when it does not extend
 * {@code net.minestom.server.extensions.Extension} (only checked when that class is on the compile
 * classpath), and when a repository declaration is unusable. It warns about a missing version and
 * about duplicate extension dependencies.
 *
 * @see ExtensionInfo
 * @since 2.0.0
 */
@SupportedAnnotationTypes("net.onelitefeather.minestom.extensions.processor.ExtensionInfo")
public final class ExtensionInfoProcessor extends AbstractProcessor {

    /**
     * Compiler option that overrides {@link ExtensionInfo#version()}:
     * {@code -Aminestom.extension.version=1.2.3}.
     */
    public static final String OPTION_VERSION = "minestom.extension.version";

    /**
     * Compiler option that overrides {@link ExtensionInfo#name()}:
     * {@code -Aminestom.extension.name=MyExtension}.
     */
    public static final String OPTION_NAME = "minestom.extension.name";

    /**
     * The name of the generated resource, relative to the root of the class output.
     */
    public static final String OUTPUT_FILE = "extension.json";

    /**
     * Fully qualified name of the class every entrypoint has to extend. Referenced by name only:
     * the processor must never load Minestom classes, see {@link #EXTENSION_NAME_REGEX}.
     */
    private static final String EXTENSION_CLASS = "net.minestom.server.extensions.Extension";

    /**
     * Intentional duplicate of {@code net.minestom.server.extensions.DiscoveredExtension#NAME_REGEX}.
     *
     * <p>The regex is copied instead of referenced because this module must not depend on
     * {@code net.onelitefeather:minestom-extensions}. An annotation processor is put on the
     * <em>processor path</em> of every project that uses it, so a dependency here would drag
     * Minestom, its Gson and its SLF4J onto the processor path of every single extension build.
     * Keep this constant in sync with {@code DiscoveredExtension.NAME_REGEX} - if they ever drift
     * apart, the runtime one wins and the extension is rejected with {@code INVALID_NAME}.
     */
    private static final String EXTENSION_NAME_REGEX = "[A-Za-z][_A-Za-z0-9]+";

    private static final Pattern NAME_PATTERN = Pattern.compile(EXTENSION_NAME_REGEX);

    /** {@code group:artifact:version} with an optional fourth {@code :classifier} segment. */
    private static final Pattern COORDINATE_PATTERN =
            Pattern.compile("[^:\\s]+:[^:\\s]+:[^:\\s]+(:[^:\\s]+)?");

    /** The single annotated entrypoint collected over all rounds, or {@code null} if there is none. */
    private TypeElement entrypoint;

    /** Set once a duplicate entrypoint was reported, which suppresses the file generation. */
    private boolean duplicateReported;

    /** Guards against writing the descriptor twice, e.g. when {@code process} is invoked again. */
    private boolean written;

    /**
     * Creates a new processor. Invoked reflectively by the compiler through the
     * {@code META-INF/services/javax.annotation.processing.Processor} registration.
     */
    public ExtensionInfoProcessor() {
        // Annotation processors must offer a public no-arg constructor.
    }

    /**
     * {@inheritDoc}
     *
     * @return always the latest source version the running compiler supports, so the processor never
     * emits a "source version not supported" warning after a JDK upgrade
     */
    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    /**
     * {@inheritDoc}
     *
     * @return {@link #OPTION_VERSION} and {@link #OPTION_NAME}
     */
    @Override
    public Set<String> getSupportedOptions() {
        return Set.of(OPTION_VERSION, OPTION_NAME);
    }

    /**
     * Collects the annotated entrypoint during the regular rounds and writes {@code extension.json}
     * in the final round.
     *
     * <p>The descriptor can only be written once every round has been seen: annotation processing is
     * iterative, and a later round may still contribute the annotated class (for instance when it is
     * itself generated by another processor). Collecting first and writing in the round where
     * {@link RoundEnvironment#processingOver()} is {@code true} also guarantees that the duplicate
     * check sees every candidate.
     *
     * @param annotations the annotation types requested to be processed
     * @param roundEnv    the environment of the current round
     * @return always {@code true}; {@link ExtensionInfo} and its nested annotation types are fully
     * consumed here and no other processor needs to see them
     */
    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (!roundEnv.processingOver()) {
            collect(roundEnv);
            return true;
        }

        if (entrypoint != null && !duplicateReported && !written) {
            written = true;
            generate(entrypoint);
        }
        return true;
    }

    /** Remembers the annotated type of this round and rejects any additional one. */
    private void collect(RoundEnvironment roundEnv) {
        for (Element element : roundEnv.getElementsAnnotatedWith(ExtensionInfo.class)) {
            // @ExtensionInfo is @Target(TYPE), so the cast is always safe.
            final TypeElement type = (TypeElement) element;
            if (entrypoint == null) {
                entrypoint = type;
                continue;
            }

            duplicateReported = true;
            error("Found more than one class annotated with @ExtensionInfo ('"
                    + entrypoint.getQualifiedName() + "' and '" + type.getQualifiedName()
                    + "'). An extension.json describes exactly one entrypoint, so only a single class "
                    + "per compilation may be annotated.", type);
        }
    }

    /** Validates the entrypoint and, if it is sound, writes the descriptor. */
    private void generate(TypeElement type) {
        final ExtensionInfo info = type.getAnnotation(ExtensionInfo.class);
        if (info == null) {
            // Cannot happen: the element was collected through exactly this annotation.
            return;
        }

        boolean valid = validateEntrypointShape(type);
        valid &= validateSuperclass(type);

        final String name = resolveName(info, type);
        valid &= name != null;

        final List<String> dependencies = resolveDependencies(info, type);
        final List<String> artifacts = new ArrayList<>();
        valid &= collectArtifacts(info, type, artifacts);

        final List<Repository> repositories = new ArrayList<>(List.of(info.repositories()));
        valid &= validateRepositories(repositories, type);

        if (!valid) {
            return;
        }

        // The binary name, not the canonical one: ExtensionManager resolves the entrypoint with
        // Class.forName(), which expects a nested class as 'com.example.Outer$Inner'.
        final String entrypointName = processingEnv.getElementUtils().getBinaryName(type).toString();

        final String json = render(
                name,
                entrypointName,
                resolveVersion(info, type),
                List.of(info.authors()),
                dependencies,
                repositories,
                artifacts);

        write(json, type);
    }

    /**
     * Checks everything {@code ExtensionManager} needs to instantiate the entrypoint reflectively.
     *
     * <p>Mirrors {@code ExtensionManager#loadExtension}, which does
     * {@code Class.forName(...).asSubclass(Extension.class).getDeclaredConstructor()} followed by
     * {@code constructor.setAccessible(true)} and {@code newInstance()}. Because of the
     * {@code setAccessible(true)} a non-public class or constructor still works at runtime, so those
     * only produce a warning - everything that genuinely breaks instantiation is an error.
     */
    private boolean validateEntrypointShape(TypeElement type) {
        boolean valid = true;

        if (type.getKind() != ElementKind.CLASS) {
            error("@ExtensionInfo can only be applied to a class, but '" + type.getQualifiedName()
                    + "' is a " + type.getKind().name().toLowerCase()
                    + ". The entrypoint has to be a class extending " + EXTENSION_CLASS + ".", type);
            return false;
        }

        if (type.getModifiers().contains(Modifier.ABSTRACT)) {
            error("The extension entrypoint '" + type.getQualifiedName()
                    + "' must not be abstract: ExtensionManager instantiates it reflectively.", type);
            valid = false;
        }

        final NestingKind nesting = type.getNestingKind();
        if (nesting != NestingKind.TOP_LEVEL) {
            if (nesting != NestingKind.MEMBER) {
                error("The extension entrypoint '" + type.getQualifiedName()
                        + "' must be a top-level or static nested class, not a local or anonymous one.",
                        type);
                valid = false;
            } else if (!type.getModifiers().contains(Modifier.STATIC)) {
                error("The extension entrypoint '" + type.getQualifiedName()
                        + "' is an inner class. Inner classes have no no-arg constructor (they capture "
                        + "their enclosing instance) and cannot be instantiated by ExtensionManager. "
                        + "Make it static or move it to its own file.", type);
                valid = false;
            }
        }

        final List<ExecutableElement> constructors =
                ElementFilter.constructorsIn(type.getEnclosedElements());
        if (constructors.isEmpty()) {
            // Only the implicit default constructor exists, which is always a valid no-arg one.
            if (!type.getModifiers().contains(Modifier.PUBLIC)) {
                warning("The extension entrypoint '" + type.getQualifiedName() + "' is not public. "
                        + "ExtensionManager can still instantiate it because it calls "
                        + "setAccessible(true), but making it public is strongly recommended.", type);
            }
            return valid;
        }

        ExecutableElement noArg = null;
        for (ExecutableElement constructor : constructors) {
            if (constructor.getParameters().isEmpty()) {
                noArg = constructor;
                break;
            }
        }

        if (noArg == null) {
            error("The extension entrypoint '" + type.getQualifiedName()
                    + "' has no no-arg constructor. ExtensionManager instantiates the entrypoint via "
                    + "getDeclaredConstructor().newInstance() and cannot supply any arguments.", type);
            return false;
        }

        if (!noArg.getModifiers().contains(Modifier.PUBLIC)
                || !type.getModifiers().contains(Modifier.PUBLIC)) {
            warning("The extension entrypoint '" + type.getQualifiedName()
                    + "' or its no-arg constructor is not public. ExtensionManager can still "
                    + "instantiate it because it calls setAccessible(true), but declaring both public "
                    + "is strongly recommended.", type);
        }
        return valid;
    }

    /**
     * Verifies that the entrypoint extends {@code net.minestom.server.extensions.Extension}.
     *
     * <p>The check walks the superclass chain and compares fully qualified names, so no Minestom
     * class ever has to be referenced from this module. When {@code Extension} is not on the compile
     * classpath at all the check is skipped silently: a project may well compile its extension
     * against a provided-scope Minestom that is not visible during this particular compilation, and
     * failing there would be worse than not checking.
     */
    private boolean validateSuperclass(TypeElement type) {
        if (processingEnv.getElementUtils().getTypeElement(EXTENSION_CLASS) == null) {
            // Extension is not on the classpath - nothing to check against.
            return true;
        }

        TypeMirror current = type.getSuperclass();
        while (current.getKind() == TypeKind.DECLARED) {
            final Element element = ((DeclaredType) current).asElement();
            if (!(element instanceof TypeElement superType)) {
                break;
            }
            if (superType.getQualifiedName().contentEquals(EXTENSION_CLASS)) {
                return true;
            }
            current = superType.getSuperclass();
        }

        error("The extension entrypoint '" + type.getQualifiedName() + "' does not extend "
                + EXTENSION_CLASS + ". ExtensionManager casts the entrypoint to that type and refuses "
                + "to load the extension otherwise.", type);
        return false;
    }

    /** Resolves the extension name from the option or the annotation, or {@code null} if invalid. */
    private String resolveName(ExtensionInfo info, TypeElement type) {
        final String override = processingEnv.getOptions().get(OPTION_NAME);
        final boolean overridden = override != null && !override.isBlank();
        final String name = overridden ? override.trim() : info.name();

        if (name.isBlank()) {
            error("The extension name is empty. Set name() on @ExtensionInfo or pass -A"
                    + OPTION_NAME + "=<name>.", type);
            return null;
        }

        if (!NAME_PATTERN.matcher(name).matches()) {
            error("The extension name '" + name + "' " + (overridden ? "(from -A" + OPTION_NAME + ") " : "")
                    + "is invalid: it must match " + EXTENSION_NAME_REGEX
                    + ", i.e. start with a letter, contain only letters, digits and underscores, and be "
                    + "at least two characters long. The runtime would reject it with INVALID_NAME.",
                    type);
            return null;
        }
        return name;
    }

    /**
     * Resolves the version from the option or the annotation. Returns {@code null} when neither is
     * set, in which case the field is omitted and the runtime defaults it to {@code "Unspecified"}.
     */
    private String resolveVersion(ExtensionInfo info, TypeElement type) {
        final String override = processingEnv.getOptions().get(OPTION_VERSION);
        if (override != null && !override.isBlank()) {
            return override.trim();
        }
        if (!info.version().isBlank()) {
            return info.version();
        }

        warning("The extension '" + type.getQualifiedName() + "' does not declare a version. Set "
                + "version() on @ExtensionInfo or pass -A" + OPTION_VERSION + "=<version>. The "
                + "version field is omitted and the runtime will report 'Unspecified'.", type);
        return null;
    }

    /** Deduplicates the declared extension dependencies, warning about every duplicate. */
    private List<String> resolveDependencies(ExtensionInfo info, TypeElement type) {
        final Set<String> unique = new LinkedHashSet<>();
        for (String dependency : info.dependencies()) {
            if (!unique.add(dependency)) {
                warning("The extension dependency '" + dependency + "' is declared more than once on '"
                        + type.getQualifiedName() + "'. The duplicate is ignored.", type);
            }
        }
        return List.copyOf(unique);
    }

    /**
     * Collects the Maven coordinates, erroring on blank ones, warning about malformed ones and
     * dropping duplicates.
     *
     * <p>Duplicates are worth a diagnostic because {@code ExtensionManager.loadDependencies}
     * resolves each coordinate it is given, so a repeated entry costs a second resolution and adds
     * the same URL to the {@code ExtensionClassLoader} twice.
     */
    private boolean collectArtifacts(ExtensionInfo info, TypeElement type, List<String> target) {
        boolean valid = true;
        final Set<String> unique = new LinkedHashSet<>();
        for (ExternalDependency dependency : info.externalDependencies()) {
            final String coordinate = dependency.value();
            if (coordinate.isBlank()) {
                error("An @ExternalDependency of '" + type.getQualifiedName() + "' has an empty "
                        + "coordinate. Expected group:artifact:version.", type);
                valid = false;
                continue;
            }
            if (!COORDINATE_PATTERN.matcher(coordinate).matches()) {
                warning("The external dependency '" + coordinate + "' of '" + type.getQualifiedName()
                        + "' does not look like a Maven coordinate (group:artifact:version). The "
                        + "runtime resolver will most likely fail to resolve it.", type);
            }
            if (!unique.add(coordinate)) {
                warning("The external dependency '" + coordinate + "' is declared more than once on '"
                        + type.getQualifiedName() + "'. The duplicate is ignored.", type);
            }
        }
        target.addAll(unique);
        return valid;
    }

    /**
     * Validates that every repository has a usable name and an http(s) URL, and removes duplicates
     * from the given list in place.
     *
     * <p>The same name declared with two different URLs is an error rather than a warning: which of
     * the two the runtime would end up using is not something the author can have meant either way.
     */
    private boolean validateRepositories(List<Repository> repositories, TypeElement type) {
        boolean valid = true;
        final Map<String, Repository> byName = new LinkedHashMap<>();
        for (Repository repository : repositories) {
            if (repository.name().isBlank()) {
                error("A @Repository of '" + type.getQualifiedName() + "' has an empty name. "
                        + "ExtensionManager rejects repositories without a name at runtime.", type);
                valid = false;
            }

            final String url = repository.url();
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                error("The repository URL '" + url + "' of '" + type.getQualifiedName()
                        + "' is invalid: repository URLs must start with 'http://' or 'https://'.",
                        type);
                valid = false;
            }

            final Repository existing = byName.putIfAbsent(repository.name(), repository);
            if (existing == null) {
                continue;
            }
            if (existing.url().equals(url)) {
                warning("The repository '" + repository.name() + "' is declared more than once on '"
                        + type.getQualifiedName() + "'. The duplicate is ignored.", type);
            } else {
                error("The repository name '" + repository.name() + "' is declared twice on '"
                        + type.getQualifiedName() + "' with different URLs ('" + existing.url()
                        + "' and '" + url + "'). Repository names must be unique.", type);
                valid = false;
            }
        }

        repositories.clear();
        repositories.addAll(byName.values());
        return valid;
    }

    /**
     * Renders the descriptor. Empty collections are omitted entirely instead of being written as
     * empty arrays - {@code DiscoveredExtension} defaults every missing field, so the smaller file is
     * equivalent and easier to read inside a jar.
     */
    private String render(String name,
                          String entrypointClass,
                          String version,
                          List<String> authors,
                          List<String> dependencies,
                          List<Repository> repositories,
                          List<String> artifacts) {
        // Rendering goes through ExtensionDescriptor so the build plugins, which rewrite this same
        // file to add the dependencies declared in the build, cannot drift from this format.
        return new ExtensionDescriptor()
                .name(name)
                .entrypoint(entrypointClass)
                .version(version)
                .authors(authors)
                .dependencies(dependencies)
                .repositories(repositories.stream()
                        .map(r -> new ExtensionDescriptor.RepositoryEntry(r.name(), r.url()))
                        .toList())
                .artifacts(artifacts)
                .toJson();
    }

    /** Writes the descriptor to the root of the class output. */
    private void write(String json, TypeElement type) {
        final Filer filer = processingEnv.getFiler();
        try {
            final FileObject resource =
                    filer.createResource(StandardLocation.CLASS_OUTPUT, "", OUTPUT_FILE, type);
            try (Writer writer = resource.openWriter()) {
                writer.write(json);
            }
        } catch (IOException e) {
            error("Could not write " + OUTPUT_FILE + ": " + e, type);
        }
    }

    private void error(String message, Element element) {
        messager().printMessage(Diagnostic.Kind.ERROR, message, element);
    }

    private void warning(String message, Element element) {
        messager().printMessage(Diagnostic.Kind.WARNING, message, element);
    }

    private Messager messager() {
        return processingEnv.getMessager();
    }
}
