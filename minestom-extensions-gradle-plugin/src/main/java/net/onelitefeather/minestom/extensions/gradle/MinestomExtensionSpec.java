package net.onelitefeather.minestom.extensions.gradle;

import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;

/**
 * Configuration for the {@code net.onelitefeather.minestom-extensions} plugin, available as the
 * {@code minestomExtension} block.
 *
 * <pre>{@code
 * minestomExtension {
 *     // add a repository the runtime should search, on top of the project's own
 *     repository("onelitefeather", "https://repo.onelitefeather.dev/releases")
 *
 *     // or take full control and ignore the project repositories entirely
 *     inheritProjectRepositories = false
 * }
 * }</pre>
 */
public abstract class MinestomExtensionSpec {

    /**
     * Whether the repositories declared in the project are written into the descriptor.
     *
     * <p>Defaults to {@code true}, which is right for the common case where an extension resolves
     * its libraries from the same places the build does. Turn it off when the server should use
     * different repositories than the build machine - an internal mirror, for instance.
     *
     * @return the property, defaulting to {@code true}
     */
    public abstract Property<Boolean> getInheritProjectRepositories();

    /**
     * Whether the project version is written into the descriptor, overriding
     * {@code @ExtensionInfo(version = ...)}.
     *
     * <p>Defaults to {@code true}: the build already knows the version, and a copy kept in the
     * annotation is the one that goes stale. Turn it off to keep the version in the source.
     *
     * <p>Has no effect while the project version is Gradle's {@code unspecified} placeholder —
     * writing that into a descriptor would be worse than leaving the annotation alone.
     *
     * @return the property, defaulting to {@code true}
     */
    public abstract Property<Boolean> getUseProjectVersion();

    /**
     * Additional repositories, keyed by name.
     *
     * @return the property
     */
    public abstract MapProperty<String, String> getRepositories();

    /**
     * Adds a repository the runtime should search for external dependencies.
     *
     * @param name identifier of the repository
     * @param url  base url, must be http(s)
     */
    public void repository(String name, String url) {
        getRepositories().put(name, url);
    }
}
