package mb.spoofax.gradle.util

import mb.spoofax.gradle.plugin.SpoofaxBasePlugin
import org.gradle.api.Project
import org.gradle.api.artifacts.*
import org.gradle.kotlin.dsl.create
import org.metaborg.core.language.LanguageIdentifier
import org.metaborg.core.language.LanguageVersion

fun Dependency.toSpoofaxDependency(): LanguageIdentifier {
  val group = this.group
    ?: error("Cannot convert Gradle dependency '$this' to a Spoofax dependency, as it it has no group")
  val name = this.name
  val version = this.version
    ?: error("Cannot convert Gradle dependency '$this' to a Spoofax dependency, as it it has no version")
  val spoofaxVersion = LanguageVersion.parse(version)
  return LanguageIdentifier(group, name, spoofaxVersion)
}

fun LanguageIdentifier.toGradleDependency(project: Project, configuration: String? = Dependency.DEFAULT_CONFIGURATION, classifier: String? = null, ext: String? = null): ExternalModuleDependency {
  return project.dependencies.create(this.groupId, this.id, this.version.toString(), configuration, classifier, ext)
}

fun configureSpoofaxLanguageArtifact(dependency: ModuleDependency) {
  dependency.artifact {
    name = dependency.name
    type = SpoofaxBasePlugin.spoofaxLanguageExtension
    extension = SpoofaxBasePlugin.spoofaxLanguageExtension
  }
}
