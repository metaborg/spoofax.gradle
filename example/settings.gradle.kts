rootProject.name = "spoofax.gradle.example"

pluginManagement {
  repositories {
    maven("https://artifacts.metaborg.org/content/groups/public/")
  }
}

enableFeaturePreview("GRADLE_METADATA")

// Only include composite builds when this is the root project (it has no parent), for example when running Gradle tasks
// from the command-line. Otherwise, the parent project will include these composite builds.
if(gradle.parent == null) {
  includeBuild("..")
}

fun String.includeProject(id: String, path: String = "$this/$id") {
  include(id)
  project(":$id").projectDir = file(path)
}

"empty".run {
  includeProject("empty")
  includeProject("empty.example")
  includeProject("empty.test")
}
