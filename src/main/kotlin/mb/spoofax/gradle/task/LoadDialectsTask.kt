package mb.spoofax.gradle.task

import org.apache.commons.vfs2.FileObject
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.*
import org.metaborg.core.language.dialect.IDialectProcessor
import org.metaborg.core.resource.*
import org.metaborg.spoofax.core.Spoofax
import org.metaborg.spoofax.core.resource.SpoofaxIgnoresSelector
import java.io.File
import javax.inject.Inject

fun TaskContainer.registerLoadDialectsTask(spoofax: Spoofax, projectLoc: FileObject, name: String = "spoofaxLoadDialects") =
  register(name, LoadDialectsTask::class.java, spoofax.dialectProcessor, spoofax.resourceService, projectLoc)

open class LoadDialectsTask @Inject constructor(
  private val dialectProcessor: IDialectProcessor,
  private val resourceService: ResourceService,
  private val projectLoc: FileObject
) : DefaultTask() {
  @InputDirectory
  fun projectDir(): File = resourceService.localPath(projectLoc)!!

  @TaskAction
  fun execute() {
    val resources = ResourceUtils.find(projectLoc, SpoofaxIgnoresSelector())
    val creations = ResourceUtils.toChanges(resources, ResourceChangeKind.Create)
    // TODO: can make this incremental based on changes in the project directory?
    dialectProcessor.update(projectLoc, creations)
  }
}
