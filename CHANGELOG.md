# Changelog
All notable changes to this project are documented in this file, based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).


## [Unreleased] - yyyy-mm-dd


## [0.5.8] - 2023-09-21
### Changed
- Ensure 'sourcesJar' and 'javadoc' also depend on 'spoofaxBuildLanguageSpec' task.


## [0.5.7] - 2023-07-26
### Changed
- Support [Spoofax 2.5.18](https://spoofax.dev/release/note/2.5.18/).
- Update `org.metaborg.gradle.config` plugin to 0.4.8.
- Remove unused `de.set.ecj` dependency.


## [0.5.6] - 2022-05-11
### Changed
- `resource` requirement to `0.14.1`.
- `common` requirement to `0.11.0`.
- `pie` requirement to `0.21.0`.


## [0.5.5] - 2022-03-11
### Fixed
- Fixed `UnknownDomainObjectException` in certain cases.


## [0.5.4] - 2022-03-11
### Fixed
- Ignore directories created by `languageSpecBuilder.initialize`, fixing incrementality issues from clean builds.


## [0.5.3] - 2022-03-09
### Fixed
- Always very verbose logging. Notes are now logged at Gradle INFO level, meaning that they are only shown with `--info` or `--debug`. Warnings are now logged at Gradle WARN level, so they can be ignored with `--warning-mode=none`. Errors are now logged at Gradle ERROR level, so they are always shown and outputted to stderr.
- Language specifications rebuilding without changes due to `target/metaborg/table.bin` or `target/metaborg/table-completions.bin` being changed after the build.

### Changed
- Minimum required Gradle version is 6.1.
- Support for Gradle 7.
- Massive memory usage improvements due to only creating one Spoofax instance per build.
- Less incrementality bugs due to revised task dependencies.
- Small speedups due to language specification builds only being split into 2 tasks instead of 4.

### Added
- Language contributions can be overridden with the `addLanguageContributionsFromMetaborgYaml` and `languageContributions` settings in the `spoofaxLanguageSpecification` extension.
- `spoofaxBuildApproximateAdditionalInputExcludePatterns` and `spoofaxBuildApproximateAdditionalOutputExcludePatterns` to `spoofaxLanguageSpecification` extension to support setting up additional include/exclude patterns when building language specifications.


[Unreleased]: https://github.com/metaborg/spoofax.gradle/compare/release-0.5.8...HEAD
[0.5.8]: https://github.com/metaborg/spoofax.gradle/compare/release-0.5.7...release-0.5.8
[0.5.7]: https://github.com/metaborg/spoofax.gradle/compare/release-0.5.6...release-0.5.7
[0.5.6]: https://github.com/metaborg/spoofax.gradle/compare/release-0.5.5...release-0.5.6
[0.5.5]: https://github.com/metaborg/spoofax.gradle/compare/release-0.5.4...release-0.5.5
[0.5.4]: https://github.com/metaborg/spoofax.gradle/compare/release-0.5.3...release-0.5.4
[0.5.3]: https://github.com/metaborg/spoofax.gradle/compare/release-0.5.2...release-0.5.3
