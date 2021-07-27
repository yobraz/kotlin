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
inline fun withBreak(limit: Int): Int {
    var x = 0
    while(true) {
        if(x < limit) x++ else break
    }
    return x
}

@PartialEvaluation
inline fun withInnerContinue(): Int {
    var cycles = 0
    var i = 1
    var j = 1
    L@while (i <= 5) {
        j = 1
        while (j <= 5) {
            if (i % 2 == 0) {
                i += 1
                continue@L
            }
            cycles += 1
            j += 1
        }
        i += 1
    }

    return cycles
}

val a1 = loop1(1, 2, 3)
val a2 = loop1(*nonConstArray())
//val b = loop2() // TODO need serialization/bodies for ranges
val c1 = withBreak(5)
val c2 = withInnerContinue()
