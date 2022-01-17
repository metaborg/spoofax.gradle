@file:Suppress("UnstableApiUsage")

package mb.spoofax.gradle.util

import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty

fun <T> Property<T>.finalizeAndGet(): T {
  finalizeValue()
  return get()
}

fun <T> ListProperty<T>.finalizeAndGet(): List<T> {
  finalizeValue()
  return get()
}

fun <T> SetProperty<T>.finalizeAndGet(): Set<T> {
  finalizeValue()
  return get()
}
