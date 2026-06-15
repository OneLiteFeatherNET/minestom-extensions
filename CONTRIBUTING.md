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

## Building and testing

```bash
./gradlew build      # compile and run the full test suite
./gradlew test       # run the tests only
```

Tests run on JUnit 5 with `-Dminestom.inside-test=true` already configured by the build.
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
`gradle.properties` and updates `CHANGELOG.md`. Merging that PR tags the release and publishes the
artifact to the OneLiteFeather Maven repository. Contributors do not need to bump versions manually.

## Reporting bugs and requesting features

Please use the [issue templates](https://github.com/OneLiteFeatherNET/minestom-extensions/issues/new/choose).
For security issues, do **not** open a public issue — see [SECURITY.md](SECURITY.md).

## Questions and help

For general questions, usage help, or to chat with the maintainers, join our
[Discord](https://1lf.link/discord).

## License

By contributing, you agree that your contributions will be licensed under the
[Apache License 2.0](LICENSE), the same license that covers this project.
