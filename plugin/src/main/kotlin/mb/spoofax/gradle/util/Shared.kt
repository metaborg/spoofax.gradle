package mb.spoofax.gradle.util

import org.apache.commons.vfs2.FileObject
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.metaborg.core.MetaborgRuntimeException
import org.metaborg.core.project.IProject
import org.metaborg.core.project.ISimpleProjectService
import org.metaborg.spoofax.core.Spoofax
import org.metaborg.spoofax.meta.core.SpoofaxMeta
import org.metaborg.spoofax.meta.core.project.ISpoofaxLanguageSpec

internal fun Spoofax.getProjectLocation(project: Project): FileObject {
  try {
    return this.resourceService.resolve(project.projectDir)
  } catch(e: MetaborgRuntimeException) {
    throw GradleException("Failed to get project location for '$project'", e)
  }
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
