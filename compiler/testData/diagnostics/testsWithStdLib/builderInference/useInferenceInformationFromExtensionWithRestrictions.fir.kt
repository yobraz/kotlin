// !DIAGNOSTICS: -UNUSED_PARAMETER

// FILE: annotation.kt

package kotlin

annotation class BuilderInference

// FILE: test.kt

class GenericController<T> {
    suspend fun yield(t: T) {}
}

suspend fun <S> GenericController<S>.extensionYield(s: S) {}

@<!EXPERIMENTAL_API_USAGE_ERROR!>BuilderInference<!>
suspend fun <S> GenericController<S>.safeExtensionYield(s: S) {}

fun <S> generate(@<!EXPERIMENTAL_API_USAGE_ERROR!>BuilderInference<!> g: suspend GenericController<S>.() -> Unit): List<S> = TODO()

val normal = generate {
    yield(42)
}

val extension = generate {
    extensionYield("foo")
}

val safeExtension = generate {
    safeExtensionYield("foo")
}
