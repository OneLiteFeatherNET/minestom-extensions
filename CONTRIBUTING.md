# Contributing to minestom-extensions

Thanks for your interest in contributing! This project is the actively maintained
[OneLiteFeather](https://github.com/OneLiteFeatherNET) fork of
[minestom-ce-extensions](https://github.com/hollow-cube/minestom-ce-extensions). The notes below
should help you get a change merged smoothly.

## Code of Conduct

By participating in this project you agree to abide by our
[Code of Conduct](CODE_OF_CONDUCT.md). Please report unacceptable behavior to the maintainers.

## Prerequisites

- **Java 25** or newer (the build uses a Java 25 toolchain)
- Git
- No local Gradle install required — use the bundled wrapper (`./gradlew`)

## Project layout

This is a Gradle multi-module build. The root project is an aggregator and publishes nothing itself;
all five published artifacts come from subprojects that share a single version:

| Module | Artifact | Contents |
|--------|----------|----------|
| `minestom-extensions/` | `net.onelitefeather:minestom-extensions` | The extension system: `ExtensionBootstrap`, `ExtensionManager`, `Extension`, `DiscoveredExtension`, `ExtensionClassLoader`. |
| `minestom-extensions-processor/` | `net.onelitefeather:minestom-extensions-processor` | The `@ExtensionInfo` annotation and the annotation processor that generates `extension.json`. Runtime-dependency-free by design — JDK APIs only. |
| `minestom-extensions-gradle-plugin/` | `net.onelitefeather:minestom-extensions-gradle-plugin` | Gradle plugin adding build-declared libraries to `extension.json`. Built with `java-gradle-plugin`, which supplies its own `pluginMaven` publication — the root build skips creating a second one for it. |
| `minestom-extensions-maven-plugin/` | `net.onelitefeather:minestom-extensions-maven-plugin` | The same for Maven. Its `META-INF/maven/plugin.xml` is maintained by hand, see below. |
| `minestom-extensions-bom/` | `net.onelitefeather:minestom-extensions-bom` | A `java-platform` BOM pinning the modules above. |

The packages and class names under `minestom-extensions/` are a **public contract** with downstream
consumers such as CloudNet. Do not rename or move `net.minestom.server.extensions.*` or
`net.hollowcube.minestom.extensions.ExtensionBootstrap`, even if a rename looks tidier.

`MavenDependencyResolver` is package-private on purpose — it resolves an extension's
`externalDependencies` at startup and is an implementation detail, not part of that contract. It
wires Maven Artifact Resolver through the deprecated `MavenRepositorySystemUtils.newServiceLocator()`
rather than the newer `RepositorySystemSupplier`, and that is deliberate: the supplier builds a
resolver whose descriptor reader does not interpret POMs, so transitive dependencies silently
resolve to nothing. Read the class javadoc before "modernising" it.

Shared build logic — the Java 25 toolchain, sources/javadoc jars, and the whole `maven-publish`
setup — lives once in the root `build.gradle.kts`. A module's own build file should only carry what
is genuinely specific to it (its `description` and its dependencies).

## Building and testing

```bash
./gradlew build                              # build and test every module
./gradlew test                               # run the tests only
./gradlew :minestom-extensions-processor:test  # a single module
```

Tests run on JUnit 5. The core module's test task additionally sets `-Dminestom.inside-test=true`,
which Minestom requires; the processor's tests are plain JUnit and need no such flag. Both are
already configured by the build — you do not need to pass anything by hand.

One test spans both code modules and is worth knowing about before you touch either end.
`minestom-extensions/src/test/resources/extension-descriptor-contract.json` is the exact
`extension.json` the processor emits for a fully populated `@ExtensionInfo`. The processor module
copies that file in and asserts it still reproduces it; the core module deserializes it into the
real `DiscoveredExtension` and asserts every field arrives. This is deliberate belt-and-braces:
the processor does not depend on the core module, and Gson silently ignores JSON members it does
not recognise — so without those two tests, renaming a field in `DiscoveredExtension` would break
every extension at runtime while leaving the whole build green. If you change the descriptor format,
regenerate that file and expect both suites to move together.

The Maven plugin's descriptor needs the same kind of care. A Maven build would generate
`META-INF/maven/plugin.xml` from the `@Mojo` annotations, but the Gradle equivalent for that calls an
API Gradle 9 removed, so the file is maintained by hand under `src/main/resources`. `PluginDescriptorTest`
compares it against the mojo's fields, because a drifting descriptor fails in the *user's* build —
Maven either reports a parameter as unknown or silently never injects it.

Both build plugins deliberately write their result to a file separate from the one the annotation
processor produced, instead of editing it in place. Reading back their own output would mean that a
dependency removed from the build lingers in the descriptor forever, since the compile task stays up
to date and never regenerates it. Both have a regression test for exactly that.

Please make sure `./gradlew build` passes before opening a pull request.

## Branching and pull requests

- Create your changes on a branch and open a pull request against `main`.
- PRs from forks are expected; direct pushes to `main` are not accepted and invalid PRs are
  closed automatically.
- Every PR is built by the `Build PR` workflow. Keep the build green.
- Fill out the pull request template so reviewers have the context they need.
- Code ownership is defined in [`.github/CODEOWNERS`](.github/CODEOWNERS); the relevant maintainers
  are requested for review automatically.

## Commit messages

This repository uses [release-please](https://github.com/googleapis/release-please) to automate
versioning and the changelog, so commits **must** follow the
[Conventional Commits](https://www.conventionalcommits.org/) specification:

```
<type>[optional scope]: <description>

[optional body]

[optional footer(s)]
```

Common types:

| Type       | Use for                                              | Version bump |
|------------|------------------------------------------------------|--------------|
| `feat`     | A new feature                                        | minor        |
| `fix`      | A bug fix                                             | patch        |
| `docs`     | Documentation only changes                           | none         |
| `refactor` | Code change that neither fixes a bug nor adds a feat | none         |
| `test`     | Adding or correcting tests                           | none         |
| `chore`    | Build process, tooling, dependencies                 | none         |
| `ci`       | CI configuration changes                             | none         |

Breaking changes: append `!` after the type/scope (e.g. `feat!:`) or add a `BREAKING CHANGE:`
footer to trigger a major version bump.

Examples:

```
feat: expose extension reload via ExtensionBootstrap
fix(classloader): prevent leaking parent classpath resources
refactor!: remove deprecated DemoServer entrypoint
```

## Releases

Releases are handled automatically: release-please opens a release PR that bumps the version in
`gradle.properties` and updates `CHANGELOG.md`. Merging that PR tags the release and publishes all
five modules to the OneLiteFeather Maven repository. Contributors do not need to bump versions
manually.

All modules share one version, inherited from the single `version` entry in `gradle.properties` —
they are always released together and never versioned independently. The `# x-release-please-version`
marker comment on that line is what release-please rewrites, so leave it in place.

## Reporting bugs and requesting features

Please use the [issue templates](https://github.com/OneLiteFeatherNET/minestom-extensions/issues/new/choose).
For security issues, do **not** open a public issue — see [SECURITY.md](SECURITY.md).

## Questions and help

For general questions, usage help, or to chat with the maintainers, join our
[Discord](https://1lf.link/discord).

## License

By contributing, you agree that your contributions will be licensed under the
[Apache License 2.0](LICENSE), the same license that covers this project.
