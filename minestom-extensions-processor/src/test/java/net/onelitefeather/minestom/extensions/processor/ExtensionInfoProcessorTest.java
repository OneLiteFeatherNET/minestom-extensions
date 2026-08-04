package net.onelitefeather.minestom.extensions.processor;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.annotation.processing.Processor;
import javax.tools.Diagnostic;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end tests: they run the real {@code javac} over generated sources with the real processor
 * attached and assert on the generated {@code extension.json}.
 */
class ExtensionInfoProcessorTest {

    private static final String IMPORTS = """
            import net.onelitefeather.minestom.extensions.processor.ExtensionInfo;
            import net.onelitefeather.minestom.extensions.processor.ExternalDependency;
            import net.onelitefeather.minestom.extensions.processor.Repository;
            """;

    @Test
    @DisplayName("happy path: every element ends up in extension.json")
    void happyPath(@TempDir Path workDir) {
        final var result = CompilationHarness.javac(workDir)
                .source("com.example.MyExtension", """
                        package com.example;
                        %s
                        @ExtensionInfo(
                                name = "MyExtension",
                                version = "1.2.3",
                                authors = {"Alice", "Bob"},
                                dependencies = {"OtherExtension"},
                                repositories = {
                                        @Repository(name = "central", url = "https://repo1.maven.org/maven2/")
                                },
                                externalDependencies = {
                                        @ExternalDependency("com.google.guava:guava:33.4.0-jre")
                                }
                        )
                        public class MyExtension {
                        }
                        """.formatted(IMPORTS))
                .compile();

        assertTrue(result.success(), result::describe);

        final JsonObject json = parse(result.extensionJson());
        assertAll(
                () -> assertEquals("MyExtension", json.get("name").getAsString()),
                () -> assertEquals("com.example.MyExtension", json.get("entrypoint").getAsString()),
                () -> assertEquals("1.2.3", json.get("version").getAsString()),
                () -> assertEquals(List.of("Alice", "Bob"), strings(json.getAsJsonArray("authors"))),
                () -> assertEquals(List.of("OtherExtension"),
                        strings(json.getAsJsonArray("dependencies"))),
                () -> assertFalse(json.has("meta"), "meta must never be generated"));

        final JsonObject external = json.getAsJsonObject("externalDependencies");
        assertNotNull(external, "externalDependencies missing");
        assertEquals(List.of("com.google.guava:guava:33.4.0-jre"),
                strings(external.getAsJsonArray("artifacts")));

        final JsonArray repositories = external.getAsJsonArray("repositories");
        assertEquals(1, repositories.size());
        final JsonObject repository = repositories.get(0).getAsJsonObject();
        assertAll(
                () -> assertEquals("central", repository.get("name").getAsString()),
                () -> assertEquals("https://repo1.maven.org/maven2/",
                        repository.get("url").getAsString()));
    }

    @Test
    @DisplayName("minimal extension: empty collections are omitted entirely")
    void minimalExtension(@TempDir Path workDir) {
        final var result = CompilationHarness.javac(workDir)
                .source("com.example.Tiny", """
                        package com.example;
                        %s
                        @ExtensionInfo(name = "Tiny", version = "1.0.0")
                        public class Tiny {
                        }
                        """.formatted(IMPORTS))
                .compile();

        assertTrue(result.success(), result::describe);

        final JsonObject json = parse(result.extensionJson());
        assertAll(
                () -> assertEquals("Tiny", json.get("name").getAsString()),
                () -> assertEquals("com.example.Tiny", json.get("entrypoint").getAsString()),
                () -> assertEquals("1.0.0", json.get("version").getAsString()),
                () -> assertFalse(json.has("authors")),
                () -> assertFalse(json.has("dependencies")),
                () -> assertFalse(json.has("externalDependencies")),
                () -> assertFalse(json.has("meta")));
    }

    @Test
    @DisplayName("entrypoint is the binary name, so nested classes use '$'")
    void entrypointIsBinaryName(@TempDir Path workDir) {
        final var result = CompilationHarness.javac(workDir)
                .source("com.example.Outer", """
                        package com.example;
                        %s
                        public class Outer {
                            @ExtensionInfo(name = "Nested", version = "1.0.0")
                            public static class Inner {
                            }
                        }
                        """.formatted(IMPORTS))
                .compile();

        assertTrue(result.success(), result::describe);
        assertEquals("com.example.Outer$Inner",
                parse(result.extensionJson()).get("entrypoint").getAsString());
    }

