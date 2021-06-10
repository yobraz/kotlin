fun main() {
    val x: Unit = <!NEW_INFERENCE_NO_INFORMATION_FOR_PARAMETER!>materialize<!>() // 1
    <!NEW_INFERENCE_NO_INFORMATION_FOR_PARAMETER!>foo<!>(materialize()) // 2
    return <!NEW_INFERENCE_NO_INFORMATION_FOR_PARAMETER!>materialize<!>() // 3
}

fun foo(x: Unit) {}

fun <T> materialize(): T = TODO()
