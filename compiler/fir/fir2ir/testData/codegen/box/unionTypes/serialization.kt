// MODULE: lib
// FILE: A.kt

fun foo(): Int | String = 5

// MODULE: main(lib)
// FILE: B.kt

fun box() = when (val x = foo()) {
    is Int -> "OK"
    is String -> "Fail"
}