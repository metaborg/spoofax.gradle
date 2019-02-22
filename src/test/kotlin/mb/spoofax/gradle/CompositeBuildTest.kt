package mb.spoofax.gradle

import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.io.File

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CompositeBuildTest {
  @Test
  fun compositeBuildTest() {
    GradleRunner.create()
      .withPluginClasspath()
      .withProjectDir(File("../example/calc"))
      .withArguments("--info", "--stacktrace", "buildAll")
      .forwardOutput()
      .build()
  }
}
