package net.onelitefeather.minestom.extensions.processor;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares the entrypoint class of a Minestom extension and every piece of metadata that ends up
 * in the generated {@code extension.json}.
 *
 * <p>Annotate the class that extends {@code net.minestom.server.extensions.Extension} with this
 * annotation and the {@link ExtensionInfoProcessor} writes an {@code extension.json} into the root
 * of the compiled output (and therefore into the root of the resulting jar), which is exactly where
 * {@code ExtensionManager} looks for it at runtime.
 *
 * <h2>The entrypoint is never declared</h2>
 * There is deliberately no {@code entrypoint()} element. The entrypoint is always the fully
 * qualified name of the annotated class, derived by the processor. That removes the single most
 * common source of a broken {@code extension.json}: a hand written entrypoint string that silently
 * drifts away from the class it is supposed to point at after a rename or a package move.
 *
 * <h2>Why external dependencies are modelled flat</h2>
 * {@link #repositories()} and {@link #externalDependencies()} are two independent flat arrays of
 * annotations instead of one nested {@code @ExternalDependencies(...)} wrapper. In the generated
 * JSON both end up inside the single {@code externalDependencies} object, but for the user the flat
 * form reads better: an extension that only needs artifacts from Maven Central never has to mention
 * a wrapper type at all, and both lists stay at the same indentation level as {@link #authors()}
 * and {@link #dependencies()} instead of being pushed one level deeper. The nesting that the
 * runtime format requires is an implementation detail of the JSON, not something the author of an
 * extension should have to type.
 *
 * <h2>Example</h2>
 * <pre>{@code
 * @ExtensionInfo(
 *         name = "MyExtension",
 *         version = "1.0.0",
 *         authors = {"Alice", "Bob"},
 *         dependencies = {"SomeOtherExtension"},
 *         repositories = {
 *                 @Repository(name = "central", url = "https://repo1.maven.org/maven2/")
 *         },
 *         externalDependencies = {
 *                 @ExternalDependency("com.google.guava:guava:33.4.0-jre")
 *         }
 * )
 * public final class MyExtension extends Extension {
 *
 *     @Override
 *     public void initialize() {
 *         // ...
 *     }
 * }
 * }</pre>
 *
 * produces
 *
 * <pre>{@code
 * {
 *   "name": "MyExtension",
 *   "entrypoint": "com.example.MyExtension",
 *   "version": "1.0.0",
 *   "authors": [
 *     "Alice",
 *     "Bob"
 *   ],
 *   "dependencies": [
 *     "SomeOtherExtension"
 *   ],
 *   "externalDependencies": {
 *     "repositories": [
 *       {
 *         "name": "central",
 *         "url": "https://repo1.maven.org/maven2/"
 *       }
 *     ],
 *     "artifacts": [
 *       "com.google.guava:guava:33.4.0-jre"
 *     ]
 *   }
 * }
 * }</pre>
 *
 * <h2>Constraints checked at compile time</h2>
 * <ul>
 *     <li>Only <b>one</b> class per compilation may carry this annotation - an
 *     {@code extension.json} has exactly one entrypoint.</li>
 *     <li>{@link #name()} must match {@code [A-Za-z][_A-Za-z0-9]+}.</li>
 *     <li>The annotated type must be a non-abstract class with a no-arg constructor, and it must
 *     not be an inner (non-static nested) class - {@code ExtensionManager} instantiates it
 *     reflectively.</li>
 *     <li>Repository URLs must start with {@code http://} or {@code https://}.</li>
 * </ul>
 *
 * @see ExtensionInfoProcessor
 * @see Repository
 * @see ExternalDependency
 * @since 2.0.0
 */
@Documented
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.TYPE)
public @interface ExtensionInfo {

    /**
     * The unique name of the extension.
     *
     * <p>Must match {@code [A-Za-z][_A-Za-z0-9]+}: it has to start with a letter, may only contain
     * letters, digits and underscores, and has to be at least two characters long. A value that
     * does not match fails the build, because the runtime would reject the extension with
     * {@code INVALID_NAME}.
     *
     * <p>Can be overridden at compile time with {@code -Aminestom.extension.name=...}, which is
     * useful when the name has to be injected by the build instead of being hardcoded.
     *
     * @return the extension name
     */
    String name();

    /**
     * The version of the extension, for example {@code "1.0.0"}.
     *
     * <p>Defaults to the empty string. When it is left empty and no
     * {@code -Aminestom.extension.version=...} compiler option is given, the processor emits a
     * warning and omits the field; the runtime then reports the version as {@code "Unspecified"}.
     * The usual setup is to leave this element empty and let the build pass the project version via
     * {@code -Aminestom.extension.version=$version}, so that the version lives in exactly one place.
     *
     * @return the extension version, or the empty string to fall back to the compiler option
     */
    String version() default "";

    /**
     * The people who wrote this extension, purely informational.
     *
     * <p>Omitted from the generated JSON when empty.
     *
     * @return the author names
     */
    String[] authors() default {};

    /**
     * Names of other extensions that must be loaded before this one.
     *
     * <p>Each entry is the {@link #name()} of another extension, not a Maven coordinate - use
     * {@link #externalDependencies()} for those. The runtime refuses to load an extension whose
     * declared dependency is missing, and it wires the class loader of every dependency as a parent
     * of this extension's class loader. Duplicate entries produce a warning and are collapsed.
     *
     * <p>Omitted from the generated JSON when empty.
     *
     * @return the names of the extensions this one depends on
     */
    String[] dependencies() default {};

    /**
     * Maven artifacts that are downloaded at startup and added to this extension's class loader.
     *
     * <p>Every entry is a single Maven coordinate wrapped in {@link ExternalDependency}, for example
     * {@code @ExternalDependency("com.google.guava:guava:33.4.0-jre")}. They are resolved against
     * Maven Central plus whatever is listed in {@link #repositories()}.
     *
     * <p>Ends up as {@code externalDependencies.artifacts} in the generated JSON; the whole
     * {@code externalDependencies} object is omitted when neither an artifact nor a repository is
     * declared.
     *
     * @return the Maven coordinates to resolve at runtime
     */
    ExternalDependency[] externalDependencies() default {};

    /**
     * Additional Maven repositories used to resolve {@link #externalDependencies()}.
     *
     * <p>Ends up as {@code externalDependencies.repositories} in the generated JSON. URLs must start
     * with {@code http://} or {@code https://}, otherwise the build fails.
     *
     * @return the repositories to resolve external dependencies from
     */
    Repository[] repositories() default {};
}
