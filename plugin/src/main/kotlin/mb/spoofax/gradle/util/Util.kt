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

fun <T> Property<T>.finalizeAndGetOrNull(): T? {
    finalizeValue()
    return getOrNull()
}

fun <T> ListProperty<T>.finalizeAndGetOrNull(): List<T>? {
    finalizeValue()
    return getOrNull()
}

fun <T> SetProperty<T>.finalizeAndGetOrNull(): Set<T>? {
    finalizeValue()
    return getOrNull()
}