    @Test
    @DisplayName("-Aminestom.extension.version overrides the annotation")
    void versionOptionOverridesAnnotation(@TempDir Path workDir) {
        final var result = CompilationHarness.javac(workDir)
                .source("com.example.Versioned", """
                        package com.example;
                        %s
                        @ExtensionInfo(name = "Versioned", version = "1.0.0")
                        public class Versioned {
                        }
                        """.formatted(IMPORTS))
                .option(ExtensionInfoProcessor.OPTION_VERSION, "9.9.9-SNAPSHOT")
                .compile();

        assertTrue(result.success(), result::describe);
        assertEquals("9.9.9-SNAPSHOT", parse(result.extensionJson()).get("version").getAsString());
    }

    @Test
    @DisplayName("-Aminestom.extension.name overrides the annotation")
    void nameOptionOverridesAnnotation(@TempDir Path workDir) {
        final var result = CompilationHarness.javac(workDir)
                .source("com.example.Named", """
                        package com.example;
                        %s
                        @ExtensionInfo(name = "FromAnnotation", version = "1.0.0")
                        public class Named {
                        }
                        """.formatted(IMPORTS))
                .option(ExtensionInfoProcessor.OPTION_NAME, "FromOption")
                .compile();

        assertTrue(result.success(), result::describe);
        assertEquals("FromOption", parse(result.extensionJson()).get("name").getAsString());
    }

    @Test
    @DisplayName("missing version is a warning and the field is omitted")
    void missingVersionIsAWarning(@TempDir Path workDir) {
        final var result = CompilationHarness.javac(workDir)
                .source("com.example.NoVersion", """
                        package com.example;
                        %s
                        @ExtensionInfo(name = "NoVersion")
                        public class NoVersion {
                        }
                        """.formatted(IMPORTS))
                .compile();

        assertTrue(result.success(), result::describe);
        assertTrue(result.firstMessageContaining(Diagnostic.Kind.WARNING, "does not declare a version")
                .isPresent(), result::describe);
        assertFalse(parse(result.extensionJson()).has("version"));
    }

    @Test
    @DisplayName("duplicate dependencies warn and are collapsed")
    void duplicateDependenciesWarn(@TempDir Path workDir) {
        final var result = CompilationHarness.javac(workDir)
                .source("com.example.Dupes", """
                        package com.example;
                        %s
                        @ExtensionInfo(
                                name = "Dupes",
                                version = "1.0.0",
                                dependencies = {"A_Extension", "B_Extension", "A_Extension"}
                        )
                        public class Dupes {
                        }
                        """.formatted(IMPORTS))
                .compile();

        assertTrue(result.success(), result::describe);
        assertTrue(result.firstMessageContaining(Diagnostic.Kind.WARNING, "declared more than once")
                .isPresent(), result::describe);
        assertEquals(List.of("A_Extension", "B_Extension"),
                strings(parse(result.extensionJson()).getAsJsonArray("dependencies")));
    }

    @Test
    @DisplayName("an invalid name fails the compilation")
    void invalidNameFailsCompilation(@TempDir Path workDir) {
        final var result = CompilationHarness.javac(workDir)
                .source("com.example.Bad", """
                        package com.example;
                        %s
                        @ExtensionInfo(name = "1nvalid Name!", version = "1.0.0")
                        public class Bad {
                        }
                        """.formatted(IMPORTS))
                .compile();

        assertFalse(result.success(), "compilation should have failed");
        assertTrue(result.firstMessageContaining(Diagnostic.Kind.ERROR, "is invalid: it must match")
                .isPresent(), result::describe);
        assertFalse(result.hasExtensionJson(), "no descriptor may be written on error");
    }

    @Test
    @DisplayName("two annotated classes fail the compilation")
    void twoAnnotatedClassesFailCompilation(@TempDir Path workDir) {
        final var result = CompilationHarness.javac(workDir)
                .source("com.example.First", """
                        package com.example;
                        %s
                        @ExtensionInfo(name = "First", version = "1.0.0")
                        public class First {
                        }
                        """.formatted(IMPORTS))
                .source("com.example.Second", """
                        package com.example;
                        %s
                        @ExtensionInfo(name = "Second", version = "1.0.0")
                        public class Second {
                        }
                        """.formatted(IMPORTS))
                .compile();

        assertFalse(result.success(), "compilation should have failed");
        assertTrue(result.firstMessageContaining(
                        Diagnostic.Kind.ERROR, "more than one class annotated with @ExtensionInfo")
                .isPresent(), result::describe);
        assertFalse(result.hasExtensionJson(), "no descriptor may be written on error");
    }

