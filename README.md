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

## Requirements

- Java 25 or newer

## Install

Artifacts are published to the OneLiteFeather Maven repository. Add the repository and the dependency to your build.

### Gradle (Kotlin DSL)

```kotlin
repositories {
    mavenCentral()
    maven("https://repo.onelitefeather.dev/releases")
}

dependencies {
    implementation("net.onelitefeather:minestom-extensions:<release version>")
}
```

### Gradle (Groovy DSL)

```groovy
repositories {
    mavenCentral()
    maven { url = 'https://repo.onelitefeather.dev/releases' }
}

dependencies {
    implementation 'net.onelitefeather:minestom-extensions:<release version>'
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

<dependencies>
    <dependency>
        <groupId>net.onelitefeather</groupId>
        <artifactId>minestom-extensions</artifactId>
        <version>RELEASE_VERSION</version>
    </dependency>
</dependencies>
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

## Building from source

The project uses the Gradle wrapper and a Java 25 toolchain.

```bash
./gradlew build      # compile and run the tests
./gradlew test       # run the tests only
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
