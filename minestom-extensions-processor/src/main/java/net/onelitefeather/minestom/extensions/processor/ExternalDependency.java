package net.onelitefeather.minestom.extensions.processor;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * A single Maven coordinate that is downloaded at server startup and added to the class loader of
 * the extension.
 *
 * <p>This annotation is only ever used inside {@link ExtensionInfo#externalDependencies()}. It is
 * declared as a top-level annotation type rather than as a nested one so that the usage site stays
 * flat and readable - see the class documentation of {@link ExtensionInfo} for the reasoning.
 * Applying it directly to a class does nothing; the processor only looks at {@link ExtensionInfo}.
 *
 * <p>The value is passed to the runtime dependency resolver verbatim, so it has to be a plain Maven
 * coordinate in {@code group:artifact:version} form (an optional fourth {@code :classifier} segment
 * is accepted). Anything that does not look like a coordinate produces a warning; a blank value is
 * an error.
 *
 * <h2>Example</h2>
 * <pre>{@code
 * @ExtensionInfo(
 *         name = "MyExtension",
 *         externalDependencies = {
 *                 @ExternalDependency("com.google.guava:guava:33.4.0-jre"),
 *                 @ExternalDependency("org.apache.commons:commons-lang3:3.17.0")
 *         }
 * )
 * public final class MyExtension extends Extension { }
 * }</pre>
 *
 * which contributes
 *
 * <pre>{@code
 * "externalDependencies": {
 *   "artifacts": [
 *     "com.google.guava:guava:33.4.0-jre",
 *     "org.apache.commons:commons-lang3:3.17.0"
 *   ]
 * }
 * }</pre>
 *
 * @see ExtensionInfo#externalDependencies()
 * @since 2.0.0
 */
@Documented
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.TYPE)
public @interface ExternalDependency {

    /**
     * The Maven coordinate, for example {@code "com.google.guava:guava:33.4.0-jre"}.
     *
     * @return the Maven coordinate to resolve
     */
    String value();
}
