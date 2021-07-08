const val constInt = 15

@CompileTimeCalculation
fun <T> echo(a: T) = a

@CompileTimeCalculation
fun compileTimeInt() = 42

fun notCompileTimeInt() = 0

@CompileTimeCalculation
class A(var a: Int)

@PartialEvaluation
inline fun simpleWhen(): Boolean {
    return when (constInt) {
        15 -> true
        else -> false
    }
}

@PartialEvaluation
inline fun simpleWhen2(): Boolean {
    return when (constInt) {
        -1 -> false
        -2 -> false
        15 -> true
        else -> false
    }
}

@PartialEvaluation
inline fun rollbackAfterWhen(): Int {
    var a = 0
    when (compileTimeInt()) {
        0 -> { a += 1 }
        1 -> { a += 2 }
        2 -> { a += 3 }
        else -> { a += 4 }
    }
    return a
}

@PartialEvaluation
inline fun rollbackFieldAfterWhen(obj: A): Int {
    when (compileTimeInt()) {
        0 -> { obj.a += 1 }
        1 -> { obj.a += 2 }
        2 -> { obj.a += 3 }
        else -> { obj.a += 4 }
    }
    return obj.a
}

@PartialEvaluation
inline fun optimizeAll(flag: Boolean): Int {
    var a = 0
    when (notCompileTimeInt()) {
        0 -> {
            if (flag) a = constInt() else a = -1
        }
        else -> {
            a = if (flag) constInt() else -1
        }
    }
    return a // must not be set to zero
}

val a = simpleWhen()
val b = simpleWhen2()
val c = rollbackAfterWhen()
val d = rollbackFieldAfterWhen(A(0))
val e = optimizeAll(true)