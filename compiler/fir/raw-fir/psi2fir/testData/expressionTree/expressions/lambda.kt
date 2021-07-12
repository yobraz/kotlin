// WITH_RUNTIME
data class Tuple(val x: Int, val y: Int)

fun expressionTree(block: () -> Unit) {
    TODO("intrinsic")
}

inline fun use(f: (Tuple) -> Int) = expressionTree { f(Tuple(1, 2)) }

fun foo() = expressionTree {
    val l1 = { t: Tuple ->
        val x = t.x
        val y = t.y
        x + y
    }
    use { (x, y) -> x + y }

    return@expressionTree use {
        if (it.x == 0) return@expressionTree 0
        return@use it.y
    }
}

fun bar() = expressionTree {
    return use lambda@{
        if (it.x == 0) return@expressionTree 0
        return@lambda it.y
    }
}

fun test(list: List<Int>) = expressionTree {
    val map = mutableMapOf<Int, String>()
    list.forEach { map.getOrPut(it, { mutableListOf() }) += "" }
}

val simple = expressionTree { { } }

val another = expressionTree { { 42 } }
