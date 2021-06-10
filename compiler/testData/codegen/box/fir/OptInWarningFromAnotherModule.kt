// !USE_EXPERIMENTAL: kotlin.RequiresOptIn
// TARGET_BACKEND: JVM
// WITH_RUNTIME

// MODULE: api
// FILE: api.kt

@RequiresOptIn(level = RequiresOptIn.Level.WARNING, message = "This API might change in the future")
annotation class ExperimentalGradleToolingApi

@ExperimentalGradleToolingApi
fun String.getCompilations(): Set<String> = setOf("OK", this)

// MODULE: test(api)
// FILE: test.kt

fun box(): String {
    return "FAIL".getCompilations().first()
}
