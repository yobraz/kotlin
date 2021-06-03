// ERROR_POLICY: SEMANTIC

// MODULE: lib
// FILE: t.kt

fun bar(a: String, b: String): String

fun foo(): String {
    return bar("O", "K")
}

// MODULE: main(lib)
// FILE: b.kt

fun box(): String {
    try {
        val r = foo()
        if (r is String) return r
    } catch (e: Throwable) {
        return "OK"
    }
    return "FAIL 2"
}
