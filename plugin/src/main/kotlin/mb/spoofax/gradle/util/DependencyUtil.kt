package mb.spoofax.gradle.util

import org.gradle.api.Project
import org.gradle.api.artifacts.ExternalModuleDependency
import org.gradle.api.artifacts.ResolvedArtifact
import org.gradle.api.artifacts.ResolvedDependency
import org.gradle.kotlin.dsl.*
import org.metaborg.core.language.LanguageIdentifier
import org.metaborg.core.language.LanguageVersion

fun ResolvedArtifact.toSpoofaxDependency(): LanguageIdentifier? {
    return asSpoofaxDependency(moduleVersion.id.group, moduleVersion.id.name, moduleVersion.id.version)
}

fun ResolvedDependency.toSpoofaxDependency(): LanguageIdentifier? {
    return asSpoofaxDependency(module.id.group, module.id.name, module.id.version)
}

private fun asSpoofaxDependency(group: String, name: String, version: String): LanguageIdentifier? {
    if (group == "dev.spoofax" && name.endsWith("-platform")) {
        // Skip the Spoofax platform dependency, it is not a Spoofax dependency.
        return null
    }
    return LanguageIdentifier(group, name, LanguageVersion.parse(version))
}

fun LanguageIdentifier.toGradleDependency(project: Project, configuration: String? = null, classifier: String? = null, ext: String? = null): ExternalModuleDependency {
    return project.dependencies.create(this.groupId, this.id, this.version.toString(), configuration, classifier, ext)
}
