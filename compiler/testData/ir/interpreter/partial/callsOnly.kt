const val constInt = 15

@CompileTimeCalculation
fun <T> echo(a: T) = a

@CompileTimeCalculation
fun compileTimeInt() = 42

fun notCompileTimeInt() = 0

@PartialEvaluation
inline fun singleCall(a: Int): Int {
    return a + 1
}

@PartialEvaluation
inline fun withSingleVariable(a: Int): Int {
    val b = a * 2
    return a + b
}

@PartialEvaluation
inline fun withCompileTimeCall(): Int {
    val b = compileTimeInt()
    return b
}

@PartialEvaluation
inline fun withNotCompileTimeCode(a: Int): Int {
    val b = a + notCompileTimeInt()
    return b
}

@PartialEvaluation
inline fun withNotCompileTimeArgs(): Int {
    val b = echo(notCompileTimeInt())
    val c = b * compileTimeInt()
    return c
}

val a = singleCall(constInt)
val b = withSingleVariable(constInt)
val c = withCompileTimeCall()
val d = withNotCompileTimeCode(constInt)
val e = withNotCompileTimeArgs()