    @Test
    @DisplayName("a repository URL without http(s) scheme fails the compilation")
    void invalidRepositoryUrlFailsCompilation(@TempDir Path workDir) {
        final var result = CompilationHarness.javac(workDir)
                .source("com.example.BadRepo", """
                        package com.example;
                        %s
                        @ExtensionInfo(
                                name = "BadRepo",
                                version = "1.0.0",
                                repositories = {@Repository(name = "local", url = "file:///tmp/repo")}
                        )
                        public class BadRepo {
                        }
                        """.formatted(IMPORTS))
                .compile();

        assertFalse(result.success(), "compilation should have failed");
        assertTrue(result.firstMessageContaining(Diagnostic.Kind.ERROR, "must start with 'http://'")
                .isPresent(), result::describe);
        assertFalse(result.hasExtensionJson(), "no descriptor may be written on error");
    }

    @Test
    @DisplayName("strings are escaped according to the JSON spec")
    void stringsAreEscaped(@TempDir Path workDir) {
        // The generated source contains a string with a quote, a backslash, a tab, a
        // newline and the control character 0x01.
        final String author = "He said \\\"hi\\\" \\\\ and\\ttab\\nnewline\\u0001";
        final var result = CompilationHarness.javac(workDir)
                .source("com.example.Escapes", """
                        package com.example;
                        %s
                        @ExtensionInfo(name = "Escapes", version = "1.0.0", authors = {"%s"})
                        public class Escapes {
                        }
                        """.formatted(IMPORTS, author))
                .compile();

        assertTrue(result.success(), result::describe);

        final String raw = result.extensionJson();
        assertAll(
                () -> assertTrue(raw.contains("\\\""), "quote must be escaped: " + raw),
                () -> assertTrue(raw.contains("\\\\"), "backslash must be escaped: " + raw),
                () -> assertTrue(raw.contains("\\t"), "tab must be escaped: " + raw),
                () -> assertTrue(raw.contains("\\n"), "newline must be escaped: " + raw),
                () -> assertTrue(raw.contains("\\u0001"), "control char must be escaped: " + raw));

        // The decisive check: a JSON parser has to round-trip the exact original string.
        assertEquals("He said \"hi\" \\ and\ttab\nnewline" + (char) 0x01,
                strings(parse(raw).getAsJsonArray("authors")).get(0));
    }

    @Test
    @DisplayName("the superclass check is skipped when Extension is not on the classpath")
    void superclassCheckSkippedWithoutExtensionOnClasspath(@TempDir Path workDir) {
        final var result = CompilationHarness.javac(workDir)
                .source("com.example.Standalone", """
                        package com.example;
                        %s
                        @ExtensionInfo(name = "Standalone", version = "1.0.0")
                        public class Standalone {
                        }
                        """.formatted(IMPORTS))
                .compile();

        assertTrue(result.success(), result::describe);
        assertTrue(result.firstMessageContaining(Diagnostic.Kind.ERROR, "does not extend").isEmpty(),
                result::describe);
        assertTrue(result.hasExtensionJson());
    }

    @Test
    @DisplayName("extending Extension transitively is accepted")
    void transitiveExtensionSubclassIsAccepted(@TempDir Path workDir) {
        final var result = CompilationHarness.javac(workDir)
                .withExtensionClass()
                .source("com.example.Base", """
                        package com.example;
                        public abstract class Base extends net.minestom.server.extensions.Extension {
                        }
                        """)
                .source("com.example.Real", """
                        package com.example;
                        %s
                        @ExtensionInfo(name = "Real", version = "1.0.0")
                        public class Real extends Base {
                        }
                        """.formatted(IMPORTS))
                .compile();

        assertTrue(result.success(), result::describe);
        assertEquals("com.example.Real", parse(result.extensionJson()).get("entrypoint").getAsString());
    }

    @Test
    @DisplayName("not extending Extension fails when Extension is on the classpath")
    void notExtendingExtensionFails(@TempDir Path workDir) {
        final var result = CompilationHarness.javac(workDir)
                .withExtensionClass()
                .source("com.example.NotAnExtension", """
                        package com.example;
                        %s
                        @ExtensionInfo(name = "NotAnExtension", version = "1.0.0")
                        public class NotAnExtension {
                        }
                        """.formatted(IMPORTS))
                .compile();

        assertFalse(result.success(), "compilation should have failed");
        assertTrue(result.firstMessageContaining(Diagnostic.Kind.ERROR,
                "does not extend net.minestom.server.extensions.Extension").isPresent(),
                result::describe);
        assertFalse(result.hasExtensionJson());
    }

