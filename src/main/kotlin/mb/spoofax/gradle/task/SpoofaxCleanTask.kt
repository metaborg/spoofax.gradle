package mb.spoofax.gradle.task

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.TaskContainer
import org.metaborg.core.build.CleanInput
import org.metaborg.core.language.ILanguageService
import org.metaborg.core.project.IProject
import org.metaborg.spoofax.core.Spoofax
import org.metaborg.spoofax.core.build.ISpoofaxBuilder
import javax.inject.Inject

fun TaskContainer.registerSpoofaxCleanTask(spoofax: Spoofax, spoofaxProject: IProject, name: String = "spoofaxClean") =
  register(name, SpoofaxCleanTask::class.java, spoofax.languageService, spoofax.builder, spoofaxProject)

open class SpoofaxCleanTask @Inject constructor(
  private val languageService: ILanguageService,
  private val builder: ISpoofaxBuilder,
  private val spoofaxProject: IProject
) : DefaultTask() {
  @TaskAction
  fun execute() {
    builder.clean(CleanInput(spoofaxProject, languageService.allImpls, null))
  }
}