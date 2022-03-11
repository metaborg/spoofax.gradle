# Changelog
All notable changes to this project are documented in this file, based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).


## [Unreleased]
- Ignore directories created by `languageSpecBuilder.initialize`, fixing incrementality issues from clean builds.


## [0.5.3]
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


[Unreleased]: https://github.com/metaborg/spoofax.gradle/compare/release-0.5.3...HEAD
[0.5.3]: https://github.com/metaborg/spoofax.gradle/compare/release-0.5.2...release-0.5.3
