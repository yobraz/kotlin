data class Some(val first: Int, val second: Double, val third: String)

fun expressionTree(block: () -> Unit) {
    TODO("intrinsic")
}

fun foo(some: Some) = expressionTree {
    var (x, y, z: String) = some

    x++
    y *= 2.0
    z = ""
}

fun bar(some: Some) = expressionTree {
    val (a, _, `_`) = some
}