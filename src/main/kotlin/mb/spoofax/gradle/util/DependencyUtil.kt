package mb.spoofax.gradle.util

import org.gradle.api.Project
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.ExternalModuleDependency
import org.gradle.kotlin.dsl.*
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

fun LanguageIdentifier.toGradleDependency(project: Project, configuration: String? = null, classifier: String? = null, ext: String? = null): ExternalModuleDependency {
  return project.dependencies.create(this.groupId, this.id, this.version.toString(), configuration, classifier, ext)
}
