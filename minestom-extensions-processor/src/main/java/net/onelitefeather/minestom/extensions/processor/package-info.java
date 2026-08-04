/**
 * Compile-time generation of the {@code extension.json} descriptor for Minestom extensions.
 *
 * <p>Annotate the entrypoint class with
 * {@link net.onelitefeather.minestom.extensions.processor.ExtensionInfo} and
 * {@link net.onelitefeather.minestom.extensions.processor.ExtensionInfoProcessor} writes the
 * descriptor into the root of the compiled output, where {@code ExtensionManager} expects it inside
 * the finished jar.
 *
 * <p>This package has no runtime dependencies at all - not even on
 * {@code net.onelitefeather:minestom-extensions} - so that adding it to a project's annotation
 * processor path never drags Minestom onto that path.
 *
 * @since 2.0.0
 */
package net.onelitefeather.minestom.extensions.processor;
