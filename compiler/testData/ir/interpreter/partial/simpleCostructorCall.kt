const val constInt = 15

@CompileTimeCalculation
fun <T> echo(a: T) = a

@CompileTimeCalculation
fun compileTimeInt() = 42

fun notCompileTimeInt() = 0

@CompileTimeCalculation
class A(var a: Int) {
    fun multiplyBy2(): Int {
        a *= 2
    }
}

@PartialEvaluation
inline fun simpleReturn(obj: A): Int {
    return obj.a
}

@PartialEvaluation
inline fun simpleMutableVar(obj: A): Int {
    obj.a = compileTimeInt()
    return obj.a
}

@PartialEvaluation
inline fun simpleMethodCall(obj: A): Int {
    obj.multiplyBy2()
    return obj.a
}

val a = simpleReturn(A(constInt))
val b = simpleMutableVar(A(constInt))
val c = simpleMethodCall(A(constInt))