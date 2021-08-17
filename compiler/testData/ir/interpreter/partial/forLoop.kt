@CompileTimeCalculation
class A(val a: Int) {
    fun getValue() = a
}

fun nonConstArray() = arrayOf(1, 2, 3)

@PartialEvaluation
inline fun loop1(vararg array: Int): Int {
    var sum = 0
    for (i in array) {
        sum += i
    }
    return sum
}

@PartialEvaluation
inline fun loop2(): Int {
    var sum = 0
    for (i in 0..3) {
        val obj = A(i)
        sum += obj.getValue()
    }
    return sum
}

@PartialEvaluation
inline fun loop3(): Int {
    var sum = 0
    for (i in 0..10) {
        if (i >= 5) break
        sum += i
    }
    return sum
}

val a1 = loop1(1, 2, 3)
val a2 = loop1(*nonConstArray())
//val b = loop2() // TODO need serialization/bodies for ranges
//val c = loop3()
