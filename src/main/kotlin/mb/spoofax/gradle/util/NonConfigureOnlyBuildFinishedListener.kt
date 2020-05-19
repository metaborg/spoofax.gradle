package mb.spoofax.gradle.util

import org.gradle.BuildListener
import org.gradle.BuildResult
import org.gradle.api.initialization.Settings
import org.gradle.api.invocation.Gradle

class NonConfigureOnlyBuildFinishedListener(val action: () -> Unit) : BuildListener {
  override fun buildFinished(result: BuildResult) {
    // IntelliJ Gradle imports have "Configure" as the action. IntelliJ configures tasks after the build has been
    // finished, requiring certain things to be available even after this buildFinished has been observed. Therefore,
    // this listener only runs the given action when the build is finished and the action is not "Configure".
    if(result.action != "Configure") {
      action()
    }
  }

  override fun settingsEvaluated(settings: Settings) {}

  override fun projectsLoaded(gradle: Gradle) {}

  override fun buildStarted(gradle: Gradle) {}

  override fun projectsEvaluated(gradle: Gradle) {}
}
