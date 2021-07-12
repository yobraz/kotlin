// WITH_RUNTIME
fun foo(limit: Int) = expressionTree {
    var k = 0
    some@ while (k < limit) {
        k++
        println(k)
        while (k == 13) {
            k++
            if (k < limit) break@some
            if (k > limit) continue
        }
    }
}

fun bar(limit: Int) = expressionTree {
    var k = limit
    do {
        k--
        println(k)
    } while (k >= 0)
}

fun expressionTree(block: () -> Unit) {
    TODO("intrinsic")
}