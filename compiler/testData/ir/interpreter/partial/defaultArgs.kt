const val constInt = 15

fun notCompileTimeInt() = 0

@PartialEvaluation
inline fun funWithDefaults(a: Int, b: Int = a * 2): Int {
    return b
}

@CompileTimeCalculation
fun bar(a: Int, b: Int = a * 2): Int {
    return b
}

@PartialEvaluation
inline fun funWithCallWithDefaults(a: Int): Int {
    return bar(a)
}

val a = funWithDefaults(10)
val b = funWithCallWithDefaults(10)

