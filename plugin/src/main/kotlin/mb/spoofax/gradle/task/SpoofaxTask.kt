package mb.spoofax.gradle.task

import mb.spoofax.gradle.plugin.SpoofaxLangSpecExtension
import mb.spoofax.gradle.plugin.spoofaxBuildService
import mb.spoofax.gradle.util.SpoofaxBuildService
import org.gradle.api.DefaultTask
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Internal
import org.gradle.kotlin.dsl.*

abstract class SpoofaxTask : DefaultTask() {
  @get:Internal
  abstract val spoofaxBuildService: Property<SpoofaxBuildService>

  init {
    val service = project.spoofaxBuildService
    spoofaxBuildService.convention(service)
    usesService(service)
  }
}
