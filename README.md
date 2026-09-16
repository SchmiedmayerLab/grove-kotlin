<!--

This source file is part of the Grove open-source project

SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)

SPDX-License-Identifier: MIT

-->

# Grove Kotlin

[![Build and Test](https://github.com/SchmiedmayerLab/grove-kotlin/actions/workflows/build-test-analyze.yml/badge.svg)](https://github.com/SchmiedmayerLab/grove-kotlin/actions/workflows/build-test-analyze.yml)
[![REUSE status](https://api.reuse.software/badge/github.com/SchmiedmayerLab/grove-kotlin)](https://api.reuse.software/info/github.com/SchmiedmayerLab/grove-kotlin)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE.md)

Kotlin and Android foundation for building digital health applications, alongside [Grove for Swift](https://github.com/SchmiedmayerLab/Grove) and [Grove for TypeScript](https://github.com/SchmiedmayerLab/grove-ts).

Grove is an ecosystem of modules. An application picks the ones it needs — accounts, onboarding, consent, questionnaires, scheduling, health data — rather than adopting a framework whole.


## Modules

### Foundation

| Module | Purpose |
| --- | --- |
| [`core`](core/) | Primitives and Kotlin extensions the other modules build on. |
| [`core-coroutines`](core-coroutines/) | Dispatchers and scopes, injectable so tests control them. |
| [`core-lifecycle`](core-lifecycle/) | Android lifecycle helpers. |
| [`core-logging`](core-logging/) | The logger every module writes through. |
| [`core-time`](core-time/) | Clocks and date handling that tests can drive. |
| [`core-viewmodel`](core-viewmodel/) | ViewModel plumbing shared across features. |
| [`foundation`](foundation/) | Serialization and shared data types. |
| [`resources`](resources/) | Android resources shared between modules. |

### Features

| Module | Purpose |
| --- | --- |
| [`account`](account/) | Account model, account keys, and storage. |
| [`account-firebase`](account-firebase/) | Firebase-backed account service and storage. |
| [`onboarding`](onboarding/) | Onboarding flow and its steps. |
| [`consent`](consent/) | Consent documents and signature capture. |
| [`contact`](contact/) | Contact information screens. |
| [`questionnaire`](questionnaire/) | FHIR questionnaire rendering and responses. |
| [`markdown`](markdown/) | Markdown parsing and rendering. |
| [`health`](health/) | Health Connect access. |
| [`health-fhir`](health-fhir/README.md) | Converts Health Connect records into FHIR R4 against the shared Grove contract. |
| [`scheduler`](scheduler/) | Task schedules, occurrences, and notifications. |
| [`study`](study/) | Study enrollment and lifecycle. |
| [`study-definition`](study-definition/) | Study bundles and the study definition model. |
| [`storage-credential`](storage-credential/) | Credential storage. |
| [`storage-local`](storage-local/) | Local key-value storage. |

### User Interface

| Module | Purpose |
| --- | --- |
| [`ui`](ui/) | The design system: layouts, controls, and app chrome. |
| [`ui-account`](ui-account/) | Account screens. |
| [`ui-scheduler`](ui-scheduler/) | Schedule and task screens. |
| [`ui-theme`](ui-theme/) | Typography, colors, and theming. |
| [`ui-validation`](ui-validation/) | Input validation and its presentation. |

### Testing and Tooling

| Module | Purpose |
| --- | --- |
| [`testing-core`](testing-core/) | Shared test utilities. |
| [`testing-concurrency`](testing-concurrency/) | Coroutine test support. |
| [`testing-screenshot`](testing-screenshot/) | Paparazzi screenshot testing setup. |
| [`testing-ui`](testing-ui/) | Compose UI test support. |
| [`sample-app`](sample-app/) | A sample application exercising the modules. |
| [`build-logic`](build-logic/) | The `grove.*` convention plugins every module applies. |


## Add Grove to Your App

Grove Kotlin is consumed as a git submodule that Gradle builds from source. One checkout serves both roles: the version your app is pinned to, and a working copy you can edit from the app that uses it.

1. Add the submodule:

   ```bash
   git submodule add https://github.com/SchmiedmayerLab/grove-kotlin.git grove-kotlin
   ```

2. Include it as a composite build in your `settings.gradle.kts`:

   ```kotlin
   includeBuild(providers.gradleProperty("grove.path").getOrElse("grove-kotlin"))
   ```

3. Declare the modules you need in your version catalog:

   ```toml
   [versions]
   grove = "0.1.0"

   [libraries]
   grove-ui = { module = "org.grovealliance:ui", version.ref = "grove" }
   grove-account = { module = "org.grovealliance:account", version.ref = "grove" }
   ```

   Gradle resolves these against the included build, so the version is what a published artifact would carry rather than something that has to be kept current.

4. Depend on them the usual way:

   ```kotlin
   dependencies {
       implementation(libs.grove.ui)
       implementation(libs.grove.account)
   }
   ```

Your application supplies its own Android and Kotlin plugin versions. Keep the Android Gradle Plugin and Kotlin versions the same as the ones in [`gradle/libs.versions.toml`](gradle/libs.versions.toml); a composite build loads both builds' plugins into one process, and mismatched versions fail confusingly.

### Working on Grove From Your App

The submodule is an ordinary checkout. Edit it in Android Studio and your application compiles against the change on the next build — no publishing step in between.

Move to a branch or a released commit the way you would with any submodule, and commit the new pin alongside the code that needs it:

```bash
git -C grove-kotlin switch feature/the-feature
git add grove-kotlin
```

To build against a checkout somewhere else — one shared between several applications, or a worktree — set `grove.path` in `~/.gradle/gradle.properties` rather than editing the build:

```properties
grove.path=/absolute/path/to/grove-kotlin
```


## Development

Grove Kotlin builds on JDK 21.

```bash
./gradlew detekt test            # static analysis and unit tests
./gradlew verifyPaparazziDebug   # screenshot tests against their baselines
./gradlew recordPaparazziDebug   # re-record baselines after an intended UI change
./gradlew :sample-app:installDebug
./gradlew installGitHooks        # runs detekt before each commit
```

The [Detekt guide](internal/guides/Detekt.md) and the [Paparazzi guide](internal/guides/Paparazzi%20screenshot%20testing.md) cover the Android Studio setup for each.

## Contributing

Contributions to this project are welcome. Please make sure to read the [contribution guidelines](https://github.com/SchmiedmayerLab/.github/blob/main/CONTRIBUTING.md) and the [contributor covenant code of conduct](https://github.com/SchmiedmayerLab/.github/blob/main/CODE_OF_CONDUCT.md) first. You can find a list of contributors in the [CONTRIBUTORS.md](CONTRIBUTORS.md) file.

## License

This project is licensed under the MIT License. See [LICENSE.md](LICENSE.md) for more information.

## Citation

If you use this software, please cite it using the metadata in [CITATION.cff](CITATION.cff), which GitHub surfaces through the [*Cite this repository*](https://docs.github.com/en/repositories/managing-your-repositorys-settings-and-features/customizing-your-repository/about-citation-files) button.

## Our Research

For more information, visit the [Schmiedmayer Lab GitHub organization](https://github.com/SchmiedmayerLab).

![Schmiedmayer Lab](https://raw.githubusercontent.com/SchmiedmayerLab/.github/main/assets/footer-light.png#gh-light-mode-only)
![Schmiedmayer Lab](https://raw.githubusercontent.com/SchmiedmayerLab/.github/main/assets/footer-dark.png#gh-dark-mode-only)
