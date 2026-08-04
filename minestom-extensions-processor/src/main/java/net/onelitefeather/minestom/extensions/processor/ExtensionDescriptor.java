package net.onelitefeather.minestom.extensions.processor;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * The content of an {@code extension.json}, and the single place that renders it.
 *
 * <p>This is shared API rather than an internal detail of the processor: the Gradle and Maven
 * plugins read a descriptor the processor already generated, add the external dependencies declared
 * in the build, and write it back. Routing every writer through this class is what guarantees they
 * all produce byte-identical formatting - in particular the escaping of non-ASCII characters, which
 * a general-purpose JSON library would not do and whose absence corrupts author names on any build
 * that does not run in UTF-8.
 *
 * <p>Deliberately dependency-free, like the rest of this module. Reading an existing descriptor is
 * not offered here; callers that need it (the plugins) already have a JSON parser available and
 * populate an instance themselves.
 *
 * <p>Instances are mutable and not thread-safe.
 */
public final class ExtensionDescriptor {

    private String name;
    private String entrypoint;
    private String version;
    private final List<String> authors = new ArrayList<>();
    private final List<String> dependencies = new ArrayList<>();
    private final List<RepositoryEntry> repositories = new ArrayList<>();
    private final List<String> artifacts = new ArrayList<>();

    /**
     * A Maven repository the runtime should search for external dependencies.
     *
     * @param name identifier of the repository, must not be blank
     * @param url  base url of the repository
     */
    public record RepositoryEntry(String name, String url) {

        /**
         * @param name identifier of the repository
         * @param url  base url of the repository
         */
        public RepositoryEntry {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(url, "url");
        }
    }

    /**
     * Reads a descriptor back, typically one the annotation processor just generated.
     *
     * <p>Used by the Gradle and Maven plugins, which add the dependencies declared in the build to
     * what the annotation already stated. Anything not understood is rejected rather than dropped
     * silently - losing a field on a rewrite would only surface as a runtime failure on the server.
     *
     * @param json the descriptor content
     * @return the parsed descriptor
     * @throws IllegalArgumentException if the content is malformed or carries an unknown field
     */
    public static ExtensionDescriptor fromJson(String json) {
        final Map<String, Object> root = JsonReader.parseObject(json);

        for (String key : root.keySet()) {
            if (!KNOWN_FIELDS.contains(key)) {
                throw new IllegalArgumentException("Unknown field '" + key + "' in extension.json. "
                        + "Known fields are " + KNOWN_FIELDS + ".");
            }
        }

        final ExtensionDescriptor descriptor = new ExtensionDescriptor()
                .name(string(root, "name"))
                .entrypoint(string(root, "entrypoint"))
                .version(string(root, "version"))
                .authors(stringList(root, "authors"))
                .dependencies(stringList(root, "dependencies"));

        final Object external = root.get("externalDependencies");
        if (external != null) {
            if (!(external instanceof Map<?, ?> map)) {
                throw new IllegalArgumentException("'externalDependencies' must be an object");
            }
            @SuppressWarnings("unchecked")
            final Map<String, Object> externals = (Map<String, Object>) map;

            descriptor.artifacts(stringList(externals, "artifacts"));

            final Object repositories = externals.get("repositories");
            if (repositories != null) {
                final List<RepositoryEntry> entries = new ArrayList<>();
                for (Object element : list(repositories, "repositories")) {
                    if (!(element instanceof Map<?, ?> raw)) {
                        throw new IllegalArgumentException("Each repository must be an object");
                    }
                    @SuppressWarnings("unchecked")
                    final Map<String, Object> repository = (Map<String, Object>) raw;
                    entries.add(new RepositoryEntry(
                            string(repository, "name"), string(repository, "url")));
                }
                descriptor.repositories(entries);
            }
        }
        return descriptor;
    }

    private static final Set<String> KNOWN_FIELDS = Set.of(
            "name", "entrypoint", "version", "authors", "dependencies", "externalDependencies", "meta");

    private static String string(Map<String, Object> object, String key) {
        final Object value = object.get(key);
        if (value == null) {
            return null;
        }
        if (!(value instanceof String text)) {
            throw new IllegalArgumentException("'" + key + "' must be a string");
        }
        return text;
    }

    private static List<String> stringList(Map<String, Object> object, String key) {
        final Object value = object.get(key);
        if (value == null) {
            return List.of();
        }
        final List<String> values = new ArrayList<>();
        for (Object element : list(value, key)) {
            if (!(element instanceof String text)) {
                throw new IllegalArgumentException("'" + key + "' must contain only strings");
            }
            values.add(text);
        }
        return values;
    }

