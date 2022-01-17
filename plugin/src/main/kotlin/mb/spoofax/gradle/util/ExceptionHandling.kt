package mb.spoofax.gradle.util

import org.gradle.api.Task
import org.gradle.api.tasks.TaskContainer
import org.gradle.api.tasks.TaskProvider
import org.gradle.kotlin.dsl.*
import kotlin.reflect.KClass

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

fun <T : Task> TaskContainer.registerSafely(name: String, type: KClass<T>, fn: T.() -> Unit): TaskProvider<T> {
  return register(name, type) {
    try {
      fn(this)
    } catch(e: Throwable) {
      e.printStackTrace()
      throw e
    }
  }
}