    @Test
    @DisplayName("an abstract entrypoint fails the compilation")
    void abstractEntrypointFails(@TempDir Path workDir) {
        final var result = CompilationHarness.javac(workDir)
                .source("com.example.AbstractExtension", """
                        package com.example;
                        %s
                        @ExtensionInfo(name = "AbstractExtension", version = "1.0.0")
                        public abstract class AbstractExtension {
                        }
                        """.formatted(IMPORTS))
                .compile();

        assertFalse(result.success(), "compilation should have failed");
        assertTrue(result.firstMessageContaining(Diagnostic.Kind.ERROR, "must not be abstract")
                .isPresent(), result::describe);
    }

    @Test
    @DisplayName("a non-static inner class fails the compilation")
    void innerClassEntrypointFails(@TempDir Path workDir) {
        final var result = CompilationHarness.javac(workDir)
                .source("com.example.Holder", """
                        package com.example;
                        %s
                        public class Holder {
                            @ExtensionInfo(name = "InnerExtension", version = "1.0.0")
                            public class Inner {
                            }
                        }
                        """.formatted(IMPORTS))
                .compile();

        assertFalse(result.success(), "compilation should have failed");
        assertTrue(result.firstMessageContaining(Diagnostic.Kind.ERROR, "is an inner class")
                .isPresent(), result::describe);
    }

    @Test
    @DisplayName("a missing no-arg constructor fails the compilation")
    void missingNoArgConstructorFails(@TempDir Path workDir) {
        final var result = CompilationHarness.javac(workDir)
                .source("com.example.NeedsArgs", """
                        package com.example;
                        %s
                        @ExtensionInfo(name = "NeedsArgs", version = "1.0.0")
                        public class NeedsArgs {
                            public NeedsArgs(String argument) {
                            }
                        }
                        """.formatted(IMPORTS))
                .compile();

        assertFalse(result.success(), "compilation should have failed");
        assertTrue(result.firstMessageContaining(Diagnostic.Kind.ERROR, "has no no-arg constructor")
                .isPresent(), result::describe);
    }

    @Test
    @DisplayName("non-ASCII characters are escaped so the file survives any build encoding")
    void nonAsciiIsEscaped(@TempDir Path workDir) {
        final var result = CompilationHarness.javac(workDir)
                .source("com.example.Umlauts", """
                        package com.example;
                        %s
                        @ExtensionInfo(
                                name = "Umlauts",
                                version = "1.0.0",
                                authors = {"J\\u00f6rg M\\u00fcller", "\\u4e2d\\u6587", "\\ud83d\\ude00"}
                        )
                        public class Umlauts {
                        }
                        """.formatted(IMPORTS))
                .compile();

        assertTrue(result.success(), result::describe);

        final String raw = result.extensionJson();
        // The whole point: the bytes on disk are pure ASCII, so neither javac's -encoding on the
        // way out nor the JVM default charset on the way in can corrupt them.
        for (int i = 0; i < raw.length(); i++) {
            final char c = raw.charAt(i);
            assertTrue(c <= 0x7e, () -> "non-ASCII character in output: " + raw);
        }

        // ...and a parser still recovers the exact original strings, surrogate pair included.
        assertEquals(List.of("Jörg Müller", "中文", "😀"),
                strings(parse(raw).getAsJsonArray("authors")));
    }

    @Test
    @DisplayName("duplicate external dependencies and repositories warn and are collapsed")
    void duplicateExternalDependenciesWarn(@TempDir Path workDir) {
        final var result = CompilationHarness.javac(workDir)
                .source("com.example.DupExternals", """
                        package com.example;
                        %s
                        @ExtensionInfo(
                                name = "DupExternals",
                                version = "1.0.0",
                                repositories = {
                                        @Repository(name = "central", url = "https://repo1.maven.org/maven2/"),
                                        @Repository(name = "central", url = "https://repo1.maven.org/maven2/")
                                },
                                externalDependencies = {
                                        @ExternalDependency("com.google.guava:guava:33.4.0-jre"),
                                        @ExternalDependency("com.google.guava:guava:33.4.0-jre")
                                }
                        )
                        public class DupExternals {
                        }
                        """.formatted(IMPORTS))
                .compile();

        assertTrue(result.success(), result::describe);

        final JsonObject external = parse(result.extensionJson())
                .getAsJsonObject("externalDependencies");
        assertAll(
                () -> assertEquals(List.of("com.google.guava:guava:33.4.0-jre"),
                        strings(external.getAsJsonArray("artifacts"))),
                () -> assertEquals(1, external.getAsJsonArray("repositories").size(),
                        "the duplicate repository must be collapsed"),
                () -> assertTrue(result.firstMessageContaining(
                                Diagnostic.Kind.WARNING, "external dependency 'com.google.guava:guava:33.4.0-jre' is declared more than once")
                        .isPresent(), result::describe),
                () -> assertTrue(result.firstMessageContaining(
                                Diagnostic.Kind.WARNING, "repository 'central' is declared more than once")
                        .isPresent(), result::describe));
    }

