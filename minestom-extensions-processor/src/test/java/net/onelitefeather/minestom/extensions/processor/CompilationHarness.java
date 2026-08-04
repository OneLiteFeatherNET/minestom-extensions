package net.onelitefeather.minestom.extensions.processor;

import javax.tools.Diagnostic;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Runs {@code javac} in-process over a set of in-memory sources with {@link ExtensionInfoProcessor}
 * attached, and exposes the diagnostics plus whatever landed in the class output directory.
 */
final class CompilationHarness {

    private CompilationHarness() {
    }

    /**
     * The classpath handed to the test compilations: exactly the directory (or jar) that contains
     * the processor and its annotations.
     *
     * <p>Deliberately not {@code java.class.path} - the Gradle test worker does not necessarily put
     * the test classpath there, and more importantly this keeps the compilation classpath minimal.
     * Notably {@code net.minestom.server.extensions.Extension} is <b>not</b> on it, because this
     * module does not depend on {@code minestom-extensions}. Tests that need {@code Extension}
     * declare it as an additional source.
     */
    private static final String CLASSPATH = codeSourceOf();

    /** Builder for one compilation run. */
    static final class Builder {

        private final Path workDir;
        private final Map<String, String> sources = new LinkedHashMap<>();
        private final List<String> options = new ArrayList<>();

        private Builder(Path workDir) {
            this.workDir = workDir;
        }

        /**
         * Adds a source file.
         *
         * @param fqn     fully qualified name of the declared type
         * @param content the source code
         * @return this builder
         */
        Builder source(String fqn, String content) {
            sources.put(fqn, content);
            return this;
        }

        /**
         * Adds a {@code -A} compiler option.
         *
         * @param key   option name
         * @param value option value
         * @return this builder
         */
        Builder option(String key, String value) {
            options.add("-A" + key + "=" + value);
            return this;
        }

        /**
         * Declares a stand-in for {@code net.minestom.server.extensions.Extension}, compiled together
         * with the other sources. This is how a test puts {@code Extension} on the compilation's
         * classpath without this module depending on {@code minestom-extensions}.
         *
         * @return this builder
         */
        Builder withExtensionClass() {
            return source("net.minestom.server.extensions.Extension",
                    "package net.minestom.server.extensions;\n"
                            + "public abstract class Extension {\n"
                            + "    public void initialize() {}\n"
                            + "}\n");
        }

        /**
         * Compiles everything.
         *
         * @return the result of the compilation
         */
        Result compile() {
            final JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
            if (compiler == null) {
                throw new IllegalStateException("No system java compiler available");
            }

            final Path classOutput = workDir.resolve("classes");
            try {
                Files.createDirectories(classOutput);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }

            final javax.tools.DiagnosticCollector<JavaFileObject> diagnostics =
                    new javax.tools.DiagnosticCollector<>();

            final List<String> allOptions = new ArrayList<>(List.of(
                    "-d", classOutput.toString(),
                    "-classpath", CLASSPATH,
                    "-proc:full"));
            allOptions.addAll(options);

            final List<JavaFileObject> units = sources.entrySet().stream()
                    .map(entry -> (JavaFileObject) new InMemorySource(entry.getKey(), entry.getValue()))
                    .toList();

            final boolean success;
            try (StandardJavaFileManager fileManager =
                         compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8)) {
                final JavaCompiler.CompilationTask task =
                        compiler.getTask(null, fileManager, diagnostics, allOptions, null, units);
                // Explicit registration keeps the test independent of how the harness classpath is
                // assembled; the META-INF/services registration is verified separately.
                task.setProcessors(List.of(new ExtensionInfoProcessor()));
                success = task.call();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }

            return new Result(success, diagnostics.getDiagnostics(), classOutput);
        }
    }

    /**
     * Starts a new compilation.
     *
     * @param workDir a scratch directory, usually a JUnit {@code @TempDir}
     * @return a builder
     */
    static Builder javac(Path workDir) {
        return new Builder(workDir);
    }

    /** The outcome of one compilation. */
    record Result(boolean success,
                  List<Diagnostic<? extends JavaFileObject>> diagnostics,
                  Path classOutput) {

        /**
         * @return the raw content of the generated {@code extension.json}
         */
        String extensionJson() {
            final Path file = classOutput.resolve(ExtensionInfoProcessor.OUTPUT_FILE);
            try {
                return Files.readString(file, StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new UncheckedIOException("extension.json was not generated", e);
            }
        }

        /**
         * @return whether an {@code extension.json} was generated at all
         */
        boolean hasExtensionJson() {
            return Files.exists(classOutput.resolve(ExtensionInfoProcessor.OUTPUT_FILE));
        }

        /**
         * @param kind the diagnostic kind to filter for
         * @return all messages of that kind
         */
        List<String> messages(Diagnostic.Kind kind) {
            return diagnostics.stream()
                    .filter(diagnostic -> diagnostic.getKind() == kind)
                    .map(diagnostic -> diagnostic.getMessage(null))
                    .toList();
        }

        /**
         * @param kind     the diagnostic kind to filter for
         * @param fragment a substring to look for
         * @return the first matching message, if any
         */
        Optional<String> firstMessageContaining(Diagnostic.Kind kind, String fragment) {
            return messages(kind).stream().filter(message -> message.contains(fragment)).findFirst();
        }

        /**
         * @return every diagnostic rendered as text, for assertion failure messages
         */
        String describe() {
            return diagnostics.stream()
                    .map(diagnostic -> diagnostic.getKind() + ": " + diagnostic.getMessage(null))
                    .collect(Collectors.joining("\n"));
        }
    }

    /** A source file that lives purely in memory. */
    private static final class InMemorySource extends SimpleJavaFileObject {

        private final String content;

        private InMemorySource(String fqn, String content) {
            super(URI.create("string:///" + fqn.replace('.', '/') + Kind.SOURCE.extension),
                    Kind.SOURCE);
            this.content = content;
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return content;
        }
    }

    private static String codeSourceOf() {
        try {
            return Path.of(ExtensionInfo.class.getProtectionDomain()
                            .getCodeSource()
                            .getLocation()
                            .toURI())
                    .toString();
        } catch (URISyntaxException e) {
            throw new IllegalStateException("Cannot locate the processor classes", e);
        }
    }
}
