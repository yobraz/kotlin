fun foo(x: Int | String): String {
    return when(x) {
        is Int -> x.toString()
        is String -> x
    }
}

fun box(): String {
    val x: Int | String = 5
    foo(x)
    foo(5)
    return foo("OK")
}