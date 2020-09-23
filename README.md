# Spoofax Gradle

Spoofax Gradle is a Gradle plugin for building and using Spoofax language specifications.

## Requirements

Gradle 5.3 or higher is required.
The following code snippets assume you are using Gradle with Kotlin, but should be translatable to Groovy as well.

## Prerequisites

The Coronium plugin is not yet published to the Gradle plugins repository.
Therefore, to enable the plugin, add our repository to your settings.gradle(.kts) file:

```kotlin
pluginManagement {
  repositories {
    maven("https://artifacts.metaborg.org/content/repositories/releases/")
  }
}
```

If you are on Gradle 5.3-5.6, Gradle metadata needs to be enabled. Add the following line to your settings.gradle(.kts) file:

```kotlin
enableFeaturePreview("GRADLE_METADATA")
```

## Spoofax version

Due to the complex nature of language development and Spoofax's API, this plugin only works with the single version of Spoofax it was built for.
The following table lists the supported versions:

| Spoofax Gradle Plugin | Spoofax |
|-----------------------|---------|
| 0.3.6-0.3.10          | 2.5.9   |
| 0.4.0-0.4.1           | 2.5.10  |
| 0.4.2-latest          | 2.5.11  |

## Building and testing a language specification

Apply the langspec plugin to a project (a build.gradle(.kts) file) as follows:

```kotlin
plugins {
  id("org.metaborg.spoofax.gradle.langspec") version("0.4.0")
}
```

The plugin uses your `metaborg.yaml` file to configure the build.
When running the `build` task for this project, the Spoofax Gradle plugin will build your language specification.
The plugin can be customized using the `spoofaxLanguageSpecification` extension.

If you have any SPT files in your language, the plugin will automatically run those against your built language specification.
This behaviour can be disabled if desired:

```kotlin
spoofaxLanguageSpecification {
  runTests.set(false)
}
```

If you also want to build the example files of your language, enable the following setting:

```kotlin
spoofaxLanguageSpecification {
  buildExamples.set(true)
  examplesDir.set("examples")
}
```

The Spoofax Gradle plugin will automatically create a publication for the build language implementation, which can be published with [Gradle's publishing plugins](https://docs.gradle.org/current/userguide/publishing_setup.html#publishing_overview).
This behaviour can be disabled if desired:

```kotlin
spoofaxLanguageSpecification {
  createPublication.set(false)
}
```

Finally, the Stratego format can be overrode, enabling developing using `ctree` while building with `jar`:

```kotlin
spoofaxLanguageSpecification {
  strategoFormat.set(org.metaborg.spoofax.meta.core.config.StrategoFormat.jar)
}
```

## Making dependencies

The plugin reads your `metaborg.yaml` file and creates dependencies to the corresponding language implementations.
Therefore, in most cases, dependencies do not have to be specified in Gradle.
However, if you require compile dependency on a language implementation in the same [multi-project build](https://docs.gradle.org/current/userguide/multi_project_builds.html), a project dependency needs to be added as follows:

```kotlin
dependencies {
  compileLanguage(project(":my.lang"))
}
```

Likewise, for source dependencies in the same multi-project build, use:

```kotlin
dependencies {
  sourceLanguage(project(":my.lang"))
}
```

## Using a language implementation

Built language implementations can be used to build/test examples, or to apply the compiler of your language to a Gradle project.

Apply the project plugin to a project (a build.gradle(.kts) file) as follows:

```kotlin
plugins {
  id("org.metaborg.spoofax.gradle.project") version("0.4.0")
}
```

The plugin uses your `metaborg.yaml` file to configure the build.
When running the `build` task for this project, the Spoofax Gradle plugin will run the compiler for all languages to which you have a compile dependency.

If the `java` plugin is enabled for this project, the Spoofax Gradle plugin will run before Java compilation.
Therefore, if your language generates Java files in a [Java source set](https://docs.gradle.org/current/userguide/java_plugin.html#source_sets), the Java compiler will pick up these files and compile them, enabling Java-generating DSLs.

## Separately testing a language specification

A language specification can be tested with SPT files in the same project.
However, it is also possible to test a language specification with tests in a separate project.

Apply the test plugin to a project (a build.gradle(.kts) file) as follows:

```kotlin
plugins {
  id("org.metaborg.spoofax.gradle.test") version("0.4.0")
}
```

Set the language-under-test as follows:

```kotlin
spoofaxTest {
  languageUnderTest.set(org.metaborg.core.language.LanguageIdentifier.parse("org.example:my.lang:0.1.0-SNAPSHOT"))
}
```

This language probably comes from another project in a multi-project build, so you will need to add a project dependency as explained earlier:

```kotlin
dependencies {
  compileLanguage(project(":my.lang"))
}
```

## Examples

The `example` directory of this repository has several examples using this plugin.