    @Test
    @DisplayName("the same repository name with two different URLs fails the compilation")
    void conflictingRepositoryUrlFailsCompilation(@TempDir Path workDir) {
        final var result = CompilationHarness.javac(workDir)
                .source("com.example.Conflict", """
                        package com.example;
                        %s
                        @ExtensionInfo(
                                name = "Conflict",
                                version = "1.0.0",
                                repositories = {
                                        @Repository(name = "olf", url = "https://repo.onelitefeather.dev/releases"),
                                        @Repository(name = "olf", url = "https://repo.onelitefeather.dev/snapshots")
                                }
                        )
                        public class Conflict {
                        }
                        """.formatted(IMPORTS))
                .compile();

        assertFalse(result.success(), "conflicting repository URLs must fail the build");
        assertTrue(result.firstMessageContaining(Diagnostic.Kind.ERROR, "with different URLs")
                .isPresent(), result::describe);
        assertFalse(result.hasExtensionJson(), "no descriptor may be written for a failed build");
    }

    @Test
    @DisplayName("the generated descriptor matches the contract shared with minestom-extensions")
    void generatedDescriptorMatchesSharedContract(@TempDir Path workDir) throws IOException {
        // The @ExtensionInfo that produced extension-descriptor-contract.json. Keep the two in sync:
        // minestom-extensions deserializes that same file into the real DiscoveredExtension, so this
        // test is the half that proves the processor is what actually writes it.
        final var result = CompilationHarness.javac(workDir)
                .source("com.example.ContractExtension", """
                        package com.example;
                        %s
                        @ExtensionInfo(
                                name = "ContractExtension",
                                version = "4.2.0",
                                authors = {"TheMeinerLP", "J\\u00f6rg M\\u00fcller"},
                                dependencies = {"FirstDependency", "SecondDependency"},
                                repositories = {
                                        @Repository(name = "central", url = "https://repo1.maven.org/maven2/"),
                                        @Repository(name = "onelitefeather", url = "https://repo.onelitefeather.dev/releases")
                                },
                                externalDependencies = {
                                        @ExternalDependency("com.google.guava:guava:33.4.0-jre"),
                                        @ExternalDependency("org.apache.commons:commons-lang3:3.17.0")
                                }
                        )
                        public class ContractExtension {
                        }
                        """.formatted(IMPORTS))
                .compile();

        assertTrue(result.success(), result::describe);

        final String expected;
        try (InputStream in = getClass()
                .getResourceAsStream("/extension-descriptor-contract.json")) {
            assertNotNull(in, "missing shared contract resource - is the descriptorContract "
                    + "copy task wired into the test resources?");
            expected = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }

        // Line endings are normalised rather than compared. .gitattributes checks the contract out
        // as LF everywhere, but a Windows working copy predating it — or an editor that rewrites the
        // file on save — would otherwise fail this on CRLF alone. What the contract is about is the
        // field names, their order, the indentation and the escaping.
        assertEquals(normaliseLineEndings(expected), normaliseLineEndings(result.extensionJson()),
                "the processor no longer reproduces the descriptor contract that "
                        + "minestom-extensions asserts against");
    }

    private static String normaliseLineEndings(String value) {
        return value.replace("\r\n", "\n").strip();
    }

    @Test
    @DisplayName("the processor is registered through META-INF/services")
    void processorIsRegisteredAsAService() throws IOException {
        final ClassLoader loader = ExtensionInfoProcessor.class.getClassLoader();
        try (InputStream in = loader.getResourceAsStream(
                "META-INF/services/javax.annotation.processing.Processor")) {
            assertNotNull(in, "service registration file missing");
            assertEquals(ExtensionInfoProcessor.class.getName(),
                    new String(in.readAllBytes(), StandardCharsets.UTF_8).strip());
        }

        final List<String> discovered = new ArrayList<>();
        ServiceLoader.load(Processor.class, loader)
                .forEach(processor -> discovered.add(processor.getClass().getName()));
        assertTrue(discovered.contains(ExtensionInfoProcessor.class.getName()),
                "ServiceLoader did not discover the processor, found: " + discovered);
    }

    private static JsonObject parse(String json) {
        return JsonParser.parseString(json).getAsJsonObject();
    }

    private static List<String> strings(JsonArray array) {
        final List<String> values = new ArrayList<>(array.size());
        array.forEach(element -> values.add(element.getAsString()));
        return values;
    }
}
