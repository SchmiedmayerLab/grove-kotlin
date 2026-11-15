# Module license

Lists the app and the third-party software it is built with, each with its license label and
version. A library whose license text is bundled opens it on a page of its own; one without opens
its website.

## App setup

The list is generated at build time by the
[AboutLibraries](https://github.com/mikepenz/AboutLibraries) Gradle plugin, which must be applied
in the **app**, because only the app sees its whole dependency graph. The module reads the result
with `aboutlibraries-core`.

```kotlin
// app/build.gradle.kts
plugins {
    id("com.mikepenz.aboutlibraries.plugin.android") version "15.3.0"
}
```

Use **the same version as Grove's `aboutLibraries` entry** in `gradle/libs.versions.toml`. The
plugin writes the list in the format of its own release, and `aboutlibraries-core` reads that
format, so a plugin newer or older than the core library can produce a list the module fails to
read, which the screen reports as an error. Bump both together.

Libraries the plugin can't see, such as Grove itself when built from source in a composite build,
can be declared in the plugin's config directory:

```kotlin
aboutLibraries {
    collect {
        configPath = file("aboutlibraries") // e.g. aboutlibraries/libraries/grove.json
    }
}
```

The module keeps the generated list when the app's resources are shrunk.

## Usage

Register the module with the app's own entry. The lambda has the dependency graph as its receiver:

```kotlin
license {
    project {
        LicenseProject(
            name = StringResource(R.string.app_name),
            version = dependency<AppRevision>().version,
            licenseIdentifier = "MIT",
            url = "https://github.com/example/app",
        )
    }
    // Optional: replaces the generated list.
    // dependencies { listOf(Dependency(…)) }
}
```

Show the screen, for example as a navigation entry:

```kotlin
LicenseInformationScreen(
    onDismiss = { backStack.removeLastOrNull() },
    // Optional: handle links yourself instead of opening them with the platform handler.
    // onOpenUrl = { url -> … },
)
```
