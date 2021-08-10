// !DIAGNOSTICS: -EXPERIMENTAL_IS_NOT_ENABLED
// WITH_RUNTIME

import kotlin.experimental.ExperimentalTypeInference

class Foo<A>

@OptIn(ExperimentalTypeInference::class)
fun <T> buildFoo(@BuilderInference builderAction: Foo<T>.() -> Unit): Foo<T> = Foo()

fun <K> Foo<K>.bar(x: Int = 1) {}

fun main() {
    val x = <!NEW_INFERENCE_NO_INFORMATION_FOR_PARAMETER!>buildFoo<!> {
        bar()
    }
}