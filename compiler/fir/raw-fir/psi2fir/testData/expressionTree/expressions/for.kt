fun foo() = expressionTree {
    for (i in 1..10) {
        println(i)
    }
}

fun expressionTree(block: () -> Unit) {
    TODO("intrinsic")
}

fun fooLabeled() = expressionTree {
    println("!!!")
    label@ for (i in 1..10) {
        if (i == 5) continue@label
        println(i)
    }
    println("!!!")
}

fun bar(list: List<String>) = expressionTree {
    for (element in list.subList(0, 10)) {
        println(element)
    }
    for (element in list.subList(10, 20)) println(element)
}

data class Some(val x: Int, val y: Int)

fun baz(set: Set<Some>) = expressionTree {
    for ((x, y) in set) {
        println("x = $x y = $y")
    }
}

fun withParameter(list: List<Some>) = expressionTree {
    for (s: Some in list) {
        println(s)
    }
}
