rootProject.name = "org.metaborg.lang.calc"

pluginManagement {
  repositories {
    // Maven local for locally published builds of spoofax.gradle, until IntelliJ properly supports composite builds with plugins.
    mavenLocal()

    maven(url = "https://artifacts.metaborg.org/content/repositories/releases/")
    maven(url = "https://artifacts.metaborg.org/content/repositories/snapshots/")
    // Required by `org.metaborg.spoofax.gradle.langspec` plugin.
    maven("https://pluto-build.github.io/mvnrepository/")
    maven("https://sugar-lang.github.io/mvnrepository/")
    maven("http://nexus.usethesource.io/content/repositories/public/")

    gradlePluginPortal()
  }
}