    private static List<?> list(Object value, String key) {
        if (!(value instanceof List<?> values)) {
            throw new IllegalArgumentException("'" + key + "' must be an array");
        }
        return values;
    }

    /**
     * @param name the extension name
     * @return this descriptor
     */
    public ExtensionDescriptor name(String name) {
        this.name = name;
        return this;
    }

    /**
     * @param entrypoint binary name of the class extending {@code Extension}
     * @return this descriptor
     */
    public ExtensionDescriptor entrypoint(String entrypoint) {
        this.entrypoint = entrypoint;
        return this;
    }

    /**
     * @param version the extension version, or {@code null} to omit the field
     * @return this descriptor
     */
    public ExtensionDescriptor version(String version) {
        this.version = version;
        return this;
    }

    /**
     * @param authors the authors to add
     * @return this descriptor
     */
    public ExtensionDescriptor authors(List<String> authors) {
        this.authors.addAll(authors);
        return this;
    }

    /**
     * @param dependencies names of other extensions this one depends on
     * @return this descriptor
     */
    public ExtensionDescriptor dependencies(List<String> dependencies) {
        this.dependencies.addAll(dependencies);
        return this;
    }

    /**
     * Adds repositories, ignoring any whose name is already present.
     *
     * @param repositories the repositories to add
     * @return this descriptor
     */
    public ExtensionDescriptor repositories(List<RepositoryEntry> repositories) {
        final Set<String> known = new LinkedHashSet<>();
        this.repositories.forEach(existing -> known.add(existing.name()));
        for (RepositoryEntry repository : repositories) {
            if (known.add(repository.name())) {
                this.repositories.add(repository);
            }
        }
        return this;
    }

    /**
     * Adds Maven coordinates, ignoring duplicates.
     *
     * @param artifacts coordinates as {@code group:artifact:version}
     * @return this descriptor
     */
    public ExtensionDescriptor artifacts(List<String> artifacts) {
        for (String artifact : artifacts) {
            if (!this.artifacts.contains(artifact)) {
                this.artifacts.add(artifact);
            }
        }
        return this;
    }

    /**
     * @return the extension name, or {@code null} if unset
     */
    public String name() {
        return name;
    }

    /**
     * @return the entrypoint, or {@code null} if unset
     */
    public String entrypoint() {
        return entrypoint;
    }

    /**
     * @return the coordinates currently declared
     */
    public List<String> artifacts() {
        return List.copyOf(artifacts);
    }

    /**
     * @return the repositories currently declared
     */
    public List<RepositoryEntry> repositories() {
        return List.copyOf(repositories);
    }

    /**
     * Renders the descriptor.
     *
     * <p>Empty collections are omitted rather than written as empty arrays: {@code
     * DiscoveredExtension} defaults every missing field, so the shorter file is equivalent and
     * easier to read inside a jar.
     *
     * @return the {@code extension.json} content, pretty printed and pure ASCII
     * @throws IllegalStateException if name or entrypoint is missing
     */
    public String toJson() {
        if (name == null || name.isBlank()) {
            throw new IllegalStateException("An extension descriptor needs a name");
        }
        if (entrypoint == null || entrypoint.isBlank()) {
            throw new IllegalStateException("An extension descriptor needs an entrypoint");
        }

        final JsonWriter json = new JsonWriter();
        json.beginObject();
        json.stringMember("name", name);
        json.stringMember("entrypoint", entrypoint);

        if (version != null) {
            json.stringMember("version", version);
        }
        if (!authors.isEmpty()) {
            json.arrayMember("authors", authors);
        }
        if (!dependencies.isEmpty()) {
            json.arrayMember("dependencies", dependencies);
        }

        if (!repositories.isEmpty() || !artifacts.isEmpty()) {
            json.name("externalDependencies").beginObject();
            if (!repositories.isEmpty()) {
                json.name("repositories").beginArray();
                for (RepositoryEntry repository : repositories) {
                    json.beginObject()
                            .stringMember("name", repository.name())
                            .stringMember("url", repository.url())
                            .endObject();
                }
                json.endArray();
            }
            if (!artifacts.isEmpty()) {
                json.arrayMember("artifacts", artifacts);
            }
            json.endObject();
        }

        json.endObject();
        return json.toString();
    }
}
