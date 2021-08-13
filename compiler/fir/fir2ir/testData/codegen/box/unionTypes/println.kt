// WITH_RUNTIME

fun foo(arg: Int | String) {
    println(arg)
}

fun box(): String {
    try {
        throw IllegalStateException("All is well!")
    } catch (e: Exception | Error) {
        println(e.message)
    }

    foo(42)
    foo("Omega")

    var x: Int | Double = 42
    println(x)
    x = 3.14
    println(x)

    return "OK"
}