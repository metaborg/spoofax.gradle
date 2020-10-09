package mb.spoofax.gradle.util

import org.gradle.api.Task
import org.gradle.api.tasks.TaskContainer
import org.gradle.api.tasks.TaskProvider

fun <T : Task> TaskProvider<T>.configureSafely(fn: T.() -> Unit) {
  configure {
    try {
      this.fn()
    } catch(e: Throwable) {
      e.printStackTrace()
      throw e
    }
  }
}

fun TaskContainer.registerSafely(name: String, fn: Task.() -> Unit): TaskProvider<Task> {
  return register(name) {
    try {
      this.fn()
    } catch(e: Throwable) {
      e.printStackTrace()
      throw e
    }
  }
}
