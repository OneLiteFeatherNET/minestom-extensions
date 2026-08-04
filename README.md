# minestom-extensions

A library for bringing extensions back to [Minestom](https://github.com/Minestom/Minestom).

> This is the [OneLiteFeather](https://github.com/OneLiteFeatherNET) fork of
> [hollow-cube/minestom-ce-extensions](https://github.com/hollow-cube/minestom-ce-extensions)
> (now archived), maintained against current Minestom and published under the `net.onelitefeather`
> coordinates.
>
> **Why this fork?** CloudNet RC16 depends on this extension system, and the upstream project is
> archived, so we provide an actively maintained fork to keep it working against current Minestom
> and Java versions.

This library is not quite a drop-in replacement for the original Minestom extensions, but it is pretty close.
For many extensions it should work out of the box. If an extension references
`MinecraftServer.getExtensionManager()` this will have to be updated, see [Usage](#usage) for more information.

## Modules

| Artifact | Description |
| --- | --- |
| `net.onelitefeather:minestom-extensions` | The extension system itself: `ExtensionBootstrap`, `ExtensionManager`, `Extension`, `DiscoveredExtension` and `ExtensionClassLoader`. This is what a server depends on. |
| `net.onelitefeather:minestom-extensions-processor` | The `@ExtensionInfo` annotation and the annotation processor that generates `extension.json` at compile time. Used when *writing* an extension, see [Generating extension.json](#generating-extensionjson). |
| `net.onelitefeather:minestom-extensions-gradle-plugin` | Optional. Lets the Gradle build declare the libraries an extension loads at runtime, instead of repeating their coordinates in the annotation. See [Declaring dependencies in the build](#declaring-dependencies-in-the-build). |
| `net.onelitefeather:minestom-extensions-maven-plugin` | Optional. The same for Maven builds. |
| `net.onelitefeather:minestom-extensions-bom` | Bill of Materials (`pom` packaging) that pins the versions of the modules above so they can be declared without a version. |

## Requirements

- Java 25 or newer

## Install

Artifacts are published to the OneLiteFeather Maven repository. Add the repository and the
dependencies to your build.

The recommended way is to import the BOM and declare the modules without a version, so the two
artifacts can never drift apart. The annotation processor is declared twice: as
`annotationProcessor` so it runs during compilation, and as `compileOnly` so the annotations are
visible to the compiler. It is deliberately not a regular `implementation` dependency, because
`@ExtensionInfo` is `@Retention(SOURCE)` and therefore has no business being in the finished jar.

Note that the BOM has to be imported into `annotationProcessor` as well. That configuration extends
nothing, so a platform declared on `implementation` never reaches it and the versionless
`annotationProcessor(...)` line would have no version to resolve against.

### Gradle (Kotlin DSL)

```kotlin
repositories {
    mavenCentral()
    maven("https://repo.onelitefeather.dev/releases")
}

dependencies {
    implementation(platform("net.onelitefeather:minestom-extensions-bom:<release version>"))
    annotationProcessor(platform("net.onelitefeather:minestom-extensions-bom:<release version>"))

    implementation("net.onelitefeather:minestom-extensions")

    compileOnly("net.onelitefeather:minestom-extensions-processor")
    annotationProcessor("net.onelitefeather:minestom-extensions-processor")
}
```

### Gradle (Groovy DSL)

```groovy
repositories {
    mavenCentral()
    maven { url = 'https://repo.onelitefeather.dev/releases' }
}

dependencies {
    implementation platform('net.onelitefeather:minestom-extensions-bom:<release version>')
    annotationProcessor platform('net.onelitefeather:minestom-extensions-bom:<release version>')

    implementation 'net.onelitefeather:minestom-extensions'

    compileOnly 'net.onelitefeather:minestom-extensions-processor'
    annotationProcessor 'net.onelitefeather:minestom-extensions-processor'
}
```

### Maven

```xml
<repositories>
    <repository>
        <id>onelitefeather</id>
        <url>https://repo.onelitefeather.dev/releases</url>
    </repository>
</repositories>

<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>net.onelitefeather</groupId>
            <artifactId>minestom-extensions-bom</artifactId>
            <version>RELEASE_VERSION</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>

<dependencies>
    <dependency>
        <groupId>net.onelitefeather</groupId>
        <artifactId>minestom-extensions</artifactId>
    </dependency>
    <dependency>
        <groupId>net.onelitefeather</groupId>
        <artifactId>minestom-extensions-processor</artifactId>
        <scope>provided</scope>
    </dependency>
</dependencies>

<build>
    <plugins>
        <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-compiler-plugin</artifactId>
            <version>3.15.0</version>
            <configuration>
                <annotationProcessorPaths>
                    <path>
                        <groupId>net.onelitefeather</groupId>
                        <artifactId>minestom-extensions-processor</artifactId>
                    </path>
                </annotationProcessorPaths>
            </configuration>
        </plugin>
    </plugins>
</build>
```

The `<annotationProcessorPaths>` block is not optional. Since JDK 23 `javac` no longer discovers
annotation processors from the compile classpath, so a processor that is only a `provided`
dependency is silently never run: the build stays green and the jar ships without an
`extension.json`, and the extension then dies at server startup with
`Missing extension.json in extension <jar>`. Putting the processor on the processor path is what
actually makes it run.

The `provided` dependency is still needed - it puts `@ExtensionInfo` on the compile classpath while
keeping it out of the packaged artifact. The `<path>` entry needs no `<version>`; the compiler
plugin takes it from that dependency, and therefore from the BOM (requires
`maven-compiler-plugin` 3.12.0 or newer).

### Without the BOM

If you would rather not use the BOM, declare the versions yourself. Only the core module is
required; the processor is optional and only needed if you want `extension.json` generated for you.

```kotlin
dependencies {
    implementation("net.onelitefeather:minestom-extensions:<release version>")
}
```

Snapshot builds are available from `https://repo.onelitefeather.dev/snapshots`.

## Usage

Once installed, using the library is as simple as replacing `MinecraftServer` with `ExtensionBootstrap` during initialization.

```java
// Without minestom-extensions
var server = MinecraftServer.init();
// do something
server.start("0.0.0.0", 25565);

// With minestom-extensions
var server = ExtensionBootstrap.init();
// do something
server.start("0.0.0.0", 25565);
```

If you need to access the `ExtensionManager` from your code, it can be done using `ExtensionBootstrap.getExtensionManager()`.

## Generating extension.json

Every extension needs an `extension.json` in the root of its jar. Instead of writing and maintaining
that file by hand, annotate your entrypoint with `@ExtensionInfo` and let
`minestom-extensions-processor` generate it during compilation. There is deliberately no
`entrypoint()` element: the entrypoint is always the annotated class itself, so it can never drift
away from a rename or a package move.

```java
package com.example.chat;

import net.minestom.server.extensions.Extension;
import net.onelitefeather.minestom.extensions.processor.ExtensionInfo;
import net.onelitefeather.minestom.extensions.processor.ExternalDependency;
import net.onelitefeather.minestom.extensions.processor.Repository;

@ExtensionInfo(
        name = "ChatFormatter",
        authors = {"Alice", "Bob"},
        dependencies = {"PermissionBridge"},
        repositories = {
                @Repository(name = "onelitefeather", url = "https://repo.onelitefeather.dev/releases")
        },
        externalDependencies = {
                @ExternalDependency("com.google.guava:guava:33.4.0-jre"),
                @ExternalDependency("org.apache.commons:commons-lang3:3.17.0")
        }
)
public final class ChatFormatter extends Extension {

    @Override
    public void initialize() {
        getLogger().info("ChatFormatter enabled");
    }

    @Override
    public void terminate() {
    }
}
```

> `Extension#getLogger()` returns an Adventure `ComponentLogger`, whose `info(...)` is inherited
> from `org.slf4j.Logger`. That interface reaches you only at runtime, so calling it needs
> `compileOnly("org.slf4j:slf4j-api")` in your own build — otherwise javac reports
> *"cannot access Logger"*.

This produces `extension.json` in the root of the compiled output, and therefore in the root of the
resulting jar, which is exactly where `ExtensionManager` looks for it (`version` comes from the
compiler argument shown in the next section — the annotation above does not declare one):

```json
{
  "name": "ChatFormatter",
  "entrypoint": "com.example.chat.ChatFormatter",
  "version": "1.4.0",
  "authors": [
    "Alice",
    "Bob"
  ],
  "dependencies": [
    "PermissionBridge"
  ],
  "externalDependencies": {
    "repositories": [
      {
        "name": "onelitefeather",
        "url": "https://repo.onelitefeather.dev/releases"
      }
    ],
    "artifacts": [
      "com.google.guava:guava:33.4.0-jre",
      "org.apache.commons:commons-lang3:3.17.0"
    ]
  }
}
```

`authors`, `dependencies` and `externalDependencies` are omitted entirely when they are empty. The
runtime defaults every missing field, so a minimal descriptor only carries `name` and `entrypoint`.

At startup, `ExtensionManager` resolves everything under `externalDependencies` from the declared
repositories — transitive dependencies included — and adds each jar to the extension's classloader.
Downloads are cached in `extensions/.libs`. Resolution runs on [Maven Artifact
Resolver](https://maven.apache.org/resolver/), the resolver Maven itself uses, with checksum
verification enabled.

> Declare a repository you control or a Maven Central mirror. Using `repo1.maven.org` directly as a
> download endpoint for shipped software is against the [Maven Central Terms of
> Service](https://central.sonatype.org/faq/is-there-a-limit-on-artifact-size/), and your users may
> run into rate limits.

### The version comes from the build

Note that the example above does not set `version()` on the annotation. The version usually already
lives in the build, and duplicating it in the source is how the two get out of sync. Pass it as a
compiler argument instead:

```kotlin
tasks.withType<JavaCompile> {
    options.compilerArgs.add("-Aminestom.extension.version=${project.version}")
}
```

The compiler argument takes precedence over `version()`. If neither is set, the processor warns and
omits the field, and the runtime reports the version as `Unspecified`. The name can be injected the
same way with `-Aminestom.extension.name=<name>`.

### What the processor validates

The generated descriptor is only useful if the runtime accepts it, so the processor checks at
compile time what `ExtensionManager` would otherwise only discover at server startup. It fails the
build when:

- more than one class in the compilation is annotated with `@ExtensionInfo` (an `extension.json`
  describes exactly one entrypoint),
- `name()` does not match `[A-Za-z][_A-Za-z0-9]+`, which the runtime would reject as `INVALID_NAME`,
- the annotated type is not a class, is abstract, is an inner (non-static nested) class, or has no
  no-arg constructor (`ExtensionManager` instantiates the entrypoint reflectively),
- the annotated type does not extend `Extension` (only checked when `Extension` is on the compile
  classpath, otherwise skipped silently),
- a `@Repository` has a blank name or a URL that does not start with `http://` or `https://`,
- an `@ExternalDependency` has a blank coordinate.

It warns, without failing the build, about a missing version, duplicate entries in
`dependencies()` (the duplicate is dropped), a coordinate that does not look like
`group:artifact:version`, and a non-public entrypoint class or constructor. The last one is only a
warning on purpose: `ExtensionManager` calls `setAccessible(true)`, so a package-private entrypoint
does load, it is simply not recommended.

## Declaring dependencies in the build

Writing `@ExternalDependency("com.google.guava:guava:33.4.0-jre")` in the annotation means the
version lives in the source as well as in the build, and the two drift apart. The Gradle and Maven
plugins let the build state it once.

Both plugins run after the annotation processor and add to what it generated, so anything declared
in `@ExtensionInfo` is kept. On a clash the annotation wins.

### Gradle

```kotlin
plugins {
    java
    id("net.onelitefeather.minestom-extensions") version "<release version>"
}

repositories {
    mavenCentral()
}

dependencies {
    extensionLibrary("com.google.guava:guava:33.4.0-jre")
}
```

`extensionLibrary` is a declaration-only configuration: it is on no compile or runtime classpath and
nothing from it is bundled. It exists purely to describe what the extension resolves for itself at
startup. Coordinates need an explicit group and version — the server resolves them with no access to
your version catalog or platforms, so a versionless entry fails the build rather than producing a
descriptor that cannot be resolved.

The repositories declared in the project are written into the descriptor. To add one that the build
itself does not use, or to take full control:

```kotlin
minestomExtension {
    repository("onelitefeather", "https://repo.onelitefeather.dev/releases")
    inheritProjectRepositories = false
}
```

### Maven

```xml
<plugin>
  <groupId>net.onelitefeather</groupId>
  <artifactId>minestom-extensions-maven-plugin</artifactId>
  <version>RELEASE_VERSION</version>
  <configuration>
    <externalDependencies>
      <!-- version taken from <dependencies> -->
      <externalDependency>org.apache.commons:commons-lang3</externalDependency>
      <!-- or pinned here -->
      <externalDependency>com.google.guava:guava:33.4.0-jre</externalDependency>
    </externalDependencies>
  </configuration>
  <executions>
    <execution>
      <goals><goal>describe</goal></goals>
    </execution>
  </executions>
</plugin>
```

The goal binds to `process-classes` and needs no further configuration. Which dependencies to record
is stated explicitly rather than derived from a scope: everything a Minestom extension compiles
against is `provided` — Minestom, `minestom-extensions`, the processor itself — and none of those
belong in the descriptor.

## Building from source

The project uses the Gradle wrapper and a Java 25 toolchain, and is split into the modules listed
under [Modules](#modules).

```bash
./gradlew build                                   # build and test every module
./gradlew test                                    # run the tests of every module
./gradlew projects                                # list the modules

./gradlew :minestom-extensions:build              # the extension system only
./gradlew :minestom-extensions-processor:test     # the annotation processor tests only
./gradlew publishToMavenLocal                     # all three artifacts into ~/.m2
```

Publishing to the OneLiteFeather repository requires the `ONELITEFEATHER_MAVEN_USERNAME` and
`ONELITEFEATHER_MAVEN_PASSWORD` credentials to be available (as environment variables in CI, or
via the `OneLiteFeatherRepository` credentials in your Gradle properties locally).

## Support

Questions or need help? Join our [Discord](https://1lf.link/discord). For bugs and feature
requests, please use the [issue tracker](https://github.com/OneLiteFeatherNET/minestom-extensions/issues).

## Credits

Huge thanks to [hollow-cube](https://github.com/hollow-cube) and the original
[minestom-ce-extensions](https://github.com/hollow-cube/minestom-ce-extensions) contributors for
building the extension system this fork is based on (the upstream project is now archived), and to
the [Minestom](https://github.com/Minestom/Minestom) project for the platform it runs on. This fork
would not exist without their work.

## License

This project is licensed under the [Apache License Version 2.0](LICENSE).
