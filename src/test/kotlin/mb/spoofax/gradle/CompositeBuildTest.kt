package mb.spoofax.gradle

import io.github.glytching.junit.extension.folder.TemporaryFolder
import io.github.glytching.junit.extension.folder.TemporaryFolderExtension
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CompositeBuildTest {
  @Test
  @ExtendWith(TemporaryFolderExtension::class)
  fun compositeBuildTest(tempDir: TemporaryFolder) {
    // Root
    tempDir.createFile("settings.gradle.kts").apply {
      writeText("""
        rootProject.name = "compositeBuildTest"

        includeBuild("calc")
        includeBuild("calc.lib")
      """.trimIndent())
    }
    tempDir.createFile("build.gradle.kts").apply {
      writeText("""
        tasks {
          register("buildAll") {
            dependsOn(gradle.includedBuilds.map { it.task(":build") })
          }
        }
      """.trimIndent())
    }

    // calc language
    val calcDir = tempDir.createDirectory("calc")
    calcDir.resolve("build.gradle.kts").apply {
      createNewFile()
      writeText("""
        plugins {
          id("org.metaborg.spoofax.gradle.langspec")
        }

        group = "org.metaborg"
        version = "0.1.0"

        spoofax {
          addCompileLanguageDep("org.metaborg", "org.metaborg.meta.lang.esv", metaborgVersion)
          addCompileLanguageDep("org.metaborg", "org.metaborg.meta.lang.template", metaborgVersion)
          addSourceLanguageDep("org.metaborg", "meta.lib.spoofax", metaborgVersion)
        }
      """.trimIndent())
    }
    calcDir.resolve("metaborg.yaml").apply {
      createNewFile()
      writeText("""
        ---
        id: org.metaborg:calc:0.1.0
        name: calc
        dependencies:
          compile:
          - org.metaborg:org.metaborg.meta.lang.esv:${'$'}{metaborgVersion}
          - org.metaborg:org.metaborg.meta.lang.template:${'$'}{metaborgVersion}
          source:
          - org.metaborg:meta.lib.spoofax:${'$'}{metaborgVersion}
        pardonedLanguages:
        - EditorService
        - Stratego-Sugar
        - SDF
      """.trimIndent())
    }

    // calc.lib 'language'
    val calcLib = tempDir.createDirectory("calc.lib")
    calcLib.resolve("build.gradle.kts").apply {
      createNewFile()
      writeText("""
        plugins {
          id("org.metaborg.spoofax.gradle.langspec")
        }

        group = "org.metaborg"
        version = "0.1.0"

        spoofax {
        }
      """.trimIndent())
    }
    calcLib.resolve("metaborg.yaml").apply {
      createNewFile()
      writeText("""
        ---
        id: org.metaborg:calc.lib:0.1.0
        name: calc.lib
      """.trimIndent())
    }

    GradleRunner.create()
      .withPluginClasspath()
      .withProjectDir(tempDir.root)
      .withArguments("--info", "--stacktrace", "buildAll")
      .forwardOutput()
      .build()
  }
}