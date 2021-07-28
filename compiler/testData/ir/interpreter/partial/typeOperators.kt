@CompileTimeCalculation
interface Base
@CompileTimeCalculation
class A(var a: Int) : Base
@CompileTimeCalculation
class B(var b: Int) : Base
@CompileTimeCalculation
class C(var c: Int) : Base

@PartialEvaluation
inline fun whenWithInstanceCheck(obj: Base): Int {
    return when (obj) {
        is A -> obj.a
        is B -> obj.b
        is C -> obj.c
        else -> -1
    }
}

@PartialEvaluation
inline fun castToA(obj: Base): String {
    return (obj as? A)?.a?.toString()
}

val a1 = whenWithInstanceCheck(A(1))
val a2 = whenWithInstanceCheck(B(2))
val a3 = whenWithInstanceCheck(C(3))
val b1 = castToA(A(1))
val b2 = castToA(B(2))
