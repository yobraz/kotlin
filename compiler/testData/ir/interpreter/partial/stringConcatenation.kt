const val constInt = 15

fun notCompileTimeInt() = 0

@CompileTimeCalculation
class A(val a: Int) {
    override fun toString(): String {
        return "A=$a"
    }
}

@PartialEvaluation
inline fun simpleConcatenation(number: Int): String {
    return "($number)"
}

@PartialEvaluation
inline fun concatenationWithConstructorCall(number: Int): String {
    return "(${A(number)})"
}

val a1 = simpleConcatenation(constInt)
val a2 = simpleConcatenation(notCompileTimeInt())
val b1 = concatenationWithConstructorCall(constInt)
val b2 = concatenationWithConstructorCall(notCompileTimeInt())