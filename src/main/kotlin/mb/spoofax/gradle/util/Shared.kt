package mb.spoofax.gradle.util

import org.apache.commons.vfs2.FileObject
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.invocation.Gradle
import org.metaborg.core.project.IProject
import org.metaborg.core.project.ISimpleProjectService
import org.metaborg.spoofax.core.Spoofax
import org.metaborg.spoofax.meta.core.SpoofaxExtensionModule
import org.metaborg.spoofax.meta.core.SpoofaxMeta
import org.metaborg.spoofax.meta.core.project.ISpoofaxLanguageSpec

internal fun createSpoofax(gradle: Gradle): Spoofax {
  // Use a null module plugin loader for SpoofaxMeta, as service loading does not work well in a Gradle environment.
  val spoofax = Spoofax(NullModulePluginLoader(), SpoofaxGradleModule(), SpoofaxExtensionModule())
  spoofax.configureAsHeadlessApplication()
  gradle.buildFinished {
    spoofax.close()
  }
  return spoofax
}

internal fun Spoofax.createSpoofaxMeta(gradle: Gradle): SpoofaxMeta {
  // Use a null module plugin loader for SpoofaxMeta, as service loading does not work well in a Gradle environment.
  val spoofaxMeta = SpoofaxMeta(this, NullModulePluginLoader(), SpoofaxGradleMetaModule())
  gradle.buildFinished {
    spoofaxMeta.close()
  }
  return spoofaxMeta;
}

internal fun Spoofax.getProjectLocation(project: Project): FileObject {
  return this.resourceService.resolve(project.projectDir)
}

internal fun Spoofax.recreateProject(project: Project): IProject {
  val projectService = (this.projectService as ISimpleProjectService)
  val projectLocation = getProjectLocation(project)
  val spoofaxProject = projectService.get(projectLocation)
  if(spoofaxProject != null) {
    projectService.remove(spoofaxProject)
  }
  return projectService.create(projectLocation)
    ?: throw GradleException("Project at '$projectLocation' is not a Spoofax project")
}

internal fun Spoofax.getProject(project: Project): IProject {
  val projectLocation = getProjectLocation(project)
  return projectService.get(projectLocation)
    ?: throw GradleException("Project at '$projectLocation' is not a Spoofax project")
}

internal fun SpoofaxMeta.getLanguageSpecification(project: Project): ISpoofaxLanguageSpec {
  val spoofaxProject = parent.getProject(project)
  return this.languageSpecService.get(spoofaxProject)
    ?: throw GradleException("Project '$spoofaxProject' is not a Spoofax language specification project")
}
