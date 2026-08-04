package net.onelitefeather.minestom.extensions.processor;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * A single Maven repository that {@link ExtensionInfo#externalDependencies()} are resolved against.
 *
 * <p>This annotation is only ever used inside {@link ExtensionInfo#repositories()}. It is declared
 * as a top-level annotation type rather than as a nested one so that the usage site stays flat and
 * readable - see the class documentation of {@link ExtensionInfo} for the reasoning. Applying it
 * directly to a class does nothing; the processor only looks at {@link ExtensionInfo}.
 *
 * <p>Both elements are mandatory and must be non-blank: {@code ExtensionManager} rejects a
 * repository with a missing {@code name} or {@code url} at runtime, so the processor rejects it at
 * compile time. The URL has to start with {@code http://} or {@code https://}.
 *
 * <h2>Example</h2>
 * <pre>{@code
 * @ExtensionInfo(
 *         name = "MyExtension",
 *         repositories = {
 *                 @Repository(name = "onelitefeather", url = "https://repo.onelitefeather.dev/onelitefeather")
 *         },
 *         externalDependencies = {
 *                 @ExternalDependency("net.onelitefeather:some-library:1.2.3")
 *         }
 * )
 * public final class MyExtension extends Extension { }
 * }</pre>
 *
 * which contributes
 *
 * <pre>{@code
 * "externalDependencies": {
 *   "repositories": [
 *     {
 *       "name": "onelitefeather",
 *       "url": "https://repo.onelitefeather.dev/onelitefeather"
 *     }
 *   ],
 *   "artifacts": [
 *     "net.onelitefeather:some-library:1.2.3"
 *   ]
 * }
 * }</pre>
 *
 * @see ExtensionInfo#repositories()
 * @since 2.0.0
 */
@Documented
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.TYPE)
public @interface Repository {

    /**
     * The identifier of the repository, for example {@code "central"}.
     *
     * <p>Must not be blank. It is only used to identify the repository in logs and in the resolver.
     *
     * @return the repository name
     */
    String name();

    /**
     * The base URL of the repository, for example {@code "https://repo1.maven.org/maven2/"}.
     *
     * <p>Must start with {@code http://} or {@code https://}; anything else fails the build, since
     * the runtime dependency resolver can only speak HTTP.
     *
     * @return the repository URL
     */
    String url();
}
