rootProject.name = "spoofax.gradle.root"

pluginManagement {
  repositories {
    maven("https://artifacts.metaborg.org/content/groups/public/")
  }

// This downloads an appropriate JVM if not already available
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}


// Only include composite builds when this is the root project (it has no parent). Otherwise, the parent project will
// include these composite builds, as IntelliJ does not support nested composite builds.
if(gradle.parent == null) {
  // We split the build up into one main composite build in the 'plugin' directory, because it builds Gradle plugins,
  // which we want to test. Gradle plugins are not directly available in a multi-project build, therefore a separate
  // composite build is required.
  includeBuild("plugin")
  // Included builds listed below can use the Gradle plugins built in 'plugin'.
  includeBuild("example")
}
