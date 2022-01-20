# Changelog
All notable changes to this project are documented in this file, based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).


## [Unreleased]


## [0.5.3]
### Changed
- Minimum required Gradle version is 6.1.
- Support for Gradle 7.
- Massive memory usage improvements due to only creating one Spoofax instance per build.
- Less incrementality bugs due to revised task dependencies.
- Small speedups due to language specification builds only being split into 2 tasks instead of 4.

### Added
- Language contributions can be overridden with the `addLanguageContributionsFromMetaborgYaml` and `languageContributions` settings in the `spoofaxLanguageSpecification` extension.


[Unreleased]: https://github.com/metaborg/spoofax.gradle/compare/release-0.5.3...HEAD
[0.5.3]: https://github.com/metaborg/spoofax.gradle/compare/release-0.5.2...release-0.5.3
